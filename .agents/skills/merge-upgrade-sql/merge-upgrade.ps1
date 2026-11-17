# =============================================================================
# merge-upgrade.ps1 - airedge 发版升级脚本合并流水线 (skill 宿主侧驱动)
#
# 流程 (与 2026-09-20 PoC 一致):
#   1. base 库  : 空库经 Go migrate 重放到上一发版终态 (V<from-max>) -> pg_dump 快照
#   2. seq 库   : 快照恢复 -> Go migrate 继续执行目标目录全部 0.x -> "逐脚本执行真值库"
#   3. 生成     : dbdiff 双库对比 (server1=seq 基准, server2=base) -> 截取 UP 段 + manifest
#   4. ver 库   : 快照恢复 -> 执行合并脚本
#   5. 收敛循环 : postgrelcomparer(ver vs seq) <=3 轮, 差异作为补丁回灌
#                 (自动处理 ALTER TYPE 的视图依赖: 先 DROP 相关视图, 补后从基准库重建)
#   6. 盲区同步 : 视图定义/视图注释从 seq 真值库同步 (postgrelcomparer 只比基表, dbdiff 不生成 COMMENT)
#   7. 终审     : pg_dump --schema-only 归一化 (剥 --/\restrict/空行) MD5 比对
#   8. 落仓     : V<to>__upgradesql.sql 写入目标版本目录 + 0.x 归档到 migration_archive/
#
# 产物结构 (自顶向下, 顺序即执行顺序):
#   1) 固定头 00_header.sql : 逐字固定, 置于产物最前 (默认: 动态 DROP public.air_inst_view_ws_* 视图)
#   2) manifest             : 合并来源清单 + sha256
#   3) 合并正文 gen_up.sql  : dbdiff 双库对比生成的语义合并结果
#   4) 验证补丁             : 收敛循环补丁 + 视图/注释同步补丁
#   5) fix 脚本             : 文件名含 fix_upgradesql 的脚本不参与语义合并,
#                            按原文件逐字附加于产物末尾 (STEP 6.8, 幂等复跑校验)
#
# 依赖 (默认直接拉阿里云固定版本, 可用参数覆盖):
#   registry.cn-hangzhou.aliyuncs.com/plm/dbdiff:3.0.0-rc.12          (对比生成, 钉住已验证版本)
#   registry.cn-hangzhou.aliyuncs.com/plm/postgrelcomparer:distroless (收敛验证, 仅 arm64)
#   registry.cn-hangzhou.aliyuncs.com/plm/postgres16:16-alpine        (psql/pg_dump/createdb)
#   registry.cn-hangzhou.aliyuncs.com/plm/binfmt:latest               (仅 amd64 宿主跑 comparer 时需要)
# backend/upgrade/bin/migrate_windows_amd64.exe
#
# 用法示例:
#   .\merge-upgrade.ps1 -Folder versionupdates.v3.7 `
#     -PgBase 'postgres://airedge:air20220401@127.0.0.1:5432/postgres'
# =============================================================================
param(
    [Parameter(Mandatory = $true)][string]$Folder,      # 目标版本目录, 如 versionupdates.v3.7
    [Parameter(Mandatory = $true)][string]$PgBase,      # 管理连接串(含库, 任意可 createdb 的库), 如 postgres://user:pass@127.0.0.1:5432/postgres
    [string]$RepoUpgrade = "",                          # backend/upgrade 根目录; 默认按脚本位置自动推断
    [switch]$NoArchive,                                 # 不归档 0.x 脚本 (默认执行归档)
    [switch]$Keep,                                      # 成功后也保留工作库 (排查用)
    [int]$MaxRounds = 3,                                # 收敛循环轮数上限
    [string]$HeaderFile = "",                           # 产物固定头文件; 省略则使用内置默认头 ($HeaderSqlDefault)
    [string]$FixPattern = 'fix_upgradesql',             # 不参与合并、末尾原样附加的脚本名特征
    # ---- 镜像 (默认阿里云固定版本; 本地旧名字会自动回退并提示改名) ----
    [string]$PgImage = 'registry.cn-hangzhou.aliyuncs.com/plm/postgres16:16-alpine',               # psql / pg_dump / createdb / dropdb
    [string]$ComparerImage = 'registry.cn-hangzhou.aliyuncs.com/plm/postgrelcomparer:distroless',  # 收敛验证引擎 (仅 arm64)
    [string]$DbdiffImage = 'registry.cn-hangzhou.aliyuncs.com/plm/dbdiff:3.0.0-rc.12',             # 钉住已验证版本 (方向语义/撇号转义均基于 rc.12 实测)
    [string]$BinfmtImage = 'registry.cn-hangzhou.aliyuncs.com/plm/binfmt:latest'                   # 仅 amd64 宿主跑 arm64 comparer 时用
)

$ErrorActionPreference = 'Stop'
$ExcludeTables = 'public.schema_migrations,public.flyway_schema_history'

# Docker Desktop 自动解析 host.docker.internal; 但若 engine 是远程/WSL 端点 (docker context 非 default)
# 则容器内无法解析。这里统一显式映射到宿主网关, Docker Desktop 下同样安全。
$DockerNetArgs = @('--add-host', 'host.docker.internal:host-gateway')

# 挂载路径形式: Docker Desktop 用 Windows 路径; 远程/WSL Linux engine 用 WSL 风格 /mnt/<drive>/...
# 启动时自动探测 (见 STEP 0 末尾), 两个都不用担心。
$script:UseWslMount = $false
function Get-ContainerMountPath([string]$WinPath) {
    if (-not $script:UseWslMount) { return $WinPath }
    $p = $WinPath -replace '\\', '/'
    if ($p -match '^([A-Za-z]):/(.*)$') { return "/mnt/$($Matches[1].ToLower())/$($Matches[2])" }
    return $p
}

# ---------- 产物固定头 (置于合并产物最前, 逐字保留, 不参与语义合并) ----------
$HeaderSqlDefault = @'
DO $$
DECLARE
v_view RECORD;
    v_sql TEXT;
BEGIN
FOR v_view IN (
        SELECT schemaname, viewname
        FROM pg_views
        WHERE viewname LIKE 'air_inst_view_ws_%'
          AND schemaname = 'public' -- 同样，请替换为实际的 schema
    ) LOOP
        -- 拼接删除语句
        v_sql := 'DROP VIEW IF EXISTS ' || v_view.schemaname || '.' || v_view.viewname;

        -- 执行删除
EXECUTE v_sql;

-- 打印执行日志（可选）
RAISE NOTICE '已删除视图: %.%', v_view.schemaname, v_view.viewname;
END LOOP;
END $$;
'@

# ---------- 路径推断 ----------
if (-not $RepoUpgrade) {
    $skillRoot = Split-Path -Parent $MyInvocation.MyCommand.Path   # <repo>\.agents\skills\merge-upgrade-sql
    $RepoUpgrade = (Resolve-Path (Join-Path $skillRoot '..\..\..\backend\upgrade')).Path
}
$MigRoot = Join-Path $RepoUpgrade 'src\main\resources\flyway\migration'
if (-not (Test-Path (Join-Path $MigRoot $Folder))) { throw "目标版本目录不存在: $MigRoot\$Folder" }
$MigrateExe = Join-Path $RepoUpgrade 'bin\migrate_windows_amd64.exe'
if (-not (Test-Path $MigrateExe)) { throw "找不到 migrate 工具: $MigrateExe" }

# ---------- 依赖镜像: 解析 + 可用性探活 ----------
# 默认引用阿里云固定版本; 本地若只有旧名字 (ghcr.io / docker hub) 则自动回退并提示改名。
$LegacyPgImage = 'postgres:16-alpine'
$LegacyComparerImage = 'fancylk/postgrelcomparer:distroless'
$LegacyDbdiffImage = 'ghcr.io/dbdiff/dbdiff:3.0.0-rc.12'

function Resolve-Image([string]$Image, [string]$Legacy, [string]$PullHint) {
    $eap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try {
        docker image inspect $Image *> $null
        if ($LASTEXITCODE -eq 0) { return $Image }
        if ($Legacy -and $Legacy -ne $Image) {
            docker image inspect $Legacy *> $null
            if ($LASTEXITCODE -eq 0) {
                Write-Warning "本地缺少 $Image -> 回退使用 $Legacy (建议: docker tag $Legacy $Image)"
                return $Legacy
            }
        }
    } finally { $ErrorActionPreference = $eap }
    throw "缺少镜像: $Image`n  拉取并改成期望名字:`n$PullHint"
}

$PgImage = Resolve-Image $PgImage $LegacyPgImage "  docker pull $PgImage`n  docker tag $PgImage $LegacyPgImage"
$ComparerImage = Resolve-Image $ComparerImage $LegacyComparerImage "  docker pull $ComparerImage`n  docker tag $ComparerImage $LegacyComparerImage"
$DbdiffImage = Resolve-Image $DbdiffImage $LegacyDbdiffImage "  docker pull $DbdiffImage`n  docker tag $DbdiffImage $LegacyDbdiffImage"

# 三个镜像各跑一次最小探活 (以退出码为准; 输出捕获仅用于给出平台不匹配提示)
$eapImg = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
try {
    docker run --rm $PgImage sh -c 'psql --version >/dev/null 2>&1 && pg_dump --version >/dev/null 2>&1' *> $null
    if ($LASTEXITCODE -ne 0) { throw "镜像 $PgImage 不可用 (缺 psql/pg_dump)" }

    docker run --rm $DbdiffImage --version *> $null
    if ($LASTEXITCODE -ne 0) { throw "镜像 $DbdiffImage 不可用 (--version 执行失败, 请确认钉的是 3.0.0-rc.12)" }

    $cmpOut = docker run --rm $ComparerImage --help 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        $hint = "comparer 镜像无法在本机执行 (rc=$LASTEXITCODE)。若为平台不匹配 (官方仅 arm64 构建), amd64 宿主需先装 qemu 模拟器:`n" +
                "  docker pull $BinfmtImage`n  docker tag $BinfmtImage tonistiigi/binfmt:latest`n" +
                '  docker run --privileged --rm tonistiigi/binfmt:latest --install arm64'
        if ($cmpOut -match 'exec format error') { throw $hint }
        throw ("镜像 $ComparerImage 不可用 (rc=$LASTEXITCODE)`n$cmpOut`n$hint")
    }

    # 架构提示 (best-effort, 捕获不到就跳过)
    $hostArch = (((docker version --format '{{.Server.Arch}}' 2>$null) -join '')).Trim()
    $cmpArch = (((docker image inspect --format '{{.Architecture}}' $ComparerImage 2>$null) -join '')).Trim()
    if ($hostArch -and $cmpArch -and $hostArch -ne $cmpArch) {
        Write-Host "  提示: comparer 架构 $cmpArch 与宿主 $hostArch 不同, 依赖 qemu 模拟 (binfmt: $BinfmtImage)" -ForegroundColor DarkYellow
    }
    Write-Host "  镜像就绪: pg=$PgImage | comparer=$ComparerImage | dbdiff=$DbdiffImage" -ForegroundColor DarkGray
} finally { $ErrorActionPreference = $eapImg }

# ---------- 连接串解析 ----------
if ($PgBase -notmatch '^postgres://(?<u>[^:]+):(?<p>[^@]+)@(?<h>[^:/]+):(?<port>\d+)(?<rest>.*)$') {
    throw "PgBase 连接串格式应为 postgres://user:pass@host:port/db"
}
$PgUser = $Matches['u']; $PgPass = $Matches['p']; $PgHostRaw = $Matches['h']; $PgPort = $Matches['port']
$ContainerHost = if ($PgHostRaw -in @('127.0.0.1', 'localhost')) { 'host.docker.internal' } else { $PgHostRaw }
$HostForMigrate = if ($PgHostRaw -in @('127.0.0.1', 'localhost')) { '127.0.0.1' } else { $PgHostRaw }
$BaseUrlNoDb = "postgres://${PgUser}:${PgPass}@${PgHostRaw}:${PgPort}"           # 宿主侧(migrate.exe)
$ContainerUrlNoDb = "postgres://${PgUser}:${PgPass}@${ContainerHost}:${PgPort}"  # 容器侧(对比/验证工具)

# ---------- 工具函数 ----------
function Write-Step([string]$Msg) { Write-Host "`n=== $Msg" -ForegroundColor Cyan }
function Write-Detail([string]$Msg) { Write-Host "  $Msg" }

# Flyway 版本排序 key: "3.6.0" -> 定长分段数字串
function Get-VersionKey([string]$V) {
    $segs = $V -split '\.'
    $out = ''
    for ($i = 0; $i -lt 5; $i++) { $out += ('{0:D5}.' -f [int]($segs[$i] ?? '0')) }
    return $out.TrimEnd('.')
}

# 列出目录下全部 V*.sql, 输出 [pscustomobject]@{Version; Path; Key} 按 Flyway 序
function Get-VersionedScripts([string]$Dir) {
    Get-ChildItem $Dir -Recurse -Filter 'V*.sql' -File | ForEach-Object {
        $b = $_.Name; $v = $b.Substring(1); $v = $v.Substring(0, $v.IndexOf('__'))
        [pscustomobject]@{ Version = $v; Path = $_.FullName; Key = Get-VersionKey $v }
    } | Sort-Object Key
}

# docker 化 psql (默认不吞 stderr; -q 静默由调用方决定)
function Invoke-Psql([string]$DbName, [string[]]$PsqlArgs) {
    docker run --rm @DockerNetArgs -e PGPASSWORD="$PgPass" -v "${MountWorkDir}:/workmnt" $PgImage `
        psql -h $ContainerHost -p $PgPort -U $PgUser -d $DbName @PsqlArgs
    if ($LASTEXITCODE -ne 0) { throw "psql 失败 (db=$DbName args=$($PsqlArgs -join ' '))" }
}
function Invoke-PsqlText([string]$DbName, [string]$Sql) {   # 返回文本输出, 不抛错
    docker run --rm @DockerNetArgs -e PGPASSWORD="$PgPass" $PgImage `
        psql -h $ContainerHost -p $PgPort -U $PgUser -d $DbName -t -A -c $Sql
}
# 执行工作目录下的 SQL 文件 (相对 $WorkDir)。
# 为何不用 Invoke-Psql: 本环境下直接 docker run psql(作为容器 PID1) 时 stderr 会丢失 (实测),
# 出错时看不到原因。改为容器内 sh -c 把 stderr 重定向到挂载目录, 失败时在宿主侧回显尾部。
function Invoke-SqlFile([string]$DbName, [string]$FileName) {
    $errRel = "_psql_$([System.IO.Path]::GetFileNameWithoutExtension($FileName)).err"
    $inner = "psql -h $ContainerHost -p $PgPort -U $PgUser -d $DbName -v ON_ERROR_STOP=1 -q -o /dev/null " +
             "-f /workmnt/$FileName 2>/workmnt/$errRel"
    docker run --rm @DockerNetArgs -e PGPASSWORD="$PgPass" -e PGCLIENTENCODING=UTF8 `
        -v "${MountWorkDir}:/workmnt" $PgImage sh -c $inner
    if ($LASTEXITCODE -ne 0) {
        $errPath = Join-Path $WorkDir $errRel
        if (Test-Path $errPath) {
            Write-Host "  psql 错误详情 ($FileName):" -ForegroundColor Yellow
            Get-Content $errPath | Select-Object -Last 15 | ForEach-Object { Write-Host "    $_" -ForegroundColor Yellow }
        }
        throw "psql 执行失败: $FileName (rc=$LASTEXITCODE, err=$errPath)"
    }
}

function Invoke-DropDb([string]$DbName) {
    docker run --rm @DockerNetArgs -e PGPASSWORD="$PgPass" $PgImage `
        dropdb -h $ContainerHost -p $PgPort -U $PgUser --force $DbName 2>$null
    if ($LASTEXITCODE -gt 1) { throw "dropdb 失败: $DbName" }
}
function Invoke-CreateDb([string]$DbName) {
    docker run --rm @DockerNetArgs -e PGPASSWORD="$PgPass" $PgImage `
        createdb -h $ContainerHost -p $PgPort -U $PgUser $DbName
    if ($LASTEXITCODE -ne 0) { throw "createdb 失败: $DbName" }
}
function Invoke-Dump([string]$DbName, [string]$OutFile, [switch]$SchemaOnly) {
    $extra = @(); if ($SchemaOnly) { $extra += '--schema-only' }
    docker run --rm @DockerNetArgs -e PGPASSWORD="$PgPass" -v "${MountWorkDir}:/workmnt" $PgImage `
        pg_dump -h $ContainerHost -p $PgPort -U $PgUser -d $DbName --no-owner --no-privileges @extra -f "/workmnt/$OutFile"
    if ($LASTEXITCODE -ne 0) { throw "pg_dump 失败: $DbName" }
}
function Restore-FromSnapshot([string]$DbName) {
    # -o /dev/null: 屏蔽快照中 setval/set_config 语句的结果输出
    Invoke-SqlFile $DbName 'snapshot.sql'
}
function Invoke-Migrate([string[]]$MigrateArgs, [string]$LogFile) {
    $eap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'  # 2>&1 下 stderr 行不应触发 Stop
    try { & $MigrateExe @MigrateArgs 2>&1 | Tee-Object -FilePath $LogFile | Out-Null } finally { $ErrorActionPreference = $eap }
    if ($LASTEXITCODE -ne 0) { throw "migrate 执行失败 (log=$LogFile)" }
}

# psql 逐文件回放 (自动提交): 目标目录脚本的真实回放方式。
# 为何需要: Go migrate 把整个文件当一条 multi-statement 简单查询发出 -> PG 隐式事务,
#   CREATE INDEX CONCURRENTLY / REINDEX / VACUUM / ALTER SYSTEM / CREATE|DROP DATABASE|TABLESPACE|SUBSCRIPTION
#   会报 "cannot run inside a transaction block"。而产品侧的 Flyway 7 PostgreSQLParser
#   .detectCanExecuteInTransaction() 明确把这些语句排除在事务外 (已反编译确认), 即产品能跑。
# psql -f 逐语句自动提交 -> 与 Flyway 语义一致。
# 差异: 不写 flyway_schema_history (该表在 postgrelcomparer 与 pg_dump 终审中都不参与行对比)。
function Invoke-SequentialReplay([string]$DbName, $Scripts, [string]$LogFile) {
    $eap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $log = @("-- sequential replay (psql, autocommit): $($Scripts.Count) files @ $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    try {
        foreach ($s in $Scripts) {
            $leaf = Split-Path $s.Path -Leaf
            $rel = '/' + ($s.Path.Substring($MigRoot.Length + 1).Replace('\', '/'))
            $eapInner = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
            try {
                $out = docker run --rm @DockerNetArgs -e PGPASSWORD="$PgPass" -e PGCLIENTENCODING=UTF8 `
                    -v "$(Get-ContainerMountPath $MigRoot):/mig" $PgImage `
                    psql -h $ContainerHost -p $PgPort -U $PgUser -d $DbName -v ON_ERROR_STOP=1 -q -o /dev/null -f "/mig$rel" 2>&1
                $rc = $LASTEXITCODE
            } finally { $ErrorActionPreference = $eapInner }
            $log += "apply $leaf exit=$rc"
            if ($out) { $log += @($out) }
            if ($rc -ne 0) { throw "psql 回放失败: $leaf (log=$LogFile)" }
            Write-Detail "  applied $leaf"
        }
        $log += '-- OK'
    } finally {
        Set-Content -Path $LogFile -Value $log -Encoding UTF8
        $ErrorActionPreference = $eap
    }
}

# 视图依赖闭包: 返回 "层级|视图名" 行集合
function Get-ViewDeps([string]$DbName, [string[]]$Tables) {
    if (-not $Tables -or $Tables.Count -eq 0) { return @() }
    $arr = ($Tables | ForEach-Object { "'$_'" }) -join ','
    $sql = @"
WITH RECURSIVE d(lv, viewname) AS (
  SELECT 1, c.relname::text FROM pg_depend dg
    JOIN pg_rewrite rw ON rw.oid = dg.objid
    JOIN pg_class c ON c.oid = rw.ev_class
    JOIN pg_class t ON t.oid = dg.refobjid
    JOIN pg_namespace n ON n.oid = t.relnamespace
   WHERE n.nspname='public' AND t.relname = ANY(ARRAY[$arr])
     AND c.relkind='v' AND dg.classid='pg_rewrite'::regclass AND c.relname <> t.relname
  UNION
  SELECT d.lv+1, c2.relname::text FROM d
    JOIN pg_depend dg ON dg.refobjid = ('public."' || d.viewname || '"')::regclass
    JOIN pg_rewrite rw ON rw.oid = dg.objid
    JOIN pg_class c2 ON c2.oid = rw.ev_class
   WHERE c2.relkind='v' AND dg.classid='pg_rewrite'::regclass AND c2.relname <> d.viewname
)
SELECT DISTINCT lv, viewname FROM d ORDER BY lv, viewname;
"@
    $out = Invoke-PsqlText $DbName $sql
    return @($out | Where-Object { $_ -match '\|' })
}

# 从 seq 库取视图定义+注释, 生成重建 SQL 行
function Get-ViewRecreateSql([string]$ViewName) {
    $def = (Invoke-PsqlText $DbSeq "select definition from pg_views where schemaname='public' and viewname='$ViewName';") -join ' '
    $lines = @("CREATE OR REPLACE VIEW `"public`".`"$ViewName`" AS $def;")
    $cmt = Invoke-PsqlText $DbSeq ("select 'COMMENT ON VIEW `"public`".`"$ViewName`" IS ' || quote_nullable(d.description) || ';' " +
        "from pg_class c join pg_description d on d.objoid=c.oid and d.objsubid=0 " +
        "where c.relname='$ViewName' and c.relnamespace='public'::regnamespace and c.relkind='v';")
    if ($cmt) { $lines += @($cmt) }
    return $lines
}

# 取 表.列 -> attnum 映射 (仅 public 普通列)
function Get-AttnumMap([string]$DbName, [string[]]$Tables) {
    $map = @{}
    if (-not $Tables -or $Tables.Count -eq 0) { return $map }
    $arr = ($Tables | Sort-Object -Unique | ForEach-Object { "'$_'" }) -join ','
    $sql = "select c.relname||'.'||a.attname||'='||a.attnum from pg_attribute a " +
           "join pg_class c on c.oid=a.attrelid join pg_namespace n on n.oid=c.relnamespace " +
           "where n.nspname='public' and a.attnum>0 and not a.attisdropped and c.relname = ANY(ARRAY[$arr])"
    foreach ($l in @(Invoke-PsqlText $DbName $sql)) { if ($l -match '^(.+)=(\d+)$') { $map[$Matches[1]] = [int]$Matches[2] } }
    return $map
}

# 取某列的 类型 | 是否 NOT NULL | 默认值表达式 (真值以数据库为准, 不做 SQL 文本解析)
function Get-ColumnAttrs([string]$DbName, [string]$Table, [string]$Column) {
    $sql = "select format_type(a.atttypid,a.atttypmod)||'|'||a.attnotnull||'|'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') " +
           "from pg_attribute a left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum " +
           "where a.attrelid='public.$Table'::regclass and a.attname='$Column' and not a.attisdropped"
    $p = ((Invoke-PsqlText $DbName $sql) -join '') -split '\|'
    return @{ Type = $p[0]; NotNull = ($p[1] -eq 't'); Default = $p[2] }
}

# dbdiff 会把「RENAME COLUMN」表达成 DROP COLUMN + ADD COLUMN; 而 PostgreSQL 的 ADD COLUMN **不复用**
# 被删列释放的 attnum 槽位, 于是合并产物的物理列序与逐脚本终态不一致 (v3.8 实证: seq 的
# execute_strictly_in_order@187 来自 V3.8.0.17 的 RENAME COLUMN is_sequential, ver 里却变成新增列@207)。
# postgrelcomparer 不比列序, 只有 pg_dump 终审会暴露。
# 判据: 同表内, seq 中新增列的 attnum == base 中被删列的 attnum => 原脚本是 RENAME COLUMN。
# 处理: 把该 ADD 与 DROP 两行替换为 RENAME + 必要的属性调整 (类型/默认值/NOT NULL 均取自 seq 真值库)。
function Repair-RenamedColumns([string]$SqlFile, [string]$DbSeqName, [string]$DbBaseName) {
    $lines = @(Get-Content $SqlFile)
    $adds = @{}; $drops = @{}
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $l = $lines[$i]
        if ($l -match '^ALTER TABLE (?:"public"\.)?"([^"]+)" ADD COLUMN "([^"]+)"') {
            $t = $Matches[1]; if (-not $adds.ContainsKey($t)) { $adds[$t] = @() }
            $adds[$t] += @{ Idx = $i; Col = $Matches[2] }
        } elseif ($l -match '^ALTER TABLE (?:"public"\.)?"([^"]+)" DROP COLUMN (?:IF EXISTS )?"([^"]+)"') {
            $t = $Matches[1]; if (-not $drops.ContainsKey($t)) { $drops[$t] = @() }
            $drops[$t] += @{ Idx = $i; Col = $Matches[2] }
        }
    }
    $tables = @($adds.Keys | Where-Object { $drops.ContainsKey($_) })
    if ($tables.Count -eq 0) { return 0 }
    $seqMap = Get-AttnumMap $DbSeqName $tables
    $baseMap = Get-AttnumMap $DbBaseName $tables
    $replace = @{}; $removed = @{}
    $count = 0
    foreach ($t in $tables) {
        foreach ($a in $adds[$t]) {
            $akey = "$t.$($a.Col)"
            if (-not $seqMap.ContainsKey($akey)) { continue }
            foreach ($d in $drops[$t]) {
                $dkey = "$t.$($d.Col)"
                if ($removed.ContainsKey($d.Idx)) { continue }
                if (-not $baseMap.ContainsKey($dkey) -or $baseMap[$dkey] -ne $seqMap[$akey]) { continue }
                $b = Get-ColumnAttrs $DbBaseName $t $d.Col
                $s = Get-ColumnAttrs $DbSeqName $t $a.Col
                $block = @("ALTER TABLE `"$t`" RENAME COLUMN `"$($d.Col)`" TO `"$($a.Col)`";")
                if ($s.Type -ne $b.Type) { $block += "ALTER TABLE `"$t`" ALTER COLUMN `"$($a.Col)`" TYPE $($s.Type);" }
                if ($s.Default -ne $b.Default) {
                    if ($s.Default) { $block += "ALTER TABLE `"$t`" ALTER COLUMN `"$($a.Col)`" SET DEFAULT $($s.Default);" }
                    else { $block += "ALTER TABLE `"$t`" ALTER COLUMN `"$($a.Col)`" DROP DEFAULT;" }
                }
                if ($s.NotNull -ne $b.NotNull) {
                    if ($s.NotNull) { $block += "ALTER TABLE `"$t`" ALTER COLUMN `"$($a.Col)`" SET NOT NULL;" }
                    else { $block += "ALTER TABLE `"$t`" ALTER COLUMN `"$($a.Col)`" DROP NOT NULL;" }
                }
                $where = [Math]::Min($a.Idx, $d.Idx)
                $replace[$where] = $block
                $removed[[int]$a.Idx] = $true
                $removed[[int]$d.Idx] = $true
                $count++
                Write-Detail "列重命名还原: $t.$($d.Col) -> $($a.Col) (attnum=$($seqMap[$akey]))"
                break
            }
        }
    }
    if ($count -eq 0) { return 0 }
    $out = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($replace.ContainsKey($i)) { $out += $replace[$i] }
        if ($removed.ContainsKey($i)) { continue }
        $out += $lines[$i]
    }
    Set-Content -Path $SqlFile -Value $out -Encoding UTF8
    return $count
}

# 应用补丁(含视图依赖 prepass), 输出自包含生效补丁文件
# 从 SQL 文件提取 ALTER COLUMN TYPE 涉及的表名 (dbdiff 输出通常不带 schema 限定, 正则两者兼容)
function Get-AlteredTables([string]$File) {
    return @(Select-String -Path $File -Pattern 'ALTER TABLE (?:"public"\.)?"([^"]+)" ALTER COLUMN "[^"]+" TYPE' -AllMatches |
        ForEach-Object { $_.Matches } | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
}

# 索引 DDL 容错归一化 (硬性规范): 产物里每个 CREATE [UNIQUE] INDEX 都改写成"先删后建"。
# 为何必须:
#   * CREATE INDEX IF NOT EXISTS —— 目标库已有同名索引(定义可能不同)时会**静默跳过**,
#     产物终态与验证态不一致;
#   * 裸 CREATE INDEX —— 已存在则直接报 relation already exists, 不可重复执行;
#   * CREATE INDEX CONCURRENTLY —— 不能出现在事务内 (Go migrate 整文件执行必失败),
#     且产物是单脚本、没有并发建索引的必要。
# 约束背书的唯一索引不适用 DROP INDEX (会报“被约束依赖”), 那类对象用 DROP CONSTRAINT + ADD CONSTRAINT;
# dbdiff / verifier 输出的 CREATE INDEX 均为普通索引, 不会误伤。
function Repair-IndexDdl([string]$SqlFile) {
    $lines = @(Get-Content $SqlFile)
    $out = @(); $n = 0
    foreach ($l in $lines) {
        if ($l -match '^CREATE\s+(?<uniq>UNIQUE\s+)?INDEX\s+(?:CONCURRENTLY\s+)?(?:IF NOT EXISTS\s+)?(?<name>\S+)\s+(?<tail>ON\s.*)$') {
            $out += "DROP INDEX IF EXISTS $($Matches['name']);"
            $out += ("CREATE {0}INDEX {1} {2}" -f $Matches['uniq'], $Matches['name'], $Matches['tail'])
            $n++
        } else { $out += $l }
    }
    if ($n -gt 0) { Set-Content -Path $SqlFile -Value $out -Encoding UTF8 }
    return $n
}

function Apply-Patch([string]$PatchFile) {
    $tables = Get-AlteredTables $PatchFile
    $effective = $PatchFile + '.effective.sql'
    if ($tables.Count -eq 0) {
        Copy-Item $PatchFile $effective -Force; [void](Repair-IndexDdl $effective); return $effective
    }
    $deps = Get-ViewDeps $DbVer $tables
    if ($deps.Count -eq 0) {
        Copy-Item $PatchFile $effective -Force; [void](Repair-IndexDdl $effective); return $effective
    }
    Write-Detail ("prepass: 临时解除视图依赖: " + (($deps | ForEach-Object { ($_ -split '\|')[1] }) -join ' '))
    $dropLines = @($deps | Sort-Object { [int](($_ -split '\|')[0]) } -Descending |
        ForEach-Object { 'DROP VIEW IF EXISTS "public"."{0}";' -f (($_ -split '\|')[1]) })
    $createLines = @()
    foreach ($dep in ($deps | Sort-Object { [int](($_ -split '\|')[0]) })) {
        $createLines += Get-ViewRecreateSql (($dep -split '\|')[1])
    }
    $content = @('-- (prepass: 视图依赖解除)') + $dropLines + @('-- (补丁正文)') +
        (Get-Content $PatchFile) + @('-- (视图重建, 定义取自基准库)') + $createLines
    Set-Content -Path $effective -Value $content -Encoding UTF8
    [void](Repair-IndexDdl $effective)
    return $effective
}

function Invoke-Comparer([string]$OutDirHost) {
    docker run --rm @DockerNetArgs -v "$(Get-ContainerMountPath $OutDirHost):/work/out" $ComparerImage `
        --source-url "$ContainerUrlNoDb/${DbVer}?sslmode=disable" `
        --target-url "$ContainerUrlNoDb/${DbSeq}?sslmode=disable" `
        --output-dir /work/out --compare-content both --sql-output-mode merged `
        --schema-exclude-tables $ExcludeTables --data-exclude-tables $ExcludeTables
    if ($LASTEXITCODE -ne 0) { throw "postgrelcomparer 运行失败 (输出: $OutDirHost)" }
}
function Get-StatementCount([string]$File) {
    @(Get-Content $File | Where-Object { $_ -notmatch '^\s*(--|$)' }).Count
}

# ---------- 主流程 ----------
try {
    Write-Step "STEP 0 解析版本: folder=$Folder"
    $folderNum = $Folder -replace '^versionupdates\.v', ''
    $parts = $folderNum -split '\.'
    $MM = $parts[0]; $NN = $parts[1]
    $DbTag = ($folderNum -replace '\.', '_')
    $DbBase = "upmerge_${DbTag}_base"; $DbSeq = "upmerge_${DbTag}_seq"; $DbVer = "upmerge_${DbTag}_ver"

    $scripts = Get-VersionedScripts (Join-Path $MigRoot $Folder)
    if (-not $scripts) { throw "目标目录没有任何 V*.sql 脚本" }
    # 文件名含 fix_upgradesql 的脚本: 不参与语义合并, 原样附加于产物末尾
    $fixScripts = @($scripts | Where-Object { (Split-Path $_.Path -Leaf) -match $FixPattern })
    $mergedScripts = @($scripts | Where-Object { (Split-Path $_.Path -Leaf) -notmatch $FixPattern })
    # 索引 DDL 兼容性提醒 (规范: 索引一律 DROP INDEX IF EXISTS + CREATE INDEX)
    $idxHits = @(Select-String -Path (Join-Path $MigRoot "$Folder\*.sql") `
        -Pattern 'CREATE\s+(UNIQUE\s+)?INDEX\s+(CONCURRENTLY\s+)?IF NOT EXISTS|CREATE\s+(UNIQUE\s+)?INDEX\s+CONCURRENTLY' -ErrorAction SilentlyContinue)
    if ($idxHits.Count -gt 0) {
        Write-Warning "目标目录有 $($idxHits.Count) 处不兼容的索引写法 (IF NOT EXISTS / CONCURRENTLY):"
        $idxHits | Group-Object Filename | ForEach-Object { Write-Warning ('  {0} x{1}' -f $_.Name, $_.Count) }
        Write-Warning '  规范: DROP INDEX IF EXISTS <name>; CREATE INDEX <name> ...; (产物侧驱动已自动归一化)'
    }
    $maxInFolder = ($scripts | Select-Object -Last 1).Version
    $to = $null
    for ($k = 1; $k -le 99; $k++) {
        $cand = "$MM.$NN.$k"
        $exists = Get-ChildItem (Join-Path $MigRoot $Folder) -Filter "V$cand`__*.sql" -ErrorAction SilentlyContinue
        if (-not $exists -and (Get-VersionKey $cand) -gt (Get-VersionKey $maxInFolder)) { $to = $cand; break }
    }
    if (-not $to) { throw "无法确定目标版本号 (folder=$Folder)" }
    $Artifact = Join-Path $MigRoot "$Folder\V$to`__upgradesql.sql"
    if (Test-Path $Artifact) { throw "产物已存在: $Artifact (如需重跑请先移走)" }

    $fromScripts = Get-VersionedScripts $MigRoot | Where-Object { $_.Path -notmatch "\\$Folder\\" }
    if (-not $fromScripts) { throw "除目标目录外没有任何脚本" }
    $FromMax = ($fromScripts | Select-Object -Last 1).Version
    Write-Detail "目标: V$to`__upgradesql.sql (from=V$FromMax, 目标目录 $($scripts.Count) 个 0.x 脚本: 合并 $($mergedScripts.Count) + 末尾原样固定 $($fixScripts.Count))"
    if ($fixScripts.Count -gt 0) {
        Write-Detail ("末尾原样附加的 fix 脚本: " + (($fixScripts | ForEach-Object { Split-Path $_.Path -Leaf }) -join ', '))
    }

    # Java 迁移提醒 (Java 迁移无法被合并)
    $javaDir = Join-Path $RepoUpgrade 'src\main\java\com\airedgesoft\ae\upgrade'
    if (Test-Path $javaDir) {
        $javaHits = Get-ChildItem $javaDir -Filter "V${MM}_${NN}*.java" -ErrorAction SilentlyContinue
        if ($javaHits) { Write-Warning "检测到 Java 迁移类 $($javaHits.Name -join ', '), Java 迁移无法被本流水线合并, 请人工确认!" }
    }

    $ts = Get-Date -Format 'yyyyMMdd_HHmmss'
    $ReportDir = Join-Path $RepoUpgrade ".merge-work\$to-$ts"
    New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
    Set-Content -Path (Join-Path $ReportDir '.gitignore') -Value '*'
    $WorkDir = Join-Path $ReportDir 'work'; New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
    $WorkMount = $WorkDir

    # 挂载路径形式探测: 先试 Windows 路径 (Docker Desktop), 失败则改用 WSL 风格 /mnt/<drive>/...
    $MountWorkDir = $WorkDir
    $eapMount = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try {
        docker run --rm -v "${WorkDir}:/probe" $PgImage sh -c 'true' *> $null
        $probeOk = ($LASTEXITCODE -eq 0)
        if (-not $probeOk) {
            $script:UseWslMount = $true
            $MountWorkDir = Get-ContainerMountPath $WorkDir
            docker run --rm -v "${MountWorkDir}:/probe" $PgImage sh -c 'true' *> $null
            if ($LASTEXITCODE -ne 0) { throw "无法把 $WorkDir 挂载进容器 (Windows 与 WSL 两种写法都失败)" }
        }
    } finally { $ErrorActionPreference = $eapMount }
    if ($script:UseWslMount) { Write-Detail "engine 不接受 Windows 路径挂载 -> 已改用: $MountWorkDir" }
    else { Write-Detail "挂载形式: Windows 路径" }

    # 产物固定头: 逐字写入 00_header.sql (固定头文件优先于内置默认头)
    $headerText = if ($HeaderFile) {
        if (-not (Test-Path $HeaderFile)) { throw "固定头文件不存在: $HeaderFile" }
        [System.IO.File]::ReadAllText((Resolve-Path $HeaderFile).Path)
    } else { $HeaderSqlDefault }
    $headerText = $headerText.TrimEnd([char[]]"`r`n") + "`n"
    [System.IO.File]::WriteAllText((Join-Path $WorkDir '00_header.sql'), $headerText, [System.Text.UTF8Encoding]::new($false))
    $sv = @(Invoke-PsqlText 'postgres' 'show server_version_num;') | Select-Object -First 1
    Write-Detail "数据库 server_version_num=$sv"

    Write-Step "STEP 1 重建工作库 ($DbBase / $DbSeq / $DbVer)"
    foreach ($db in @($DbBase, $DbSeq, $DbVer)) { Invoke-DropDb $db; Invoke-CreateDb $db }

    Write-Step "STEP 2 base: 重放到上一发版终态 V$FromMax 并快照"
    Invoke-Migrate @('exec', "V$FromMax", "--db=$BaseUrlNoDb/${DbBase}?sslmode=disable", "--dir=$MigRoot") (Join-Path $ReportDir '02_base_replay.log')
    Invoke-Dump $DbBase 'snapshot.sql'
    Write-Detail ("快照: {0:N0} bytes" -f (Get-Item (Join-Path $WorkDir 'snapshot.sql')).Length)

    Write-Step "STEP 3 seq: 快照 + 逐脚本执行目标目录 (真值库)"
    Restore-FromSnapshot $DbSeq
    $seqMode = 'migrate'
    try {
        Invoke-Migrate @('exec', "--db=$BaseUrlNoDb/${DbSeq}?sslmode=disable", "--dir=$MigRoot") (Join-Path $ReportDir '03_seq_replay.log')
    } catch {
        # Go migrate 整文件隐式事务 -> 遇 CONCURRENTLY 类语句必失败; 回退 psql 逐文件自动提交
        Write-Detail "migrate 回放失败: $($_.Exception.Message)"
        Write-Detail "回退 psql 逐文件回放 (事务外语句兼容, 语义同 Flyway detectCanExecuteInTransaction)"
        $seqMode = 'psql-sequential'
        Invoke-DropDb $DbSeq; Invoke-CreateDb $DbSeq; Restore-FromSnapshot $DbSeq
        Invoke-SequentialReplay $DbSeq $scripts (Join-Path $ReportDir '03_seq_replay_psql.log')
    }
    Write-Detail "seq 回放方式: $seqMode"

    Write-Step "STEP 4 dbdiff 生成 (server1=seq 基准, server2=base)"
    $eap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'  # dbdiff 警告在 stderr, 不应触发 Stop
    try {
        docker run --rm @DockerNetArgs -v "${MountWorkDir}:/workmnt" $DbdiffImage diff `
            --driver=pgsql --type=all `
            --server1-url="$ContainerUrlNoDb/${DbSeq}?sslmode=disable" `
            --server2-url="$ContainerUrlNoDb/${DbBase}?sslmode=disable" `
            --allow-destructive --ignore-tables=$ExcludeTables `
            --output=/workmnt/gen_raw.sql 2>&1 | Tee-Object -FilePath (Join-Path $ReportDir '04_dbdiff.log') | Out-Null
    } finally { $ErrorActionPreference = $eap }
    if ($LASTEXITCODE -ne 0) { throw "dbdiff 生成失败 (log=04_dbdiff.log)" }
    $rawLines = Get-Content (Join-Path $WorkDir 'gen_raw.sql')
    $downIdx = @($rawLines | Select-String -Pattern '^-- ==================== DOWN').LineNumber
    $upLines = if ($downIdx.Count -gt 0) { $rawLines[0..($downIdx[0] - 2)] } else { $rawLines }
    Set-Content -Path (Join-Path $WorkDir 'gen_up.sql') -Value $upLines -Encoding UTF8
    # dbdiff 撇号转义修复: dbdiff 按 MySQL 风格把值内撇号转义成 \' ，在 standard_conforming_strings=on 下
    # 会提前终结字符串导致解析错位（实证 2026-09-24 v3.7 合并）。归一化为 '' ；已有 \\ 前缀的(值以反斜杠结尾)跳过。
    $genUpPath = Join-Path $WorkDir 'gen_up.sql'
    $upText = [System.IO.File]::ReadAllText($genUpPath)
    $escAposCount = ([regex]::Matches($upText, "(?<!\\)\\'")).Count
    if ($escAposCount -gt 0) {
        $upText = [regex]::Replace($upText, "(?<!\\)\\'", "''")
        [System.IO.File]::WriteAllText($genUpPath, $upText, [System.Text.UTF8Encoding]::new($false))
        Write-Detail "已归一化 $escAposCount 处 dbdiff 撇号转义"
    }
    $nRename = Repair-RenamedColumns $genUpPath $DbSeq $DbBase
    if ($nRename -gt 0) { Write-Detail "已还原 $nRename 处 RENAME COLUMN (物理列序/attnum 与逐脚本一致)" }
    $nIdx = Repair-IndexDdl $genUpPath
    if ($nIdx -gt 0) { Write-Detail "索引容错: $nIdx 个 CREATE INDEX 已改写为 DROP INDEX IF EXISTS + CREATE INDEX" }
    Write-Detail ("UP 段: {0:N0} bytes" -f (Get-Item $genUpPath).Length)

    # manifest 头
    $manifest = @(
        '-- ============================================================================'
        "-- V$to`__upgradesql.sql  (由 merge-upgrade-sql skill 自动生成)"
        "-- 生成时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        "-- 结构: 固定头 -> 本清单 -> prepass(解除视图依赖) -> 合并正文 -> prepass(视图重建) -> 验证补丁 -> fix 脚本(原样附加)"
        "-- 合并来源: $Folder 以下 $($scripts.Count) 个脚本 (自 V$FromMax 起; 合并正文 $($mergedScripts.Count) 个)"
    )
    foreach ($s in $scripts) {
        $sha = (Get-FileHash $s.Path -Algorithm SHA256).Hash.ToLower()
        $leaf = Split-Path $s.Path -Leaf
        $tag = if ($leaf -match $FixPattern) { '  [不合并, 末尾原样附加]' } else { '' }
        $manifest += "--   $leaf  sha256=$sha$tag"
    }
    $manifest += @(
        "-- 工具: dbdiff ($DbdiffImage) + postgrelcomparer + pg_dump (容器化)"
        "-- seq 回放: $seqMode"
        "-- 验证: postgrelcomparer 收敛循环 + pg_dump 归一化 MD5 终审 (报告: .merge-work/$to-$ts)"
    )
    if ($fixScripts.Count -gt 0) {
        $manifest += "-- 末尾原样附加: $($fixScripts.Count) 个文件名含 '$FixPattern' 的脚本 (逐字保留, 幂等复跑已校验)"
    }
    $manifest += '-- ============================================================================'
    Set-Content -Path (Join-Path $WorkDir 'manifest.sql') -Value $manifest -Encoding UTF8

    Write-Step "STEP 4.5 生成末尾 fix 原样片段 (含 fix_upgradesql 的脚本)"
    $fixPart = $null
    if ($fixScripts.Count -gt 0) {
        $fixPart = Join-Path $WorkDir 'fix_verbatim.sql'
        $fixHead = @(
            ''
            '-- ============================================================================'
            "-- 以下 $($fixScripts.Count) 个脚本文件名含 '$FixPattern': 不参与语义合并, 按原文件逐字保留"
            '-- 追加于产物末尾 (合并正文 + 验证补丁之后执行)'
        )
        foreach ($s in $fixScripts) { $fixHead += "--   $(Split-Path $s.Path -Leaf)" }
        $fixHead += @(
            '-- 注意: 这些脚本按约定必须幂等; 合并正文已包含其净效果, 故此处复跑应为零变更。'
            '--       STEP 6.8 会在最终顺序上复跑并比对, 出现差异则流水线 FAIL (需人工处理)。'
            '-- ============================================================================'
        )
        $fixText = ($fixHead -join "`n") + "`n"
        foreach ($s in $fixScripts) {
            $leaf = Split-Path $s.Path -Leaf
            $raw = [System.IO.File]::ReadAllText($s.Path)
            $fixText += "`n-- ==== BEGIN (verbatim) $leaf ====`n" + $raw
            if (-not $raw.EndsWith("`n")) { $fixText += "`n" }
            $fixText += "-- ==== END (verbatim) $leaf ====`n"
        }
        [System.IO.File]::WriteAllText($fixPart, $fixText, [System.Text.UTF8Encoding]::new($false))
        Copy-Item $fixPart $ReportDir -Force
        Write-Detail "已生成: $($fixScripts.Count) 个脚本 -> $fixPart"
    } else {
        Write-Detail "目标目录无匹配 '$FixPattern' 的脚本, 无末尾 fix 片段"
    }

    Write-Step "STEP 5 ver: 快照 + 执行合并脚本(初版: 固定头 + 清单 + 合并正文)"
    Restore-FromSnapshot $DbVer
    # dbdiff 给的是「净差异」: 原脚本里 drop view -> ALTER COLUMN TYPE -> create view 的
    # 中间步骤被消掉, base 态视图会直接挡住 ALTER (ERROR 0A000 cannot alter type of a column
    # used by a view or rule)。所以正文前先 DROP 依赖视图闭包, 正文后从 seq 真值库重建。
    $r0Parts = @((Join-Path $WorkDir '00_header.sql'), (Join-Path $WorkDir 'manifest.sql'))
    $postCreate = $null
    $alteredTables = Get-AlteredTables (Join-Path $WorkDir 'gen_up.sql')
    if ($alteredTables.Count -gt 0) {
        Write-Detail ('正文 ALTER COLUMN TYPE 涉及表: ' + ($alteredTables -join ' '))
        $deps = Get-ViewDeps $DbVer $alteredTables
        if ($deps.Count -gt 0) {
            $depDesc = @($deps | Sort-Object { [int](($_ -split '\|')[0]) } -Descending)
            Write-Detail ("prepass: 先解除 base 态视图依赖 ($($deps.Count) 个): " + (@($depDesc | ForEach-Object { ($_ -split '\|')[1] }) -join ' '))
            $preDrop = Join-Path $WorkDir '05a_prepass_drop.sql'
            Set-Content -Path $preDrop -Encoding UTF8 -Value (@('-- (prepass: 合并正文含 ALTER COLUMN TYPE, 先解除 base 态视图依赖)') +
                @($depDesc | ForEach-Object { 'DROP VIEW IF EXISTS "public"."{0}";' -f (($_ -split '\|')[1]) }))
            $postCreate = Join-Path $WorkDir '05b_prepass_recreate.sql'
            $postLines = @('-- (prepass: 视图重建, 定义取自逐脚本执行真值库)')
            foreach ($dep in ($deps | Sort-Object { [int](($_ -split '\|')[0]) })) {
                $vName = ($dep -split '\|')[1]
                $inSeq = (Invoke-PsqlText $DbSeq "select 1 from pg_views where schemaname='public' and viewname='$vName';") -join ''
                if ($inSeq -match '1') { $postLines += Get-ViewRecreateSql $vName }
            }
            Set-Content -Path $postCreate -Value $postLines -Encoding UTF8
            Copy-Item $preDrop $ReportDir -Force; Copy-Item $postCreate $ReportDir -Force
            $r0Parts += $preDrop
        }
    }
    $r0Parts += (Join-Path $WorkDir 'gen_up.sql')
    if ($postCreate -and (Test-Path $postCreate)) { $r0Parts += $postCreate }
    $mergedR0 = Join-Path $WorkDir 'merged_r0.sql'
    Get-Content $r0Parts | Set-Content $mergedR0 -Encoding UTF8
    Invoke-SqlFile $DbVer 'merged_r0.sql'
    # 末尾 fix 片段的净效果本就在 seq 真值库里 (它也是逐脚本回放的一部分), 所以先在这里
    # 执行一次, 让收敛循环看到正确状态; 产物里它仍然置于最末, STEP 6.8 会在最终顺序上复跑校验。
    if ($fixPart) {
        Invoke-SqlFile $DbVer 'fix_verbatim.sql'
        Write-Detail 'STEP 5.5 预执行末尾 fix 片段 (幂等重放, 产物中仍在最末)'
    }

    Write-Step "STEP 6 postgrelcomparer 收敛循环 (上限 $MaxRounds 轮)"
    $artifactParts = @($mergedR0)
    $pass = $false
    $prevStmts = $null
    for ($round = 1; $round -le $MaxRounds; $round++) {
        $vOut = Join-Path $WorkDir "verify_r$round"
        New-Item -ItemType Directory -Force -Path $vOut | Out-Null
        try { Invoke-Comparer $vOut } catch {
            Get-Content (Join-Path $vOut 'incremental.sql') -Tail 5 -ErrorAction SilentlyContinue
            throw
        }
        $n = Get-StatementCount (Join-Path $vOut 'incremental.sql')
        if ($n -eq 0) { Write-Detail "round ${round}: 零差异 ✓"; $pass = $true; break }
        Write-Detail "round ${round}: $n 条差异 -> 生成补丁并回灌"
        # 补丁未生效检测: 本轮语句与上轮完全相同 => 回灌无效, 典型是表达式的 deparse 往返差异
        # (如 varchar 列的 CHECK ("c" IN ('a','b')) 反解成 ANY((ARRAY[...])::text[]) 后重解析会变成逐元素 ::text 形态)
        $curStmts = @(Get-Content (Join-Path $vOut 'incremental.sql') | Where-Object { $_ -notmatch '^\s*(--|$)' })
        if ($prevStmts -and (($curStmts -join "`n") -eq ($prevStmts -join "`n"))) {
            Write-Warning "round ${round}: 差异与上一轮完全相同 -> 补丁回灌未生效 (疑似 deparse 往返差异, 无法靠补丁收敛)"
            Write-Warning "  处置: 按原始脚本的表达式文本写一个 fix_upgradesql 脚本重建该对象 (会被原样附加到产物末尾), 或与负责人确认"
            $curStmts | Select-Object -First 5 | ForEach-Object { Write-Warning "    $_" }
        }
        $prevStmts = $curStmts
        $eff = Apply-Patch (Join-Path $vOut 'incremental.sql')
        # 容器内路径: workmnt 挂载点对应 $WorkDir, 取相对路径
        $effRel = $eff.Substring($WorkDir.Length + 1).Replace('\', '/')
        Invoke-SqlFile $DbVer $effRel
        $artifactParts += $eff
        Copy-Item $eff $ReportDir -Force
    }
    if (-not $pass) { throw "$MaxRounds 轮收敛未达成零差异, 请人工检查 $ReportDir" }

    Write-Step "STEP 6.5 视图/注释同步 (postgrelcomparer 不覆盖的盲区)"
    # postgrelcomparer 只比基表; dbdiff 不生成 COMMENT。从 seq(真值库) 同步视图定义与视图注释到 ver。
    $syncLines = @()
    $seqViewsRaw = @(Invoke-PsqlText $DbSeq ("select viewname || chr(1) || replace(definition, chr(10), chr(2)) " +
        "from pg_views where schemaname='public' order by viewname;") | Where-Object { $_ -match [char]1 })
    $verViewsRaw = @(Invoke-PsqlText $DbVer ("select viewname || chr(1) || replace(definition, chr(10), chr(2)) " +
        "from pg_views where schemaname='public' order by viewname;") | Where-Object { $_ -match [char]1 })
    $seqViews = @{}; foreach ($r in $seqViewsRaw) { $p = $r -split [char]1; $seqViews[$p[0]] = $p[1].Replace([char]2, "`n") }
    $verViews = @{}; foreach ($r in $verViewsRaw) { $p = $r -split [char]1; $verViews[$p[0]] = $p[1].Replace([char]2, "`n") }
    foreach ($name in @($verViews.Keys) | Where-Object { -not $seqViews.ContainsKey($_) }) {
        $syncLines += "DROP VIEW IF EXISTS `"public`".`"$name`";"
    }
    foreach ($name in $seqViews.Keys) {
        if (-not $verViews.ContainsKey($name) -or $verViews[$name] -ne $seqViews[$name]) {
            $syncLines += "CREATE OR REPLACE VIEW `"public`".`"$name`" AS $($seqViews[$name]);"
        }
    }
    $seqCmtRaw = @(Invoke-PsqlText $DbSeq ("select c.relname || chr(1) || coalesce(d.description, '') " +
        "from pg_class c join pg_description d on d.objoid=c.oid and d.objsubid=0 " +
        "where c.relnamespace='public'::regnamespace and c.relkind='v' order by c.relname;") | Where-Object { $_ -match [char]1 })
    $verCmtRaw = @(Invoke-PsqlText $DbVer ("select c.relname || chr(1) || coalesce(d.description, '') " +
        "from pg_class c join pg_description d on d.objoid=c.oid and d.objsubid=0 " +
        "where c.relnamespace='public'::regnamespace and c.relkind='v' order by c.relname;") | Where-Object { $_ -match [char]1 })
    $seqCmt = @{}; foreach ($r in $seqCmtRaw) { $p = $r -split [char]1; $seqCmt[$p[0]] = $p[1] }
    $verCmt = @{}; foreach ($r in $verCmtRaw) { $p = $r -split [char]1; $verCmt[$p[0]] = $p[1] }
    foreach ($name in $seqCmt.Keys) {
        if (-not $verCmt.ContainsKey($name) -or $verCmt[$name] -ne $seqCmt[$name]) {
            $desc = ($seqCmt[$name] -replace "'", "''")
            $syncLines += "COMMENT ON VIEW `"public`".`"$name`" IS '$desc';"
        }
    }
    if ($syncLines.Count -gt 0) {
        Write-Detail "同步补丁: $($syncLines.Count) 条 (视图/注释)"
        $syncFile = Join-Path $WorkDir 'sync_schema.sql'
        @('-- (STEP 6.5 视图/注释同步, 定义与注释取自逐脚本执行真值库)') + $syncLines |
            Set-Content $syncFile -Encoding UTF8
        Invoke-SqlFile $DbVer 'sync_schema.sql'
        $artifactParts += $syncFile
        Copy-Item $syncFile $ReportDir -Force
    } else {
        Write-Detail "视图/注释无差异"
    }

    Write-Step "STEP 6.8 最终顺序复跑末尾 fix 片段 ($($fixScripts.Count) 个)"
    if ($fixPart) {
        # 此刻 ver 的变更顺序 = 产物顺序: 正文 -> 各轮补丁 -> 同步补丁 -> fix
        Invoke-SqlFile $DbVer 'fix_verbatim.sql'
        # 幂等复跑校验: fix 的效果已在 seq 真值库中, 复跑后应零差异
        $vFixOut = Join-Path $WorkDir 'verify_fix'
        New-Item -ItemType Directory -Force -Path $vFixOut | Out-Null
        Invoke-Comparer $vFixOut
        $nFix = Get-StatementCount (Join-Path $vFixOut 'incremental.sql')
        if ($nFix -ne 0) {
            Copy-Item (Join-Path $vFixOut 'incremental.sql') $ReportDir -Force
            throw "fix_upgradesql 复跑后出现 $nFix 条差异 (脚本可能非幂等), 详见 $ReportDir\incremental.sql"
        }
        Write-Detail "fix 片段复跑零差异 ✓ (且已按产物最终顺序校验)"
        $artifactParts += $fixPart
    } else {
        Write-Detail "无末尾 fix 片段, 跳过"
    }

    Write-Step "STEP 7 pg_dump 归一化终审"
    Invoke-Dump $DbVer 'schema_ver.sql' -SchemaOnly
    Invoke-Dump $DbSeq 'schema_seq.sql' -SchemaOnly
    function Get-NormHash([string]$File) {
        $norm = Get-Content $File | Where-Object { $_ -notmatch '^(--|\\restrict|\\unrestrict|\s*$)' }
        $bytes = [System.Text.Encoding]::UTF8.GetBytes(($norm -join "`n"))
        $md5 = [System.Security.Cryptography.MD5]::Create()
        ([System.BitConverter]::ToString($md5.ComputeHash($bytes)) -replace '-', '').ToLower()
    }
    $hVer = Get-NormHash (Join-Path $WorkDir 'schema_ver.sql')
    $hSeq = Get-NormHash (Join-Path $WorkDir 'schema_seq.sql')
    if ($hVer -ne $hSeq) {
        $d = Compare-Object (Get-Content (Join-Path $WorkDir 'schema_ver.sql')) (Get-Content (Join-Path $WorkDir 'schema_seq.sql'))
        $d | Out-File (Join-Path $ReportDir '07_schema_diff.txt') -Encoding UTF8
        throw "schema 终审不一致 (ver=$hVer seq=$hSeq), diff 见 07_schema_diff.txt"
    }
    Write-Detail "schema MD5 一致: $hVer"

    Write-Step "STEP 8 产物落仓 + 0.x 归档"
    $outText = ''
    foreach ($p in $artifactParts) { $outText += [System.IO.File]::ReadAllText($p) }
    [System.IO.File]::WriteAllText($Artifact, $outText, [System.Text.UTF8Encoding]::new($false))
    Write-Detail "产物: $Artifact ($((Get-Item $Artifact).Length) bytes)"
    Write-Detail "产物片段: $($artifactParts.Count) 段 (固定头/清单/正文/补丁/末fix)"
    if (-not $NoArchive) {
        $archDir = Join-Path $MigRoot "..\migration_archive\$Folder"
        New-Item -ItemType Directory -Force -Path $archDir | Out-Null
        $moved = @()
        foreach ($s in $scripts) {
            if ($s.Path -like "*\V$to`__*") { continue }
            Move-Item $s.Path $archDir -Force; $moved += (Split-Path $s.Path -Leaf)
        }
        Set-Content -Path (Join-Path $ReportDir '08_archived_files.txt') -Value $moved -Encoding UTF8
        Write-Detail "已归档 $($moved.Count) 个 0.x 脚本 -> $archDir"
    }

    Write-Step "STEP 9 清理工作库"
    if ($Keep) { Write-Detail "-Keep: 保留 $DbBase / $DbSeq / $DbVer" }
    else { foreach ($db in @($DbBase, $DbSeq, $DbVer)) { Invoke-DropDb $db } }

    Write-Host ""
    Write-Host "PASS ✓  V$to`__upgradesql.sql 已生成并验证 (结构+数据零差异, schema MD5=$hVer)" -ForegroundColor Green
    if ($fixScripts.Count -gt 0) {
        Write-Host "  产物末尾已原样附加 $($fixScripts.Count) 个 fix_upgradesql 脚本 (逐字保留, 幂等复跑零差异)" -ForegroundColor Green
    }
    Write-Host "后续人工步骤:"
    Write-Host "  1) 检查产物与归档: git status / git diff"
    Write-Host "  2) 本地冒烟: 重置一个库全链路重放, 确认 V$to 一次通过"
    Write-Host "  3) 已执行过 0.x 的环境按团队约定人工处理 (历史表清理或重置)"
    exit 0
}
catch {
    Write-Host ""
    Write-Host "FAIL: $_" -ForegroundColor Red
    Write-Host "证据保留: $ReportDir (工作库 $DbBase/$DbSeq/$DbVer 未清理)" -ForegroundColor Yellow
    exit 1
}

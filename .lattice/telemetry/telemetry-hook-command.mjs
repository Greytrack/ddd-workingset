/**
 * 跨平台 Hook 命令构造器 — 各 Agent 平台（zcode / claude-code / codex / cursor）
 * 的 Stop 类 Hook 注册命令的唯一事实来源。
 *
 * 为什么不用 `AGENT_TOOL=x node "$(git rev-parse --show-toplevel)/..."`：
 *   1. `VAR=value command` 前缀与 `$(...)` 命令替换是 POSIX shell 专属语法，
 *      Windows 的 cmd.exe / PowerShell 不支持（cmd 会把它们原样传给 node，
 *      导致模块路径不存在；前缀则直接报命令找不到）。
 *   2. Hook 平台在 Windows 上经 cmd/PowerShell 执行命令串，在 macOS/Linux 上
 *      经 /bin/sh — 命令必须对三者同时成立。
 *
 * 方案：`node -e "<bootstrap>" <agentTool> <scriptName> [projectDirHint]`
 *   - bootstrap 内联脚本（HOOK_BOOTSTRAP_SCRIPT）从 cwd 逐级向上查找
 *     `.lattice/telemetry` 目录，等价于 `git rev-parse --show-toplevel` 的
 *     就近语义，但完全在 Node 内完成，不依赖任何 shell 能力；
 *   - 末位的 projectDirHint（如 `${CLAUDE_PROJECT_DIR}`）是可选兜底：
 *     平台做了模板/环境展开时直击仓库根；未展开（残留 `${...}` 字面量）时
 *     existsSync 为 false，自然回落到 cwd 上溯，两种情况都安全；
 *   - AGENT_TOOL 由 bootstrap 写入子进程环境（替代 POSIX 前缀写法）；
 *   - stdin 经 `stdio:[0]` 直接透传给子脚本，平台喂入的 JSON 载荷不落两遍。
 *
 * bootstrap 字符的硬约束（在 cmd / PowerShell / POSIX sh 的双引号串中都必须
 * 是纯字面量）：不含 `"` `\` `` ` `` `$` `%` `!`。`&&` `||` `<` 在双引号内
 * 三个 shell 中均为字面量，可安全使用。测试 telemetry-hook-command.test.mjs
 * 会强制校验该约束并保持配置文件与本模块同步。
 */

export const HOOK_BOOTSTRAP_SCRIPT =
  'var p=require(\'path\'),f=require(\'fs\'),c=require(\'child_process\'),d=process.cwd(),t=null,n=0;' +
  'while(d&&n<32){var j=p.join(d,\'.lattice\',\'telemetry\');try{if(f.existsSync(j)){t=j;break}}catch(e){break}' +
  'var u=p.dirname(d);if(u===d)break;d=u;n++}' +
  'if(t===null&&process.argv[3]){var h=p.join(process.argv[3],\'.lattice\',\'telemetry\');try{if(f.existsSync(h))t=h}catch(e){}}' +
  'if(t){var s=p.join(t,process.argv[2]||\'telemetry-emitter.mjs\');' +
  'try{if(f.existsSync(s)){if(process.argv[1])process.env.AGENT_TOOL=process.argv[1];' +
  'c.spawnSync(process.execPath,[s,\'hook\'],{stdio:[0,\'ignore\',\'ignore\'],windowsHide:true})}}catch(e){}}';

/** claude-code 会做 ${CLAUDE_PROJECT_DIR} 模板展开；POSIX shell 亦按环境变量展开；
 *  cmd 下残留字面量则由 bootstrap 的 existsSync 兜底失效，始终安全。 */
export const CLAUDE_PROJECT_DIR_HINT = '${CLAUDE_PROJECT_DIR}';
export const ZCODE_PROJECT_DIR_HINT = '${ZCODE_PROJECT_DIR}';

/**
 * 渲染 shell 命令串（zcode / claude-code / codex / cursor 的 `type: "command"`
 * Hook 通用；命令串不依赖任何 shell 专属语法）。
 * @param {object} opts
 * @param {string} opts.agentTool - 写入子进程 AGENT_TOOL 的标识（如 'cursor'）
 * @param {string} opts.scriptName - `.lattice/telemetry/` 下的入口脚本文件名
 * @param {string} [opts.projectDirHint] - 可选的平台项目目录模板变量兜底
 * @returns {string}
 */
export function renderHookCommand({ agentTool, scriptName, projectDirHint }) {
  const tail = projectDirHint ? ` "${projectDirHint}"` : '';
  return `node -e "${HOOK_BOOTSTRAP_SCRIPT}" "${agentTool}" "${scriptName}"${tail}`;
}

/** 各平台 telemetry Hook 的标准超时（秒，与既有配置一致）。 */
export const HOOK_TIMEOUT_SECONDS = 10;

/**
 * zcode 专用：`type: "process"` hook（argv 直派、不经 shell）。
 *
 * Windows 上 command 型由 zcode 经 `cmd.exe /d /s /c` 执行——即使 zcode 对
 * 直接子进程设置了 windowsHide，cmd 这一层在旧版构建/GUI 场景下仍可能
 * 闪现控制台窗口；process 型直接 CreateProcess node.exe，链路里没有 cmd。
 * zcode 对 process 型同样：从 stdin 喂 JSON 载荷、对 args 做模板变量展开
 * （${ZCODE_PROJECT_DIR}），超时字段用 timeoutMs（毫秒）。
 * @param {object} opts
 * @param {string} opts.scriptName - `.lattice/telemetry/` 下的入口脚本文件名
 * @returns {{type:string, command:string, args:string[], timeoutMs:number}}
 */
export function renderZcodeProcessHook({ scriptName }) {
  return {
    type: 'process',
    command: 'node',
    args: ['-e', HOOK_BOOTSTRAP_SCRIPT, 'zcode', scriptName, ZCODE_PROJECT_DIR_HINT],
    timeoutMs: HOOK_TIMEOUT_SECONDS * 1000,
  };
}

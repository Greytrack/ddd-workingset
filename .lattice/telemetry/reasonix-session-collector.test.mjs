import { describe, test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, utimesSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import zlib from 'node:zlib';
import {
  collectReasonixSessions,
  sessionToken,
  registryRootForToken,
  textOf,
  parseV4Frames,
  isHookActive,
  touchHookActiveMarker,
  hookActiveMarkerPath,
  stripStudioInjectedContext,
} from './reasonix-session-collector.mjs';

const createdTmpDirs = [];
afterEach(() => {
  for (const dir of createdTmpDirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

function gitRootOf(dir) {
  return execFileSync('git', ['-C', dir, 'rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
}

const REPO_ROOT = gitRootOf(process.cwd());

/** 建一个临时 reasonix home（REASONIX_HOME），并写入 config.json 的 projects 注册表。 */
function makeReasonixHome(projects) {
  const home = mkdtempSync(join(tmpdir(), 'rx-test-'));
  createdTmpDirs.push(home);
  mkdirSync(join(home, 'sessions'), { recursive: true });
  writeFileSync(join(home, 'config.json'), JSON.stringify({ projects: projects ?? {} }));
  return home;
}

/** 建一个临时 v4 home：desktop-projects.json 注册表 + projects 目录。 */
function makeV4Home(roots) {
  const home = mkdtempSync(join(tmpdir(), 'rx-v4-test-'));
  createdTmpDirs.push(home);
  writeFileSync(
    join(home, 'desktop-projects.json'),
    JSON.stringify({ projects: (roots ?? []).map((root) => ({ root, topics: [] })) }),
  );
  return home;
}

/** 项目根 → 项目目录名（与收集器 mangleProjectDir 一致）。 */
function mangleDir(root) {
  return root.replace(/[\\/:]/g, '-').toLowerCase();
}

/** 构造 v4 记录：commits = [{ ts, events: [{ seq, role, content, raw?, createdAt? }] }] */
function v4Records(commits) {
  const out = [];
  for (const c of commits) {
    const begin = {
      schemaVersion: 4,
      codec: 'reasonix.session.linear/v4',
      recordType: 'batch/begin',
      commitId: `commit-${c.events[0]?.seq ?? 0}`,
      firstSeq: c.events[0]?.seq ?? 0,
      eventCount: c.events.length,
      writerGeneration: 1,
      createdAt: c.ts,
    };
    out.push(begin);
    for (const ev of c.events) {
      const msg = { role: ev.role, id: `msg-${ev.seq}`, content: ev.content };
      if (ev.raw) msg.raw_content = ev.raw;
      if (ev.createdAt != null) msg.createdAt = ev.createdAt;
      out.push({
        schemaVersion: 4,
        codec: 'reasonix.session.linear/v4',
        recordType: 'batch/event',
        createdAt: '0001-01-01T00:00:00Z',
        event: {
          id: `evt-${ev.seq}`,
          seq: ev.seq,
          kind: 'message/complete',
          payload: Buffer.from(JSON.stringify({ message: msg }), 'utf8').toString('base64'),
        },
      });
    }
    out.push({
      schemaVersion: 4,
      codec: 'reasonix.session.linear/v4',
      recordType: 'batch/end',
      commitId: begin.commitId,
      firstSeq: begin.firstSeq,
      eventCount: begin.eventCount,
      createdAt: '0001-01-01T00:00:00Z',
      sha256: 'x',
    });
  }
  return out;
}

/** 编码 RX4F + zstd 分帧的 events.frames。 */
function encodeV4Frames(records) {
  const jsonl = records.map((r) => JSON.stringify(r)).join('');
  const compressed = zlib.zstdCompressSync(Buffer.from(jsonl, 'utf8'));
  const header = Buffer.concat([Buffer.from('RX4F', 'ascii'), Buffer.from([0, 0, 1, 2, 0, 0, 1, 0x4f])]);
  return Buffer.concat([header, compressed]);
}

/** 写一个 v4 会话到 <home>/projects/<dir>/sessions-v4/<hash>/events.frames，可指定文件 mtime。 */
function writeV4Session(home, projectRoot, hash, records, mtimeMs) {
  const dir = join(home, 'projects', mangleDir(projectRoot), 'sessions-v4', hash);
  mkdirSync(dir, { recursive: true });
  const file = join(dir, 'events.frames');
  writeFileSync(file, encodeV4Frames(records));
  writeFileSync(join(dir, 'manifest.json'), JSON.stringify({ schemaVersion: 4, codec: 'reasonix.session.linear/v4', sessionId: hash }));
  if (mtimeMs != null) {
    const t = new Date(mtimeMs);
    utimesSync(file, t, t);
  }
  return file;
}

/** 写一份会话（chat jsonl + 可选 events jsonl）。 */
function writeSession(home, name, chatLines, eventsLines) {
  const sessions = join(home, 'sessions');
  writeFileSync(join(sessions, name), chatLines.map((l) => JSON.stringify(l)).join('\n') + '\n');
  if (eventsLines) {
    writeFileSync(
      join(sessions, name.replace(/\.jsonl$/, '.events.jsonl')),
      eventsLines.map((l) => JSON.stringify(l)).join('\n') + '\n',
    );
  }
}

/** 组装 collector 依赖：repoRoot 指向本仓库，telemetryDir 用临时目录。 */
function makeDeps(telemetryDir) {
  let appended = 0;
  const events = [];
  return {
    repoRoot: REPO_ROOT,
    telemetryDir,
    appendEvent: (e) => {
      appended += 1;
      events.push(e);
    },
    eventId: () => `evt-${appended}`,
    gitUserName: () => 'test-user',
    gitRemoteUrl: () => 'git@test:repo.git',
    gitBranchName: () => 'dev-test',
    nowTimestamp: () => 1780000000000,
    sourceSessionId: (payload) => `hash-${payload?.session_id}`,
    get appended() {
      return appended;
    },
    get events() {
      return events;
    },
  };
}

function makeEvents(name, turns) {
  // turns: [{ turn, model?, finals: [{ content, ts }] }]
  const lines = [];
  for (const t of turns) {
    lines.push({ id: 1, ts: '2026-06-01T12:00:00.000Z', turn: t.turn, type: 'model.turn.started', model: t.model ?? 'deepseek-v4-pro' });
    for (const f of t.finals) {
      lines.push({ id: 2, ts: f.ts, turn: t.turn, type: 'model.final', content: f.content ?? '' });
    }
  }
  return lines;
}

/** 写 STUDIO 项目会话：<home>/projects/<dir>/sessions/<name>.jsonl + .jsonl.meta + 可选 .events.jsonl / .ckpt。 */
function writeStudioProjectSession(home, projectRoot, name, messages, meta, events, turns) {
  const dir = join(home, 'projects', mangleDir(projectRoot), 'sessions');
  mkdirSync(dir, { recursive: true });
  const file = join(dir, `${name}.jsonl`);
  writeFileSync(file, messages.map((m) => JSON.stringify(m)).join('\n') + '\n');
  writeFileSync(file + '.meta', JSON.stringify({ id: name, model: 'deepseek/deepseek-flash', turns: 1, ...meta }));
  if (events) {
    writeFileSync(join(dir, `${name}.events.jsonl`), events.map((e) => JSON.stringify(e)).join('\n') + '\n');
  }
  if (turns) {
    const ckpt = join(dir, `${name}.ckpt`);
    mkdirSync(ckpt, { recursive: true });
    for (const turn of turns) {
      writeFileSync(join(ckpt, `turn-${turn.turn}.json`), JSON.stringify({ schemaVersion: 2, files: [], ...turn }));
    }
  }
  return file;
}

/** 写 STUDIO 桌面会话：<home>/desktop-sessions-v5/by-id/<id>/{header.json, events.frames}。 */
function writeStudioDesktopSession(home, id, cwd, records, mtimeMs) {
  const dir = join(home, 'desktop-sessions-v5', 'by-id', id);
  mkdirSync(dir, { recursive: true });
  const file = join(dir, 'events.frames');
  writeFileSync(file, encodeV4Frames(records));
  writeFileSync(
    join(dir, 'header.json'),
    JSON.stringify({ schemaVersion: 1, sessionId: id, cwd, createdAt: '2026-10-01T00:00:00Z' }),
  );
  if (mtimeMs != null) {
    const t = new Date(mtimeMs);
    utimesSync(file, t, t);
  }
  return file;
}

describe('sessionToken', () => {
  test('testSessionToken_WithTimestamp_ReturnsProjectToken', () => {
    assert.equal(sessionToken('code-airedge-backend-2_1-20260603012915.jsonl'), 'airedge-backend-2_1');
  });
  test('testSessionToken_WithoutTimestamp_ReturnsProjectToken', () => {
    assert.equal(sessionToken('code-13218.jsonl'), '13218');
  });
  test('testSessionToken_AcpSession_ReturnsNull', () => {
    assert.equal(sessionToken('acp-202606091148.jsonl'), null);
  });
});

describe('registryRootForToken', () => {
  test('testRegistryRootForToken_MatchByBasename', () => {
    const root = 'D:/coding/IdeaProjects/union/airedge';
    assert.equal(registryRootForToken('airedge', [root]), root);
  });
  test('testRegistryRootForToken_MatchByFullPathSuffix', () => {
    const root = 'D:/coding/IdeaProjects/links2/airedge-backend-2.1';
    assert.equal(registryRootForToken('airedge-backend-2_1', [root]), root);
  });
  test('testRegistryRootForToken_NoMatch_ReturnsNull', () => {
    assert.equal(registryRootForToken('nope', ['D:/x/y']), null);
  });
});

describe('collectReasonixSessions', () => {
  test('testCollect_AttributedRepo_EmitsRoundsWithPreciseTs', async () => {
    const home = makeReasonixHome({ [REPO_ROOT]: {} });
    writeSession(
      home,
      'code-airedge-20260601120000.jsonl',
      [
        { role: 'user', content: '第一轮问题' },
        { role: 'assistant', content: '第一轮回复' },
        { role: 'user', content: '第二轮问题' },
        { role: 'assistant', content: '第二轮回复' },
      ],
      makeEvents('code-airedge-20260601120000', [
        { turn: 1, finals: [{ content: '第一轮回复', ts: '2026-06-01T12:05:00.000Z' }] },
        { turn: 2, finals: [{ content: '', ts: '2026-06-01T12:19:00.000Z' }, { content: '第二轮回复', ts: '2026-06-01T12:20:00.000Z' }] },
      ]),
    );
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-state-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;

    const n = await collectReasonixSessions(deps);

    assert.equal(n, 2);
    assert.equal(deps.appended, 2);
    const [r1, r2] = deps.events;
    assert.equal(r1.user_input_text, '第一轮问题');
    assert.equal(r1.ai_response_text, '第一轮回复');
    assert.equal(r1.generate_time, Date.parse('2026-06-01T12:05:00.000Z'));
    assert.equal(r1.agent_tool, 'reasonix');
    assert.equal(r1.ai_model, 'deepseek-v4-pro');
    assert.equal(r1.user, 'test-user');
    assert.equal(r1.repo_remote_url, 'git@test:repo.git');
    // 第二轮取最后一条非空 model.final 的精确时间戳
    assert.equal(r2.user_input_text, '第二轮问题');
    assert.equal(r2.ai_response_text, '第二轮回复');
    assert.equal(r2.generate_time, Date.parse('2026-06-01T12:20:00.000Z'));
    delete process.env.REASONIX_HOME;
  });

  test('testCollect_InProgressTurn_NotEmittedUntilModelFinal', async () => {
    const home = makeReasonixHome({ [REPO_ROOT]: {} });
    const chat = [
      { role: 'user', content: '问题一' },
      { role: 'assistant', content: '回复一' },
      { role: 'user', content: '问题二' },
      { role: 'assistant', content: '回复二' }, // 尚无 model.final → 轮次未完结
    ];
    const events = makeEvents('x', [{ turn: 1, finals: [{ content: '回复一', ts: '2026-06-01T12:05:00.000Z' }] }]);
    writeSession(home, 'code-airedge-20260601120000.jsonl', chat, events);
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-state-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;

    // 第一遍：第 2 轮未完结 → 只收 1 条
    assert.equal(await collectReasonixSessions(deps), 1);

    // 第 2 轮 model.final 落盘后再次收集 → 补收第 2 条
    events.push({ id: 3, ts: '2026-06-01T12:30:00.000Z', turn: 2, type: 'model.final', content: '回复二' });
    writeSession(home, 'code-airedge-20260601120000.jsonl', chat, events);
    assert.equal(await collectReasonixSessions(deps), 1);
    assert.equal(deps.appended, 2);
    assert.equal(deps.events[1].user_input_text, '问题二');
    assert.equal(deps.events[1].generate_time, Date.parse('2026-06-01T12:30:00.000Z'));
    delete process.env.REASONIX_HOME;
  });

  test('testCollect_OtherRepoSession_Skipped_AndWatermarkUntouched', async () => {
    const otherRepo = mkdtempSync(join(tmpdir(), 'rx-other-'));
    createdTmpDirs.push(otherRepo);
    const home = makeReasonixHome({ [otherRepo]: {} }); // 非 git 仓库 → gitRootOf 为 null ≠ repoRoot
    writeSession(
      home,
      'code-other_repo-20260601120000.jsonl',
      [
        { role: 'user', content: '别处的问题' },
        { role: 'assistant', content: '别处的回复' },
      ],
      makeEvents('x', [{ turn: 1, finals: [{ content: '别处的回复', ts: '2026-06-01T12:05:00.000Z' }] }]),
    );
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-state-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;

    assert.equal(await collectReasonixSessions(deps), 0);
    assert.equal(deps.appended, 0);
    // 水位不推进：state 文件不存在该会话 key
    const stateFile = join(telemetryDir, 'cache', 'reasonix-collect-state.json');
    assert.ok(existsSync(stateFile));
    const state = JSON.parse(readFileSync(stateFile, 'utf8'));
    assert.ok(!Object.keys(state.files).some((k) => k.includes('code-other_repo')));
    delete process.env.REASONIX_HOME;
  });

  test('testCollect_AcpSession_Skipped', async () => {
    const home = makeReasonixHome({ [REPO_ROOT]: {} });
    writeSession(
      home,
      'acp-20260601120000.jsonl',
      [
        { role: 'user', content: 'acp 问题' },
        { role: 'assistant', content: 'acp 回复' },
      ],
      makeEvents('x', [{ turn: 1, finals: [{ content: 'acp 回复', ts: '2026-06-01T12:05:00.000Z' }] }]),
    );
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-state-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;

    assert.equal(await collectReasonixSessions(deps), 0);
    assert.equal(deps.appended, 0);
    delete process.env.REASONIX_HOME;
  });

  test('testCollect_FallbackToTranscriptPath_WhenRegistryMisses', async () => {
    // 注册表为空：token 匹配不到，但转录里的绝对路径能定位到本仓库 → 仍收集
    const home = makeReasonixHome({});
    writeSession(
      home,
      'code-unknownproj-20260601120000.jsonl',
      [
        { role: 'user', content: '本仓库的问题' },
        { role: 'tool', name: 'read_file', content: `{"path": "${REPO_ROOT}/backend/pom.xml"}` },
        { role: 'assistant', content: '本仓库的回复' },
      ],
      makeEvents('x', [{ turn: 1, finals: [{ content: '本仓库的回复', ts: '2026-06-01T12:05:00.000Z' }] }]),
    );
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-state-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;

    const n = await collectReasonixSessions(deps);
    assert.equal(n, 1);
    assert.equal(deps.events[0].user_input_text, '本仓库的问题');
    assert.equal(deps.events[0].ai_response_text, '本仓库的回复');
    delete process.env.REASONIX_HOME;
  });
});

describe('textOf', () => {
  test('testTextOf_StringAndBlocks', () => {
    assert.equal(textOf('hello'), 'hello');
    assert.equal(textOf('   '), null);
    assert.equal(textOf([{ type: 'text', text: 'a' }, { type: 'text', text: 'b' }]), 'a\nb');
    assert.equal(textOf([{ type: 'tool_use' }]), null);
    assert.equal(textOf(null), null);
  });
});

// ---------------------------------------------------------------------------
// 新版 v4（%APPDATA%\reasonix\projects\<dir>\sessions-v4\<hash>\events.frames）
// ---------------------------------------------------------------------------

const hasZstd = typeof zlib.zstdCompressSync === 'function' && typeof zlib.zstdDecompressSync === 'function';

describe('collectReasonixSessions · v4', { skip: !hasZstd && '需要 Node ≥22.15（内置 zstd）' }, () => {
  test('testCollect_V4Session_ExtractsRoundsWithCommitTs_AndExcludesInjectedContext', async () => {
    const home = makeV4Home([REPO_ROOT]);
    const T = {
      t1: '2026-09-21T01:00:00.000Z',
      t2: '2026-09-21T01:01:00.000Z',
      t3: '2026-09-21T01:02:00.000Z',
      t4: '2026-09-21T01:03:00.000Z',
      t5: '2026-09-21T01:04:00.000Z',
      t6: '2026-09-21T01:05:00.000Z',
    };
    // 文件 mtime 设为 2 分钟前 → 已静默，末尾轮次也会收
    const hash = 'a1b2c3d4';
    writeV4Session(home, REPO_ROOT, hash, v4Records([
      { ts: T.t1, events: [{ seq: 1, role: 'system', content: 'You are Reasonix' }] },
      { ts: T.t2, events: [{ seq: 2, role: 'user', content: '<session-context> host injected' }] }, // 注入，无 raw_content
      { ts: T.t3, events: [{ seq: 3, role: 'user', content: '<reasoning-language>…问题一', raw: '问题一', createdAt: 1780000000000 }] },
      { ts: T.t4, events: [{ seq: 4, role: 'assistant', content: '回复一' }] },
      { ts: T.t5, events: [{ seq: 5, role: 'user', content: '<reasoning-language>…问题二', raw: '问题二' }] },
      { ts: T.t6, events: [{ seq: 6, role: 'assistant', content: '回复二' }] },
    ]), Date.now() - 120000);

    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-state-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;

    const n = await collectReasonixSessions(deps);

    assert.equal(n, 2);
    assert.equal(deps.appended, 2);
    const [r1, r2] = deps.events;
    assert.equal(r1.user_input_text, '问题一');
    assert.equal(r1.ai_response_text, '回复一');
    assert.equal(r1.generate_time, Date.parse(T.t4)); // 提交级：回复一所在 commit 时间
    assert.equal(r1.agent_tool, 'reasonix');
    assert.equal(r2.user_input_text, '问题二');
    assert.equal(r2.ai_response_text, '回复二');
    assert.equal(r2.generate_time, Date.parse(T.t6));
    delete process.env.REASONIX_HOME;
  });

  test('testCollect_V4LastRound_NotSettled_Deferred', async () => {
    // 新文件（mtime≈now，未静默）：第 1 轮在边界收，末尾第 2 轮推迟
    const home = makeV4Home([REPO_ROOT]);
    const hash = 'b2c3d4e5';
    writeV4Session(home, REPO_ROOT, hash, v4Records([
      { ts: '2026-09-21T02:00:00.000Z', events: [{ seq: 1, role: 'user', content: 'x', raw: '问题一' }] },
      { ts: '2026-09-21T02:01:00.000Z', events: [{ seq: 2, role: 'assistant', content: '回复一' }] },
      { ts: '2026-09-21T02:02:00.000Z', events: [{ seq: 3, role: 'user', content: 'y', raw: '问题二' }] },
      { ts: '2026-09-21T02:03:00.000Z', events: [{ seq: 4, role: 'assistant', content: '回复二' }] },
    ]));
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-state-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;

    assert.equal(await collectReasonixSessions(deps), 1);
    assert.equal(deps.appended, 1);
    assert.equal(deps.events[0].user_input_text, '问题一');
    delete process.env.REASONIX_HOME;
  });

  test('testCollect_V4LastRound_Settled_Collected', async () => {
    // 文件 mtime 拨到 2 分钟前（已静默）：末尾第 2 轮一并收齐
    const home = makeV4Home([REPO_ROOT]);
    const hash = 'c5d6e7f8';
    writeV4Session(home, REPO_ROOT, hash, v4Records([
      { ts: '2026-09-21T03:00:00.000Z', events: [{ seq: 1, role: 'user', content: 'x', raw: '问题一' }] },
      { ts: '2026-09-21T03:01:00.000Z', events: [{ seq: 2, role: 'assistant', content: '回复一' }] },
      { ts: '2026-09-21T03:02:00.000Z', events: [{ seq: 3, role: 'user', content: 'y', raw: '问题二' }] },
      { ts: '2026-09-21T03:03:00.000Z', events: [{ seq: 4, role: 'assistant', content: '回复二' }] },
    ]), Date.now() - 120000);
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-state-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;

    assert.equal(await collectReasonixSessions(deps), 2);
    assert.equal(deps.appended, 2);
    assert.equal(deps.events[0].user_input_text, '问题一');
    assert.equal(deps.events[1].user_input_text, '问题二');
    delete process.env.REASONIX_HOME;
  });

  test('testCollect_V4OtherRepoProject_Skipped_AndWatermarkUntouched', async () => {
    const otherRepo = mkdtempSync(join(tmpdir(), 'rx-v4-other-'));
    createdTmpDirs.push(otherRepo);
    const home = makeV4Home([otherRepo]); // 非 git 仓库 → gitRootOf 为 null ≠ repoRoot
    writeV4Session(home, otherRepo, 'c3d4e5f6', v4Records([
      { ts: '2026-09-21T03:00:00.000Z', events: [{ seq: 1, role: 'user', content: 'x', raw: '别处问题' }] },
      { ts: '2026-09-21T03:01:00.000Z', events: [{ seq: 2, role: 'assistant', content: '别处回复' }] },
    ]), Date.now() - 120000);

    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-state-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;

    assert.equal(await collectReasonixSessions(deps), 0);
    assert.equal(deps.appended, 0);
    const stateFile = join(telemetryDir, 'cache', 'reasonix-collect-state.json');
    assert.ok(existsSync(stateFile));
    const state = JSON.parse(readFileSync(stateFile, 'utf8'));
    assert.ok(!Object.keys(state.files).some((k) => k.includes('c3d4e5f6')));
    delete process.env.REASONIX_HOME;
  });

  test('testParseV4Frames_MultiFrame_ConcatenatedRecords', () => {
    // 双帧 fixtures：把压缩内容切成两段 zstd 帧，验证收集器逐帧解压并拼回
    const records = v4Records([
      { ts: '2026-09-21T04:00:00.000Z', events: [{ seq: 1, role: 'user', content: 'x', raw: '问题' }] },
      { ts: '2026-09-21T04:01:00.000Z', events: [{ seq: 2, role: 'assistant', content: '回复' }] },
    ]);
    const jsonl = records.map((r) => JSON.stringify(r)).join('');
    const compressed = zlib.zstdCompressSync(Buffer.from(jsonl, 'utf8'));
    const half = Math.floor(compressed.length / 2);
    const twoFrames = Buffer.concat([
      Buffer.from('RX4F', 'ascii'),
      Buffer.from([0, 0, 1, 2, 0, 0, 1, 0x4f]),
      compressed.subarray(0, half),
      compressed.subarray(half),
    ]);
    const home = makeV4Home([REPO_ROOT]);
    const dir = join(home, 'projects', mangleDir(REPO_ROOT), 'sessions-v4', 'f6e5d4c3');
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, 'events.frames'), twoFrames);

    const evs = parseV4Frames(join(dir, 'events.frames'));
    assert.equal(evs.length, 2);
    assert.equal(evs[0].role, 'user');
    assert.equal(evs[1].role, 'assistant');
    assert.equal(evs[1].commitTs, Date.parse('2026-09-21T04:01:00.000Z'));
  });
});

// ---------------------------------------------------------------------------
// hook 活跃 marker — 实时 hook 链路活跃期间收集器让位（只推进水位不追加），
// marker 失效后自动接管且不重复收集 hook 期轮次
// ---------------------------------------------------------------------------

describe('hook active marker', () => {
  test('testHookActive_TouchThenFresh_ExpiredThenInactive', () => {
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-state-'));
    createdTmpDirs.push(telemetryDir);

    assert.equal(isHookActive(telemetryDir), false); // 无 marker → 收集器接管

    touchHookActiveMarker(telemetryDir);
    assert.equal(isHookActive(telemetryDir), true);

    // marker 停更超过 24h 窗口 → 视为 hook 链路失效
    const markerFile = hookActiveMarkerPath(telemetryDir);
    const stale = JSON.parse(readFileSync(markerFile, 'utf8'));
    stale.updated = Date.now() - 25 * 60 * 60 * 1000;
    writeFileSync(markerFile, JSON.stringify(stale));
    assert.equal(isHookActive(telemetryDir), false);
  });

  test('testCollect_HookActive_SkipsAppends_ButAdvancesWatermark_NoBackfillDuplication', async () => {
    const home = makeReasonixHome({ [REPO_ROOT]: {} });
    const chat = [
      { role: 'user', content: '第一轮问题' },
      { role: 'assistant', content: '第一轮回复' },
    ];
    const events = makeEvents('x', [{ turn: 1, finals: [{ content: '第一轮回复', ts: '2026-06-01T12:05:00.000Z' }] }]);
    writeSession(home, 'code-airedge-20260601120000.jsonl', chat, events);
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-state-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;

    // hook 链路活跃（marker 新鲜）→ 不追加（该轮由 hook 实时上报）
    touchHookActiveMarker(telemetryDir);
    assert.equal(await collectReasonixSessions(deps), 0);
    assert.equal(deps.appended, 0);

    // marker 失效（信任撤销/hooks 下线）→ 接管，但水位已推进到头部：
    // hook 期的轮次不得被再收一遍（防止回填重复）
    const markerFile = hookActiveMarkerPath(telemetryDir);
    rmSync(markerFile, { force: true });
    assert.equal(await collectReasonixSessions(deps), 0);
    assert.equal(deps.appended, 0);

    // 之后的"新"轮次正常收集
    chat.push(
      { role: 'user', content: '第二轮问题' },
      { role: 'assistant', content: '第二轮回复' },
    );
    events.push({ id: 3, ts: '2026-06-01T13:00:00.000Z', turn: 2, type: 'model.final', content: '第二轮回复' });
    writeSession(home, 'code-airedge-20260601120000.jsonl', chat, events);
    assert.equal(await collectReasonixSessions(deps), 1);
    assert.equal(deps.appended, 1);
    assert.equal(deps.events[0].user_input_text, '第二轮问题');
    delete process.env.REASONIX_HOME;
  });
});

describe('collectReasonixSessions · Reasonix STUDIO', { concurrency: false }, () => {
  /** system / user(注入+真实输入) / 中间 assistant / 最终 assistant / 第二轮... */
  const studioMessages = [
    { role: 'system', content: 'sys' },
    { role: 'user', content: '<workspace>ctx</workspace><current-date>d</current-date>第一轮真实问题' },
    { role: 'assistant', content: '' },
    { role: 'assistant', content: '第一轮回复' },
    {
      role: 'user',
      content: '<workspace>ctx2</workspace>\n<execution-policy preset="balanced">p</execution-policy>\n第二轮真实问题',
    },
    { role: 'assistant', content: '第二轮回复' },
  ];

  test('testStripStudioInjectedContext_RemovesHostBlocks', () => {
    assert.equal(
      stripStudioInjectedContext(
        '<workspace>w</workspace><current-date>d</current-date><memory-recall>m</memory-recall>\n真实输入',
      ),
      '真实输入',
    );
    assert.equal(stripStudioInjectedContext('<workspace>w</workspace><response-language>r</response-language>'), '');
  });

  test('testCollect_StudioProjectSession_ExtractsRealUserInput', async () => {
    const home = makeV4Home([REPO_ROOT]);
    writeStudioProjectSession(home, REPO_ROOT, 's1', studioMessages, {
      updated_at: '2026-10-28T09:00:00.000Z',
      turns: 2,
    });
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-studio-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;
    try {
      assert.equal(await collectReasonixSessions(deps), 2);
      assert.deepEqual(
        deps.events.map((e) => e.user_input_text),
        ['第一轮真实问题', '第二轮真实问题'],
      );
      assert.deepEqual(
        deps.events.map((e) => e.ai_response_text),
        ['第一轮回复', '第二轮回复'],
      );
      assert.equal(deps.events[0].agent_tool, 'reasonix');
      assert.equal(deps.events[0].ai_model, 'deepseek/deepseek-flash');
    } finally {
      delete process.env.REASONIX_HOME;
    }
  });

  test('testCollect_StudioProjectUsesCkptPrompt_AndMsgIndex', async () => {
    const home = makeV4Home([REPO_ROOT]);
    // jsonl 的 user 内容故意保留注入块/垃圾；真实输入只存在于 ckpt turn 文件
    const messages = [
      { role: 'system', content: 'sys' },
      {
        role: 'user',
        content: '<workspace>ctx</workspace><interrupted-turn-recovery>noise</interrupted-turn-recovery>jsonl 注入',
      },
      { role: 'assistant', content: '第一轮回复' },
      { role: 'user', content: '<workspace>ctx</workspace>jsonl 第二轮注入' },
      { role: 'assistant', content: '第二轮回复' },
    ];
    writeStudioProjectSession(
      home,
      REPO_ROOT,
      's-ckpt',
      messages,
      { updated_at: '2026-10-28T09:00:00.000Z' },
      undefined,
      [
        { turn: 0, time: '2026-10-28T08:23:01.000Z', prompt: '第一轮真实输入', msgIndex: 1 },
        { turn: 1, time: '2026-10-28T08:33:41.000Z', prompt: '第二轮真实输入', msgIndex: 3 },
      ],
    );
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-studio-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;
    try {
      assert.equal(await collectReasonixSessions(deps), 2);
      assert.deepEqual(
        deps.events.map((e) => e.user_input_text),
        ['第一轮真实输入', '第二轮真实输入'],
      );
      assert.deepEqual(
        deps.events.map((e) => e.ai_response_text),
        ['第一轮回复', '第二轮回复'],
      );
      assert.equal(deps.events[0].generate_time, Date.parse('2026-10-28T08:23:01.000Z'));
    } finally {
      delete process.env.REASONIX_HOME;
    }
  });

  test('testCollect_StudioCkptInFlight_DeferredThenCollected', async () => {
    const home = makeV4Home([REPO_ROOT]);
    const messages = [
      { role: 'system', content: 'sys' },
      { role: 'user', content: 'user0' },
      { role: 'assistant', content: '回复0' },
      { role: 'user', content: 'user1' },
      { role: 'assistant', content: '回复1' },
    ];
    const file = writeStudioProjectSession(
      home,
      REPO_ROOT,
      's-inflight',
      messages,
      { updated_at: '2026-10-28T09:00:00.000Z', in_flight_turn: { start_message_index: 3 } },
      undefined,
      [
        { turn: 0, time: '2026-10-28T08:23:01.000Z', prompt: '第一轮', msgIndex: 1 },
        { turn: 1, time: '2026-10-28T08:33:41.000Z', prompt: '第二轮', msgIndex: 3 },
      ],
    );
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-studio-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;
    try {
      assert.equal(await collectReasonixSessions(deps), 1);
      assert.equal(deps.events[0].user_input_text, '第一轮');

      // 轮次结束（meta 去掉 in_flight_turn）后补收第二轮
      writeFileSync(
        file + '.meta',
        JSON.stringify({ id: 's-inflight', model: 'deepseek/deepseek-flash', updated_at: '2026-10-28T09:00:00.000Z' }),
      );
      const now = new Date();
      utimesSync(file, now, now); // 模拟会话文件有新写入（initAt 水位之后）
      assert.equal(await collectReasonixSessions(deps), 1);
      assert.equal(deps.events[1].user_input_text, '第二轮');
    } finally {
      delete process.env.REASONIX_HOME;
    }
  });

  test('testCollect_StudioProjectLastRoundInFlight_Deferred', async () => {
    const home = makeV4Home([REPO_ROOT]);
    // 第 4 条消息（第二轮 user）仍在 in_flight_turn 中 → 只收第一轮
    writeStudioProjectSession(home, REPO_ROOT, 's1', studioMessages, {
      in_flight_turn: { start_message_index: 4 },
    });
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-studio-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;
    try {
      assert.equal(await collectReasonixSessions(deps), 1);
      assert.equal(deps.events[0].user_input_text, '第一轮真实问题');
    } finally {
      delete process.env.REASONIX_HOME;
    }
  });

  test('testCollect_StudioImportedV4Session_Skipped', async () => {
    const home = makeV4Home([REPO_ROOT]);
    writeStudioProjectSession(home, REPO_ROOT, 'v4-migrated', studioMessages, {
      imported_from: { line: '1.x sessions-v4' },
    });
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-studio-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;
    try {
      assert.equal(await collectReasonixSessions(deps), 0);
      assert.equal(deps.appended, 0);
    } finally {
      delete process.env.REASONIX_HOME;
    }
  });

  test('testCollect_StudioDesktopSession_AttributedByHeaderCwd', async () => {
    const home = makeV4Home([REPO_ROOT]);
    const records = v4Records([
      {
        ts: '2026-10-01T00:00:00.000Z',
        events: [
          { seq: 1, role: 'user', content: '<session-context>ctx</session-context>', raw: '桌面真实问题', createdAt: 1790000000000 },
          { seq: 2, role: 'assistant', content: '桌面回复' },
        ],
      },
    ]);
    writeStudioDesktopSession(home, 'desktop-manual-test', REPO_ROOT, records, Date.now() - 120_000);
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-studio-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;
    try {
      assert.equal(await collectReasonixSessions(deps), 1);
      assert.equal(deps.events[0].user_input_text, '桌面真实问题');
      assert.equal(deps.events[0].ai_response_text, '桌面回复');
      assert.equal(deps.events[0].agent_tool, 'reasonix');
    } finally {
      delete process.env.REASONIX_HOME;
    }
  });

  test('testCollect_StudioDesktopOtherRepo_Skipped', async () => {
    const home = makeV4Home([REPO_ROOT]);
    const other = mkdtempSync(join(tmpdir(), 'rx-other-'));
    createdTmpDirs.push(other);
    const records = v4Records([
      {
        ts: '2026-10-01T00:00:00.000Z',
        events: [
          { seq: 1, role: 'user', content: '', raw: '别处的真实问题', createdAt: 1790000000000 },
          { seq: 2, role: 'assistant', content: '别处回复' },
        ],
      },
    ]);
    writeStudioDesktopSession(home, 'desktop-manual-other', other, records, Date.now() - 120_000);
    const telemetryDir = mkdtempSync(join(tmpdir(), 'rx-studio-'));
    createdTmpDirs.push(telemetryDir);
    const deps = makeDeps(telemetryDir);
    process.env.REASONIX_HOME = home;
    try {
      assert.equal(await collectReasonixSessions(deps), 0);
      assert.equal(deps.appended, 0);
    } finally {
      delete process.env.REASONIX_HOME;
    }
  });
});

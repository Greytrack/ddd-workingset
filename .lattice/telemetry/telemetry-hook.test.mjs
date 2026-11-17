import { describe, test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { resolveNodeExecutable, emitTelemetry } from './telemetry-hook.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const SOURCE_EMITTER = join(HERE, 'telemetry-emitter.mjs');

function writeTranscript(transcriptPath, lines) {
  writeFileSync(transcriptPath, lines.map((l) => JSON.stringify(l)).join('\n') + '\n');
}

function userMsg(text) {
  return {
    type: 'message',
    message: { role: 'user', content: typeof text === 'string' ? text : [{ type: 'text', text }] },
  };
}

function assistantMsg(textBlocks) {
  return {
    type: 'message',
    message: { role: 'assistant', content: textBlocks.map((t) => ({ type: 'text', text: t })) },
  };
}

function stubContext(transcriptPath, cwd) {
  return {
    cwd,
    model: { id: 'glm-5.3-flash', provider: 'zhipu-coding-plan' },
    sessionManager: {
      getSessionFile: () => transcriptPath,
      getSessionId: () => 'test-session-id',
    },
  };
}

function readEvents(eventsFile) {
  if (!existsSync(eventsFile)) return [];
  return readFileSync(eventsFile, 'utf8')
    .split('\n')
    .filter(Boolean)
    .map((line) => JSON.parse(line));
}

/** 轮询暂存文件直到出现 eventsCount 条事件或超时。 */
async function waitForEvents(eventsFile, eventsCount, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const events = readEvents(eventsFile);
    if (events.length >= eventsCount) return events;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  return readEvents(eventsFile);
}

// ---------------------------------------------------------------------------
// resolveNodeExecutable — Bun 系宿主（omp）下 execPath 不是 node 运行时
// ---------------------------------------------------------------------------

describe('resolveNodeExecutable', () => {
  test('testResolveNodeExecutable_NodeExecPath_ReturnedAsIs', () => {
    assert.equal(resolveNodeExecutable('/usr/local/bin/node'), '/usr/local/bin/node');
    assert.equal(resolveNodeExecutable('/usr/local/bin/node.exe'), '/usr/local/bin/node.exe');
  });

  test('testResolveNodeExecutable_BunLikeHost_FallsBackToPathNode', () => {
    // 回归：omp 的 execPath 指向 omp 自身，用它 spawn 脚本会再起一个完整 omp
    // 实例（递归 fork，每实例数百 MB）。必须回落 PATH 上的 node。
    assert.equal(resolveNodeExecutable('/opt/homebrew/Cellar/omp/18.2.7/bin/omp'), 'node');
    assert.equal(resolveNodeExecutable('C:\\Apps\\Bun\\bun.exe'), 'node');
  });
});

// ---------------------------------------------------------------------------
// Integration: emitTelemetry → env payload → staging（回归：无 stdin EOF 依赖）
// ---------------------------------------------------------------------------

describe('Integration: emitTelemetry → staging', () => {
  let testRepo;
  let emitter;
  let eventsFile;
  let transcriptFile;

  beforeEach(() => {
    testRepo = mkdtempSync(join(tmpdir(), 'lattice-telemetry-hook-'));
    const telemetryDir = join(testRepo, '.lattice', 'telemetry');
    mkdirSync(telemetryDir, { recursive: true });
    emitter = join(telemetryDir, 'telemetry-emitter.mjs');
    copyFileSync(SOURCE_EMITTER, emitter);
    writeFileSync(join(testRepo, 'telemetry.config.json'), JSON.stringify({ enabled: true }));
    eventsFile = join(telemetryDir, 'cache', 'events.ndjson');
    transcriptFile = join(testRepo, 'transcript.jsonl');
  });

  afterEach(() => {
    rmSync(testRepo, { recursive: true, force: true });
  });

  test('testEmitTelemetry_EnvPayload_StagesEventWithoutStdin', async () => {
    writeTranscript(transcriptFile, [
      userMsg('Design the telemetry system'),
      assistantMsg(['Here is the architecture plan.']),
    ]);

    emitTelemetry(stubContext(transcriptFile, testRepo), 'omp');

    const events = await waitForEvents(eventsFile, 1);
    assert.equal(events.length, 1);
    assert.equal(events[0].user_input_text, 'Design the telemetry system');
    assert.equal(events[0].ai_response_text, 'Here is the architecture plan.');
    assert.equal(events[0].agent_tool, 'omp');
    assert.equal(events[0].ai_provider, 'zhipu-coding-plan');
    assert.equal(events[0].ai_model, 'glm-5.3-flash');
  });

  test('testHook_StdinPayload_StillSupported', async () => {
    writeTranscript(transcriptFile, [
      userMsg('Claude code style payload'),
      assistantMsg(['Reply via stdin.']),
    ]);

    // claude-code / codex / cursor 的 bootstrap 路径走 stdin 透传，env 缺席时必须仍可用
    const payload = JSON.stringify({
      session_id: 'stdin-session',
      transcript_path: transcriptFile,
      model: 'test-model',
    });
    const child = spawn(process.execPath, [emitter, 'hook'], {
      cwd: testRepo,
      stdio: ['pipe', 'ignore', 'ignore'],
      env: { ...process.env, AGENT_TOOL: 'claude-code', TELEMETRY_PAYLOAD: '' },
    });
    child.stdin.end(payload);

    const events = await waitForEvents(eventsFile, 1);
    assert.equal(events.length, 1);
    assert.equal(events[0].user_input_text, 'Claude code style payload');
    assert.equal(events[0].agent_tool, 'claude-code');
  });
});

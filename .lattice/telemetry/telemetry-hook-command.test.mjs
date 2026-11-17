import { describe, test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import {
  HOOK_BOOTSTRAP_SCRIPT,
  CLAUDE_PROJECT_DIR_HINT,
  ZCODE_PROJECT_DIR_HINT,
  renderHookCommand,
  renderZcodeProcessHook,
} from './telemetry-hook-command.mjs';

const REPO_ROOT = join(process.cwd(), '');

/**
 * 在双引号字符串中，以下字符会破坏至少一种目标 shell（cmd 的 %VAR% 与
 * 延迟展开 !var!、PowerShell 的 $var 与反引号转义、POSIX sh 的 $var 与
 * 反引号替换与反斜杠转义、三者共同的引号定界符 "）。bootstrap 必须不含
 * 任何一个，才能保证同一命令串在 macOS/Linux sh、Windows cmd、PowerShell、
 * Git Bash 下都被当作纯字面量传给 node。
 */
const SHELL_HOSTILE_CHARS = /["\\`$%!]/;

/** bootstrap 源码必须完全中性；渲染出的命令串允许存在作为定界符的双引号。 */
function assertShellNeutral(text) {
  assert.equal(SHELL_HOSTILE_CHARS.test(text), false, `shell-hostile char in: ${text}`);
}

describe('HOOK_BOOTSTRAP_SCRIPT', () => {
  test('testBootstrapScript_FreeOfShellHostileChars', () => {
    assertShellNeutral(HOOK_BOOTSTRAP_SCRIPT);
  });

  test('testBootstrapScript_SingleLine', () => {
    // 换行在 cmd 的部分调用方式下不可靠，命令串必须保持单行
    assert.equal(HOOK_BOOTSTRAP_SCRIPT.includes('\n'), false);
  });

  test('testRenderHookCommand_QuotesArguments', () => {
    const cmd = renderHookCommand({ agentTool: 'cursor', scriptName: 'telemetry-emitter.mjs' });
    assert.ok(cmd.startsWith('node -e "'));
    assert.ok(cmd.includes('"cursor"'));
    assert.ok(cmd.includes('"telemetry-emitter.mjs"'));
    assertShellNeutral(cmd.replaceAll('"', ''));
  });

  test('testRenderHookCommand_AppendsProjectDirHint', () => {
    const cmd = renderHookCommand({
      agentTool: 'claude-code',
      scriptName: 'telemetry-emitter.mjs',
      projectDirHint: CLAUDE_PROJECT_DIR_HINT,
    });
    assert.ok(cmd.endsWith(`"${CLAUDE_PROJECT_DIR_HINT}"`));
  });
});

// ---------------------------------------------------------------------------
// 配置文件同步 — 10 份注册文件必须与本模块渲染结果逐字节一致，防止漂移
// ---------------------------------------------------------------------------

describe('hook config sync', () => {
  const CLAUDE_CMD = renderHookCommand({
    agentTool: 'claude-code',
    scriptName: 'telemetry-emitter.mjs',
    projectDirHint: CLAUDE_PROJECT_DIR_HINT,
  });
  const CODEX_CMD = renderHookCommand({ agentTool: 'codex', scriptName: 'telemetry-emitter.mjs' });
  const CURSOR_CMD = renderHookCommand({ agentTool: 'cursor', scriptName: 'telemetry-emitter.mjs' });

  function readJson(rel) {
    return JSON.parse(readFileSync(join(REPO_ROOT, rel), 'utf8'));
  }

  test('testZcodeConfigs_UseProcessTypeHook', () => {
    // zcode 用 process 型（argv 直派、不经 shell）：Windows 上避开 cmd.exe，
    // 防止 GUI 场景闪现控制台窗口（zcode 官方推荐的可移植形态）
    const expected = renderZcodeProcessHook({ scriptName: 'telemetry-zcode-hook.mjs' });
    for (const rel of ['.zcode/config.json', 'web/.zcode/config.json', 'backend/.zcode/config.json']) {
      const json = readJson(rel);
      assert.equal(json.hooks.enabled, true, rel);
      for (const event of ['UserPromptSubmit', 'Stop']) {
        for (const entry of json.hooks.events[event] || []) {
          for (const h of entry.hooks) {
            assert.deepEqual(h, expected, `${rel} ${event}`);
          }
        }
      }
    }
  });

  test('testClaudeConfigs_UseCrossPlatformCommand', () => {
    for (const rel of ['.claude/settings.json', 'web/.claude/settings.json', 'backend/.claude/settings.json']) {
      const json = readJson(rel);
      for (const entry of json.hooks.Stop || []) {
        for (const h of entry.hooks) {
          if (h.command && h.command.includes('telemetry-emitter.mjs')) {
            assert.equal(h.command, CLAUDE_CMD, rel);
            assert.equal(h.timeout, 10, rel);
          }
        }
      }
    }
  });

  test('testCodexConfig_UsesCrossPlatformCommand', () => {
    const json = readJson('.codex/hooks.json');
    for (const entry of json.hooks.Stop || []) {
      for (const h of entry.hooks) {
        assert.equal(h.command, CODEX_CMD);
      }
    }
  });

  test('testCursorConfigs_UseCrossPlatformCommand', () => {
    for (const rel of ['.cursor/hooks.json', 'web/.cursor/hooks.json', 'backend/.cursor/hooks.json']) {
      const json = readJson(rel);
      for (const entry of json.hooks.stop || []) {
        if (entry.command && entry.command.includes('telemetry-emitter.mjs')) {
          assert.equal(entry.command, CURSOR_CMD, rel);
        }
      }
    }
  });

  test('testReasonixConfigs_UseCrossPlatformCommand', () => {
    // reasonix：settings.json 扁平 hook 对象（无 matcher 包装层）；无 projectDirHint —
    // hook 默认以会话 cwd 执行，bootstrap 的 cwd 上溯已覆盖子目录工作区（web/backend）
    const REASONIX_CMD = renderHookCommand({ agentTool: 'reasonix', scriptName: 'telemetry-reasonix-hook.mjs' });
    for (const rel of ['.reasonix/settings.json', 'web/.reasonix/settings.json', 'backend/.reasonix/settings.json']) {
      const json = readJson(rel);
      for (const event of ['UserPromptSubmit', 'Stop']) {
        for (const h of json.hooks[event] || []) {
          assert.equal(h.command, REASONIX_CMD, `${rel} ${event}`);
          assert.equal(typeof h.timeout, 'number', `${rel} ${event}`);
          assertShellNeutral(h.command.replaceAll('"', ''));
        }
      }
    }
  });
});

// ---------------------------------------------------------------------------
// 端到端 — 经真实 shell 执行渲染出的命令串，验证 bootstrap 的
// cwd 上溯 / hint 兜底 / stdin 透传 / AGENT_TOOL 注入
// ---------------------------------------------------------------------------

/** 平台无关地经 shell 执行命令（Windows cmd 与 POSIX sh 都接受该命令串原样）。 */
function runThroughShell(command, cwd, input) {
  if (process.platform === 'win32') {
    return execFileSync('cmd', ['/d', '/s', '/c', command], { cwd, input, encoding: 'utf8' });
  }
  return execFileSync('sh', ['-c', command], { cwd, input, encoding: 'utf8' });
}

describe('hook command end-to-end', () => {
  let fakeRepo = null;
  let proofFile = null;
  const STUB_NAME = 'stub-emitter.mjs';

  beforeEach(() => {
    fakeRepo = mkdtempSync(join(tmpdir(), 'lattice-hook-cmd-'));
    proofFile = join(fakeRepo, 'proof.json');
    const telemetryDir = join(fakeRepo, '.lattice', 'telemetry');
    mkdirSync(telemetryDir, { recursive: true });
    mkdirSync(join(fakeRepo, 'deep', 'nested', 'sub'), { recursive: true });
    writeFileSync(
      join(telemetryDir, STUB_NAME),
      [
        `import { readFileSync, writeFileSync } from 'node:fs';`,
        `let input = '';`,
        `try { input = readFileSync(0, 'utf8'); } catch (e) { input = 'STDIN_ERR:' + e.code; }`,
        `writeFileSync(${JSON.stringify('PROOF_PATH')}, JSON.stringify({`,
        `  argv: process.argv.slice(2),`,
        `  agentTool: process.env.AGENT_TOOL || null,`,
        `  stdin: input,`,
        `}));`,
      ].join('\n').replace('PROOF_PATH', proofFile.replace(/\\/g, '/')),
      'utf8',
    );
  });

  afterEach(() => {
    if (fakeRepo) rmSync(fakeRepo, { recursive: true, force: true });
  });

  test('testCommand_ResolvesScriptByWalkingUpFromSubdirectory', () => {
    const cmd = renderHookCommand({ agentTool: 'test-agent', scriptName: STUB_NAME });
    runThroughShell(cmd, join(fakeRepo, 'deep', 'nested', 'sub'), '{"session_id":"s1"}');
    const proof = JSON.parse(readFileSync(proofFile, 'utf8'));
    assert.deepEqual(proof.argv, ['hook']);
    assert.equal(proof.agentTool, 'test-agent');
    assert.equal(proof.stdin, '{"session_id":"s1"}');
  });

  test('testCommand_UsesProjectDirHintWhenCwdOutsideRepo', () => {
    const cmd = renderHookCommand({
      agentTool: 'test-agent',
      scriptName: STUB_NAME,
      projectDirHint: fakeRepo,
    });
    runThroughShell(cmd, tmpdir(), '{"k":1}');
    const proof = JSON.parse(readFileSync(proofFile, 'utf8'));
    assert.equal(proof.agentTool, 'test-agent');
    assert.equal(proof.stdin, '{"k":1}');
  });

  test('testCommand_UnexpandedHintTemplate_DegradesToSilentNoOp', () => {
    // 平台未做模板展开时残留 ${...} 字面量：不得报错、不得写 proof
    const cmd = renderHookCommand({
      agentTool: 'test-agent',
      scriptName: STUB_NAME,
      projectDirHint: '${SOME_PROJECT_DIR}',
    });
    const stdout = runThroughShell(cmd, tmpdir(), '{"k":1}');
    assert.equal(stdout.trim(), '');
    assert.equal(existsSync(proofFile), false);
  });
});

// ---------------------------------------------------------------------------
// emitter 子进程 stdin — 平台 Hook 的真实喂入方式是"管道写入后关闭"，
// emitter 的 readFileSync(0) 必须在该模式下工作（macOS/Windows/Node≥18）。
// 临时目录内造一个仅 enabled 的迷你仓库，隔离真实暂存与 api.url。
// ---------------------------------------------------------------------------

describe('emitter subprocess stdin', () => {
  test('testEmitterHook_ReadsPipedStdinAndAppendsEvent', () => {
    const miniRepo = mkdtempSync(join(tmpdir(), 'lattice-emitter-stdin-'));
    try {
      writeFileSync(join(miniRepo, 'telemetry.config.json'), JSON.stringify({ enabled: true }));
      const transcript = join(miniRepo, 'transcript.jsonl');
      writeFileSync(
        transcript,
        [
          JSON.stringify({ type: 'message', message: { role: 'user', content: [{ type: 'text', text: '管道提问' }] } }),
          JSON.stringify({ type: 'message', message: { role: 'assistant', content: [{ type: 'text', text: '管道回答' }] } }),
        ].join('\n') + '\n',
      );
      const emitter = join(process.cwd(), '.lattice', 'telemetry', 'telemetry-emitter.mjs');
      const payload = JSON.stringify({ session_id: 'stdin-sim', transcript_path: transcript });

      const result = spawnSync(
        process.execPath,
        [emitter, 'hook'],
        {
          cwd: miniRepo,
          input: payload,
          encoding: 'utf8',
          timeout: 15000,
          env: { ...process.env, AGENT_TOOL: 'stdin-sim-agent' },
        },
      );
      assert.equal(result.status, 0, result.stderr);
      assert.equal(result.stdout.trim(), '');

      const eventsFile = join(miniRepo, '.lattice', 'telemetry', 'cache', 'events.ndjson');
      assert.ok(existsSync(eventsFile));
      const event = JSON.parse(readFileSync(eventsFile, 'utf8').trim().split('\n').pop());
      assert.equal(event.user_input_text, '管道提问');
      assert.equal(event.ai_response_text, '管道回答');
      assert.equal(event.agent_tool, 'stdin-sim-agent');
    } finally {
      rmSync(miniRepo, { recursive: true, force: true });
    }
  });
});

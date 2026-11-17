import { describe, test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  buildSyntheticTranscript,
  assistantTextFromPayload,
  savePendingPrompt,
  loadPendingPrompt,
  pendingKey,
  pendingPath,
} from './telemetry-reasonix-hook.mjs';
import { touchHookActiveMarker, isHookActive, hookActiveMarkerPath } from './reasonix-session-collector.mjs';
import { extractMessagesFromTranscript } from './telemetry-emitter.mjs';

const TMP_TRANSCRIPT = join(
  process.cwd(),
  '.lattice',
  'telemetry',
  'cache',
  'test-reasonix-transcript.jsonl',
);

// ---------------------------------------------------------------------------
// buildSyntheticTranscript — 合成转录必须能被 emitter 的解析器识别
// ---------------------------------------------------------------------------

describe('buildSyntheticTranscript', () => {
  test('testBuildSyntheticTranscript_ExtractableByEmitter', () => {
    const content = buildSyntheticTranscript('修复登录页白屏', '已定位并修复，是路由守卫时序问题');
    writeFileSync(TMP_TRANSCRIPT, content);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, '修复登录页白屏');
    assert.equal(assistantText, '已定位并修复，是路由守卫时序问题');
  });

  afterEach(() => {
    if (existsSync(TMP_TRANSCRIPT)) rmSync(TMP_TRANSCRIPT);
  });
});

// ---------------------------------------------------------------------------
// assistantTextFromPayload — reasonix Stop 载荷的 lastAssistantText
// ---------------------------------------------------------------------------

describe('assistantTextFromPayload', () => {
  test('testAssistantTextFromPayload_DirectField', () => {
    const text = assistantTextFromPayload({ event: 'Stop', lastAssistantText: '已修复' });
    assert.equal(text, '已修复');
  });

  test('testAssistantTextFromPayload_EmptyOrMissing_ReturnsNull', () => {
    assert.equal(assistantTextFromPayload({ event: 'Stop' }), null);
    assert.equal(assistantTextFromPayload({ event: 'Stop', lastAssistantText: '   ' }), null);
    assert.equal(assistantTextFromPayload(null), null);
  });
});

// ---------------------------------------------------------------------------
// pending prompt 暂存 / 配对 — 键为 cwd + turn（reasonix 载荷无 session_id）
// ---------------------------------------------------------------------------

describe('pendingPrompt', () => {
  const CWD = '/tmp/rx-test-workspace';
  const TURN = 3;

  beforeEach(() => {
    mkdirSync(join(pendingPath(pendingKey(CWD, TURN)), '..'), { recursive: true });
    rmSync(pendingPath(pendingKey(CWD, TURN)), { force: true });
  });

  afterEach(() => {
    rmSync(pendingPath(pendingKey(CWD, TURN)), { force: true });
  });

  test('testPendingKey_SameInputsStable_DifferentTurnOrCwdDiverge', () => {
    assert.equal(pendingKey(CWD, TURN), pendingKey(CWD, TURN));
    assert.notEqual(pendingKey(CWD, TURN), pendingKey(CWD, TURN + 1));
    assert.notEqual(pendingKey(CWD, TURN), pendingKey('/other', TURN));
  });

  test('testSaveLoadPendingPrompt_Roundtrip', () => {
    savePendingPrompt(CWD, TURN, '帮我跑测试');
    assert.equal(loadPendingPrompt(CWD, TURN), '帮我跑测试');
    // 读取即消费：二次读取为空
    assert.equal(loadPendingPrompt(CWD, TURN), null);
  });

  test('testSaveLoadPendingPrompt_TurnMismatch_NoPairing', () => {
    savePendingPrompt(CWD, TURN, '第一轮问题');
    assert.equal(loadPendingPrompt(CWD, TURN + 1), null);
  });

  test('testSavePendingPrompt_BlankPrompt_NoFile', () => {
    savePendingPrompt(CWD, TURN, '   ');
    savePendingPrompt(CWD, TURN, '');
    savePendingPrompt(CWD, TURN, null);
    assert.equal(existsSync(pendingPath(pendingKey(CWD, TURN))), false);
  });
});

// ---------------------------------------------------------------------------
// hook 活跃 marker — wrapper 依赖收集器导出的触碰/读取函数（单一事实来源）
// ---------------------------------------------------------------------------

describe('hookActiveMarker', () => {
  test('testTouchHookActiveMarker_MarksFresh', () => {
    const telemetryDir = join(process.cwd(), '.lattice', 'telemetry');
    const marker = hookActiveMarkerPath(telemetryDir);
    const existed = existsSync(marker);
    const before = existed ? JSON.parse(readFileSync(marker, 'utf8')).updated : 0;

    touchHookActiveMarker(telemetryDir);
    assert.equal(isHookActive(telemetryDir), true);
    const after = JSON.parse(readFileSync(marker, 'utf8')).updated;
    assert.ok(after >= before);

    if (!existed) rmSync(marker, { force: true }); // 测试前不存在则不留痕
  });
});

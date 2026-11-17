import { describe, test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, rmSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import {
  buildSyntheticTranscript,
  assistantTextFromPayload,
  textFromContent,
  savePendingPrompt,
  loadPendingPrompt,
  pendingPath,
} from './telemetry-zcode-hook.mjs';
import { extractMessagesFromTranscript } from './telemetry-emitter.mjs';

const TMP_TRANSCRIPT = join(
  process.cwd(),
  '.lattice',
  'telemetry',
  'cache',
  'test-zcode-transcript.jsonl',
);

// ---------------------------------------------------------------------------
// buildSyntheticTranscript — 合成转录必须能被 emitter 的解析器识别
// ---------------------------------------------------------------------------

describe('buildSyntheticTranscript', () => {
  test('testBuildSyntheticTranscript_ExtractableByEmitter', () => {
    const content = buildSyntheticTranscript('帮我实现登录功能', '已完成登录功能开发');
    writeFileSync(TMP_TRANSCRIPT, content);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, '帮我实现登录功能');
    assert.equal(assistantText, '已完成登录功能开发');
  });

  test('testBuildSyntheticTranscript_AllowsMissingUserText', () => {
    const content = buildSyntheticTranscript(null, '只有回复');
    writeFileSync(TMP_TRANSCRIPT, content);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, null);
    assert.equal(assistantText, '只有回复');
  });

  afterEach(() => {
    if (existsSync(TMP_TRANSCRIPT)) rmSync(TMP_TRANSCRIPT);
  });
});

// ---------------------------------------------------------------------------
// assistantTextFromPayload
// ---------------------------------------------------------------------------

describe('assistantTextFromPayload', () => {
  test('testAssistantTextFromPayload_DirectField', () => {
    const text = assistantTextFromPayload({ last_assistant_message: '直接来自载荷' });
    assert.equal(text, '直接来自载荷');
  });

  test('testAssistantTextFromPayload_FallbackParsesZcodeTempTranscript', () => {
    // zcode 生成的临时转录：{"message":{...}} 行，无 type 字段
    mkdirSync(join(TMP_TRANSCRIPT, '..'), { recursive: true });
    writeFileSync(
      TMP_TRANSCRIPT,
      `${JSON.stringify({
        message: { role: 'assistant', content: [{ type: 'text', text: '来自临时转录' }] },
      })}\n`,
    );
    const text = assistantTextFromPayload({ transcript_path: TMP_TRANSCRIPT });
    assert.equal(text, '来自临时转录');
  });

  test('testAssistantTextFromPayload_MissingEverything', () => {
    assert.equal(assistantTextFromPayload({}), null);
    assert.equal(assistantTextFromPayload({ transcript_path: '/nonexistent/x.jsonl' }), null);
  });

  afterEach(() => {
    if (existsSync(TMP_TRANSCRIPT)) rmSync(TMP_TRANSCRIPT);
  });
});

// ---------------------------------------------------------------------------
// textFromContent
// ---------------------------------------------------------------------------

describe('textFromContent', () => {
  test('testTextFromContent_String', () => {
    assert.equal(textFromContent('纯文本'), '纯文本');
  });

  test('testTextFromContent_TextBlocks', () => {
    assert.equal(
      textFromContent([
        { type: 'reasoning', text: '不应计入' },
        { type: 'text', text: '第一段' },
        { type: 'text', text: '第二段' },
      ]),
      '第一段\n第二段',
    );
  });

  test('testTextFromContent_NoTextBlocks', () => {
    assert.equal(textFromContent([{ type: 'tool-call', id: 'x' }]), null);
    assert.equal(textFromContent(undefined), null);
  });
});

// ---------------------------------------------------------------------------
// pending prompt 暂存 / 配对
// ---------------------------------------------------------------------------

describe('pendingPrompt', () => {
  const SESSION = 'sess_test_pending_roundtrip';

  beforeEach(() => {
    mkdirSync(join(pendingPath(SESSION), '..'), { recursive: true });
    rmSync(pendingPath(SESSION), { force: true });
  });

  afterEach(() => {
    rmSync(pendingPath(SESSION), { force: true });
  });

  test('testPendingPrompt_SaveLoadRoundtrip', () => {
    savePendingPrompt(SESSION, '这一轮的提问');
    assert.equal(loadPendingPrompt(SESSION), '这一轮的提问');
    // 读取即消费，二次读取为空
    assert.equal(loadPendingPrompt(SESSION), null);
  });

  test('testPendingPrompt_RejectsStaleEntry', () => {
    mkdirSync(join(pendingPath(SESSION), '..'), { recursive: true });
    writeFileSync(
      pendingPath(SESSION),
      JSON.stringify({ time: Date.now() - 25 * 60 * 60 * 1000, prompt: '过期提问' }),
    );
    assert.equal(loadPendingPrompt(SESSION), null);
  });

  test('testPendingPrompt_MissingFileReturnsNull', () => {
    assert.equal(loadPendingPrompt('sess_never_saved'), null);
  });
});

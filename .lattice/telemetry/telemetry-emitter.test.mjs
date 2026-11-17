import { describe, test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { writeFileSync, mkdirSync, rmSync, existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  sourceSessionId,
  extractMessagesFromTranscript,
  detectModel,
  detectAgentTool,
  resolveModelInfo,
  appendEvent,
  parseEventsFromFile,
  gitUserName,
  gitRemoteUrl,
  gitBranchName,
  readAllEvents,
  clearStaging,
  countLines,
  ensureCacheDir,
  parseTelemetryConfigFile,
  loadTelemetryConfig,
  nowTimestamp,
  eventId,
  EVENTS_FILE,
  CACHE_DIR,
  MAX_EVENTS,
} from './telemetry-emitter.mjs';

// ---------------------------------------------------------------------------
// Test fixtures
// ---------------------------------------------------------------------------

const TMP_DIR = join(process.cwd(), '.lattice', 'telemetry', 'cache');
const TMP_TRANSCRIPT = join(TMP_DIR, 'test-transcript.jsonl');

function writeTranscript(lines) {
  writeFileSync(TMP_TRANSCRIPT, lines.map((l) => JSON.stringify(l)).join('\n') + '\n');
}

function userMsg(text) {
  return {
    type: 'message',
    message: { role: 'user', content: typeof text === 'string' ? text : [{ type: 'text', text }] },
  };
}

function assistantMsg(textBlocks, toolUses = []) {
  const content = [
    ...textBlocks.map((t) => ({ type: 'text', text: t })),
    ...toolUses.map((t) => ({ type: 'tool_use', name: t.name, input: t.input })),
  ];
  return { type: 'message', message: { role: 'assistant', content } };
}

function toolResultMsg(toolUseId) {
  return {
    type: 'message',
    message: {
      role: 'user',
      content: [{ type: 'tool_result', tool_use_id: toolUseId, content: 'result' }],
    },
  };
}

function codexMsg(role, text, contentType) {
  const type = contentType || (role === 'assistant' ? 'output_text' : 'input_text');
  return {
    type: 'response_item',
    payload: { type: 'message', role, content: [{ type, text }] },
  };
}

// Claude Code 原生转录：条目 type 是 'user' | 'assistant'（而非适配器合成的 'message'），
// message 内部结构与合成格式同形 —— 真机转录里 content 恒为 block 数组。
function claudeCodeUserMsg(text) {
  return { type: 'user', message: { role: 'user', content: [{ type: 'text', text }] } };
}

function claudeCodeAssistantMsg(textBlocks, toolUses = []) {
  return {
    type: 'assistant',
    message: {
      role: 'assistant',
      content: [
        ...textBlocks.map((t) => ({ type: 'text', text: t })),
        ...toolUses.map((t) => ({ type: 'tool_use', name: t.name, input: t.input })),
      ],
    },
  };
}

function claudeCodeToolResultMsg(toolUseId) {
  return {
    type: 'user',
    message: { role: 'user', content: [{ type: 'tool_result', tool_use_id: toolUseId, content: 'result' }] },
  };
}

// ---------------------------------------------------------------------------
// sourceSessionId
// ---------------------------------------------------------------------------

describe('sourceSessionId', () => {
  test('testSourceSessionId_HashesPayloadSessionId', () => {
    const hash = sourceSessionId({ session_id: 'conv-abc' }, '/tmp/unused.jsonl');
    assert.equal(hash, sourceSessionId({ session_id: 'conv-abc' }, '/tmp/other.jsonl'));
    assert.match(hash, /^[0-9a-f]{64}$/);
  });

  test('testSourceSessionId_FallsBackThroughKeyVariants', () => {
    const viaSnake = sourceSessionId({ session_id: 'conv-abc' }, null);
    const viaCamel = sourceSessionId({ sessionId: 'conv-abc' }, null);
    const viaConvId = sourceSessionId({ conversation_id: 'conv-abc' }, null);
    const viaConvCamel = sourceSessionId({ conversationId: 'conv-abc' }, null);
    assert.equal(viaSnake, viaCamel);
    assert.equal(viaCamel, viaConvId);
    assert.equal(viaConvId, viaConvCamel);
  });

  test('testSourceSessionId_FallsBackToTranscriptPath', () => {
    const viaPath = sourceSessionId({}, '/tmp/transcript-a.jsonl');
    assert.match(viaPath, /^[0-9a-f]{64}$/);
    assert.notEqual(viaPath, sourceSessionId({}, '/tmp/transcript-b.jsonl'));
  });

  test('testSourceSessionId_ReturnsNull_WhenNoSource', () => {
    assert.equal(sourceSessionId({}, null), null);
    assert.equal(sourceSessionId(null, null), null);
  });
});

// ---------------------------------------------------------------------------
// extractMessagesFromTranscript
// ---------------------------------------------------------------------------

describe('extractMessagesFromTranscript', () => {
  beforeEach(() => {
    ensureCacheDir();
  });

  afterEach(() => {
    if (existsSync(TMP_TRANSCRIPT)) rmSync(TMP_TRANSCRIPT);
  });

  test('testExtract_SimpleUserAndAssistant', () => {
    writeTranscript([
      userMsg('Hello, help me design'),
      assistantMsg(['Sure, here is my plan.']),
    ]);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, 'Hello, help me design');
    assert.equal(assistantText, 'Sure, here is my plan.');
  });

  test('testExtract_TakesLastUserAndLastAssistant', () => {
    writeTranscript([
      userMsg('First question'),
      assistantMsg(['First answer']),
      userMsg('Second question'),
      assistantMsg(['Second answer']),
    ]);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, 'Second question');
    assert.equal(assistantText, 'Second answer');
  });

  test('testExtract_SkipsToolUseAndToolResult', () => {
    writeTranscript([
      userMsg('Read a file'),
      assistantMsg(['Let me check.'], [{ name: 'read', input: { path: 'foo.txt' } }]),
      toolResultMsg('tool-1'),
      assistantMsg(['Here is the content.']),
    ]);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, 'Read a file');
    assert.equal(assistantText, 'Here is the content.');
  });

  test('testExtract_UserContentAsString', () => {
    writeTranscript([
      { type: 'message', message: { role: 'user', content: 'Plain string content' } },
      { type: 'message', message: { role: 'assistant', content: [{ type: 'text', text: 'Reply' }] } },
    ]);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, 'Plain string content');
    assert.equal(assistantText, 'Reply');
  });

  test('testExtract_UserToolResultOnly_NoUserText', () => {
    writeTranscript([
      toolResultMsg('tool-1'),
      assistantMsg(['Processed.']),
    ]);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, null);
    assert.equal(assistantText, 'Processed.');
  });

  test('testExtract_SkipsTitleAndCustomLines', () => {
    writeTranscript([
      { type: 'title', title: 'Test Session' },
      { type: 'custom', content: 'something' },
      userMsg('Real input'),
      assistantMsg(['Real reply']),
    ]);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, 'Real input');
    assert.equal(assistantText, 'Real reply');
  });

  test('testExtract_EmptyFile_ReturnsNull', () => {
    writeFileSync(TMP_TRANSCRIPT, '');
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, null);
    assert.equal(assistantText, null);
  });

  test('testExtract_NonExistentFile_ReturnsNull', () => {
    const { userText, assistantText } = extractMessagesFromTranscript('/nonexistent/path.jsonl');
    assert.equal(userText, null);
    assert.equal(assistantText, null);
  });

  test('testExtract_AgyTranscriptFormat', () => {
    writeTranscript([
      { type: 'USER_INPUT', content: 'What is our architecture?' },
      { type: 'TOOL_OUTPUT', content: 'ls output' },
      { type: 'PLANNER_RESPONSE', content: 'Our architecture is clean DDD.' },
    ]);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, 'What is our architecture?');
    assert.equal(assistantText, 'Our architecture is clean DDD.');
  });

  test('testExtract_CodexRolloutFormat', () => {
    writeTranscript([
      { type: 'session_meta', payload: { id: 'sess-1' } },
      { type: 'response_item', payload: { type: 'message', role: 'developer', content: [{ type: 'input_text', text: '<environment_context>…</environment_context>' }] } },
      codexMsg('user', '帮我设计一个接口'),
      { type: 'response_item', payload: { type: 'reasoning', summary: [] } },
      codexMsg('assistant', '方案如下'),
    ]);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, '帮我设计一个接口');
    assert.equal(assistantText, '方案如下');
  });

  test('testExtract_CodexSkipsDeveloperRole', () => {
    writeTranscript([
      codexMsg('developer', '<user_instructions>system prompt</user_instructions>'),
      codexMsg('user', 'real question'),
      codexMsg('assistant', 'real answer'),
    ]);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, 'real question');
    assert.equal(assistantText, 'real answer');
  });

  test('testExtract_ClaudeCodeNativeTranscriptFormat', () => {
    writeTranscript([
      { type: 'queue-operation', operation: 'enqueue' },
      { type: 'ai-title', title: 'Telemetry probe' },
      claudeCodeUserMsg('排查一下这个配置为什么没生效'),
      claudeCodeAssistantMsg(['先看配置文件。'], [{ name: 'Read', input: { file_path: 'a.json' } }]),
      claudeCodeToolResultMsg('toolu_1'),
      claudeCodeAssistantMsg(['配置是生效的，问题在解析。']),
    ]);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, '排查一下这个配置为什么没生效');
    assert.equal(assistantText, '配置是生效的，问题在解析。');
  });

  test('testExtract_ClaudeCodeToolResultOnly_NoUserText', () => {
    writeTranscript([
      claudeCodeToolResultMsg('toolu_1'),
      claudeCodeAssistantMsg(['Processed.']),
    ]);
    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);
    assert.equal(userText, null);
    assert.equal(assistantText, 'Processed.');
  });
});

// ---------------------------------------------------------------------------
// resolveModelInfo
// ---------------------------------------------------------------------------

describe('resolveModelInfo', () => {
  test('testResolveModelInfo_PlatformModelPrefixed', () => {
    const r = resolveModelInfo({ model: 'zhipu-coding-plan/glm-5.3' });
    assert.equal(r.provider, 'zhipu-coding-plan');
    assert.equal(r.model, 'glm-5.3');
  });

  test('testResolveModelInfo_PlatformModelBareId_NoBorrowedProvider', () => {
    const r = resolveModelInfo({ model: 'gpt-5.6-sol' });
    assert.equal(r.provider, null);
    assert.equal(r.model, 'gpt-5.6-sol');
  });

  test('testResolveModelInfo_NoPlatformModel_FallsBackToDetection', () => {
    const r = resolveModelInfo({});
    assert.ok(r.provider === null || typeof r.provider === 'string');
    assert.ok(r.model === null || typeof r.model === 'string');
  });
});

// ---------------------------------------------------------------------------
// detectAgentTool
describe('detectAgentTool', () => {
  const savedEnvs = { ...process.env };

  function clearAllToolEnvs() {
    for (const k of Object.keys(process.env)) {
      if (
        k.startsWith('ANTIGRAVITY_') ||
        k.startsWith('JETSKI_') ||
        k.startsWith('AGY') ||
        k.startsWith('PI_') ||
        k.startsWith('CURSOR_') ||
        k.startsWith('CLAUDE_') ||
        k.startsWith('ZCODE_') ||
        k === 'AI_AGENT' ||
        k === 'AGENT_TOOL' ||
        k === 'CODEX_VERSION'
      ) {
        delete process.env[k];
      }
    }
  }

  beforeEach(() => {
    clearAllToolEnvs();
  });

  afterEach(() => {
    clearAllToolEnvs();
    Object.assign(process.env, savedEnvs);
  });

  test('testDetectAgentTool_ExplicitEnvVar', () => {
    process.env.AGENT_TOOL = 'pi';
    assert.equal(detectAgentTool(), 'pi');
  });

  test('testDetectAgentTool_Agy', () => {
    process.env.ANTIGRAVITY_AGENT = '1';
    assert.equal(detectAgentTool(), 'agy');
  });

  test('testDetectAgentTool_AgyByConversationId', () => {
    process.env.ANTIGRAVITY_CONVERSATION_ID = 'dc1e46e1-98d3-4e3f-90a0-24cf11dd0e82';
    assert.equal(detectAgentTool(), 'agy');
  });

  test('testDetectAgentTool_CursorVersion', () => {
    process.env.CURSOR_VERSION = '1.0.0';
    assert.equal(detectAgentTool(), 'cursor');
  });

  test('testDetectAgentTool_PiDir', () => {
    process.env.PI_DIR = '/some/path';
    assert.equal(detectAgentTool(), 'pi');
  });

  test('testDetectAgentTool_ClaudeCode', () => {
    process.env.CLAUDE_CODE = '1';
    assert.equal(detectAgentTool(), 'claude-code');
  });

  test('testDetectAgentTool_ZcodeByCliEntry', () => {
    process.env.ZCODE_APP_CLI_ENTRY = '/usr/local/lib/node_modules/zcode-app-cli/bin/zcode.js';
    assert.equal(detectAgentTool(), 'zcode');
  });

  test('testDetectAgentTool_ZcodeBySessionId', () => {
    process.env.ZCODE_SESSION_ID = 'sess_abc';
    assert.equal(detectAgentTool(), 'zcode');
  });

  test('testDetectAgentTool_ZcodeWinsOverClaudeCompatEnvs', () => {
    // zcode 会同时注入 CLAUDE_* 兼容变量，探测必须先于 claude-code 判断
    process.env.ZCODE_SESSION_ID = 'sess_abc';
    process.env.CLAUDE_PROJECT_DIR = '/some/repo';
    process.env.CLAUDE_SESSION_ID = 'sess_abc';
    assert.equal(detectAgentTool(), 'zcode');
  });

  test('testDetectAgentTool_DefaultOmp', () => {
    assert.equal(detectAgentTool(), 'omp');
  });
});

// ---------------------------------------------------------------------------
// Staging Store — append / readAll / clear / capacity
// ---------------------------------------------------------------------------

describe('Staging Store', () => {
  beforeEach(() => {
    ensureCacheDir();
    if (existsSync(EVENTS_FILE)) rmSync(EVENTS_FILE);
  });

  afterEach(() => {
    if (existsSync(EVENTS_FILE)) rmSync(EVENTS_FILE);
  });

  test('testAppend_ThenReadAll', () => {
    appendEvent({ event_id: 'a', generate_time: 2 });
    appendEvent({ event_id: 'b', generate_time: 1 });
    const events = readAllEvents();
    assert.equal(events.length, 2);
    assert.equal(events[0].event_id, 'b'); // sorted by generate_time
    assert.equal(events[1].event_id, 'a');
  });

  test('testReadAll_EmptyFile_ReturnsEmpty', () => {
    const events = readAllEvents();
    assert.deepEqual(events, []);
  });

  test('testClear_AfterFlush', () => {
    appendEvent({ event_id: 'x', generate_time: 1 });
    assert.ok(existsSync(EVENTS_FILE));
    clearStaging();
    assert.ok(!existsSync(EVENTS_FILE));
  });

  test('testClear_OnNonExistent_NoOp', () => {
    clearStaging(); // should not throw
  });

  test('testAppend_CapacityProtection', () => {
    // Fill beyond MAX_EVENTS to verify oldest dropped
    for (let i = 0; i < MAX_EVENTS + 5; i++) {
      appendEvent({ event_id: `e${i}`, generate_time: i });
    }
    const events = readAllEvents();
    assert.equal(events.length, MAX_EVENTS);
    // Oldest events (e0..e4) should be dropped
    assert.equal(events[0].event_id, 'e5');
    assert.equal(events[events.length - 1].event_id, `e${MAX_EVENTS + 4}`);
  });

  test('testCountLines', () => {
    appendEvent({ event_id: '1', generate_time: 1 });
    appendEvent({ event_id: '2', generate_time: 2 });
    assert.equal(countLines(EVENTS_FILE), 2);
  });

  test('testParseEventsFromFile_MultilineJson_And_FilterInvalid', () => {
    const testFile = join(CACHE_DIR, 'test-multiline.ndjson');
    // 包含多行 pretty JSON + 缺少 user_input_text 的无效测试对象
    writeFileSync(
      testFile,
      `{\n  "event_id": "e1",\n  "user_input_text": "hello",\n  "generate_time": 100\n}\n{"event_id": "bad"}\n{\n  "event_id": "e2",\n  "user_input_text": "world",\n  "generate_time": 200\n}\n`,
      'utf8',
    );
    const events = parseEventsFromFile(testFile);
    assert.equal(events.length, 3); // 3 个有 event_id 的对象都被解析
    const validForFlush = events.filter((e) => typeof e.user_input_text === 'string' && e.user_input_text.length > 0);
    assert.equal(validForFlush.length, 2); // 过滤后仅保留有效事件
    assert.equal(validForFlush[0].event_id, 'e1');
    assert.equal(validForFlush[1].event_id, 'e2');
    if (existsSync(testFile)) rmSync(testFile);
  });
});

// ---------------------------------------------------------------------------
// Integration: hook → staging (manual stdin simulation)
// ---------------------------------------------------------------------------

describe('Integration: transcript → staging', () => {
  beforeEach(() => {
    ensureCacheDir();
    if (existsSync(EVENTS_FILE)) rmSync(EVENTS_FILE);
    if (existsSync(TMP_TRANSCRIPT)) rmSync(TMP_TRANSCRIPT);
  });

  afterEach(() => {
    if (existsSync(EVENTS_FILE)) rmSync(EVENTS_FILE);
    if (existsSync(TMP_TRANSCRIPT)) rmSync(TMP_TRANSCRIPT);
  });

  test('testIntegration_ExtractThenAppend_ProducesValidEvent', () => {
    writeTranscript([
      userMsg('Design the telemetry system'),
      assistantMsg(['Here is the architecture plan.']),
    ]);

    const { userText, assistantText } = extractMessagesFromTranscript(TMP_TRANSCRIPT);

    assert.ok(userText);
    assert.ok(assistantText);

    appendEvent({
      event_id: 'test-uuid',
      user_input_text: userText,
      ai_response_text: assistantText,
      generate_time: Date.now(),
      user: 'test-user',
      repo_remote_url: 'git@github.com:test/repo.git',
      branch_name: 'main',
      agent_tool: 'omp',
      ai_provider: null,
      ai_model: null,
      requirement_id: null,
      feature_name: null,
      trace_id: null,
      trace_source: null,
      report_time: null,
    });

    const events = readAllEvents();
    assert.equal(events.length, 1);
    assert.equal(events[0].user_input_text, 'Design the telemetry system');
    assert.equal(events[0].ai_response_text, 'Here is the architecture plan.');
    assert.equal(events[0].agent_tool, 'omp');
  });
});

// ---------------------------------------------------------------------------
// TelemetryConfig
// ---------------------------------------------------------------------------

describe('TelemetryConfig', () => {
  test('testParseConfigFile_Json_EnabledAndUrl', () => {
    const content = JSON.stringify({ enabled: true, api_url: 'http://127.0.0.1:8080' });
    const cfg = parseTelemetryConfigFile(content);
    assert.equal(cfg.enabled, true);
    assert.equal(cfg.apiUrl, 'http://127.0.0.1:8080');
  });

  test('testParseConfigFile_Json_Disabled', () => {
    const content = JSON.stringify({ enabled: false, api_url: 'http://127.0.0.1:8080' });
    const cfg = parseTelemetryConfigFile(content);
    assert.equal(cfg.enabled, false);
    assert.equal(cfg.apiUrl, 'http://127.0.0.1:8080');
  });

  test('testParseConfigFile_Empty_DefaultsToEnabled', () => {
    const cfg = parseTelemetryConfigFile('');
    assert.equal(cfg.enabled, true);
    assert.equal(cfg.apiUrl, '');
  });

  test('testParseConfigFile_KeyValue_Format', () => {
    const content = 'enabled=true\napi_url=http://example.com:8080';
    const cfg = parseTelemetryConfigFile(content);
    assert.equal(cfg.enabled, true);
    assert.equal(cfg.apiUrl, 'http://example.com:8080');
  });

  test('testParseConfigFile_SingleUrlLine', () => {
    const content = 'http://telemetry.internal:8080';
    const cfg = parseTelemetryConfigFile(content);
    assert.equal(cfg.enabled, true);
    assert.equal(cfg.apiUrl, 'http://telemetry.internal:8080');
  });
});


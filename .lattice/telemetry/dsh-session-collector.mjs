#!/usr/bin/env node
/**
 * Dsh 会话落地收集器 — 从 dsh（DeepSeek Harness）的持久化会话文件中提取
 * 用户/AI 轮次并暂存为 RawEvent。
 *
 * 为什么用落地文件而不是进程内插件：dsh 的 headless 一次性模式不加载任何
 * patch/preset 组合，host 平面插件又因 cordis 作用域隔离收不到 agent 平面
 * 会话的 session/event（2026-09 真机验证）。而所有 dsh 模式（headless/web/tui）
 * 都把会话持久化为 `~/.dsh/sessions/<项目>/<会话>/session*.jsonl.zstd`
 * （多帧 zstd 的 JSONL；compression: 'none' 时为明文 JSONL），头部含 cwd、
 * 事件含完整轮次 —— 读文件是最稳的接入面。
 *
 * 由 telemetry-emitter 的 flush 调用（Stop 钩子节流搭载 + pre-push），按
 * seq 水位增量处理；只收集 git 根等于本仓库的会话（其他仓库由它们自己的
 * flush 收集，天然避免跨仓库重复）。一切失败静默。
 */

import { execFileSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import zlib from 'node:zlib';
import { homedir } from 'node:os';
import { join } from 'node:path';

/** 首次收集只回看 24h 内有更新的会话，避免首次 flush 上传全部历史 */
const INITIAL_LOOKBACK_MS = 24 * 60 * 60 * 1000;
/** 单个压缩会话文件的大小上限（防御异常文件） */
const MAX_COMPRESSED_BYTES = 64 * 1024 * 1024;
const ZSTD_MAGIC = Buffer.from([0x28, 0xb5, 0x2f, 0xfd]);

/**
 * 扫描 dsh 会话目录，把属于本仓库的新轮次追加到暂存。
 * @param {object} deps - emitter 注入的依赖
 * @param {string} deps.repoRoot - 当前仓库根（归属判定基准）
 * @param {string} deps.telemetryDir - 本仓库遥测目录（水位状态文件位置）
 * @param {Function} deps.appendEvent - 暂存追加函数
 * @param {Function} deps.eventId - UUID 生成
 * @param {Function} deps.gitUserName - git user.name
 * @param {Function} deps.gitRemoteUrl - origin 远程地址
 * @param {Function} deps.gitBranchName - 当前分支
 * @param {Function} deps.nowTimestamp - 毫秒时间戳
 * @param {Function} deps.sourceSessionId - 会话标识哈希（payload, transcriptPath）=> hash
 * @returns {Promise<number>} 追加的事件数（供测试断言）
 */
export async function collectDshSessions(deps) {
  const {
    repoRoot,
    telemetryDir,
    appendEvent,
    eventId,
    gitUserName,
    gitRemoteUrl,
    gitBranchName,
    nowTimestamp,
    sourceSessionId,
  } = deps ?? {};
  if (!repoRoot || !telemetryDir || typeof appendEvent !== 'function') return 0;

  const sessionsRoot = dshSessionsRoot();
  if (!sessionsRoot) return 0;
  // zstd 能力检查下沉到逐文件（forEachSessionLine）：Node < 22.15 无内置
  // zstd 时只跳过 .zstd 会话文件（水位不推进，升级 Node 后补收），明文
  // JSONL 会话（compression: 'none'）在任何 Node ≥18 上照常收集。

  const state = readState(telemetryDir);
  const initAt = state.initAt || Date.now() - INITIAL_LOOKBACK_MS;
  const ctx = {
    repoRoot,
    appendEvent,
    eventId,
    sourceSessionId,
    identity: {
      user: gitUserName?.() ?? 'unknown',
      repo_remote_url: gitRemoteUrl?.() ?? '',
      branch_name: gitBranchName?.() ?? '',
    },
  };
  let appendedTotal = 0;

  let projectDirs = [];
  try {
    projectDirs = readdirSync(sessionsRoot, { withFileTypes: true }).filter((e) => e.isDirectory());
  } catch {
    return 0;
  }

  for (const project of projectDirs) {
    const projectDir = join(sessionsRoot, project.name);
    let sessionDirs = [];
    try {
      sessionDirs = readdirSync(projectDir, { withFileTypes: true }).filter((e) => e.isDirectory());
    } catch {
      continue;
    }
    for (const session of sessionDirs) {
      const sessionDir = join(projectDir, session.name);
      for (const name of ['session.v3.jsonl.zstd', 'session.jsonl.zstd', 'session.v3.jsonl', 'session.jsonl']) {
        const file = join(sessionDir, name);
        if (!existsSync(file)) continue;
        try {
          if (statSync(file).mtimeMs < initAt) break; // 历史会话不回溯收集
        } catch {
          break;
        }
        const lastSeqSeen = state.files[file] ?? 0;
        const result = await processSessionFile(file, lastSeqSeen, ctx, nowTimestamp);
        if (result.maxSeq > lastSeqSeen) {
          state.files[file] = result.maxSeq;
          appendedTotal += result.appended;
        }
        break; // 每个会话目录只取一个版本的会话文件
      }
    }
  }

  writeState(telemetryDir, state);
  return appendedTotal;
}

function dshSessionsRoot() {
  const home = process.env.DSH_HOME || join(homedir(), '.dsh');
  const root = join(home, 'sessions');
  return existsSync(root) ? root : null;
}

function readState(telemetryDir) {
  const stateFile = join(telemetryDir, 'cache', 'dsh-collect-state.json');
  try {
    const parsed = JSON.parse(readFileSync(stateFile, 'utf8'));
    if (parsed && typeof parsed === 'object') {
      return { files: parsed.files ?? {}, initAt: parsed.initAt ?? 0 };
    }
  } catch {
    // 无状态文件
  }
  return { files: {}, initAt: 0 };
}

function writeState(telemetryDir, state) {
  const stateFile = join(telemetryDir, 'cache', 'dsh-collect-state.json');
  try {
    writeFileSync(stateFile, JSON.stringify({ ...state, initAt: state.initAt || Date.now() }));
  } catch {
    // silent
  }
}

function gitRootOf(cwd) {
  try {
    return (
      execFileSync('git', ['-C', cwd, 'rev-parse', '--show-toplevel'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        windowsHide: true, // 父进程被隐藏时，git 子进程不另开控制台窗口
      }).trim() || null
    );
  } catch {
    return null;
  }
}

function textOf(content) {
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) {
    const blocks = content.filter((b) => b?.type === 'text' && typeof b.text === 'string');
    if (blocks.length > 0) return blocks.map((b) => b.text).join('\n');
  }
  return null;
}

/**
 * 逐行读取会话文件。zstd 文件为"头部帧 + 追加帧"的多帧拼接，Node 的 zstd
 * 流/Sync API 都只解第一帧，故按帧魔数切分逐帧解压（压流中出现魔数假边界
 * 的概率 ≈ 文件大小/2^32，一旦命中该帧解压抛错即放弃该文件，可接受）。
 * 行回调返回 false 可提前终止。返回 false 表示读取失败。
 */
function forEachSessionLine(file, onLine) {
  let buf;
  try {
    buf = readFileSync(file);
  } catch {
    return false;
  }
  if (file.endsWith('.zstd')) {
    if (typeof zlib.zstdDecompressSync !== 'function') return false;
    if (buf.length > MAX_COMPRESSED_BYTES) return false;
    if (buf.indexOf(ZSTD_MAGIC) !== 0) return false;
    let leftover = '';
    let idx = 0;
    while (idx !== -1 && idx < buf.length) {
      const next = buf.indexOf(ZSTD_MAGIC, idx + 4);
      const end = next === -1 ? buf.length : next;
      let text;
      try {
        text = zlib.zstdDecompressSync(buf.subarray(idx, end)).toString('utf8');
      } catch {
        return false; // 假边界或损坏帧 — 放弃该文件
      }
      const lines = (leftover + text).split('\n');
      leftover = lines.pop();
      for (const line of lines) {
        if (line.length > 0 && onLine(line) === false) return true;
      }
      idx = next;
    }
    if (leftover.length > 0) onLine(leftover);
    return true;
  }
  // 明文 JSONL（compression: 'none'）
  const lines = buf.toString('utf8').split('\n');
  const leftover = lines.pop();
  for (const line of lines) {
    if (line.length > 0 && onLine(line) === false) return true;
  }
  if (leftover.length > 0) onLine(leftover);
  return true;
}

/**
 * 处理单个会话文件：增量提取轮次并 appendEvent。
 * @returns {Promise<{ appended: number, maxSeq: number, ok: boolean }>}
 */
async function processSessionFile(file, lastSeqSeen, ctx, nowTimestamp) {
  let rootMatches = false;
  let maxSeq = lastSeqSeen;
  let turnUser = null;
  let turnAssistant = null;
  let route = null;
  let appended = 0;

  const appendTurnEvent = (userText, assistantText) => {
    ctx.appendEvent({
      event_id: ctx.eventId(),
      user_input_text: userText,
      ai_response_text: assistantText,
      generate_time: nowTimestamp(),
      ...ctx.identity,
      agent_tool: 'dsh',
      source_session_id: ctx.sourceSessionId({ session_id: file }, null),
      ai_provider: route?.provider ?? null,
      ai_model: route?.model ?? null,
      requirement_id: null,
      feature_name: null,
      trace_id: null,
      trace_source: null,
      report_time: null,
    });
    appended += 1;
  };

  const flushTurn = () => {
    if (turnUser && turnAssistant && rootMatches) appendTurnEvent(turnUser, turnAssistant);
    turnUser = null;
    turnAssistant = null;
  };

  const ok = await Promise.resolve(
    forEachSessionLine(file, (line) => {
      let entry;
      try {
        entry = JSON.parse(line);
      } catch {
        return true;
      }
      if (entry?.type === 'session') {
        const cwd = entry.cwd ?? null;
        rootMatches = cwd ? gitRootOf(cwd) === ctx.repoRoot : false;
        return true;
      }
      if (typeof entry?.seq !== 'number') return true;
      if (entry.seq <= lastSeqSeen) return true; // 水位之前的事件已收集
      if (entry.seq > maxSeq) maxSeq = entry.seq;

      switch (entry.type) {
        case 'request/header':
          route = entry.data?.header?.config ?? route;
          break;
        case 'user/message': {
          // 只认真实用户输入；agent-instructions / plugin 注入的上下文一律忽略
          if (entry.data?.source?.kind !== 'user') break;
          const text = textOf(entry.data?.content);
          if (text) turnUser = text;
          break;
        }
        case 'assistant/message': {
          // 每步一条，轮次内最后一条即最终回复
          const text = textOf(entry.data?.message?.content);
          if (text) turnAssistant = text;
          break;
        }
        case 'turn/end':
          flushTurn();
          break;
        default:
          break;
      }
      return true;
    }),
  );

  // 无 turn/end 的尾部轮次（会话被中断）不收集；水位已推进，不会重复误配
  return { appended, maxSeq, ok };
}

import { execFileSync, spawn } from 'node:child_process';
import { basename, join } from 'node:path';

function repoRoot(cwd) {
  try {
    return (
      execFileSync('git', ['rev-parse', '--show-toplevel'], {
        cwd,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        windowsHide: true, // 防止 git 子进程另开控制台窗口
      }).trim() || cwd
    );
  } catch {
    return cwd;
  }
}

/**
 * 解析真实 Node 可执行文件。宿主进程可能是 Bun 系应用（如 omp）：
 * process.execPath 指向 omp 自身而非 node 运行时，用它 spawn emitter 会再起一个
 * 完整 omp 实例 —— 该实例加载项目扩展（telemetry-stop.ts）后在自己的生命周期内
 * 再次 spawn，形成递归 fork（实测 10 分钟衍生三代、每实例 150-270MB RSS）。
 * 因此 execPath 是 node 时直接用；否则回落 PATH 上的 `node`。PATH 上没有 node 时
 * （Windows GUI 场景）spawn 触发 error 事件，由调用方静默吞掉 —— 遥测缺失可接受，
 * 递归 fork 不可接受。
 */
export function resolveNodeExecutable(execPath = process.execPath) {
  const name = basename(execPath).toLowerCase();
  if (name === 'node' || name === 'node.exe') return execPath;
  return 'node';
}

function modelSlug(model) {
  if (!model || typeof model.id !== 'string' || !model.id) return null;
  const provider = model.provider;
  if (typeof provider === 'string' && provider) return `${provider}/${model.id}`;
  if (provider && typeof provider === 'object' && typeof provider.id === 'string' && provider.id) {
    return `${provider.id}/${model.id}`;
  }
  return model.id;
}

/** Best-effort, non-blocking adapter for Pi-family extensions. */
export function emitTelemetry(context, agentTool) {
  try {
    const sessionFile = context?.sessionManager?.getSessionFile?.();
    if (!sessionFile) return;

    const root = repoRoot(context?.cwd || process.cwd());
    // 载荷经环境变量传递而非 stdin：Bun 系宿主对 spawn 子进程的 stdin end() 不保证
    // 送达 EOF，emitter 的 readFileSync(0) 会永久阻塞（实测孤儿进程挂 18 分钟以上，
    // 直到宿主进程退出才被 EOF 释放）。环境变量没有 EOF 语义；emitter 侧 env 优先、
    // stdin 兜底（claude-code / codex / cursor 的 bootstrap 透传路径不受影响）。
    const payload = JSON.stringify({
      session_id: context.sessionManager?.getSessionId?.() ?? null,
      transcript_path: sessionFile,
      model: modelSlug(context.model),
    });
    const child = spawn(
      resolveNodeExecutable(),
      [join(root, '.lattice', 'telemetry', 'telemetry-emitter.mjs'), 'hook'],
      {
        cwd: root,
        stdio: ['ignore', 'ignore', 'ignore'],
        env: { ...process.env, AGENT_TOOL: agentTool, TELEMETRY_PAYLOAD: payload },
        windowsHide: true, // 防止后台进程闪控制台
      },
    );
    child.on('error', () => {});
  } catch {
    // Telemetry must never affect an agent session.
  }
}

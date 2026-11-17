---
feature: "AirFlow Viewport Policy (Initial Zoom / Anchor / Fit)"
requirement_doc: null
created: "2026-07-23"
status: "draft-for-review"
scope: "GateFlow · AirFlow (`<uc-flow-diagram>` / @airedge/uc-graph)"
owner: "frontend · GateFlow"
---

# AirFlow 视口策略交互规格（可评审稿）

> **目的**：在「首次无用户缩放记忆」以及后续导航/resize/显式适应视图等场景下，规定缩放比例如何计算、装不下时锚点对准谁、何时 re-fit / preserve / focus。  
> **非目标**：本次不规定节点布局算法、ELK 参数、过滤开关语义；不改业务数据模型。  
> **现状基线**（实现参考，非本规格最终态）：`setupCanvas → fitToScreen`，padding=40px，fit clamp `[0.05, 1.5]`，手动缩放 `[0.05, 4.0]`，过滤重渲可 preserve transform，active 变化尝试 `_centerOnActive`，window resize 会 re-fit。

---

## 1. 术语

| 术语 | 定义 |
|------|------|
| **视口 (Viewport)** | 画布 shell 的可见矩形 `Vw × Vh`（`clientWidth/Height`） |
| **内容包围盒 (Content BBox)** | 当前可渲染节点 + 泳道等的联合包围盒 `Cw × Ch`（含 measure 边距） |
| **内容锚点 (Anchor)** | 内容坐标系中的一点 \((a_x, a_y)\)，经 transform 映射到视口焦点 |
| **视口焦点 (Focal Point)** | 视口内的归一化位置 \((f_x, f_y)∈[0,1]^2\)，锚点应对准此处（非永远 0.5,0.5） |
| **原始适配比 rawScale** | \(\min((V_w-2p)/C_w,\ (V_h-2p)/C_h)\) |
| **工作缩放 workingScale** | 实际用于主画布的 scale（可能被可读底线抬高） |
| **适应视图 Fit-All** | 显式「看全图」意图：允许低于可读底线 |
| **工作态 Working** | 默认可读浏览态：scale ≥ 可读底线，可能装不下全图 |
| **激活元素 Active** | URL `:identity` / WC `active-id` 对应、且出现在当前渲染结果中的节点 |
| **入口节点 Entry** | 当前图语义起点：Start 事件 / 拓扑序第一 / Phase 时间起点 / 表格左上逻辑原点 |
| **用户缩放记忆** | 用户主动缩放/平移后的 transform（本期可仅会话内；持久化另立决策） |
| **Preserve** | 保持当前 `tx, ty, scale` 不变（或仅做最小修正） |
| **Re-anchor** | 不改变 scale 策略结果的前提下，重算 tx/ty 使锚点对齐焦点 |
| **Re-fit** | 按 Fit-All 或 Prefer-100 重算 scale + 居中 |

### 1.1 两种互斥的主画布职责

| 模式 | 主画布目标 | 全貌由谁承担 |
|------|------------|--------------|
| **Working** | 可读、可点、对准任务兴趣点 | Minimap +「适应视图」 |
| **Fit-All** | 尽量看见整图（可牺牲字号） | 主画布自身 |

**规则 R0**：主画布不得在同一次默认进入中同时承诺「可读」与「全貌」。二者冲突时，默认走 Working；Fit-All 仅由显式动作进入。

---

## 2. 目标与非目标

### 2.1 目标（按优先级）

1. **意图对齐**：用户从树/URL/搜索进入某元素时，第一眼应看到该元素（或其入口），而不是空白或无关区域。  
2. **可读优先**：默认工作态下，节点标题应达到可读底线（见 §3）。  
3. **小图不发空**：内容远小于视口时，不放大到「海报感」；优先 100% 居中。  
4. **大图可导航**：装不下时提供方位感（minimap / 适应视图），且锚点可预测。  
5. **可解释**：同一输入（数据 + active + 事件）→ 同一视口结果；避免「刷新前后不一致」类玄学。

### 2.2 非目标

- 不规定用户缩放记忆的持久化 key 设计（见 Open Questions）。  
- 不规定双指/触控板手势细节（沿用 WC 现有 zoom-require-modifier 等）。  
- 不要求首次实现「密度聚类锚点」等启发式（列为增强）。

---

## 3. 参数表（评审旋钮）

以下为**建议默认值**，实现时应集中配置，禁止散落魔法数。

| ID | 参数 | 建议默认 | 说明 |
|----|------|----------|------|
| P1 | `PAD_RATIO` | `0.10` | 相对视口较短边的留白比例 |
| P2 | `PAD_MIN_PX` | `40` | 绝对留白下限（px） |
| P3 | `MAX_SCALE_FIT` | `1.0` | **Fit / 首次适配**时上限（不超设计分辨率） |
| P4 | `MAX_SCALE_MANUAL` | `4.0` | 用户手动放大上限（可维持现状） |
| P5 | `MIN_SCALE_MANUAL` | `0.05` | 用户手动缩小下限 |
| P6 | `MIN_SCALE_FIT_ALL` | `0.05` | 显式适应视图允许的下限 |
| P7 | `READABLE_FLOOR` | `0.85` | 工作态缩放底线（相对设计 100%；对应 ~13px→≥11px 体感） |
| P8 | `READING_BIAS` | `(0.38, 0.48)` | Working 态锚点映射的视口焦点（略偏左上） |
| P9 | `CENTER_BIAS` | `(0.50, 0.50)` | Fit-All / 小图居中时的视口焦点 |
| P10 | `EDGE_PAD_PX` | `48` | 锚点在内容边缘时，允许露白，但 pan 软限制保留的边距 |
| P11 | `OVERVIEW_HINT` | `true` | rawScale &lt; READABLE_FLOOR 时建议显示 minimap（若用户未永久关闭） |

### 3.1 padding 计算

```text
pad = max(PAD_MIN_PX, min(Vw, Vh) * PAD_RATIO)
```

四边使用同一 `pad`（首版不做非对称 pad；READING_BIAS 已承担阅读方向）。

### 3.2 rawScale

```text
rawScale = min( (Vw - 2*pad) / Cw , (Vh - 2*pad) / Ch )
若 Cw/Ch ≤ 0：视为空图，走 §6.5 空图策略
```

### 3.3 关于 READABLE_FLOOR = 0.85 的说明

- 以 Activity 标题设计字号 **13px** 为参考，有效字号目标 **≥11px** → \(11/13≈0.85\)。  
- 若评审认为 0.85 过高导致「大图永远装不下、频繁进入焦点态」，可降为 **0.70**，但须同步评估命中区与标签可读性。  
- **READABLE_FLOOR 只约束 Working 默认态**，不约束用户手动缩小，也不约束显式 Fit-All。

---

## 4. 缩放策略（Scale Policy）

### 4.1 Prefer-100（小图 / 装得下）

```text
workingScale = min(1.0, rawScale)   # 等价：raw≥1 → 1.0；否则 raw（且 ≤ MAX_SCALE_FIT）
```

- **禁止**默认把小图放大到 &gt;100%（废除现状 fit max=1.5 作为默认）。  
- 用户仍可通过手动放大到 `MAX_SCALE_MANUAL`。

### 4.2 Working-Floor（大图装不下）

当 `rawScale < READABLE_FLOOR`：

```text
workingScale = READABLE_FLOOR
# 全图不可见 → 必须配合锚点策略 §5，不得再对全图 bbox 做「伪 fit」
```

### 4.3 Fit-All（显式适应视图）

```text
fitScale = clamp(rawScale, MIN_SCALE_FIT_ALL, MAX_SCALE_FIT)
# 允许 fitScale < READABLE_FLOOR
# 锚点 = 内容 bbox 中心，bias = CENTER_BIAS
```

### 4.4 Actual-100（恢复 100%）

```text
scale = 1.0
锚点 = 当前 Active（若有）否则 Entry 否则内容中心
bias = 有 Active/Entry 时 READING_BIAS，否则 CENTER_BIAS
```

---

## 5. 锚点策略（Anchor Policy）

### 5.1 优先级（命中即停）

| 优先级 | 条件 | Anchor |
|--------|------|--------|
| **A0** | 显式 Fit-All 本次生效中 | **Content BBox 中心** |
| **A1** | 存在 **Active** 且节点在当前渲染结果中 | **Active 节点中心** |
| **A2** | Active 为容器（Phase / 含子图的父级）且子节点可见 | **容器内 Entry**（Start / 拓扑第一 / 时间最早）；若无 → 容器内子节点 bbox 中心；勿直接用空旷容器框中心 |
| **A3** | 无 Active 或 Active 不可见 | **Entry**（见 §5.2） |
| **A4** | 无 Entry | **主兴趣子图中心**（当前 scope 根的直接可见子节点 bbox；非全图 bbox） |
| **A5** | 兜底 | **Content BBox 中心** |

### 5.2 Entry 按 mode 定义

| mode | Entry |
|------|--------|
| `flow` | Start 类事件；**若无 Start（uc 流程常无显式开始节点）→ 内容 bbox 左上角**。不用"拓扑序第一节点"——多入口 / 无 Start / 有环时它有歧义；内容 bbox 左上角永远良定义，且左→右布局下天然是阅读起点 |
| `phase` / timeline 类 | 最左 / 最早 Phase 或当前 active Phase 的入口 |
| `grid` / `table` | 内容逻辑原点：**左上**（第一行第一列单元格中心或表头下第一格），**不是**几何中心 |
| 其他 / unavailable | Content BBox 中心 |

> **⚠️ 两个 uc 常见边界（务必兜住，否则大图会停在空白）**：
> 1. **无 Start 节点**：uc 流程（尤其刚建模、活动未连线）常无显式 Start → 按上表 flow 兜底到**内容 bbox 左上角**，绝不回退几何中心。
> 2. **URL `:identity` 命中不到渲染节点**（active-id 不在结果中；id/commonId 匹配差、被过滤、identity 是容器/根而非节点）→ **A1 不成立，降级到 A3 Entry**（Start / 左上角），**不得**停在旧视口或几何中心空白。此即锚点优先级 §5.1「A1 要求 Active 在当前渲染结果中」的意义所在。
> 3. 二者叠加（无 active 命中 + 无 Start）→ 内容 bbox 左上角，左上对齐（见 §5.3）。始终有一个良定义的落点。

### 5.3 视口焦点 Bias

| 场景 | Focal \((f_x, f_y)\) |
|------|----------------------|
| Working + 锚点来自 A1–A4 | `READING_BIAS` (0.38, 0.48) |
| Fit-All（A0） | `CENTER_BIAS` (0.50, 0.50) |
| Prefer-100 且锚点为全图中心 | `CENTER_BIAS` |
| grid/table **及 flow** 的 A3 Entry（Start / 内容左上原点） | **左上对齐**：锚点映射到 \((pad/Vw,\ pad/Vh)\) 附近，而不是 READING_BIAS——让 Start / 内容左上贴近视口左上、流程向右下铺进视口，顺应「从头读起」。flow 有 active（A1）时仍用 READING_BIAS，只有无 active 走 Entry 这一档才左上对齐 |

### 5.4 Transform 结算

```text
scale = workingScale | fitScale | 1.0   # 由策略选定
tx = f_x * Vw - a_x * scale
ty = f_y * Vh - a_y * scale
可选：对 (tx,ty) 做 §5.5 pan 软限制
```

### 5.5 边缘与空白（Pan Soft Clamp）

- **允许**锚点居中导致半屏空白（地图行为，可预测）。  
- **禁止**把用户拖入无限虚空：内容 bbox 经 scale 后，与视口相交区域至少保留，或平移时保证内容与视口重叠 ≥ 某最小条带（实现细节可简化为：内容边距视口 `EDGE_PAD_PX` 内）。  
- 首版：**默认应用 clamp**；Open Question 是否提供「关闭边界限制」。

---

## 6. 事件状态机

### 6.0 视口意图状态（会话内）

```text
enum ViewportIntent {
  Working,    // 默认工作态
  FitAll,     // 用户点了「适应视图」，直到被其它意图打断
  UserDriven  // 用户手动缩放/平移后，优先 preserve
}
```

**打断关系**：

| 从 \ 到 | Working | FitAll | UserDriven |
|---------|---------|--------|------------|
| 任意 | 导航到新 Active / 新图数据且策略要求 re-anchor | 点「适应视图」 | 滚轮缩放 / 拖拽平移 / 缩略图拖视口 |
| FitAll | 点 100%、点节点进入详情且策略要求 focus、或 §6.2 切元素 | — | 用户手势 |
| UserDriven | §6 规定的「强制重锚」事件 | 点适应视图 | — |

> **原则**：UserDriven 受尊重，但 **换元素 / 换版本 / 换图数据身份** 时不得把用户留在旧图的空白视口。

---

### 6.1 首进（First Paint）

**触发**：WC 首次对某 `(dataIdentity, mode)` 完成渲染；会话内无 UserDriven；无有效 FitAll。

```text
compute rawScale
if rawScale >= READABLE_FLOOR:
    scale = min(1.0, rawScale)          // Prefer-100
    anchor = ContentBBox.center         // 小图/可全览：中性居中
    bias = CENTER_BIAS
    intent = Working
else:
    scale = READABLE_FLOOR              // Working-Floor
    anchor = resolveAnchor(A1..A5)      // 通常 A1 Active
    bias = biasFor(anchor)
    intent = Working
    if OVERVIEW_HINT: 建议 show minimap
apply transform
```

**验收**：

- 小图（raw≥1）：scale=100%，整图居中，四周有 pad。  
- 中图（READABLE_FLOOR≤raw&lt;1）：scale=raw，整图可见，不放大。  
- 大图（raw&lt;READABLE_FLOOR）：scale=READABLE_FLOOR，**Active（若有）落在 READING_BIAS 附近**，不得落在无关空白。

---

### 6.2 切元素（Active / URL identity 变化）

**触发**：`active-id` 或路由 `:identity` 变化；数据可能同图也可能邻域重载。

```text
if 新 Active 在当前渲染结果中:
    // 强制任务对齐：忽略 UserDriven / FitAll 的「留在原地」
    scale = max(currentScale, READABLE_FLOOR) 的策略见下
    更稳妥的首版规则：
        scale = (intent==FitAll 且 用户未离开概览)? 保持 fitScale
                : max(READABLE_FLOOR, min(currentScale, 1.0)) 当 current 来自 UserDriven 且 ≥ floor
                : READABLE_FLOOR 当此前为 Working-Floor
    推荐首版简化（评审友好）：
        scale ← 若 rawScale >= READABLE_FLOOR 则 Prefer-100
                否则 READABLE_FLOOR
        // 切元素 = 新任务，重置为 Working 默认缩放策略，不继承上一元素的 FitAll
        intent ← Working
        anchor ← A1/A2
        re-anchor
else:
    // Active 不在图中：按 Entry / 兜底，同首进大图分支
    同 §6.1 else 分支
```

**首版定稿规则（建议锁定，减少分叉）**：

> **切元素 = 新任务上下文。**  
> 丢弃 FitAll 与上一元素的 UserDriven。  
> 重新执行 §6.1 的 scale 决策，锚点用新 Active。

**验收**：

- 从 A 跳到 B，B 必须在视口内且可读。  
- 不得出现「节点数据已换、视口仍停在旧空白」回归（历史 bug 类）。

---

### 6.3 切版本（processVersion / 数据身份变化）

**触发**：`processVersion`、工作区、或 diagram 数据身份变化（非同 data 的过滤刷新）。

```text
intent ← Working          // 版本是新世界
discard UserDriven/FitAll
执行 §6.1 首进策略（基于新数据 bbox + 当前 Active）
```

**验收**：新版本节点若布局变化大，视口必须重新锚定；禁止沿用旧版本 tx/ty/scale。

---

### 6.4 切视图 mode（flow / grid / table / …）

**触发**：TabCard mode 切换（WC 常重挂）。

```text
// mode 切换布局语义变了，bbox 与 Entry 定义变了
intent ← Working
执行 §6.1，Entry 按新 mode（§5.2）
```

**验收**：table/grid 首次进入应对准左上阅读起点，而不是几何中心。

---

### 6.5 过滤 / 选项变更（同 data 身份）

**触发**：过滤开关、showTailoredElements、edge label 等；`_lastRenderedData === data` 类同图刷新。

```text
if intent == UserDriven OR 存在有效用户 transform:
    Preserve scale + tx/ty
    // 若 Active 因过滤被隐藏：不强制跳；minimap 更新即可
    // 若 Preserve 后视口与内容完全无交集：降级 re-anchor 到 A1/A3
else if intent == FitAll:
    重新 Fit-All（过滤改变了 bbox）
else:
    // Working 且用户未拖动：
    if rawScale 跨越 READABLE_FLOOR 阈值 或 bbox 显著变化:
        重新执行 §6.1
    else:
        Preserve 或仅微调
```

**首版简化（建议）**：

> 过滤变更：**Preserve**；仅当「视口与内容 bbox 无交集」时 re-anchor 到 Active/Entry。

**验收**：用户缩放到某区域后开关过滤，视口不乱跳；滤掉一切后不出现永久空白死锁（无交集则拉回）。

---

### 6.6 点「适应视图」(Fit-All)

```text
intent ← FitAll
scale ← clamp(rawScale, MIN_SCALE_FIT_ALL, MAX_SCALE_FIT)
anchor ← ContentBBox.center
bias ← CENTER_BIAS
apply
// 可读性不保证；可在 UI 保持百分比读数真实显示（如 20%）
```

**验收**：再大的图也应尽量完整入屏（受 min scale 限制时除外）；百分比读数与真实 scale 一致。

---

### 6.7 点「100%」/ 缩放读数复位

```text
intent ← Working          // 或 UserDriven 均可；首版 → Working
scale ← 1.0
anchor ← A1 否则 A3 否则 A5
bias ← 对应 §5.3
apply
```

**验收**：100% 后 Active（若有）仍在焦点区，不是只改 scale 导致 Active 飞出屏外。

---

### 6.8 用户手动缩放 / 平移 / 缩略图拖视口

```text
intent ← UserDriven
// 应用手势；scale clamp 到 [MIN_SCALE_MANUAL, MAX_SCALE_MANUAL]
// 不自动 re-fit
```

**验收**：手势过程中无自动抢焦点；百分比读数实时更新。

---

### 6.9 双击空白（若保留现状手势）

现状 WC：双击空白 → fitToScreen。

**规格定稿建议**：

| 选项 | 行为 | 推荐 |
|------|------|------|
| B1 | 双击空白 = Fit-All（§6.6） | **推荐**（与按钮一致） |
| B2 | 双击空白 = 回到 Working 默认（§6.1） | 备选 |
| B3 | 取消双击手势 | 若与「点空白取消选中」冲突再议 |

**验收**：双击与工具栏「适应视图」结果一致（若选 B1）。

---

### 6.10 窗口 Resize / 画布高度拖拽 / 充满一屏 / 全屏

**冲突点**：现状 resize → 强制 fit，会冲掉用户缩放。

**规格**：

```text
on viewport size change (debounced rAF):
  if intent == FitAll:
      重新 Fit-All（全貌意图下尺寸变了应重算）
  else if intent == UserDriven:
      Preserve scale
      以「视口中心对应的内容点」为不动点重算 tx/ty
      // 即：resize 时保持中心内容点稳定，而不是左上角钉死
  else: // Working
      重新执行 §6.1 的 scale 决策
      锚点保持当前 Active/Entry（re-anchor）
```

**验收**：

- 用户缩放到 120% 后拖高画布：scale 仍约 120%，图不跳回 fit。  
- FitAll 下拖窗口：持续尽量看全图。  
- Working 大图下变高：仍对准 Active，scale 不低于 READABLE_FLOOR。

---

### 6.11 空图 / 不可用 mode

```text
scale = 1.0
tx,ty = 0 或轻居中
不报错；显示 WC 既有 empty/unavailable UI
```

---

## 7. 决策总表（评审用一页纸）

| 事件 | Scale | Anchor | Bias | Intent 结果 |
|------|-------|--------|------|-------------|
| 首进，raw≥floor | Prefer-100 | BBox 中心 | Center | Working |
| 首进，raw&lt;floor | floor | Active→Entry→… | Reading | Working |
| 切元素 | 重跑首进 scale | 新 Active | Reading | Working（清 Fit/User） |
| 切版本 | 重跑首进 | Active/Entry | 按锚点 | Working |
| 切 mode | 重跑首进 | 按 mode Entry | 按 mode | Working |
| 过滤 | Preserve* | — | — | 不变；无交集则 re-anchor |
| 适应视图 | Fit-All raw | BBox 中心 | Center | FitAll |
| 100% | 1.0 | Active→… | Reading/Center | Working |
| 手动缩放平移 | 用户值 | — | — | UserDriven |
| Resize | 见 §6.10 | 见 §6.10 | — | 不变（Fit 除外重算） |
| 双击空白 | =适应视图 | BBox 中心 | Center | FitAll（若采纳 B1） |

\*Preserve 前提：视口与内容仍有交集。

---

## 8. 与 Minimap / UI 文案

| 条件 | UI 行为 |
|------|---------|
| rawScale &lt; READABLE_FLOOR 且 intent=Working | **建议**自动打开 minimap（尊重用户全局关闭偏好 `gf:viz:minimap===false` 则不强开，可 toast 一次：「图较大，已聚焦当前元素」——文案 i18n 另表） |
| intent=FitAll 且 scale&lt;READABLE_FLOOR | 百分比真实显示；不强制改文案 |
| 缩放读数按钮 | 显示 `round(scale*100)%`；点击行为 = §6.7 |

---

## 9. 明确不采用的行为（Anti-Goals）

1. ~~大图默认 Fit-All 到 20%~~（蚂蚁字作为默认）  
2. ~~小图默认放大到 150%~~（现状 max 1.5）  
3. ~~装不下时仍锚全图几何中心~~  
4. ~~切元素仍 preserve 旧视口导致「空画布」~~  
5. ~~Resize 无脑 re-fit 冲掉 UserDriven~~  
6. ~~Working 与 Fit-All 参数混用同一 max/min 不区分~~  

---

## 10. 实现落点（仅指引，非本评审必合并代码）

| 层级 | 职责 |
|------|------|
| **@airedge/uc-graph** `fitToScreen` / `setupCanvas` / 首次渲染收尾 | 实现 Scale Policy、Anchor resolve、intent 是否内聚于 WC |
| **AirFlow 壳** | 传入 active-id、mode、是否 show-minimap；可选监听 transform 做偏好；**不要**再算第二套冲突公式 |
| **gf localStorage** | 今日已有 h/fill/minimap/collapsed；缩放记忆若做，key 设计单独立项 |

**建议**：视口状态机进 **uc-graph WC 内聚**（单一真相源）；AirFlow 只接线。

---

## 11. 验收用例（可给 QA）

| # | 前置 | 操作 | 期望 |
|---|------|------|------|
| AC1 | 节点 ≤5，内容远小于 600px 高画布 | 打开图 | scale=100%，整图居中，不放大发空 |
| AC2 | 中等图，raw≈0.9 | 打开图 | scale≈0.9，全图可见，字号可接受 |
| AC3 | 巨大图，raw≈0.2，URL 指向节点 B | 打开 | scale=READABLE_FLOOR；B 在视口焦点区（偏左上）；非全图中心空白 |
| AC4 | 在节点 A 的图上 | 导航到节点 B | B 入屏可读；不保留 A 的旧视口 |
| AC5 | 巨大图 Working | 点适应视图 | 尽量全图；scale 可 &lt; floor；居中 |
| AC6 | 适应视图后 | 再点 100% | scale=100%，Active 仍可见 |
| AC7 | UserDriven 缩放到 120% 并平移 | 拖拽画布高度 | scale 仍≈120%，中心内容大致稳定 |
| AC8 | UserDriven 中 | 切换 processVersion | 丢弃旧 transform，按新数据 §6.1 |
| AC9 | 过滤前已对准某区域 | 开关过滤 | 视口不跳；滤空后若无交集则拉回 Active/Entry |
| AC10 | table mode 首进大表 | 打开 | 对准左上，而非表格几何中心 |
| AC11 | Active=Phase 容器，内部子节点多 | 打开 | 锚在 Phase 内 Entry/首簇，而非空 Phase 框中心 |
| AC12 | 手动缩到 30% | 仅 resize 窗口 | 不强制抬回 floor（UserDriven 尊重手动缩小） |

---

## 12. Open Questions（评审必须拍板）

| # | 问题 | 选项 | 建议 |
|---|------|------|------|
| Q1 | `READABLE_FLOOR` 取 0.85 还是 0.70？ | A 0.85 / B 0.70 / C 按字号动态 | **A**，可配置 |
| Q2 | 切元素是否**总是**丢弃 UserDriven？ | A 总是（本文）/ B 同图平移保留 scale 只 re-anchor | **A** 更可预测；B 更连戏 |
| Q3 | 用户缩放是否 **localStorage 持久化**？ | A 否（会话）/ B 按 processId+mode / C 全局 | 首版 **A** |
| Q4 | 大图 Working 是否 **强开 minimap**？ | A 强开 / B 尊重关闭偏好仅提示 / C 不提示 | **B** |
| Q5 | 双击空白 | B1 Fit-All / B2 Working / B3 取消 | **B1** |
| Q6 | Fit-All 的 `MAX_SCALE_FIT` 是否允许 1.0 以外？ | 仅 1.0 / 允许 1.1 | **1.0** |
| Q7 | pan soft clamp 是否首版必做？ | 必做 / 可延后 | **可延后**，但不阻塞锚点策略 |
| Q8 | intent 状态放 WC 内还是 AirFlow React 层？ | WC / React / 双方 | **WC 内聚** |

---

## 13. Decisions Log

| Date | Decision | Reasoning | Alternatives |
|------|----------|-----------|--------------|
| 2026-07-23 | 起草本规格：Working vs Fit-All 双模式；装不下时锚 Active 而非 bbox 中心 | 可读与全貌互斥；GateFlow 导航带 identity | 永远 Fit-All；永远 100% |
| 2026-07-23 | 建议废除默认 fit max=1.5，改为 MAX_SCALE_FIT=1.0 | 节点按 100% 设计；小图放大发空 | 维持 1.5；改为 1.2 |
| 2026-07-23 | 切元素/切版本重置为 Working 首进策略 | 避免空画布历史回归；任务上下文切换 | 保留 UserDriven scale 只改锚点 |

---

## 14. 评审清单（请批注）

- [ ] 同意 R0：默认 Working，Fit-All 仅显式  
- [ ] 同意参数表 §3 默认值（或给出修改值）  
- [ ] 同意锚点优先级 A0–A5  
- [ ] 同意 READING_BIAS (0.38, 0.48)  
- [ ] 拍板 Open Questions Q1–Q8  
- [ ] 同意 AC1–AC12 作为实现完成标准  
- [ ] 确认实现落点：优先改 uc-graph，AirFlow 薄接线  

---

## 15. 修订历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 0.1 | 2026-07-23 | 初稿，供产品/前端/QA 评审 |
| 0.2 | 2026-07-23 | Claude 追加 §16 复核意见与增量（0.70 底线建议 / ResizeObserver 现状与 §6.10 门控 / 布局层互补修复交叉引用 / 持久化只走 scale 通道 / vs Gemini《无限画布》稿裁决表） |
| 0.3 | 2026-07-23 | Claude 精修焦点策略 §5.2/§5.3：flow 无 Start → 内容 bbox 左上角（弃"拓扑序第一"）；补 active-id 命中不到 → 降级 A3 边界；flow 的 A3 Entry 改左上对齐（有 active 仍 READING_BIAS） |

---

## 16. Claude 复核意见与增量（2026-07-23，基于 uc-graph 代码核实）

**总评**：本规格稿质量高——Working / Fit-All 互斥（R0）、锚点 A0–A5、事件状态机、反目标清单、AC1–AC12 结构完整，**本质上就是业界视口 4 条规范的完整工程化**：① Fit 不放大（P3 `MAX_SCALE_FIT=1.0` = React Flow `fitView({maxZoom:1})` / AntV X6 `zoomToFit({maxScale:1})` / bpmn-js fit-viewport）② padding 比例化（P1 `PAD_RATIO=0.1` = React Flow fitView 默认）③ 不抢用户视口（intent 状态机 = d3-zoom 从不自动重置）④ 大图聚焦兴趣点不伪 fit（Working-Floor + 锚 Active = Figma zoom-to-selection）。以下为增量与相对另一稿（Gemini《无限画布智能缩放与持久化方案》Obsidian）的裁决。

**增量一 · READABLE_FLOOR 建议 0.70 首发（回应 Q1）**。规格倾向 0.85（≈11px），但对"习惯总能看到全图"的老用户，0.85 会让 20+ 节点图默认只见局部、变化过激；Gemini 稿的 0.5 又太低（13px×0.5=6.5px 不可读）。0.70 折中（≈9px），稳定后再评估升 0.85，可配置。

**增量二 · ResizeObserver 现状提示（重要，直接影响 §6.10 落地）**。uc-graph 本轮已把 `window.resize → fitToScreen` 换成 **ResizeObserver 观察 shell**（覆盖宿主拖高 / 最大化 / 侧栏收展等"窗口没变、容器变了"的场景）——但**当前实现是无条件 re-fit**，行为等同旧 window.resize，正好踩本规格反目标 5。**§6.10 的 intent 门控（FitAll→重 fit / UserDriven→保 scale、以视口中心内容点为不动点 / Working→重跑首进）是这个 ResizeObserver 的必配升级**。落地节奏：P1 可先按无条件发（与旧行为一致、不算新回归），P2 补 intent 门控。

**增量三 · 布局层（本规格非目标）需交叉引用一条互补修复**。同意视口与布局解耦（§2.2）。但 flow 模式**无泳道分支的孤立节点竖排**（ELK 组件打包、缺 `elk.aspectRatio`）是独立的布局缺陷，与视口策略正交、需并行修（加 `elk.aspectRatio≈1.7` + 紧凑间距 96/48）。**关键约束：布局参数固定、禁止按窗口 / 显示器动态计算**，否则"拖到大屏图排布变"会破坏本规格目标 5（同一输入→同一视口结果）的可预测性。代码事实：`elk.direction:"RIGHT"` 写死正确（BPMN 惯例），不做横竖屏自适应；泳道内孤立节点已横排（`constrainNodesToLanes:855-869`），竖排只在无泳道分支。

**增量四 · 持久化只走 scale 通道（回应 Q3）**。若做缩放记忆，**只持久化 scale，锚点永远走 §5 策略，绝不持久化 tx/ty**。这样既满足 Gemini"记住缩放"诉求，又不踩本规格反目标 4（旧视口套新元素 = 空画布）。这也更正了 Gemini 稿"render-done 无脑 `centerAtZoom(savedZoom)`"的回归风险。

**裁决表（vs Gemini《无限画布》稿）**：

| 分歧点 | Gemini 稿 | 本规格 | Claude 裁决 |
|---|---|---|---|
| 可读底线 | 0.5 | 0.85（备选 0.70） | **0.70 首发**，可配置 |
| 缩放持久化 | render-done 无脑 `centerAtZoom(savedZoom)` | 首版仅会话内（Q3=A） | **只记 scale、锚点走策略；分阶段落地** |
| 布局间距 / 比例 | 按密度公式动态 | 明确非目标 | **固定值，拒绝按窗口动态** |
| aspectRatio | 固定 1.7 | 未涉及 | **固定 1.6~1.78；本规格非目标但需并行修** |
| 孤立节点竖排成因 | 泛指"垂直堆叠" | 未涉及 | **仅无泳道分支；泳道内已横排（代码核实）** |

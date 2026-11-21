# 前端脚本约定

- `layout.js` 在上述业务脚本之后加载，负责侧栏/详情尺寸偏好和分类树筛选，不发起业务写请求。尺寸拖动手柄支持方向键调整；本地存储不可用时仅保留当前页面状态。

- 加载顺序固定为 `core.js → worksets.js → features.js → merge.js`，全部 `defer`。后加载脚本的**全局函数会覆盖同名函数**
  ，新增模块的全局函数必须带模块前缀（例如 `mergeOpen`、`mergeRenderRows`），不得复用 `features.js` / `worksets.js` 已有的名字（如
  `flattenWorksets`、`openModal`、`bindEvents`）。
- 每个模块只在 `DOMContentLoaded` 中绑定自己的按钮和容器，不重写其它模块的监听器。
- `index.html` 中静态资源带版本参数（`/js/merge.js?v=N`、`/merge.css?v=N`）。任何内容改动都必须递增 `N`
  ，否则浏览器会继续执行缓存里的旧脚本——这会让"改完不生效"看起来像功能 BUG。
- 合并面板的方向语义是「源 → 目标」，只允许直接父子工作集；非法组合在前端预判并禁用提交，服务端仍是最终裁决（
  `MergeNotAllowed` → 409）。
- 比对决策使用方向切换按钮，默认关闭（`IGNORED`），打开为 `ACCEPT`；“标记已处理”保持独立，不由切换按钮自动改变。

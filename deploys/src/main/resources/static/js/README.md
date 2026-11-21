# 前端脚本约定

- 加载顺序固定为 `core.js → worksets.js → features.js → merge.js`，全部 `defer`。后加载脚本的**全局函数会覆盖同名函数**
  ，新增模块的全局函数必须带模块前缀（例如 `mergeOpen`、`mergeRenderRows`），不得复用 `features.js` / `worksets.js` 已有的名字（如
  `flattenWorksets`、`openModal`、`bindEvents`）。
- 每个模块只在 `DOMContentLoaded` 中绑定自己的按钮和容器，不重写其它模块的监听器。
- `index.html` 中静态资源带版本参数（`/js/merge.js?v=N`、`/merge.css?v=N`）。任何内容改动都必须递增 `N`
  ，否则浏览器会继续执行缓存里的旧脚本——这会让"改完不生效"看起来像功能 BUG。
- 合并面板的方向语义是「源 → 目标」，只允许直接父子工作集；非法组合在前端预判并禁用提交，服务端仍是最终裁决（
  `MergeNotAllowed` → 409）。
- 面板的决策默认值按差异类型区分：`ADDED`/`MODIFIED` 默认采纳源，`DELETED` 默认保留目标，避免误删。

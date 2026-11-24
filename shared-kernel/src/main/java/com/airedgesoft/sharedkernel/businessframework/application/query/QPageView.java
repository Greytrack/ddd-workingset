package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * 页面视图读模型（只读计算投影；非持久资源）——契约出自《后端实现任务书：页面视图、新建上下文与按钮可用性（12 月）》§2（2026-11-20）。
 *
 * <p>
 * 由 bridge 读 V3 分类配置（属性设置 + 按钮设置）经 {@code pageview-mapping.yml} 三层映射（规则映射 → 通用映射 → 按分类映射）拼装，三部分：
 * ①{@code fields}（字段：客户配置，含客户设置的位置/顺序）；②{@code actions}（按钮：DETAIL/EDIT/LIST=客户配置、带 placement；CREATE=createActions
 * 配置编排按钮、placement 为空）； ③{@code layout}（布局模板：前端定义、后端原样透传，不解析不修改）。前端用模板规则从 fields/actions 挑出每个区域显示什么。
 *
 * <p>
 * <b>对所有人相同，不含任何用户态</b>；无 name/id/lockedVersion；无 configRevision（用户裁决 2026-11-20：只留 ETag——表示 hash，配置变化自动感知）。 前端契约为 zod
 * strict 校验：响应多一个字段、少一个必填字段、类型不对都会失败，字段名以任务书 §2 为准。
 */
@Schema(description = "页面视图（只读计算投影：字段 + 按钮 + 布局模板透传；非持久资源）")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QPageView(@Schema(description = "分类 id", example = "14010102") String classificationId,
        @Schema(description = "用途（DETAIL/LIST/EDIT/CREATE）", example = "DETAIL") Purpose purpose,
        @Schema(description = "新建形态（仅 CREATE 回显；MODAL 默认）", example = "MODAL") Variant variant,
        @Schema(description = "页签 id（仅 LIST 回显；取值 = 请求 tabKey，即 V3 列表 id）", example = "420288") String tab,
        @Schema(description = "表槽位稳定键（仅 LIST 回显；服务端不消费）", example = "alm.requirement.links.upstream") String tableKey,
        @Schema(description = "字段数组（已按 purpose 过滤，每个字段只出现一次）") List<Field> fields,
        @Schema(description = "分组标题（CREATE/EDIT 可选；V3 类型 13 标题字段归此，不进 fields）") List<Section> sections,
        @Schema(description = "按钮数组（带 placement；CREATE 返回 createActions 配置编排按钮——placement 为空，前端按 key 绑定行为）") List<Action> actions,
        @Schema(description = "布局模板（前端定义，后端按 purpose 从配置原样透传，不解析不修改；未配置时前端用内置同版模板兜底）") Map<String, Object> layout) {

    /** 页面用途。 */
    public enum Purpose {

        /** 详情页。 */
        DETAIL,

        /** 列表页。 */
        LIST,

        /** 编辑页。 */
        EDIT,

        /** 创建页（新建表单）。 */
        CREATE
    }

    /** 新建形态（仅 CREATE）：MODAL 弹窗（默认）/ WINDOW 窗口。 */
    public enum Variant {

        /** 弹窗（默认）。 */
        MODAL,

        /** 窗口。 */
        WINDOW
    }

    /**
     * 分组标题（CREATE/EDIT）：V3 类型 13（标题）字段归此，不进 {@code fields}。
     *
     * @param key
     *            分组 key（V3 标题属性 apiId；其后字段以 {@code section} 引用）
     * @param displayName
     *            分组显示名
     */
    @Schema(description = "分组标题")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Section(@Schema(description = "分组 key（V3 标题属性 apiId）", example = "test11") String key,
            @Schema(description = "分组显示名", example = "基本信息") String displayName) {
    }

    /**
     * 字段信息——只下发页面渲染必需项；V3 内部配置（attributeType 数字、readOnly、各 visible、enumList、listType、textRule、
     * reference*、unionTable*、columnWidth、numberingRule、tips、icon、defaultValue 等）只在后端用于计算，不下发。
     *
     * @param key
     *            V3 属性 apiId
     * @param kind
     *            V3 属性类型 apiId（直出 AttributeTypeEnum#getApiId，如 ShortText/SingleList；STRUCTURED=yml 按字段指定；
     *            UNSUPPORTED=未识别；类型 13 标题不进 fields）——保留 V3 词汇便于前端沿用既有习惯（用户裁决 2026-11-20）
     * @param displayName
     *            客户配置后的显示名
     * @param required
     *            是否必填（V3 required："YES" → true）
     * @param valuePath
     *            详情 / 列表行数据里取值的字段名（读写同名；编辑保存 updateMask 与 _access.writableFields 用同一套名字）
     * @param displayLocation
     *            显示位置（DETAIL/EDIT 必有）：HEADER / ATTRIBUTE / HEADER_AND_ATTRIBUTE（V3 displayLocation 归一化）
     * @param height
     *            行高 1/2/3（V3 columnHeight，仅多行文本、富文本）
     * @param section
     *            所属分组 key（V3 顺序中其前最近的类型 13 标题）
     * @param constraints
     *            输入约束（可选）
     * @param listId
     *            列表 id（V3 enumList；仅选项/人员/级联类字段）——前端统一按列表 id 查询可选项；动态参数与返回条目限制由列表查询接口承载， PageView 不提供固定值（前端对齐裁决
     *            2026-11-20）。人员列表无独立 kind：kind=SingleList 且 listId 为 10119（产品空间成员）/ 527381549150528（所有用户），沿用 v3 习惯识别
     * @param behavior
     *            复合字段行为（STRUCTURED 时；如 alm.peerReview.votingRules）
     * @param behaviorVersion
     *            复合字段行为版本（STRUCTURED 时）
     */
    @Schema(description = "字段信息（客户配置：V3 属性 → 页面渲染必需项；展示顺序即数组顺序，不再下发 sortOrder）")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Field(@Schema(description = "V3 属性 apiId", example = "Owner") String key,
            @Schema(description = "V3 属性类型 apiId（ShortText/LongText/NumericalValue/SingleList/MultipleList/Date/CompositeText/CompositeNumericalValue/StartAndEndDate/RichText/Images/Number/Reference/CascadingSingleSelectList/CascadingMultiSelectList；STRUCTURED=按字段指定的复合字段；UNSUPPORTED=未识别）", example = "SingleList") String kind,
            @Schema(description = "显示名", example = "所有者") String displayName,
            @Schema(description = "是否必填", example = "true") boolean required,
            @Schema(description = "取值字段名（读写同名）", example = "owner") String valuePath,
            @Schema(description = "显示位置（DETAIL/EDIT）", example = "HEADER_AND_ATTRIBUTE") String displayLocation,
            @Schema(description = "行高 1/2/3（仅多行文本、富文本）", example = "2") Integer height,
            @Schema(description = "所属分组 key") String section, @Schema(description = "输入约束") Constraints constraints,
            @Schema(description = "列表 id（前端按列表 id 统一查询可选项；动态参数/条目限制归查询接口）", example = "10119") String listId,
            @Schema(description = "复合字段行为（STRUCTURED）", example = "alm.peerReview.votingRules") String behavior,
            @Schema(description = "复合字段行为版本（STRUCTURED）", example = "1") Integer behaviorVersion) {
    }

    /**
     * 输入约束（只能出现这些键；未命中的不序列化）。
     *
     * @param maxLength
     *            最大输入长度（文本类）
     * @param pattern
     *            正则约束（内联 V3 文本规则）
     * @param min
     *            数值最小值
     * @param max
     *            数值最大值
     * @param decimals
     *            小数位长度
     * @param allowZero
     *            数值是否允许为 0
     * @param maxCount
     *            最大数量（附件）
     * @param precision
     *            日期精度（DATE / DATETIME）
     * @param relativeToToday
     *            相对今天约束（BEFORE / NOT_AFTER / EQUAL / AFTER / NOT_BEFORE）
     */
    @Schema(description = "输入约束")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Constraints(@Schema(description = "最大输入长度", example = "1000") Integer maxLength,
            @Schema(description = "正则约束（内联文本规则）") Pattern pattern, @Schema(description = "数值最小值") Long min,
            @Schema(description = "数值最大值") Long max, @Schema(description = "小数位长度") Integer decimals,
            @Schema(description = "数值是否允许为 0", example = "true") Boolean allowZero,
            @Schema(description = "最大数量（附件）") Integer maxCount,
            @Schema(description = "日期精度（DATE/DATETIME）", example = "DATE") String precision,
            @Schema(description = "相对今天约束（BEFORE/NOT_AFTER/EQUAL/AFTER/NOT_BEFORE）") String relativeToToday) {

        /** 正则约束：内联 V3 文本规则展开。 */
        @Schema(description = "正则约束")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record Pattern(@Schema(description = "正则表达式") String regex,
                @Schema(description = "校验失败提示") String message) {
        }
    }

    /**
     * 按钮（任务书 §2.4 + 用户裁决 2026-11-20 二轮：同时下发 operationId 与 REST method/path）。能不能点不在这里（在详情 / 列表
     * {@code _access.actions}）；前端可直接用 {@code method + path} 调用，或经 {@code action}（operationId）找生成好的调用函数。
     *
     * @param key
     *            V3 按钮标识
     * @param kind
     *            ACTION / MENU（带 items，只一层，子项不带 placement）/ NAVIGATE（带 route，站内路由键）
     * @param action
     *            operationId（ACTION；前端据此找生成的调用函数）
     * @param method
     *            HTTP 方法（GET/POST/PATCH/DELETE；ACTION 且非纯前端时下发）
     * @param path
     *            REST 路径（{resource} 已按分类资源名替换；{id} 为资源占位符；纯前端动作为空）
     * @param route
     *            站内路由键（NAVIGATE）
     * @param appliesTo
     *            作用对象：INSTANCE / COLLECTION（不用选行）/ SELECTION（选中行）/ ROW
     * @param selection
     *            选中行范围（appliesTo = SELECTION）
     * @param placement
     *            摆放：HEADER（详情头部直出）/ MORE（「操作」下拉）/ TOOLBAR（表格外工具栏）/ ROW（行内）；DETAIL/EDIT/LIST 顶层必有， CREATE 编排按钮与 MENU
     *            子项不带
     * @param displayName
     *            客户配置后的名称（已解完父子继承）
     * @param sortOrder
     *            顺序
     * @param icon
     *            图标
     * @param style
     *            样式：PRIMARY / DEFAULT / DANGER
     * @param interaction
     *            交互配置（confirm/opensForm/dirtyGuard；配置原样透传）
     * @param items
     *            下拉子项（MENU；只一层）
     */
    @Schema(description = "按钮（operationId 化 + placement）")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Action(@Schema(description = "V3 按钮标识", example = "checkout") String key,
            @Schema(description = "ACTION/MENU/NAVIGATE", example = "ACTION") ActionKind kind,
            @Schema(description = "operationId（ACTION）", example = "requirementCheckout") String action,
            @Schema(description = "HTTP 方法（GET/POST/PATCH/DELETE）", example = "POST") String method,
            @Schema(description = "REST 路径（{id} 为资源占位符）", example = "/v1/requirements/{id}:checkout") String path,
            @Schema(description = "站内路由键（NAVIGATE）") String route,
            @Schema(description = "作用对象（INSTANCE/COLLECTION/SELECTION/ROW）", example = "INSTANCE") AppliesTo appliesTo,
            @Schema(description = "选中行范围（SELECTION）") Selection selection,
            @Schema(description = "摆放（HEADER/MORE/TOOLBAR/ROW；DETAIL/EDIT/LIST 顶层必有；CREATE 编排按钮与 MENU 子项不带）", example = "HEADER") Placement placement,
            @Schema(description = "显示名", example = "签出") String displayName,
            @Schema(description = "排序", example = "10") Integer sortOrder, @Schema(description = "图标") String icon,
            @Schema(description = "样式（PRIMARY/DEFAULT/DANGER）", example = "PRIMARY") Style style,
            @Schema(description = "交互配置（confirm/opensForm/dirtyGuard；原样透传）") Map<String, Object> interaction,
            @Schema(description = "下拉子项（MENU；只一层）") List<Action> items) {
    }

    /** 按钮类型。 */
    public enum ActionKind {

        /** 动作（operationId）。 */
        ACTION,

        /** 下拉菜单（items，只一层）。 */
        MENU,

        /** 站内导航（route）。 */
        NAVIGATE
    }

    /** 按钮摆放：HEADER 详情头部直出 / MORE 「操作」下拉 / TOOLBAR 表格外工具栏 / ROW 行内。 */
    public enum Placement {

        /** 详情头部直出。 */
        HEADER,

        /** 「操作」下拉。 */
        MORE,

        /** 表格外工具栏。 */
        TOOLBAR,

        /** 行内。 */
        ROW
    }

    /** 作用对象：INSTANCE 实例 / COLLECTION 集合（不用选行）/ SELECTION 选中行 / ROW 行内。 */
    public enum AppliesTo {

        /** 实例动作。 */
        INSTANCE,

        /** 集合动作（不用选行）。 */
        COLLECTION,

        /** 选中行动作。 */
        SELECTION,

        /** 行内动作。 */
        ROW
    }

    /** 按钮样式。 */
    public enum Style {

        /** 主要。 */
        PRIMARY,

        /** 默认。 */
        DEFAULT,

        /** 危险。 */
        DANGER
    }

    /**
     * 选中行范围（appliesTo = SELECTION）。
     *
     * @param min
     *            最少选中行数
     * @param max
     *            最多选中行数
     */
    @Schema(description = "选中行范围")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Selection(@Schema(description = "最少选中行数", example = "1") Integer min,
            @Schema(description = "最多选中行数", example = "200") Integer max) {
    }
}

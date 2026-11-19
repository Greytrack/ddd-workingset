package sk;

import lombok.AllArgsConstructor;
import lombok.Getter;
import sk.common.error.PersistenceResultCode;
import sk.common.error.ResCode;

/**
 * 全局响应码 0-9999 为系统保留 约定： 从左往右，第1位应用编码，第2，3位为主模块编号，第4，5位为子模块编号，第6，7，8位为内部编码 第1位：1 智研工软 第2，3位 100 全局支撑 101 业务支持 102 报表支持
 * 第4，5位 10101 国际化 10102 基础设置 10000000-10099999 保留 10001000-10001999 通用【common】，主要包含通用的响应码，infrastructure模块中的响应码
 * 10101000-10101999 国际化【i18n】 实际使用：无 10102000-10102999 ObjectFrameWork【基类,类等】 实际使用： ObjResultCode 10103000-10103999
 * RolePrivilege【角色权限】。文件格式 实际使用： RolePrivilegeResultCode，FileFormatResultCode 10104000-10104999
 * SystemIntegration【系统集成，包含：px、event、ldap、邮件等】 实际使用： SystemIntegrationResultCode 10105000-10105999 Workflow【工作流】
 * 实际使用：WorkflowReturnResultCode 10106000-10106999 criteria【条件】 实际使用：CriteriaResultCode 10107000-10107999 分类设置【分类设置】
 * 实际使用： CateGoryResultCode 10108200-10108300 文件服务器【文件服务器】 实际使用：FileServerResultCode 10108400-10108500 公告设置【公告设置】
 * 实际使用：NoticeResultCode 10108600-10108700 定时任务 实际使用：ScheduledTaskResultCode 10109000-10109999 智能规则设置，日历 实际使用：
 * SmartRuleResultCode， 10110000-10110999 产品空间 实际使用：ProductSpaceResultCode 10111000-10111999 activity
 * 实际使用：ProgramResultCode 10112000-10112999 输入输出 实际使用：InputOutputResultCode 10113000-10113999 扩展设置，检查单
 * 实际使用：EventProcessResultCode 10114000-10114999 检查单 实际使用：ChecklistResultCode 10115000-10115999 制造商
 * 实际使用：ManufactureResultCode 10116000-10116999 自动编号 实际使用：AutoNumberResultCode 10117000-10117999 职能【职能】
 * 实际使用：JobFunctionResultCode 10118000-10118999 附件格式 实际使用：FileFormatResultCode 10119000-10119999 用户 实际使用：UserResultCode
 * 10120000-10120999 列表 实际使用：ListObjResultCode 10121000-10121999 工作日历 实际使用：InstWorkCalendarResultCode 10122000-10122999
 * 水印签名 实际使用：WatermarkSignResultCode 10123000-10123999 基线 实际使用：BaselineResultCode 10124000-10124999 流程（变更）
 * 实际使用：ChangeResultCode 10125000-10125999 文件夹 实际使用：FolderResultCode 10126000-10126999 附件 实际使用：AttachmentResultCode
 * 10127000-10127999 查询模块 实际使用：QueryResultCode 10128000-10128999 文本规则 实际使用：TextRuleResultCode 10129000-10129999
 * versionRule【版本控制】 实际使用：VersionRuleResultCode 10130000-10130999 问题 实际使用：ProblemResultCode 10131000-10131999 立项管理
 * 实际使用：ProjectInitiationProcessResultCode 10135000-10135999 工时 实际使用：WorkHoursResultCode 10136000-10136999 excel导入
 * 实际使用：ImportExcelReturnResultCode 10137000-10137999 关联规则 实际使用：RelationRuleReturnResultCode 10140000-10149999 物料实例
 * 实际使用：PartResultCode 10150000-10151999 模块 实际使用：ModuleResultCode 10160000-10169999 工作集 实际使用：WorkingsetResultCode
 * 10170000-10179999 同行评审 实际使用：PeerReviewReturnResultCode 10180000-10189999 Word 往返 实际使用：WordRoundtripResultCode
 * 10200000-10200001 实际使用：SnapshotResultCode
 * <p>
 * 10201000-1029999 报表相关 实际使用：BIResultCode
 * </p>
 * 1077-1084 Gstore 实际使用：GstoreResultCode 10310000-103019999 检查单模板 实际使用：ChecklistTemplateResultCode 10133000-10133999 需求
 * 实际使用：RequirementResultCode
 *
 * @author xiaogang
 */
@Getter
@AllArgsConstructor
public enum GlobalResultCode implements ResCode {
    SUCCESS(0, "成功"),
    BAD_REQUEST(400, "res.code400"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "方法不允许"),
    LOGIN_IN_CODE_INVALIDATE(406, "登录code无效"),
    USER_NOT_EXISTS(407, "用户信息不存在，请联系管理员"),
    LOGIN_IN_CODE_EMPTY(408, "登录code不能为空"),
    LOGIN_IN_CLIENT_ID_EMPTY(409, "登录客户端ID不能为空"),
    LOGIN_IN_CLIENT_NOT_EXIST(410, "登录客户端不存在,请联系管理员"),
    LOGIN_IN_LOGIN_NAME_EMPTY(411, "登录用户名不能为空"),
    LOGIN_IN_USER_PASSWORD_EMPTY(412, "登录密码不能为空"),
    LOGIN_IN_USER_DISABLED(413, "用户%s已禁用"),
    /**
     * 找回密码重置：邮件链接中的重置码
     */
    FORGOT_PASSWORD_RESET_CODE_EMPTY(414, "邮件重置码不能为空"),
    /**
     * 找回密码重置：新密码
     */
    FORGOT_PASSWORD_NEW_PASSWORD_EMPTY(415, "新密码不能为空"),
    /**
     * 找回密码重置：链接中的重置码已失效（过期、已使用或无效）
     */
    FORGOT_PASSWORD_RESET_REQUEST_EXPIRED(416, "重设请求已过期"),
    INTERNAL_SERVER_ERROR(500, "功能异常，请联系管理员"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),
    JPA_OPTIMISTIC_LOCK_ERROR(PersistenceResultCode.VERSION_CONFLICT.getCode(),
            PersistenceResultCode.VERSION_CONFLICT.getDesc()),
    REPEAT_SUBMIT_ERROR(602, "不允许重复提交，请稍后再试"),
    I18N_LANG_NOT_FOUND_ERROR(1000, "语种不存在"),
    I18N_MULTIPLE_LANG_ERROR(1001, "同一的资源码，同一语种配置了多条记录"),
    I18N_RES_NOT_FOUND_ERROR(1002, "资源不存在"),

    /**
     * 重复提交间隔时间不能小于'1'秒
     */
    REPEAT_SUBMIT_INTERVAL_ERROR(603, "重复提交间隔时间不能小于[1]秒"),
    /**
     * ID生成异常
     */
    ID_GENERATE_ERROR(614, "ID生成异常"),
    /**
     * 请求头[Accept-Language]参数缺失
     */
    ACCEPT_LANGUAGE_ERROR(604, "请求头[Accept-Language]参数缺失"),
    /**
     * 不支持的请求方法
     */
    UNSUPPORTED_REQUEST_METHOD(605, "不支持%s的请求"),
    /**
     * 缺失必需的路径变量
     */
    MISSING_PATH_VARIABLE(606, "缺失必需的路径变量%s"),
    /**
     * 未找到数据源，请联系管理员确认
     */
    DATA_SOURCE_NOT_FOUND(607, "未找到数据源，请联系管理员确认"),
    /**
     * Mybatis执行异常
     */
    MYBATIS_EXECUTE_ERROR(608, "程序好像出错了，请联系管理员"),
    /**
     * 未知异常
     */
    UNKNOWN_ERROR(609, "程序好像出错了，请联系管理员"),
    /**
     * 请求参数类型不匹配，参数[%s]要求类型为：'%s'，但输入值为：'%s'
     */
    METHOD_ARGUMENT_TYPE_MISMATCH(610, "请求参数类型不匹配，参数[%s]要求类型为：'%s'，但输入值为：'%s'"),
    /**
     * 未找到执行策略的分发器
     */
    STRATEGY_DISPATCHER_NOT_FOUND(611, "程序好像出错了，请联系管理员"),
    CONFIG_ERROR(613, "系统配置错误"),
    /**
     * SQL语法异常
     */
    SQL_SYNTAX_ERROR(612, "程序好像出错了，请联系管理员"),
    PARAM_CHECK_ERROR(3000, "参数错误"),
    DATA_NOT_EXIST(3001, "数据不存在"),
    FILE_EXPORT_ERROR(3002, "文件导出异常"),
    FILE_IMPORT_ERROR(3003, "文件导入异常"),
    API_ID_ERROR(3004, "APIID只能输入大小写字母、数字、英文点和下划线，且长度不超过40"),
    PRIMARY_KEY_ERROR(3005, "主键不能为空"),
    AQUIRE_REDISSON_LOCK_FAIL(3006, "获取redis锁失败"),

    /**
     * 请输入%s~%s之间的文本内容(excel导出单元格长度校验占用)
     */
    EXCEL_LENGTH_NOT_MATCH(3007, "请输入%s~%s之间的文本内容"),
    /**
     * 输入文本长度有误
     */
    EXCEL_LENGTH_ERROR(3008, "输入文本长度有误"),
    /**
     * 分页参数错误
     */
    PAGE_PARAM_ERROR(3009, "分页参数错误"),
    /**
     * 领域对象不能为空
     */
    DOMAIN_OBJECT_NULL(3010, "领域对象不能为空"),
    /**
     * APIID不能为空
     */
    API_ID_EMPTY(3011, "APIID不能为空"),
    /**
     * 上传附件为空
     */
    UPLOAD_FILE_EMPTY(3012, "上传附件为空"),
    /**
     * 排序的序号已超过限制
     */
    RANK_OVER_LIMIT(3013, "排序的序号已超过限制"),

    /**
     * 系统预制数据不可以删除
     */
    SYSTEMDEFAULT_DATA_CANNOT_BE_DELETED(3014, "系统预制数据不可以删除"),
    /**
     * 系统预制数据不可以启用禁用
     */
    SYSTEMDEFAULT_DATA_CANNOT_BE_ENABLED_OR_DISABLED(3015, "系统预制数据不可以启用禁用"),
    SYSTEMDEFAULT_DATA_SETTING_ERROR(3016, "系统预制数据预制错误"),
    /**
     * 系统预制数据不可以修改
     */
    SYSTEMDEFAULT_DATA_CANNOT_BE_MODIFIED(3017, "系统预制数据不可以修改"),
    /**
     * APIID重复
     */
    APIID_UNIQUE_ERROR(3018, "APIID重复"),
    /**
     * 创建失败，名称重复
     */
    CREATE_FAIL_NAME_REPEAT(3021, "%s已存在"),
    /**
     * 创建失败，APIID重复
     */
    CREATE_FAIL_APIID_REPEAT(3022, "%s已存在"),
    /**
     * 启用失败
     */
    ENABLE_FAIL(3042, "启用失败"),

    TEXT_RULE_DYNAMIC(3043, "%s"),
    /**
     * SQL解析出错
     */
    SQL_PARSE_ERROR(3065, "SQL解析出错"),
    /**
     * 更新失败，名称重复
     */
    UPDATE_FAIL_NAME_REPEAT_ERROR(3068, "更新失败，名称重复"),
    /**
     * 更新失败，APIID重复
     */
    UPDATE_FAIL_APIID_REPEAT_ERROR(3069, "更新失败，APIID重复"),
    API_ID_IS_NULL(3083, "apiId为空"),
    // 参数校验失败
    PARAM_CHECK_ERROR_MSG(3085, "参数错误：%s"),
    SESSION_NOT_FOUND(3086, "会话异常"),
    API_ID_DUPLICATE(3087, "APIID重复"),
    NAME_DUPLICATE(3088, "名称重复"),
    LOGIN_IN_MAX_ONLINE_USER(3089, "到达最大用户数量限制"),
    BUILD_IN_DATA_CANNOT_DELETE(3090, "%s为预设数据，无法删除"),
    NOT_AUTH_TO_OPERATION(3091, "无权限操作"),

    OBJ_DISABLED(3094, "保存失败，%s已被禁用"),
    FAVORITE_MAX_LIMIT_1000(3096, "最多收藏100条数据"),

    // 定时器调度失败
    SCHEDULED_TASK_ERROR(3101, "调度定时任务失败"),

    UTIL_EXECUTE_SCRIPT_ERROR(3099, "脚本执行出错"),

    // 邮件发送失败
    EMAIL_SEND_ERROR(3100, "邮件发送失败"),

    DELETE_FAIL_TAB_WHERE_USED_NOT_EMPTY(3201, "使用位置页签不为空，不能删除"),
    DELETE_FAIL_TAB_RELATIONSHIP_NOT_EMPTY(3202, "关联页签不为空，不能删除"),
    DELETE_FAIL_TAB_ATTACHMENT_NOT_EMPTY(3203, "附件页签不为空，不能删除"),
    DELETE_FAIL_WORK_FLOW_IN_APPROVE(3204, "流程正在审批，无法删除"),
    /**
     * 导入属性之间的映射关系为空
     */
    IMPORT_ATTRIBUTE_MAPPING_ERROR(3205, "导入属性之间的映射关系为空"),
    // 属性数据库字段名称为配置
    ATTRIBUTE_DB_FIELD_NAME_NOT_CONFIGURED(3206, "属性%s数据库字段名称为空!"),
    RECYCLE_BIN_NOT_SUPPORT_JUMP(3207, "回收站数据不支持跳转"),

    DATA_ALREADY_EXISTS(3208, "%s已存在"),

    DATA_NOT_EXISTS(3209, "%s不存在"),
    DATA_ALREADY_DISABLED(3210, "%s已禁用"),

    EXPORT_CONCURRENT_LIMIT_EXCEEDED(3211, "导出并发已满，请稍后再试"),
    ;
    /**
     * 响应码
     */
    private final int code;
    /**
     * 描述
     */
    private final String desc;
}

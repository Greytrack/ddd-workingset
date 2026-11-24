package com.airedgesoft.lib.restful;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainResCode;

/**
 * lib-restful 协议错误码（模块号 001 = lib 技术库块；码段 300010001–300019999，9 位 `30{MMM}{NNNN}`，规范见
 * .lattice/standards/v4/v4-exception-handling.md §2.2）。
 */
public enum DomainRestCode implements DomainResCode {

    /** 游标 token 非法（格式 / 长度 / Base64）。 */
    PAGE_TOKEN_INVALID(DomainErrorType.INVALID_ARGUMENT, 300010001, "pageToken 非法"),
    /** 游标 token 过期。 */
    PAGE_TOKEN_EXPIRED(DomainErrorType.INVALID_ARGUMENT, 300010002, "pageToken 已过期，请从首页重新查询"),
    /** 游标与当前 filter 指纹不符。 */
    PAGE_TOKEN_FILTER_MISMATCH(DomainErrorType.INVALID_ARGUMENT, 300010003, "pageToken 与当前 filter 不匹配"),
    /** 游标 AEAD 认证失败（篡改 / 密钥不符）。 */
    PAGE_TOKEN_TAMPERED(DomainErrorType.INVALID_ARGUMENT, 300010004, "pageToken 校验失败（已篡改或密钥不符）"),
    /** 同幂等键请求体指纹不一致。 */
    IDEMPOTENCY_PAYLOAD_MISMATCH(DomainErrorType.UNPROCESSABLE_ENTITY, 300010007, "Idempotency-Key 与原请求体不一致：非法复用幂等键"),
    /** 同幂等键请求处理中。 */
    IDEMPOTENCY_IN_FLIGHT(DomainErrorType.CONFLICT, 300010008, "相同 Idempotency-Key 的请求正在处理中，请稍后"),
    /** pageSize 非法（负值；0 取默认不报错，AIP-158:63-64/68）。 */
    PAGE_SIZE_INVALID(DomainErrorType.INVALID_ARGUMENT, 300010009, "pageSize 不能为负数: %s"),
    /** 技术主键契约形态非法（规范 v4-restful-api §2：十进制纯数字字符串）。 */
    RESOURCE_ID_INVALID(DomainErrorType.INVALID_ARGUMENT, 300010010, "资源 ID 必须为十进制纯数字字符串: %s"),
    /** updateMask 含空字段路径段（连续逗号）→ 400（AIP-134：掩码可选，省略=全部已填充字段、*=全量替换、空串=no-op）。 */
    UPDATE_MASK_INVALID(DomainErrorType.INVALID_ARGUMENT, 300010012, "updateMask 含空字段路径段（逗号分隔字段路径，AIP-134）"),
    /** updateMask 含白名单外字段 → 400（AIP-161，防拼写静默丢更新）。 */
    UPDATE_MASK_UNKNOWN(DomainErrorType.INVALID_ARGUMENT, 300010013, "updateMask 含未知字段: %s"),
    /** orderBy 含白名单外字段 → 400（AIP-132 排序白名单）。 */
    ORDER_BY_FIELD_UNKNOWN(DomainErrorType.INVALID_ARGUMENT, 300010014, "orderBy 含不可排序字段: %s"),
    /** 批量请求条数超限 → 400。 */
    BATCH_LIMIT_EXCEEDED(DomainErrorType.INVALID_ARGUMENT, 300010015, "批量请求条数超限: %s（上限 %s）"),
    /** filter 语法非法（AIP-160 子集）→ 400。 */
    FILTER_SYNTAX_INVALID(DomainErrorType.INVALID_ARGUMENT, 300010016, "filter 语法非法: %s"),
    /** filter 含白名单外字段 → 400。 */
    FILTER_FIELD_UNKNOWN(DomainErrorType.INVALID_ARGUMENT, 300010017, "filter 含不可过滤字段: %s"),
    /** ABAC 字段级写权限越权 → 403（§19.3 校验链第 3 步）。 */
    FIELD_NOT_WRITABLE(DomainErrorType.FORBIDDEN, 300010018, "当前用户不可写字段: %s"),
    /** ABAC 值级约束越权 → 403（§19.3 校验链第 5 步）。 */
    VALUE_CONSTRAINT_VIOLATED(DomainErrorType.FORBIDDEN, 300010019, "字段 %s 的目标值不在允许范围内: %s"),
    /** Operation 不存在 → 404（LRO 轮询 / 取消，AIP-151）。 */
    OPERATION_NOT_FOUND(DomainErrorType.NOT_FOUND, 300010020, "Operation 不存在: %s"),
    /** Operation 已进入终态不可取消 → 409（LRO，AIP-151）。 */
    OPERATION_NOT_CANCELLABLE(DomainErrorType.CONFLICT, 300010021, "Operation 已完成，无法取消: %s"),
    /** Operation 进行中不可删除 → 409（LRO deleteOperation，AIP-151）。 */
    OPERATION_NOT_DELETABLE(DomainErrorType.CONFLICT, 300010022, "Operation 进行中，无法删除: %s"),
    /** 写请求 body 缺 {@code lockedVersion} → 400（D13：强一致写强制版本）。 */
    LOCKED_VERSION_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300010026, "写请求必须携带 lockedVersion"),
    /** 写请求 body 的 {@code lockedVersion} 非法（非正整数）→ 400。 */
    LOCKED_VERSION_INVALID(DomainErrorType.INVALID_ARGUMENT, 300010027, "lockedVersion 必须为正整数: %s"),
    /** readMask 含白名单外字段 → 400（AIP-157 部分读取，防拼写静默全量返回）。 */
    READ_MASK_UNKNOWN(DomainErrorType.INVALID_ARGUMENT, 300010028, "readMask 含未知字段: %s"),
    /** updateMask / readMask 引用已停用的预定义字段 → 400（字段不存在语义，§20 字段开关）。 */
    PREDEFINED_FIELD_DISABLED(DomainErrorType.INVALID_ARGUMENT, 300010029, "引用了已停用的预定义字段: %s"),
    /** 存储实现未提供该能力（如自定义 OperationStore 未覆盖 delete）→ 501；显式暴露缺口而非 500/静默成功。 */
    UNSUPPORTED_BY_STORE(DomainErrorType.UNIMPLEMENTED, 300010031, "当前存储实现不支持该操作"),
    /** 长流程（202 + LRO）端点缺 {@code Idempotency-Key} → 400（§16：202 长流程必须要求幂等键，重试代价最高）。 */
    LRO_IDEMPOTENCY_KEY_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300010030, "长流程端点必须携带 Idempotency-Key 请求头"),
    /** 异步批量全部失败（§4.1 异步通道全败终态；对齐 AIP-235「全败置 Operation.error = Aborted」——逐项原因见 metadata.failedRequests）。 */
    BATCH_ALL_FAILED(DomainErrorType.ABORTED, 300010032, "批量请求全部失败，逐项原因见 failedRequests"),
    /** 并发冲突（乐观锁 CAS 未命中 / body {@code lockedVersion} 不一致）→ 409（统一码见 lib-exception `CommonResCode`）。 */
    VERSION_CONFLICT(com.airedgesoft.lib.exception.CommonResCode.VERSION_CONFLICT);

    private final DomainErrorType type;
    private final int code;
    private final String desc;

    /** 委托框架通用码（lib-exception）：类型、码值与文案同源，避免两处定义漂移。 */
    DomainRestCode(com.airedgesoft.lib.exception.CommonResCode code) {
        this(code.getType(), code.getCode(), code.getDesc());
    }

    DomainRestCode(DomainErrorType type, int code, String desc) {
        this.type = type;
        this.code = code;
        this.desc = desc;
    }

    @Override
    public DomainErrorType getType() {
        return type;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}

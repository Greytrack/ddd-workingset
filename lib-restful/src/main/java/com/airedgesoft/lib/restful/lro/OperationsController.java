package com.airedgesoft.lib.restful.lro;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.restful.id.ResourceIds;
import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.lib.restful.pagination.PageSizeResolver;
import com.airedgesoft.lib.restful.pagination.PageTokens;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * LRO 通用轮询、列表、等待、取消与删除端点（AIP-151；统一资源名 {@code operations}，对齐 google.longrunning.Operations）。
 *
 * <pre>
 * GET    /v1/operations                      → 200 Page&lt;Operation&gt;（pageToken 游标分页）/ 400 problem+json
 * GET    /v1/operations/{operationId}        → 200 Operation / 404 problem+json
 * POST   /v1/operations/{operationId}:wait   → 200 Operation（长轮询至终态或超时；AIP-151 WaitOperation）/ 404
 * POST   /v1/operations/{operationId}:cancel → 200 Operation(取消终态) / 404 / 409(已完成)
 * DELETE /v1/operations/{operationId}        → 204(终态可删) / 404 / 409(进行中)
 * </pre>
 *
 * <p>
 * 由 {@code LroRedissonAutoConfiguration} / {@code LroAutoConfiguration} 按条件注册（Web 应用 +
 * {@code airedge.restful.lro.enabled=true} + 存在 {@link OperationStore}）。返回形态遵守三选一规范（§9 第 15 条）： 纯数据 / {@code Page} 信封 /
 * {@code void}+204，错误一律抛 {@link DomainException}，由 lib 自有的 {@link LroProblemAdvice}（basePackages 精确到本包，§9 第 14 条同型）渲染
 * RFC 9457 problem+json——通用端点不依赖各 BC 的 {@code @RestControllerAdvice}，也不落入宿主 / legacy catch-all。 取消语义 （CAS，AIP-151）：进行中
 * → 失败终态（{@code Operation.cancelled()}），已完成 → 409 {@code OPERATION_NOT_CANCELLABLE}；幂等取消（重复取消 已取消）→ 200 原终态。 取消与 Worker
 * 完成的终态争夺经 {@link OperationStore#compareAndStatus} 原子仲裁——<b>禁止</b> find-then-save 裸竞争（last-write-wins 会令「先 cancel 后
 * complete」时完成覆写取消，取消动作静默丢失）。 删除语义 （AIP-151 {@code deleteOperation}）：终态（成功 / 失败 / 取消）可删 → 204；进行中 → 409
 * {@code OPERATION_NOT_DELETABLE}；删除状态裁决在端点内先行判定，{@link OperationStore#delete} 不做二次校验。
 *
 * <p>
 * <b>命名注意</b>：本类内的 {@link Operation} 为 LRO 状态载荷；OpenAPI 的 {@code @io.swagger.v3.oas.annotations.Operation}
 * 注解以全限定名书写（同名冲突，类名不迁就三方注解）。
 */
@RestController
@Tag(name = "共享内核-长耗时操作", description = "LRO 长耗时操作轮询、列表、取消与删除（AIP-151）")
public class OperationsController {

    /** 全局 LRO 基路径（Location 头派生源；对齐 google.longrunning.Operations 资源名 {@code operations}）。 */
    public static final String OPERATIONS_BASE_URI = "/v1/operations";

    /** 游标复合排序键——时间位：页尾记录的规范化 {@code createTime}（ISO-8601 instant 文本）。 */
    static final String CURSOR_SORT_KEY_CREATE_TIME = "ct";

    /** 游标复合排序键——主键兜底位：页尾记录的 {@code operationId}（字符串原文，规避 Long 往返的格式漂移）。 */
    static final String CURSOR_SORT_KEY_OPERATION_ID = "id";

    /** `:wait` 缺省等待秒数（超时返回当前快照，不报错）。 */
    static final int WAIT_DEFAULT_TIMEOUT_SECONDS = 30;

    /** `:wait` 等待上限秒数（防连接长期占用）。 */
    static final int WAIT_MAX_TIMEOUT_SECONDS = 60;

    /** `:wait` 轮询间隔毫秒。 */
    static final long WAIT_POLL_INTERVAL_MILLIS = 200L;

    /** 列表固定排序：{@code createTime desc} + {@code operationId asc} 平局仲裁（末尾主键升序兜底，防翻页漂移）。 */
    private static final Comparator<Operation> LIST_ORDER = Comparator
            .comparing(OperationsController::createTimeOrEpoch).reversed().thenComparing(Operation::operationId);

    private final OperationStore store;

    private final OperationOwnerResolver ownerResolver;

    private final PageTokens pageTokens;

    private final PageSizeResolver pageSizeResolver;

    public OperationsController(OperationStore store, OperationOwnerResolver ownerResolver, PageTokens pageTokens,
            PageSizeResolver pageSizeResolver) {
        this.store = store;
        this.ownerResolver = ownerResolver;
        this.pageTokens = pageTokens;
        this.pageSizeResolver = pageSizeResolver;
    }

    /**
     * 列表（AIP-158 游标分页；「我的」口径经 {@link OperationOwnerResolver} 过滤）。排序口径见 {@link #LIST_ORDER}；游标为 AES-GCM 不透明 token（复用
     * {@link PageTokens}，载荷含页尾复合排序键 {@code ct}/{@code id}），非法 / 篡改 / 过期 → 400（{@link LroProblemAdvice} 渲染）。
     *
     * <p>
     * <b>内存排序切片</b>：{@code store.list} 返回的 owner 快照本就全量（Redis 键空间扫描无存储层下推入口），本端点为无 filter / 固定排序的通用轮询面，数据规模受终态 TTL
     * 清理约束——在端点内排序 + 切片即可，不为通用端点引入存储层 seek 复杂度。
     */
    @GetMapping(OPERATIONS_BASE_URI)
    @io.swagger.v3.oas.annotations.Operation(operationId = "operationList", summary = "我的长运行任务列表（owner 经 OperationOwnerResolver 解析过滤；未配置解析器 → 全量；AIP-158 游标分页）")
    @ApiResponse(responseCode = "200", description = "Page 信封返回（含 nextPageToken；末页缺省）")
    @ApiResponse(responseCode = "400", description = "pageSize / pageToken 非法")
    public Page<Operation> list(
            @Parameter(description = "单页数量（0 取默认；负数或非数字 → 400；超上限钳到上限）", example = "20") @RequestParam(name = "pageSize", required = false) Integer pageSize,
            @Parameter(description = "下一页游标（AIP-158，不透明字符串，首页不传；非法/篡改/过期 → 400）") @RequestParam(name = "pageToken", required = false) String pageToken) {
        int size = pageSizeResolver.resolve(pageSize);
        // LRO 无 filter 入参：指纹两侧恒空串一致；签发见 nextCursorToken
        PageTokens.Cursor cursor = pageTokens.parseCursor(pageToken, null);
        String owner = ownerResolver != null ? ownerResolver.currentOwner() : null;
        List<Operation> sorted = store.list(owner).stream().sorted(LIST_ORDER).toList();
        boolean firstPage = cursor.lastId() == null; // 无游标 = 从头开始（parseCursor 对 null pageToken 返回空游标）
        int start = firstPage ? 0 : seekStart(sorted, cursor);
        int end = Math.min(start + size, sorted.size());
        List<Operation> items = sorted.subList(start, end);
        String nextToken = end < sorted.size() ? nextCursorToken(items.get(items.size() - 1)) : null;
        return Page.of(items, nextToken, sorted.size());
    }

    @GetMapping(OPERATIONS_BASE_URI + "/{operationId}")
    @io.swagger.v3.oas.annotations.Operation(operationId = "operationGet", summary = "轮询 LRO 操作状态")
    @ApiResponse(responseCode = "200", description = "Operation 状态（done=true 为终态）")
    @ApiResponse(responseCode = "404", description = "操作不存在")
    public Operation get(
            @Parameter(description = "操作 ID（十进制规范形态；非数字/前导零 → 400）") @PathVariable("operationId") String operationId) {
        requireCanonicalId(operationId);
        return store.find(operationId).orElseThrow(() -> notFound(operationId));
    }

    /**
     * 等待（AIP-151 {@code WaitOperation}，可选方法）：长轮询阻塞至操作达终态（{@code done=true}）或超时，返回当前 Operation——减少客户端空轮询。 超时缺省 30s（上限
     * 60s，防连接长期占用）；超时返回当前快照（仍 {@code done=false}），不报错。操作不存在 → 404。
     *
     * <p>
     * <b>演示端点</b>：实现为 Servlet 线程内的定间隔轮询（{@code Thread.sleep}），高并发等待会占用容器工作线程——生产部署建议客户端走标准 {@code GET} 轮询（AIP-151
     * 该方法为可选能力）。
     */
    @PostMapping(OPERATIONS_BASE_URI + "/{operationId}:wait")
    @io.swagger.v3.oas.annotations.Operation(operationId = "operationWait", summary = "长轮询等待 LRO 操作终态（AIP-151 WaitOperation；超时返回当前快照）")
    @ApiResponse(responseCode = "200", description = "Operation 状态（终态或超时快照）")
    @ApiResponse(responseCode = "404", description = "操作不存在")
    public Operation await(
            @Parameter(description = "操作 ID（十进制规范形态；非数字/前导零 → 400）") @PathVariable("operationId") String operationId,
            @Parameter(description = "最长等待秒数（缺省 30，上限 60；超时返回当前快照）") @RequestParam(name = "timeout", required = false) Integer timeout) {
        requireCanonicalId(operationId);
        Operation current = store.find(operationId).orElseThrow(() -> notFound(operationId));
        int budgetSeconds = timeout == null
                ? WAIT_DEFAULT_TIMEOUT_SECONDS
                : Math.min(Math.max(timeout, 0), WAIT_MAX_TIMEOUT_SECONDS);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(budgetSeconds);
        while (!current.done() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(WAIT_POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            current = store.find(operationId).orElseThrow(() -> notFound(operationId));
        }
        return current;
    }

    @PostMapping(OPERATIONS_BASE_URI + "/{operationId}:cancel")
    @io.swagger.v3.oas.annotations.Operation(operationId = "operationCancel", summary = "取消 LRO 操作（进行中 → 取消终态）")
    @ApiResponse(responseCode = "200", description = "取消成功（返回取消终态；重复取消幂等 200）")
    @ApiResponse(responseCode = "409", description = "操作已完成，无法取消")
    public Operation cancel(
            @Parameter(description = "操作 ID（十进制规范形态；非数字/前导零 → 400）") @PathVariable("operationId") String operationId) {
        requireCanonicalId(operationId);
        Operation current = store.find(operationId).orElseThrow(() -> notFound(operationId));
        if (OperationStatus.of(current) == OperationStatus.CANCELLED) {
            return current; // 幂等取消
        }
        // CAS：仅 RUNNING 态可取消——与 Worker 完成 / 并发取消的终态争夺由 Store 原子仲裁
        return store.compareAndStatus(operationId, OperationStatus.RUNNING, op -> op.withError(Operation.cancelled()))
                .orElseGet(() -> {
                    // CAS 失败（状态已被并发推进）：重读分类——记录已消失（并发删除）→ 404；已被并发取消 → 幂等 200；其余终态 → 409
                    Operation latest = store.find(operationId).orElse(null);
                    if (latest == null) {
                        throw notFound(operationId);
                    }
                    if (OperationStatus.of(latest) == OperationStatus.CANCELLED) {
                        return latest;
                    }
                    throw new DomainException(DomainRestCode.OPERATION_NOT_CANCELLABLE, operationId);
                });
    }

    /**
     * 删除（AIP-151 {@code deleteOperation}）：终态记录清理 → 204 无 body；进行中 → 409；不存在 → 404（复用
     * {@link DomainRestCode#OPERATION_NOT_FOUND}）。
     */
    @DeleteMapping(OPERATIONS_BASE_URI + "/{operationId}")
    @io.swagger.v3.oas.annotations.Operation(operationId = "operationDelete", summary = "删除终态 LRO 操作记录（成功 / 失败 / 取消可删；进行中 409）")
    @ApiResponse(responseCode = "204", description = "删除成功（无 body）")
    @ApiResponse(responseCode = "501", description = "当前存储实现未提供 delete（自定义 OperationStore 未覆盖缺省实现）")
    @ApiResponse(responseCode = "404", description = "操作不存在")
    @ApiResponse(responseCode = "409", description = "操作进行中，无法删除")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "操作 ID（十进制规范形态；非数字/前导零 → 400）") @PathVariable("operationId") String operationId) {
        requireCanonicalId(operationId);
        Operation current = store.find(operationId).orElseThrow(() -> notFound(operationId));
        if (OperationStatus.of(current) == OperationStatus.RUNNING) {
            throw new DomainException(DomainRestCode.OPERATION_NOT_DELETABLE, operationId);
        }
        store.delete(operationId);
    }

    /**
     * 定位游标之后的首个元素下标（迭代序单调谓词的线性扫描——内存切片口径见 {@link #list} javadoc）。 载荷缺复合排序键 / 时间位非法 = 非本端点签发的损坏 token → 400。
     */
    private static int seekStart(List<Operation> sorted, PageTokens.Cursor cursor) {
        Map<String, Object> sortKeys = cursor.sortKeys();
        if (!(sortKeys.get(CURSOR_SORT_KEY_CREATE_TIME) instanceof String createTime)
                || !(sortKeys.get(CURSOR_SORT_KEY_OPERATION_ID) instanceof String operationId)) {
            throw new DomainException(DomainRestCode.PAGE_TOKEN_INVALID);
        }
        Instant cursorTime;
        try {
            cursorTime = Instant.parse(createTime);
        } catch (DateTimeParseException e) {
            throw new DomainException(DomainRestCode.PAGE_TOKEN_INVALID);
        }
        int index = 0;
        while (index < sorted.size() && !afterCursor(sorted.get(index), cursorTime, operationId)) {
            index++;
        }
        return index;
    }

    /** 迭代序「严格晚于游标」判定：{@code createTime desc} → 更早者靠后；同刻按 {@code operationId asc} → 更大者靠后。 */
    private static boolean afterCursor(Operation operation, Instant cursorTime, String operationId) {
        int byTime = createTimeOrEpoch(operation).compareTo(cursorTime);
        if (byTime != 0) {
            return byTime < 0;
        }
        return operation.operationId().compareTo(operationId) > 0;
    }

    /** 由页尾记录签发下一页游标（lastId + 复合排序键 {@code ct}/{@code id}；operationId 十进制纯数字契约由 {@link Operation} 构造期校验）。 */
    private String nextCursorToken(Operation lastOfPage) {
        Map<String, Object> sortKeys = new LinkedHashMap<>();
        sortKeys.put(CURSOR_SORT_KEY_CREATE_TIME, createTimeOrEpoch(lastOfPage).toString());
        sortKeys.put(CURSOR_SORT_KEY_OPERATION_ID, lastOfPage.operationId());
        return pageTokens.nextToken(Long.parseLong(lastOfPage.operationId()), null, sortKeys);
    }

    /**
     * 排序键解析：{@code createTime}（RFC 3339）→ {@link Instant}；缺失 / 非法值按纪元处理（排在最末）——轮询端点不因单条脏数据 500。
     */
    private static Instant createTimeOrEpoch(Operation operation) {
        if (operation.createTime() == null || operation.createTime().isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(operation.createTime());
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    /** 路径变量形态校验（§2 资源 ID 规则）：非数字 / 前导零 → 400（而非落入 Store 查询返回 404）。 */
    private static void requireCanonicalId(String operationId) {
        ResourceIds.parse(operationId);
    }

    private static DomainException notFound(String operationId) {
        return new DomainException(DomainRestCode.OPERATION_NOT_FOUND, operationId);
    }
}

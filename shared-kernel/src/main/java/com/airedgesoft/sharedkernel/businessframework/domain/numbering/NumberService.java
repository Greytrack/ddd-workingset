package com.airedgesoft.sharedkernel.businessframework.domain.numbering;

import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.NumberValue;

import java.util.List;

/**
 * 编号领域服务端口（用户拍板 2026-09-23：number 域只保留 {@code NumberService} 领域服务与 bridge-v3 的 {@code V3NumberService} 桥接服务两个对象， 先复用
 * v3——原 numbering 域的 {@code NumberRule} / {@code NumberValue} / {@code NumberRuleSource} / {@code NumberRuleService} /
 * {@code NumberUniquenessPort} 与内存占位实现全部删除）。
 *
 * <p>
 * <b>能力</b>（对齐 v3 编号引擎语义，以<b>分类 type</b> 为唯一定位键——规则解析沉入桥接实现）：
 *
 * <ul>
 * <li>{@link #next(Long)}：按分类取下一号——<b>取号即占用</b>（v3 引擎内部保存，无独立 consume 段）；分类未配规则 →「编号器未设置」守卫（v3 同款）；</li>
 * <li>{@link #next(Long, NumberValue)}：创建期编号入参统一入口——编号<b>值对象</b>三形态（引擎 / 手输 /
 * 段配置）；引擎形态走引擎取号，段配置由桥接实现交引擎按段生成，手输号原样返回；解析全部沉入桥接实现；</li>
 * <li>{@link #next(Long, int)}：批量取号（拷贝等批量场景）；</li>
 * <li>{@link #exists(Long, String)}：编号是否已被占用（修改手输号查重，v3 分类主表查重口径）；</li>
 * <li>{@link #validateInput(Long, String)}：手输编号预校验（创建守卫链调用，v3 {@code preCheckCommonParam} 段位）；</li>
 * <li>{@link #withNumberLock(Long, Runnable)}：编号临界区（创建编排「预填查重→落库」窗口，多业务对象创建复用）。</li>
 * </ul>
 *
 * <p>
 * <b>2026-11-16 拍板修订</b>：原「入参统一为 String、不在 v4 建模编号值对象」口径被推翻—— String 入参迫使应用层做 JSON 嗅探（编号域知识泄漏）且同一串在应用层与桥接双重解析；
 * 编号载体改建模为值对象 {@link NumberValue}（NUMBER 属性种类的属性值，与 {@code AttributeValue} 家族同构）， 嗅探与解析收敛到值对象
 * {@code NumberValue#fromText}（转换点唯一）。v3 桥接实现在 bridge-v3（{@code V3NumberService}，封装 v3 {@code IAutoNumberEngine} /
 * {@code NumberDuplicatePredicateFactory}——全部 sdk 层接口， 模块依赖红线不变）；生产 Bean 由 deploy-full 组合根组件扫描装配。
 *
 * <p>
 * ⚠️ 挂账（建模阶段口径）：UI 表单预填号的占用语义（v3 表单预览 {@code getNextAutoNumberConfig} 不入库不占用、提交时占用）随真实对接轮收敛； PX 自定义编号的 node 上下文随 PX
 * 轮；工作集作用域查重（索引实例化 / 合并派生模式）随多工作集轮。
 */
public interface NumberService {

    /** 按分类取下一号（取号即占用，v3 引擎语义；分类未配规则 →「编号器未设置」守卫）。 */
    String next(Long type);

    /**
     * 创建期编号入参统一入口（形态三分见 {@link NumberValue}）。
     *
     * @param type
     *            分类 type
     * @param number
     *            编号值（引擎形态走引擎取号；段配置交引擎按段生成；手输号原样返回——占用查重由调用方在 {@link #withNumberLock(Long, Runnable)} 临界区内完成）
     * @return 最终业务编号
     */
    String next(Long type, NumberValue number);

    /** 批量取号（拷贝等批量场景，count&lt;=0 返回空集合）。 */
    List<String> next(Long type, int count);

    /** 编号是否已被占用（修改手输号查重，v3 分类主表查重口径；只对手输号有意义）。 */
    boolean exists(Long type, String number);

    /**
     * 手输编号预校验（v3 {@code prepareAndPreCheckNumber} 口径）：编号非空时校验字符合法性 （{@code ^[a-zA-Z0-9·\-_]+$}，v3 各分支同款正则）；空号放行（走引擎生成）。
     *
     * <p>
     * ⚠️ 挂账（bridge 依赖红线）：v3 的规则类型判定（手输类型未填必填、手输位数 manualInputLength）与属性级 maxLength 在 sdkImpl {@code AutoNumber} /
     * {@code Attribute} 上，bridge 只依赖 sdk 层无法读取——本轮先落字符合法性守卫，类型级差异随编号对接轮收敛。
     */
    void validateInput(Long type, String number);

    /**
     * 编号临界区（v3 {@code executeInsertSql} 分类级 redis 锁口径）：以分类为粒度加分布式锁、锁内执行传入动作—— 预填号占用查重 → 落库的「编号写安全窗口」收进同一临界区，缩小手输号查重
     * -插入竞态；引擎取号在临界区外（引擎号段本身原子）。 多业务对象创建复用（回调式——调用方传临界动作，锁与超时语义归编号域）。
     *
     * <p>
     * 锁 key={@code auto_number_lock:{type}}（v3 同款）；等待 10s / 持有 30s（v3 口径）； 拿不到锁按疑似并发重复拒单（v3 同语义——重试），中断转
     * {@link com.airedgesoft.lib.exception.DomainException}；临界动作抛出的异常原样传播（finally 解锁）。
     *
     * @param type
     *            分类 type（锁粒度）
     * @param criticalSection
     *            临界动作（查重 / 落库等；异常传播即整体失败）
     */
    void withNumberLock(Long type, Runnable criticalSection);
}

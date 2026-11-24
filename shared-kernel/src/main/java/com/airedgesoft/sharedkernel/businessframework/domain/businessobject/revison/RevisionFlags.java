package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison;

/**
 * v3 共表「版次标记」列（{@code air_inst_*_rev.flags}，10 位字符）的终值口径。
 *
 * <p>
 * <b>位序</b>（{@code DataObjectFlagsEnum}，主对象恒 10 位；页签表的 100 位标记不在此列）： p1=最新签入版次，p2=最新版本，p3=最新版次，p4=基线，p5=版次①签出（仅
 * {@code revision=1} 生效），p6–p10 恒 0。
 *
 * <p>
 * <b>为什么是纯函数</b>：标记是 v3 存储协议，不是领域概念——领域只交出「发生了哪个版次动作」（{@link RevisionChangeSet}），由仓储在此翻译成列值。放在共享内核是因为位序对所有
 * v3 共表 BC 都一样，但<b>不</b>含表名、列名等具体协议（那些留在各 BC 的存储常量里）。
 *
 * <p>
 * 终值以 v3 实测为准（首签出 → 签入 → 再签出 → 取消签出 的逐动作快照）：
 *
 * <pre>
 * 创建即签入      1110000000
 * 创建签出态      1110100000
 * 签出  源行      1100000000（p3→0）        新工作行 0010000000
 * 签入  前行      0100000000（p1、p3→0）    当前行   1010000000（p1→1）
 * 取消  源行      1110000000（p3→1）        工作行   删除
 * </pre>
 */
public final class RevisionFlags {

    /** p3：最新版次（版次号最大的行）。 */
    private static final int LATEST_REVISION = 2;

    /** 主对象标记位宽（页签表 100 位勿混）。 */
    private static final int WIDTH = 10;

    /** 创建即签入终态：{@code AddAction} 建行后经 {@code RevisionDataObject#checkIn}。 */
    private static final String CREATED_CHECKED_IN = "1110000000";

    /** 创建即签出终态：{@code AddAction#applyChangeControlDefaults}（第 5 位=1）。 */
    private static final String CREATED_CHECKED_OUT = "1110100000";

    /** 签出新工作版次：非最新签入版次、非最新版本，只有「是最新版次」一位。 */
    private static final String WORKING = "0010000000";

    /** 签入后的当前行：成为最新签入版次与最新版次。 */
    private static final String CHECKED_IN = "1010000000";

    /** 签入后的前一行：让出「最新签入版次」与「最新版次」，保留「最新版本」。 */
    private static final String SUPERSEDED = "0100000000";

    /** 取消签出后的回退行：重新成为最新签入版次行（列值同创建即签入）。 */
    private static final String RESTORED = "1110000000";

    private RevisionFlags() {
    }

    /**
     * 新建首个版次行的标记。
     *
     * @param draftPhase
     *            首版是否停留签出态（领域判定的草稿阶段）
     * @return 签出态 {@code 1110100000} 或签入态 {@code 1110000000}
     */
    public static String created(boolean draftPhase) {
        return draftPhase ? CREATED_CHECKED_OUT : CREATED_CHECKED_IN;
    }

    /** 签出产生的（或创建期复制的）工作版次行标记。 */
    public static String working() {
        return WORKING;
    }

    /**
     * 签出后复制源行的标记：让出「最新版次」一位，其余位不变。
     *
     * @param sourceFlags
     *            本次加载的复制源行原值（可空：空按创建签入态折算）
     */
    public static String released(String sourceFlags) {
        return withFlag(sourceFlags, LATEST_REVISION, false);
    }

    /** 签入后当前行（原工作版次行）的标记。 */
    public static String checkedIn() {
        return CHECKED_IN;
    }

    /** 签入后前一行（原最新签入版次行）的标记。 */
    public static String superseded() {
        return SUPERSEDED;
    }

    /**
     * 取消签出后回退行的标记：重新取得「最新版次」一位，其余位不变。
     *
     * @param releasedFlags
     *            该行被签出时让位后的原值（可空：空按创建签入态折算）
     */
    public static String restored(String releasedFlags) {
        return withFlag(releasedFlags, LATEST_REVISION, true);
    }

    /** 位翻转；入参为空或长度不符时以创建即签入态为基（历史行缺列值的兜底，不抛异常以免阻断保存）。 */
    private static String withFlag(String flags, int bit, boolean on) {
        char[] chars = (flags == null || flags.length() != WIDTH ? CREATED_CHECKED_IN : flags).toCharArray();
        chars[bit] = on ? '1' : '0';
        return new String(chars);
    }
}

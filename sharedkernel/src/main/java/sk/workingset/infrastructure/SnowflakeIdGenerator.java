package sk.workingset.infrastructure;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 雪花 ID 生成器（P1，用户 2026-11-11 决定）。
 *
 * <p>位宽按给定参数：datacenter 3 位、machine 3 位、sequence 5 位；单机每毫秒最多 32 个 ID。
 * 时钟回拨直接拒绝生成，不使用等待或自动修正；{@link #nextId()} 以 {@code synchronized} 保证同实例并发安全。
 */
public final class SnowflakeIdGenerator implements IdentifierGenerator {


    /**
     * 起始的时间戳，可以修改为服务第一次启动的时间 一旦服务已经开始使用，起始时间戳就不应该改变
     */
    static final long START_STAMP = 1484754361114L;

    /**
     * 序列号占用的位数
     */
    private static final long SEQUENCE_BIT = 5;
    /**
     * 机器标识占用的位数
     */
    public static final long MACHINE_BIT = 3;
    /**
     * 数据中心占用的位数
     */
    private static final long DATACENTER_BIT = 3;

    /**
     * 每一部分的最大值
     */
    private static final long MAX_DATACENTER_NUM = ~(-1L << DATACENTER_BIT);
    private static final long MAX_MACHINE_NUM = ~(-1L << MACHINE_BIT);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);

    /**
     * 每一部分向左的位移
     */
    private static final long MACHINE_LEFT = SEQUENCE_BIT;
    private static final long DATACENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
    private static final long TIMESTAMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;

    /**
     * 数据中心
     */
    private final long datacenterId;
    /**
     * 机器标识
     */
    private final long machineId;
    /**
     * 序列号
     */
    private long sequence = 0L;
    /**
     * 上一次时间戳
     */
    private long lastStamp = -1L;

    private final LongSupplier clock;


    /**
     * 通过单例模式来获取实例 分布式部署服务时，数据节点标识和机器标识作为联合键必须唯一
     *
     * @param datacenterId
     *            数据节点标识ID
     * @param machineId
     *            机器标识ID
     */
    public SnowflakeIdGenerator(long datacenterId, long machineId) {
        this(datacenterId, machineId, System::currentTimeMillis);

    }

    SnowflakeIdGenerator(long datacenterId, long machineId, LongSupplier clock) {
        if (datacenterId > MAX_DATACENTER_NUM || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId can't be greater than MAX_DATACENTER_NUM or less than 0");
        }
        if (machineId > MAX_MACHINE_NUM || machineId < 0) {
            throw new IllegalArgumentException("machineId can't be greater than MAX_MACHINE_NUM or less than 0");
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized Number nextId(Object entity) {
        long currStmp = getNewStamp();
        if (currStmp < lastStamp) {
            throw new IllegalArgumentException("ID生成异常");
        }

        if (currStmp == lastStamp) {
            // 相同毫秒内，序列号自增
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 同一毫秒的序列数已经达到最大
            if (sequence == 0L) {
                currStmp = getNextMill();
            }
        } else {
            // 不同毫秒内，序列号置为0
            sequence = 0L;
        }

        lastStamp = currStmp;

        return (currStmp - START_STAMP) << TIMESTAMP_LEFT | datacenterId << DATACENTER_LEFT | machineId << MACHINE_LEFT
                | sequence;
    }

    public synchronized long nextId() {
        return nextId(null).longValue();
    }

    private long getNextMill() {
        long mill = getNewStamp();
        while (mill <= lastStamp) {
            mill = getNewStamp();
        }
        return mill;
    }

    private long getNewStamp() {
        return System.currentTimeMillis();
    }

    public static long getMaxMachineNum() {
        return MAX_MACHINE_NUM;
    }
}

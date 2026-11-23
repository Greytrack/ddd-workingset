package com.airedgesoft.lib.id;

import java.util.function.LongSupplier;

/**
 * Twitter Snowflake 算法（与现网 {@code backend} {@code SnowflakeIdGenerator} 位布局一致）。
 * <p>
 * 格式：0 - 时间戳 - 数据中心 - 机器 - 序列号（本实现：SEQUENCE=5 / MACHINE=3 / DATACENTER=3）。 无 Spring / MyBatis 依赖，供 {@link Ids}
 * 与可注入装配使用。
 */
public final class SnowflakeIdGenerator {

    /** 起始时间戳；服务已开始用后不可改，须与现网一致。 */
    private static final long START_STAMP = 1484754361114L;

    /** 可容忍的时钟回拨上限（毫秒）：不超过则等待追平，超过视为时钟故障拒绝取号（防重复 ID）。 */
    private static final long MAX_CLOCK_BACKWARD_MILLIS = 5L;

    private static final long SEQUENCE_BIT = 5;
    private static final long MACHINE_BIT = 3;
    private static final long DATACENTER_BIT = 3;

    private static final long MAX_DATACENTER_NUM = ~(-1L << DATACENTER_BIT);
    private static final long MAX_MACHINE_NUM = ~(-1L << MACHINE_BIT);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);

    private static final long MACHINE_LEFT = SEQUENCE_BIT;
    private static final long DATACENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
    private static final long TIMESTAMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;

    private final long datacenterId;
    private final long machineId;
    private final LongSupplier clock;
    private long sequence = 0L;
    private long lastStamp = -1L;

    /**
     * @param datacenterId
     *            数据中心 ID（联合键须唯一）
     * @param machineId
     *            机器 ID（联合键须唯一）
     */
    public SnowflakeIdGenerator(long datacenterId, long machineId) {
        this(datacenterId, machineId, System::currentTimeMillis);
    }

    /** 可注入时钟（包内可见，供时钟回拨等边界单测）。 */
    SnowflakeIdGenerator(long datacenterId, long machineId, LongSupplier clock) {
        if (datacenterId > MAX_DATACENTER_NUM || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId can't be greater than MAX_DATACENTER_NUM or less than 0");
        }
        if (machineId > MAX_MACHINE_NUM || machineId < 0) {
            throw new IllegalArgumentException("machineId can't be greater than MAX_MACHINE_NUM or less than 0");
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
        this.clock = clock;
    }

    public synchronized long nextId() {
        long currStmp = getNewStamp();
        if (currStmp < lastStamp) {
            long backwardMillis = lastStamp - currStmp;
            if (backwardMillis > MAX_CLOCK_BACKWARD_MILLIS) {
                throw new IllegalStateException(
                        "Clock moved backwards. Refusing to generate id for " + backwardMillis + " milliseconds");
            }
            // 小幅回拨（NTP 校时 / 虚拟机时钟抖动，实测 1ms 级）：等待时钟追平上次时间戳，再按同毫秒递增序列，避免直接拒绝取号
            currStmp = waitUntilReached(lastStamp);
        }

        if (currStmp == lastStamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                currStmp = getNextMill();
            }
        } else {
            sequence = 0L;
        }

        lastStamp = currStmp;

        return (currStmp - START_STAMP) << TIMESTAMP_LEFT | datacenterId << DATACENTER_LEFT | machineId << MACHINE_LEFT
                | sequence;
    }

    private long waitUntilReached(long stamp) {
        long mill = getNewStamp();
        while (mill < stamp) {
            mill = getNewStamp();
        }
        return mill;
    }

    private long getNextMill() {
        long mill = getNewStamp();
        while (mill <= lastStamp) {
            mill = getNewStamp();
        }
        return mill;
    }

    private long getNewStamp() {
        return clock.getAsLong();
    }

    public static long getMaxMachineNum() {
        return MAX_MACHINE_NUM;
    }

    public static long getMaxDatacenterNum() {
        return MAX_DATACENTER_NUM;
    }
}

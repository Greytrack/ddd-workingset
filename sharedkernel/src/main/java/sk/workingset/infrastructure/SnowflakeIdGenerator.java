package sk.workingset.infrastructure;

import java.util.Objects;
import java.util.function.LongSupplier;

import sk.workingset.application.port.IdentifierGenerator;

/**
 * 雪花 ID 生成器（P1，用户 2026-11-11 决定）。
 *
 * <p>位宽按给定参数：datacenter 3 位、machine 3 位、sequence 5 位；单机每毫秒最多 32 个 ID。
 * 时钟回拨直接拒绝生成，不使用等待或自动修正；{@link #nextId()} 以 {@code synchronized} 保证同实例并发安全。
 */
public final class SnowflakeIdGenerator implements IdentifierGenerator {

    /** 起始纪元：2020-01-01T00:00:00Z。 */
    public static final long START_STAMP = 1577836800000L;
    public static final int DATACENTER_BIT = 3;
    public static final int MACHINE_BIT = 3;
    public static final int SEQUENCE_BIT = 5;

    private static final int MACHINE_LEFT = SEQUENCE_BIT;
    private static final int DATACENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
    private static final int TIMESTAMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_BIT);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_BIT);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BIT);

    private final long datacenterId;
    private final long machineId;
    private final LongSupplier clock;

    private long lastStamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long datacenterId, long machineId) {
        this(datacenterId, machineId, System::currentTimeMillis);
    }

    SnowflakeIdGenerator(long datacenterId, long machineId, LongSupplier clock) {
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException(
                    "datacenterId must be in [0, " + MAX_DATACENTER_ID + "]: " + datacenterId);
        }
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException(
                    "machineId must be in [0, " + MAX_MACHINE_ID + "]: " + machineId);
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized long nextId() {
        long stamp = clock.getAsLong();
        if (stamp < lastStamp) {
            throw new IllegalStateException(
                    "clock moved backwards, refuse to generate id: last=" + lastStamp + ", now=" + stamp);
        }
        if (stamp == lastStamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                stamp = waitNextMillis(lastStamp);
            }
        } else {
            sequence = 0L;
        }
        lastStamp = stamp;
        return ((stamp - START_STAMP) << TIMESTAMP_LEFT)
                | (datacenterId << DATACENTER_LEFT)
                | (machineId << MACHINE_LEFT)
                | sequence;
    }

    private long waitNextMillis(long lastStamp) {
        long stamp = clock.getAsLong();
        while (stamp <= lastStamp) {
            if (stamp < lastStamp) {
                throw new IllegalStateException(
                        "clock moved backwards, refuse to generate id: last=" + lastStamp + ", now=" + stamp);
            }
            stamp = clock.getAsLong();
        }
        return stamp;
    }
}

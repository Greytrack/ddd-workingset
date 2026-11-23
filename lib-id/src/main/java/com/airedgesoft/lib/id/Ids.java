package com.airedgesoft.lib.id;

import java.net.InetAddress;

/**
 * 技术侧 ID 生成门面（无业务语义）。默认使用与现网一致的雪花算法。
 * <p>
 * 多实例部署前应调用 {@link #configure(long, long)}，保证 datacenterId + machineId 联合唯一。
 */
public final class Ids {

    /** 与现网 {@code IdGeneratorConfig.DATACENTER_ID} 默认一致。 */
    private static final long DEFAULT_DATACENTER_ID = 1L;

    private static volatile SnowflakeIdGenerator generator = new SnowflakeIdGenerator(DEFAULT_DATACENTER_ID,
            defaultWorkerId());

    private Ids() {
    }

    /**
     * 覆盖默认生成器（例如启动时按节点配置）。
     */
    public static void configure(long datacenterId, long machineId) {
        generator = new SnowflakeIdGenerator(datacenterId, machineId);
    }

    /** 雪花 long ID。 */
    public static long nextId() {
        return generator.nextId();
    }

    /** 雪花 ID 的十进制字符串。 */
    public static String newId() {
        return Long.toString(nextId());
    }

    /** {@code prefix + "-" + 雪花ID}，便于演示/领域字符串标识。 */
    public static String newId(String prefix) {
        return prefix + "-" + nextId();
    }

    private static long defaultWorkerId() {
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            return (hostAddress.hashCode() & 0x000000FF) % SnowflakeIdGenerator.getMaxMachineNum();
        } catch (Exception e) {
            return 0L;
        }
    }
}
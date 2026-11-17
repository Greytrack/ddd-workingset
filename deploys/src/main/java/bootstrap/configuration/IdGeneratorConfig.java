package bootstrap.configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import sk.workingset.application.port.IdentifierGenerator;
import sk.workingset.infrastructure.SnowflakeIdGenerator;

/**
 * 雪花 ID 装配（P1）：数据中心固定为 1，机器号由本机地址哈希取模（用户给定逻辑）。
 */
@Configuration
public class IdGeneratorConfig {

    private static final long DATACENTER_ID = 1L;
    private static final int MACHINE_SLOTS = 1 << SnowflakeIdGenerator.MACHINE_BIT;

    @Bean
    public IdentifierGenerator identifierGenerator() {
        return new SnowflakeIdGenerator(DATACENTER_ID, resolveWorkerId());
    }

    private long resolveWorkerId() {
        try {
            int hash = InetAddress.getLocalHost().getHostAddress().hashCode();
            return Math.floorMod(hash, MACHINE_SLOTS);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("cannot resolve local host for snowflake worker id", e);
        }
    }
}

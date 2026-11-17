package bootstrap.web;

import java.util.Map;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 最小健康检查：验证 Web 容器、PostgreSQL 与 Redis 连接。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbc;
    private final RedisConnectionFactory redisConnectionFactory;

    public HealthController(JdbcTemplate jdbc, RedisConnectionFactory redisConnectionFactory) {
        this.jdbc = jdbc;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping
    public Map<String, Object> health() {
        String database = "UP";
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
        } catch (RuntimeException failure) {
            database = "DOWN";
        }

        String redis = "UP";
        try {
            RedisConnection connection = redisConnectionFactory.getConnection();
            try {
                connection.ping();
            } finally {
                connection.close();
            }
        } catch (RuntimeException failure) {
            redis = "DOWN";
        }

        String status = "UP".equals(database) && "UP".equals(redis) ? "UP" : "DEGRADED";
        return Map.of("status", status, "database", database, "redis", redis);
    }
}

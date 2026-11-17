package bootstrap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用定时任务（Merge Checkpoint 清理等）。
 *
 * <p>只在 web 应用上下文中生效：非 web 的测试上下文不需要调度基础设施，也避免与
 * 文档/监控自动配置产生额外依赖。
 */
@Configuration
@EnableScheduling
@ConditionalOnWebApplication
public class SchedulingConfig {
}

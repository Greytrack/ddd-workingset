package bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web 应用启动类：Spring Boot 承担内嵌容器启动、依赖装配与事件集成。
 *
 * <p>领域层（{@code sk.workingset.domain}、{@code alm.requirement.domain}）保持纯 Java，不依赖 Spring。
 */
@SpringBootApplication
public class WorkingsetApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkingsetApplication.class, args);
    }
}

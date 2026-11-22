package bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web 应用启动类：Spring Boot 承担内嵌容器启动、依赖装配与事件集成。
 *
 * <p>领域层（{@code sk.workingset.domain}、{@code alm.requirement.domain}）保持纯 Java，不依赖 Spring。
 */
@SpringBootApplication(scanBasePackages = {
        "bootstrap",
        "com.airedgesoft.lib.jdbc",
        "alm.requirement.adapters",
        "alm.requirement.application",
        "alm.requirement.infrastructure",
        "com.airedgesoft.sharekernel.workingset.adapters",
        "com.airedgesoft.sharekernel.workingset.application",
        "com.airedgesoft.sharekernel.workingset.infrastructure",
        "com.airedgesoft.sharekernel.businessframework.infrastructure.projection",
        "com.airedgesoft.sharekernel.tracker.adapters",
        "com.airedgesoft.sharekernel.tracker.application",
        "com.airedgesoft.sharekernel.tracker.infrastructure",
        "com.airedgesoft.sharekernel.productspace"
})
@MapperScan({
        "com.airedgesoft.sharekernel.workingset.infrastructure.providers",
        "alm.requirement.infrastructure.providers"
})
public class WorkingsetApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkingsetApplication.class, args);
    }
}

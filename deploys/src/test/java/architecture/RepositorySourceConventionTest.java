package architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Repository 源码约定检查：注解在源码中可见，SQL 直接表名无法由 ArchUnit 字节码规则捕获。
 */
class RepositorySourceConventionTest {

    private static final Set<String> BUSINESS_TABLES = Set.of(
            "requirements", "requirement_snapshots",
            "parts", "quality_objects", "dqa_items", "gates",
            "reviews", "test_cases", "bugs", "documents");

    private static List<Path> repositorySources() throws IOException {
        return sharedKernelSources().stream()
                .filter(path -> path.getFileName().toString().endsWith("RepositoryImpl.java"))
                .toList();
    }

    private static List<Path> sharedKernelSources() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("sharedkernel"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IOException("Cannot locate repository root");
        }
        try (Stream<Path> files = Files.walk(root.resolve("sharedkernel").resolve("src/main/java"))) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).toList();
        }
    }

    @Test
    void repository_implementations_have_repository_and_slf4j_annotations() throws IOException {
        for (Path file : repositorySources()) {
            String source = Files.readString(file);
            if (!source.contains("implements ") || !source.contains("Repository")) {
                continue;
            }
            String name = file.getFileName().toString();
            if (!name.endsWith("RepositoryImpl.java")) {
                continue;
            }
            assertTrue(source.contains("@Repository"), name + " 缺少 @Repository");
            assertTrue(source.contains("@Slf4j"), name + " 缺少 @Slf4j");
        }
    }

    @Test
    void shared_kernel_sources_do_not_query_business_tables() throws IOException {
        for (Path file : sharedKernelSources()) {
            String source = Files.readString(file).toLowerCase();
            for (String table : BUSINESS_TABLES) {
                if (source.matches("(?s).*\\b(from|join|update|into|delete\\s+from)\\s+" + table + "\\b.*")) {
                    fail(file.getFileName() + " 直接查询业务表: " + table);
                }
            }
        }
    }
}

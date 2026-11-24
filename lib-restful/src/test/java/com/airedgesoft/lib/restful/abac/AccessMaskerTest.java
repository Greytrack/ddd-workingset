package com.airedgesoft.lib.restful.abac;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AccessMasker 字段级脱敏（Omit）单测（组件 8 读路径；record 重建 / bean 置空 / Map 键移除）。
 */
class AccessMaskerTest {

    @Test
    void maskRecord_rebuildsWithNullsAndKeepsOtherFields() {
        QDemoTask resource = new QDemoTask("demoTasks/101", "101", "齿轮箱", 999999L, 3L, null);
        Object masked = AccessMasker.mask(resource, Set.of("estimatedCost"));
        assertThat(masked).isNotSameAs(resource).isInstanceOf(QDemoTask.class);
        QDemoTask result = (QDemoTask) masked;
        assertThat(result.estimatedCost()).isNull(); // Omit：值消失（NON_NULL 序列化即不出现）
        assertThat(result.title()).isEqualTo("齿轮箱");
        assertThat(result.lockedVersion()).isEqualTo(3L); // 并发代数不受脱敏影响
    }

    @Test
    void maskRecord_mapContainerKeyRemoved_byImmutableCopy() {
        QDemoTask resource = new QDemoTask("demoTasks/101", "101", "齿轮箱", null, 3L,
                Map.of("trackingCode", "TC-1", "riskLevel", "HIGH"));
        Object masked = AccessMasker.mask(resource, Set.of("customFields.trackingCode"));
        QDemoTask result = (QDemoTask) masked;
        assertThat(result.customFields()).containsOnlyKeys("riskLevel"); // 原实例 Map 不被改动
        assertThat(resource.customFields()).containsKey("trackingCode");
    }

    @Test
    void maskBean_setterNullClearsField() {
        MutableTask resource = new MutableTask("齿轮箱", 42);
        Object masked = AccessMasker.mask(resource, Set.of("cost"));
        assertThat(masked).isSameAs(resource);
        assertThat(resource.getCost()).isNull();
        assertThat(resource.getTitle()).isEqualTo("齿轮箱");
    }

    @Test
    void emptyMask_returnsOriginalInstance() {
        QDemoTask resource = new QDemoTask("demoTasks/101", "101", "齿轮箱", 1L, 3L, null);
        assertThat(AccessMasker.mask(resource, Set.of())).isSameAs(resource);
    }

    @Test
    void effectiveMaskedPaths_reportsOnlyExistingFields() {
        QDemoTask resource = new QDemoTask("demoTasks/101", "101", "齿轮箱", 1L, 3L, Map.of("trackingCode", "TC-1"));
        List<String> effective = AccessMasker.effectiveMaskedPaths(resource,
                Set.of("estimatedCost", "customFields.trackingCode", "customFields.missing", "noSuchField"));
        assertThat(effective).containsExactlyInAnyOrder("estimatedCost", "customFields.trackingCode");
    }

    // ── 测试样例类型 ──

    /** Q* record（法定形态：可空 customFields Map + lockedVersion）。 */
    record QDemoTask(String name, String taskId, String title, Long estimatedCost, Long lockedVersion,
            Map<String, Object> customFields) {
    }

    /** 可变 bean 形态（直返 DTO）。 */
    public static class MutableTask {

        private String title;

        private Integer cost;

        public MutableTask(String title, Integer cost) {
            this.title = title;
            this.cost = cost;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Integer getCost() {
            return cost;
        }

        public void setCost(Integer cost) {
            this.cost = cost;
        }
    }
}

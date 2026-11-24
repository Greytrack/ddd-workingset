package com.airedgesoft.lib.restful.updatemask;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * updateMask 解析（AIP-134 四模式）/ 白名单校验 / 模式归一 / ModelPatcher 属性覆盖（含 output-only 忽略）单测。
 */
class UpdateMaskTest {

    // ── parse：AIP-134 四模式 ──

    @Test
    void parse_explicit_splitsTrimsDedupesKeepsOrder() {
        UpdateMask mask = UpdateMask.parse(" title , description ,title,customFields.trackingCode ");
        assertThat(mask.mode()).isEqualTo(UpdateMask.Mode.EXPLICIT);
        assertThat(mask.paths()).containsExactly("title", "description", "customFields.trackingCode");
    }

    @Test
    @DisplayName("省略掩码 → ALL_POPULATED（AIP-134:104，不再 400）")
    void parse_omitted_allPopulated() {
        UpdateMask mask = UpdateMask.parse(null);
        assertThat(mask.mode()).isEqualTo(UpdateMask.Mode.ALL_POPULATED);
        assertThat(mask.paths()).isEmpty();
    }

    @Test
    @DisplayName("显式空串 / 空白 → NO_OP（AIP-134:107-109）")
    void parse_empty_noOp() {
        assertThat(UpdateMask.parse("").mode()).isEqualTo(UpdateMask.Mode.NO_OP);
        assertThat(UpdateMask.parse("   ").mode()).isEqualTo(UpdateMask.Mode.NO_OP);
    }

    @Test
    @DisplayName("* → FULL_REPLACE（AIP-134:110）")
    void parse_star_fullReplace() {
        assertThat(UpdateMask.parse("*").mode()).isEqualTo(UpdateMask.Mode.FULL_REPLACE);
        assertThat(UpdateMask.parse(" * ").mode()).isEqualTo(UpdateMask.Mode.FULL_REPLACE);
    }

    @Test
    void parse_emptySegment_throws400Invalid() {
        assertThatThrownBy(() -> UpdateMask.parse("title,,desc")).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getType()).isEqualTo(DomainErrorType.INVALID_ARGUMENT);
    }

    // ── validate：白名单 ──

    @Test
    void validate_unknownFields_failFastWithPerFieldViolations() {
        UpdateMask mask = UpdateMask.parse("title,typoField,anotherTypo");
        assertThatThrownBy(() -> mask.validate(Set.of("title", "status"))).isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    DomainException ex = (DomainException) e;
                    assertThat(ex.getType()).isEqualTo(DomainErrorType.INVALID_ARGUMENT);
                    assertThat(ex.getFieldViolations()).extracting(v -> v.field()).containsExactly("typoField",
                            "anotherTypo");
                    assertThat(ex.getFieldViolations())
                            .allSatisfy(v -> assertThat(v.code()).isEqualTo(DomainRestCode.UPDATE_MASK_UNKNOWN));
                });
    }

    @Test
    void validate_knownFields_pass() {
        UpdateMask.parse("title,status").validate(Set.of("title", "status"));
    }

    // ── resolve：模式 → 具体路径 ──

    @Test
    void resolve_allPopulated_picksNonNullWritableFields() {
        UpdateTaskRequest source = new UpdateTaskRequest("新标题", null, null);
        UpdateMask resolved = UpdateMask.parse(null).resolve(source, Set.of("title", "description"));
        assertThat(resolved.mode()).isEqualTo(UpdateMask.Mode.EXPLICIT);
        assertThat(resolved.paths()).containsExactly("title");
    }

    @Test
    void resolve_fullReplace_takesAllWritableFields() {
        UpdateTaskRequest source = new UpdateTaskRequest("新标题", null, null);
        assertThat(UpdateMask.parse("*").resolve(source, Set.of("title", "description")).paths())
                .containsExactlyInAnyOrder("title", "description");
    }

    @Test
    void resolve_noOp_emptyPaths() {
        assertThat(UpdateMask.parse("").resolve(new UpdateTaskRequest("t", "d", null), Set.of("title")).paths())
                .isEmpty();
    }

    @Test
    void resolve_allPopulated_withoutWhitelist_failsClosedToEmpty() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("trackingCode", "TC-9");
        UpdateTaskRequest source = new UpdateTaskRequest("新标题", null, fields);
        // 无白名单 = 无可写字段（fail-closed）：绝不回退枚举请求体属性，防止省略掩码放大成全写
        assertThat(UpdateMask.parse(null).resolve(source, null).paths()).isEmpty();
        assertThat(UpdateMask.parse("*").resolve(source, Set.of()).paths()).isEmpty();
    }

    // ── ModelPatcher：EXPLICIT ──

    @Test
    void modelPatcher_overwritesOnlyMaskedFields() {
        UpdateTaskRequest source = new UpdateTaskRequest("新标题", "新描述", null);
        TaskEntity target = new TaskEntity("旧标题", "旧描述", "ACTIVE");
        ModelPatcher.apply(source, target, UpdateMask.parse("title"), null);
        assertThat(target.getTitle()).isEqualTo("新标题");
        assertThat(target.getDescription()).isEqualTo("旧描述"); // 掩码外不受影响
    }

    @Test
    void modelPatcher_explicitNull_clearsField() {
        UpdateTaskRequest source = new UpdateTaskRequest("新标题", null, null);
        TaskEntity target = new TaskEntity("旧标题", "旧描述", "ACTIVE");
        ModelPatcher.apply(source, target, UpdateMask.parse("title,description"), null);
        assertThat(target.getTitle()).isEqualTo("新标题");
        assertThat(target.getDescription()).isNull(); // 掩码内显式 null = 清空
    }

    @Test
    void modelPatcher_customFieldPaths_writeMapKeys() {
        Map<String, Object> sourceFields = new HashMap<>();
        sourceFields.put("trackingCode", "TC-9");
        UpdateTaskRequest source = new UpdateTaskRequest(null, null, sourceFields);
        TaskEntity target = new TaskEntity("t", "d", "ACTIVE");
        target.setCustomFields(new HashMap<>(Map.of("trackingCode", "TC-1", "riskLevel", "HIGH")));
        ModelPatcher.apply(source, target, UpdateMask.parse("customFields.trackingCode"), null);
        assertThat(target.getCustomFields()).containsEntry("trackingCode", "TC-9");
        assertThat(target.getCustomFields()).containsKey("riskLevel"); // 掩码外键不受影响
    }

    @Test
    void propertyAccess_readThroughMapSegments() {
        Map<String, Object> sourceFields = new HashMap<>();
        sourceFields.put("trackingCode", "TC-9");
        UpdateTaskRequest source = new UpdateTaskRequest("t", "d", sourceFields);
        assertThat(PropertyAccess.read(source, "customFields.trackingCode")).isEqualTo("TC-9");
        assertThat(PropertyAccess.read(source, "customFields.missing")).isNull();
    }

    @Test
    void modelPatcher_nestedBeanPath_navigatesExistingInstance() {
        NestedSource source = new NestedSource(new Address("新路 1 号"));
        Customer target = new Customer(new Address("旧路 2 号"));
        ModelPatcher.apply(source, target, UpdateMask.parse("address.street"), null);
        assertThat(target.address().getStreet()).isEqualTo("新路 1 号");
    }

    @Test
    void modelPatcher_missingNestedInstance_failsFast() {
        NestedSource source = new NestedSource(new Address("新路 1 号"));
        Customer target = new Customer(null);
        assertThatThrownBy(() -> ModelPatcher.apply(source, target, UpdateMask.parse("address.street"), null))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── ModelPatcher：AIP-134 模式 + AIP-161 output-only ──

    @Test
    @DisplayName("省略掩码：仅覆盖已填充字段，null 字段保持不变")
    void modelPatcher_omittedMask_appliesPopulatedFieldsOnly() {
        UpdateTaskRequest source = new UpdateTaskRequest("新标题", null, null);
        TaskEntity target = new TaskEntity("旧标题", "旧描述", "ACTIVE");
        ModelPatcher.apply(source, target, UpdateMask.parse(null), Set.of("title", "description"));
        assertThat(target.getTitle()).isEqualTo("新标题");
        assertThat(target.getDescription()).isEqualTo("旧描述"); // 未填充 → 不变
    }

    @Test
    @DisplayName("* 全量替换：白名单内 null 字段被清空")
    void modelPatcher_starMask_fullReplaceClearsNullFields() {
        UpdateTaskRequest source = new UpdateTaskRequest("新标题", null, null);
        TaskEntity target = new TaskEntity("旧标题", "旧描述", "ACTIVE");
        ModelPatcher.apply(source, target, UpdateMask.parse("*"), Set.of("title", "description"));
        assertThat(target.getTitle()).isEqualTo("新标题");
        assertThat(target.getDescription()).isNull(); // 全量替换 null = 清空
    }

    @Test
    @DisplayName("空串掩码：no-op，目标完全不变")
    void modelPatcher_emptyMask_noOp() {
        UpdateTaskRequest source = new UpdateTaskRequest("新标题", "新描述", null);
        TaskEntity target = new TaskEntity("旧标题", "旧描述", "ACTIVE");
        ModelPatcher.apply(source, target, UpdateMask.parse(""), null);
        assertThat(target.getTitle()).isEqualTo("旧标题");
        assertThat(target.getDescription()).isEqualTo("旧描述");
    }

    @Test
    @DisplayName("output-only 忽略：白名单外字段即便已填充也不写（AIP-161:86-94）")
    void modelPatcher_omittedMask_ignoresOutputOnlyFields() {
        UpdateTaskRequest source = new UpdateTaskRequest("新标题", "新描述", null);
        TaskEntity target = new TaskEntity("旧标题", "旧描述", "ACTIVE");
        ModelPatcher.apply(source, target, UpdateMask.parse(null), Set.of("title")); // description 视为 output-only
        assertThat(target.getTitle()).isEqualTo("新标题");
        assertThat(target.getDescription()).isEqualTo("旧描述");
    }

    // ── 测试样例类型 ──

    /** 值源 record：customFields 组件用 Object 承载 Map（模拟 Q* 顶层 customFields 容器）。 */
    record UpdateTaskRequest(String title, String description, Object customFields) {
    }

    /** 覆盖目标：可变实体（public 访问器——PropertyAccess 经 getMethods 反射）。 */
    public static class TaskEntity {
        private String title;
        private String description;
        private String status;
        private Map<String, Object> customFields;

        public TaskEntity(String title, String description, String status) {
            this.title = title;
            this.description = description;
            this.status = status;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getStatus() {
            return status;
        }

        public Map<String, Object> getCustomFields() {
            return customFields;
        }

        public void setCustomFields(Map<String, Object> customFields) {
            this.customFields = customFields;
        }
    }

    /** 可变嵌套对象（ModelPatcher 导航 + 末段 setter 赋值）。 */
    public static class Address {
        private String street;

        public Address(String street) {
            this.street = street;
        }

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }
    }

    record NestedSource(Address address) {
    }

    record Customer(Address address) {
    }
}
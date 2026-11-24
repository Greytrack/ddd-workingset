package com.airedgesoft.lib.restful.abac;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.restful.updatemask.UpdateMask;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AbacWriteGuard 写路径校验链第 3 / 5 步单测（组件 8；403 + 逐条 fieldViolations，一次返回全部）。
 */
class AbacWriteGuardTest {

    private static final UpdateMaskRequest REQUEST = new UpdateMaskRequest("新标题", "FAST_TRACK");

    private record UpdateMaskRequest(String title, String channel) {
    }

    @Test
    void fieldNotWritable_403WithPerFieldViolations() {
        AccessMetadata metadata = new AccessMetadata(Set.of(), Set.of("title"), Set.of(), Map.of());
        assertThatThrownBy(() -> AbacWriteGuard.guard(metadata, UpdateMask.parse("title,channel"), REQUEST))
                .isInstanceOf(DomainException.class).satisfies(e -> {
                    DomainException ex = (DomainException) e;
                    assertThat(ex.getType()).isEqualTo(DomainErrorType.FORBIDDEN);
                    assertThat(ex.getFieldViolations()).extracting(v -> v.field()).containsExactly("channel");
                    assertThat(ex.getFieldViolations())
                            .allSatisfy(v -> assertThat(v.code()).isEqualTo(DomainRestCode.FIELD_NOT_WRITABLE));
                });
    }

    @Test
    void valueConstraintViolated_403WithAllowedSetHint() {
        AccessMetadata metadata = new AccessMetadata(Set.of(), Set.of("title", "channel"), Set.of(),
                Map.of("channel", Set.of("GATE_REVIEW")));
        assertThatThrownBy(() -> AbacWriteGuard.guard(metadata, UpdateMask.parse("channel"), REQUEST))
                .isInstanceOf(DomainException.class).satisfies(e -> {
                    DomainException ex = (DomainException) e;
                    assertThat(ex.getType()).isEqualTo(DomainErrorType.FORBIDDEN);
                    assertThat(ex.getFieldViolations()).singleElement().satisfies(v -> {
                        assertThat(v.field()).isEqualTo("channel");
                        assertThat(v.code()).isEqualTo(DomainRestCode.VALUE_CONSTRAINT_VIOLATED);
                        assertThat(v.args()).contains("FAST_TRACK");
                    });
                });
    }

    @Test
    void withinConstraints_passes() {
        AccessMetadata metadata = new AccessMetadata(Set.of(), Set.of("title", "channel"), Set.of(),
                Map.of("channel", Set.of("GATE_REVIEW", "FAST_TRACK")));
        assertThatCode(() -> AbacWriteGuard.guard(metadata, UpdateMask.parse("title,channel"), REQUEST))
                .doesNotThrowAnyException();
    }

    @Test
    void emptyWritableFields_meansUnrestricted_backwardCompatible() {
        // 缺省 Provider（allowAll）：enforceWritable=false 且可写集为空 = 不启用白名单，行为与未接入裁决时一致
        AccessMetadata allowAll = AccessMetadata.allowAll();
        assertThatCode(() -> AbacWriteGuard.guard(allowAll, UpdateMask.parse("title,channel"), REQUEST))
                .doesNotThrowAnyException();
    }

    @Test
    void restrictedOrDenyAllWithEmptyWritableFields_rejects403FailClosed() {
        AccessMetadata denied = AccessMetadata.denyAll(Set.of("title"));
        assertThatThrownBy(() -> AbacWriteGuard.guard(denied, UpdateMask.parse("title"), REQUEST))
                .isInstanceOf(DomainException.class).satisfies(e -> {
                    DomainException ex = (DomainException) e;
                    assertThat(ex.getType()).isEqualTo(DomainErrorType.FORBIDDEN);
                    assertThat(ex.getFieldViolations()).singleElement().satisfies(v -> {
                        assertThat(v.field()).isEqualTo("title");
                        assertThat(v.code()).isEqualTo(DomainRestCode.FIELD_NOT_WRITABLE);
                    });
                });

        AccessMetadata restrictedEmpty = AccessMetadata.restricted(Set.of(), Set.of(), Set.of(), Map.of());
        assertThatThrownBy(() -> AbacWriteGuard.guard(restrictedEmpty, UpdateMask.parse("channel"), REQUEST))
                .isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("type", DomainErrorType.FORBIDDEN);
    }

    @Test
    void explicitNullValue_skipsValueConstraint() {
        // 显式 null = 清空语义，不受值约束限制（必填性由 §19.3 第 4 步裁决）
        AccessMetadata metadata = new AccessMetadata(Set.of(), Set.of("channel"), Set.of(),
                Map.of("channel", Set.of("GATE_REVIEW")));
        NullingRequest nulling = new NullingRequest();
        assertThatCode(() -> AbacWriteGuard.guard(metadata, UpdateMask.parse("channel"), nulling))
                .doesNotThrowAnyException();
    }

    @Test
    void mixedViolations_reportedTogether() {
        AccessMetadata metadata = new AccessMetadata(Set.of(), Set.of("title"), Set.of(),
                Map.of("channel", Set.of("GATE_REVIEW")));
        assertThatThrownBy(() -> AbacWriteGuard.guard(metadata, UpdateMask.parse("title,channel"), REQUEST))
                .isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("type", DomainErrorType.FORBIDDEN)
                .satisfies(e -> assertThat(((DomainException) e).getFieldViolations()).hasSize(1));
    }

    /** 读取恒为 null 的请求体（清空语义）。 */
    public static class NullingRequest {

        public String getChannel() {
            return null;
        }
    }
}

package com.airedgesoft.lib.restful.readmask;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ReadMask} 单测：可选投影掩码的解析（缺席宽容 / 去重保序 / 空段跳过）与白名单校验（未知字段 400 + 逐字段 {@code fieldViolations}）。
 */
class ReadMaskTest {

    @DisplayName("parse：readMask 缺席或空白（null/空串/纯空格）解析为空掩码")
    @Test
    void parse_AbsentOrBlank_emptyMask() {
        assertThat(ReadMask.parse(null).paths()).isEmpty();
        assertThat(ReadMask.parse("").paths()).isEmpty();
        assertThat(ReadMask.parse("   ").paths()).isEmpty();
        assertThat(ReadMask.parse(null).isEmpty()).isTrue();
    }

    @DisplayName("parse：字段段 trim 去重且保声明序")
    @Test
    void parse_Segments_trimDedupeAndKeepOrder() {
        ReadMask mask = ReadMask.parse(" title , taskId ,title,status ");
        assertThat(mask.paths()).containsExactly("title", "taskId", "status");
        assertThat(mask.size()).isEqualTo(3);
    }

    @DisplayName("parse：连续逗号/尾逗号空段宽容跳过，不报 400")
    @Test
    void parse_EmptySegments_skippedLeniently() {
        // readMask 可选宽容语义（区别于 updateMask 空路径段 400）：连续逗号 / 尾逗号跳过，不 400
        assertThat(ReadMask.parse(",title,,status,").paths()).containsExactly("title", "status");
    }

    @DisplayName("validate：未知字段 400（READ_MASK_UNKNOWN），一次返回全部 fieldViolations 且保掩码声明序")
    @Test
    void validate_UnknownFields_throws400WithFieldViolations() {
        ReadMask mask = ReadMask.parse("title,typo,moreTypo");
        assertThatThrownBy(() -> mask.validate(Set.of("name", "title", "taskId"))).isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException domain = (DomainException) ex;
                    assertThat(domain.getType()).isEqualTo(DomainErrorType.INVALID_ARGUMENT);
                    assertThat(domain.getResCode()).isEqualTo(DomainRestCode.READ_MASK_UNKNOWN);
                    // 一次返回全部未知字段（规范 §8），保掩码声明序
                    assertThat(domain.getFieldViolations().stream().map(v -> v.field())).containsExactly("typo",
                            "moreTypo");
                });
    }

    @DisplayName("validate：掩码字段全部已知时校验通过")
    @Test
    void validate_KnownFields_passes() {
        assertThatCode(() -> ReadMask.parse("title,taskId").validate(Set.of("name", "title", "taskId")))
                .doesNotThrowAnyException();
    }

    @DisplayName("validate：空掩码（无投影语义）直接通过")
    @Test
    void validate_EmptyMask_passesWithoutProjection() {
        assertThatCode(() -> ReadMask.parse("").validate(Set.of())).doesNotThrowAnyException();
    }

    @DisplayName("includes：精确匹配已声明路径，未声明字段返回 false")
    @Test
    void includes_declaredPath_matchedExactly() {
        assertThat(ReadMask.parse("title,status").includes("title")).isTrue();
        assertThat(ReadMask.parse("title,status").includes("taskId")).isFalse();
        assertThat(ReadMask.parse("title").paths()).isEqualTo(List.of("title"));
    }
}

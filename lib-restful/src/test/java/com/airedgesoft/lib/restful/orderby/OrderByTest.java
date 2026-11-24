package com.airedgesoft.lib.restful.orderby;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderByResolver / OrderBy 单测（组件 4；多字段解析、白名单、主键兜底）。
 */
class OrderByTest {

    @Test
    void resolve_multiFieldWithDirections() {
        OrderBy orderBy = OrderBy.resolve("field1 desc,field2 asc", Set.of("field1", "field2"));
        assertThat(orderBy.keys()).containsExactly(new OrderBy.SortKey("field1", SortDirection.DESC),
                new OrderBy.SortKey("field2", SortDirection.ASC));
        assertThat(orderBy.render()).isEqualTo("field1 DESC, field2 ASC");
    }

    @Test
    void resolve_missingDirectionDefaultsAsc_caseInsensitive() {
        OrderBy orderBy = OrderBy.resolve("field1 DESC , field2", Set.of("field1", "field2"));
        assertThat(orderBy.render()).isEqualTo("field1 DESC, field2 ASC");
    }

    @Test
    void resolve_unknownFields_failFastWithViolations() {
        assertThatThrownBy(() -> OrderBy.resolve("field1, unknown1 desc", Set.of("field1")))
                .isInstanceOf(DomainException.class).satisfies(e -> {
                    DomainException ex = (DomainException) e;
                    assertThat(ex.getType()).isEqualTo(DomainErrorType.INVALID_ARGUMENT);
                    assertThat(ex.getFieldViolations()).extracting(v -> v.field()).containsExactly("unknown1");
                });
    }

    @Test
    void resolve_invalidDirection_throws() {
        assertThatThrownBy(() -> OrderBy.resolve("field1 sideways", Set.of("field1")))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void resolve_blank_returnsEmpty() {
        assertThat(OrderBy.resolve(null, Set.of("field1")).keys()).isEmpty();
        assertThat(OrderBy.resolve("  ", Set.of("field1")).render()).isEmpty();
    }

    @Test
    void resolve_withPrimaryKey_appendsAscendingAtEnd() {
        OrderBy orderBy = OrderBy.resolve("title desc", Set.of("title", "id"), "id");
        assertThat(orderBy.keys()).containsExactly(new OrderBy.SortKey("title", SortDirection.DESC),
                new OrderBy.SortKey("id", SortDirection.ASC));
    }

    @Test
    void resolve_primaryKeyAlreadyDeclared_notDuplicated() {
        OrderBy orderBy = OrderBy.resolve("id desc,title", Set.of("id", "title"), "id");
        assertThat(orderBy.keys()).containsExactly(new OrderBy.SortKey("id", SortDirection.DESC),
                new OrderBy.SortKey("title", SortDirection.ASC));
    }

    @Test
    void includes_reportsDeclaredField() {
        assertThat(OrderBy.resolve("title", Set.of("title")).includes("title")).isTrue();
        assertThat(OrderBy.resolve("title", Set.of("title")).includes("id")).isFalse();
    }
}

package com.airedgesoft.lib.restful.pagination;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InMemoryPages} 内存分页单测（AIP-158:173-176）：offset 游标切片、末页 null token、pageSize 缺省/0 取默认、 负值 400、非法 token
 * 400、totalSize 为全量条数。
 */
class InMemoryPagesTest {

    private static final List<String> FIVE = List.of("a", "b", "c", "d", "e");

    @Test
    void firstPage_slicesAndCarriesNextToken() {
        Page<String> page = InMemoryPages.paginate(FIVE, 2, null);
        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.totalSize()).isEqualTo(5);
        assertThat(page.nextPageToken()).isNotBlank();
    }

    @Test
    void tokenAdvances_untilLastPageNullToken() {
        Page<String> second = InMemoryPages.paginate(FIVE, 2, InMemoryPages.paginate(FIVE, 2, null).nextPageToken());
        assertThat(second.items()).containsExactly("c", "d");
        Page<String> last = InMemoryPages.paginate(FIVE, 2, second.nextPageToken());
        assertThat(last.items()).containsExactly("e");
        assertThat(last.nextPageToken()).isNull();
    }

    @Test
    void absentOrZeroPageSize_usesDefault() {
        assertThat(InMemoryPages.paginate(FIVE, null, null).items()).hasSize(5);
        assertThat(InMemoryPages.paginate(FIVE, 0, null).items()).hasSize(5);
    }

    @Test
    void negativePageSize_rejectedWith400() {
        assertThatThrownBy(() -> InMemoryPages.paginate(FIVE, -1, null)).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo(DomainRestCode.PAGE_SIZE_INVALID.getCode());
    }

    @Test
    void tamperedToken_rejectedWith400() {
        assertThatThrownBy(() -> InMemoryPages.paginate(FIVE, 2, "not-a-valid-token"))
                .isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(DomainRestCode.PAGE_TOKEN_INVALID.getCode());
    }

    @Test
    void offsetBeyondTotal_returnsEmptyPage() {
        Page<String> first = InMemoryPages.paginate(FIVE, 5, null);
        assertThat(first.nextPageToken()).isNull();
        Page<String> empty = InMemoryPages.paginate(List.of(), 2, null);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.totalSize()).isZero();
        assertThat(empty.nextPageToken()).isNull();
    }
}
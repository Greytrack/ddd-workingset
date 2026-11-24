package com.airedgesoft.lib.restful.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResourceIdsTest {

    @Test
    @DisplayName("parse：十进制纯数字串 → long")
    void digits_parsedToLong() {
        assertEquals(623809242866016L, ResourceIds.parse("623809242866016"));
    }

    @Test
    @DisplayName("parse：空串 / 前缀 / 非数字 / 负号 / 空白 / 溢出 / 全角数字 / 前导零 / null → 400")
    void invalidForms_throwInvalidArgument() {
        for (String bad : new String[]{"", "TSK-1", "12a", "-1", " 1", "99999999999999999999", "１２３", "٣", "00123",
                "01"}) {
            DomainException e = assertThrows(DomainException.class, () -> ResourceIds.parse(bad), bad);
            assertEquals(DomainErrorType.INVALID_ARGUMENT, e.getType());
        }
        assertThrows(DomainException.class, () -> ResourceIds.parse(null));
    }

    @Test
    @DisplayName("parse：单个 \"0\" 是规范形态")
    void singleZero_isCanonical() {
        assertEquals(0L, ResourceIds.parse("0"));
    }

    @Test
    @DisplayName("parseNullable / format：null 透传，Long → 十进制串")
    void nullableAndFormat_roundTrip() {
        assertNull(ResourceIds.parseNullable(null));
        assertNull(ResourceIds.format(null));
        assertEquals("42", ResourceIds.format(42L));
    }

    @Test
    @DisplayName("parseName：AIP-122 相对资源名 → 末端 id；集合段不符 / 缺 id → 400")
    void parseName_extractsTrailingId() {
        assertEquals(623809242866016L, ResourceIds.parseName("productSpaces/623809242866016", "productSpaces"));
        assertEquals(623809242866016L, ResourceIds.parseNullableName("requirements/623809242866016", "requirements"));
        assertNull(ResourceIds.parseNullableName(null, "requirements"));
        for (String bad : new String[]{"trackers/1", "productSpaces/", "productSpaces/001", "productSpaces"}) {
            assertThrows(DomainException.class, () -> ResourceIds.parseName(bad, "productSpaces"), bad);
        }
    }
}

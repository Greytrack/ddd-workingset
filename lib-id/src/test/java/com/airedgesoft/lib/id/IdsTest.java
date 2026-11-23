package com.airedgesoft.lib.id;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void testNextIdPositive() {
        assertTrue(Ids.nextId() > 0);
    }

    @Test
    void testNewIdWithPrefix() {
        String id = Ids.newId("TSK");
        assertTrue(id.startsWith("TSK-"));
        assertTrue(Long.parseLong(id.substring(4)) > 0);
    }
}

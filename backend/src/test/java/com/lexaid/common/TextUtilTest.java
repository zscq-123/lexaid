package com.lexaid.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextUtilTest {
    @Test
    void excerptsPreserveWhitespaceAndEllipsisAtTheDisplayBoundary() {
        assertEquals("", TextUtil.excerpt(null, 4));
        assertEquals("", TextUtil.excerpt(" \t\n", 0));
        assertEquals("…", TextUtil.excerpt("法律", 0));
        assertEquals("法 条", TextUtil.excerpt("\u0001 法\t\n条  ", 3));
        assertEquals("法 …", TextUtil.excerpt("法\r\n\t条", 2));
        assertEquals("法\u00a0条", TextUtil.excerpt("法\u00a0条", 3));
        assertEquals("证据…", TextUtil.excerpt("证据\n".repeat(100_000), 2));
    }
}

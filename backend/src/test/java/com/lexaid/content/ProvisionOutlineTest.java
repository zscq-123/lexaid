package com.lexaid.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProvisionOutlineTest {
    @Test
    void normalizesImportedAndSeedHeadingsAndKeepsTheFullAncestry() {
        String first = "第二编物权 · 第一分编通则 · 第一章一般规定";
        String fourth = "第四编 人格权·第一章 一般规定";
        var roots = ProvisionOutline.tree(List.of(first, first + " · 第一节登记", fourth,
                "第二编 物权·第一分编 通则·第一章 一般规定"));
        assertEquals(List.of("第二编 物权", "第四编 人格权"), roots.stream().map(ProvisionOutline.Node::label).toList());
        var chapter = roots.get(0).children().get(0).children().get(0);
        assertEquals(1, chapter.children().size());
        assertTrue(ProvisionOutline.within(first, chapter.path()));
        assertTrue(ProvisionOutline.within(first, "第二编 物权"));
        assertFalse(ProvisionOutline.within(fourth, chapter.path()));
        assertFalse(ProvisionOutline.within(first, "第二编 物"));
    }

    @Test
    void preservesNamesAndDoesNotInventMissingLevels() {
        assertEquals(List.of("Part I", "Civil Rights"), ProvisionOutline.parts("Part I · Civil Rights"));
        assertEquals(List.of("附则"), ProvisionOutline.parts("附则"));
        assertEquals(List.of(), ProvisionOutline.parts(null));
    }
}

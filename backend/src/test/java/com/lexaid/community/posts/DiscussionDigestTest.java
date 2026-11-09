package com.lexaid.community.posts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiscussionDigestTest {
    private final ObjectMapper json = new ObjectMapper();
    private final List<DiscussionDigest.Material> materials = List.of(
            new DiscussionDigest.Material(0, 5, "甲","要判断危险是否仍在持续。",0),
            new DiscussionDigest.Material(101, 6, "乙","不能仅以损害结果倒推。",1));

    private DiscussionDigest.Summary parse(String kind,String refs) throws Exception {
        return DiscussionDigest.parse(json.readTree("""
                {"topic":"防卫判断","points":[{"kind":"%s","text":"讨论判断标准","evidence":%s}]}
                """.formatted(kind,refs)),materials);
    }

    @Test
    void acceptsActualExcerptsAndOnlyTheExpressedKinds() throws Exception {
        var result=parse("OTHER","[{\"id\":101,\"quote\":\"损害结果\"}]");
        assertEquals(List.of(101L),result.points().get(0).sourceIds());
        var split=parse("DISAGREEMENT","[{\"id\":0,\"quote\":\"危险是否仍在持续\"},{\"id\":101,\"quote\":\"损害结果\"}]");
        assertEquals(List.of(0L,101L),split.points().get(0).sourceIds());
        assertTrue(DiscussionDigest.parse(json.readTree("{\"topic\":\"暂无实质讨论\",\"points\":[]}"),materials).points().isEmpty());
    }

    @Test
    void rejectsInventedCitationsAndSingleSpeakerConsensus() {
        assertThrows(IllegalArgumentException.class,()->parse("SUPPORT","[{\"id\":9,\"quote\":\"危险\"}]"));
        assertThrows(IllegalArgumentException.class,()->parse("OTHER","[{\"id\":101,\"quote\":\"对方有罪\"}]"));
        assertThrows(IllegalArgumentException.class,()->parse("OTHER","[{\"id\":101,\"quote\":\"。\"}]"));
        assertThrows(IllegalArgumentException.class,()->parse("AGREEMENT","[{\"id\":101,\"quote\":\"损害结果\"}]"));
        assertThrows(IllegalArgumentException.class,()->parse("OTHER","[]"));
    }
}

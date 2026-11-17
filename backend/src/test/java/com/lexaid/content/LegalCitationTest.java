package com.lexaid.content;

import com.lexaid.ai.search.SiteQueryPlanner;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;

class LegalCitationTest {
    @Test
    void separatesRequestWordsAndVersionLabelsFromTheStatuteTitle() {
        for (String q : List.of("查一下刑法第20条", "帮我查一下刑法第20条", "请帮我查一下现行刑法第20条", "看看中华人民共和国刑法第二十条")) {
            assertThat(LegalCitation.parse(q)).as(q).isEqualTo(new LegalCitation("刑法","第二十条","第20条"));
        }
        for (String q : List.of("刑法2020年版第20条", "2020年修正刑法第20条", "《刑法（2020年修正）》第二十条", "查一下刑法第20条（2020年版）")) {
            assertThat(LegalCitation.parse(q)).as(q).isEqualTo(new LegalCitation("刑法","第二十条","第20条",2020));
        }
        assertThat(LegalCitation.parse("查一下不存在法律第20条")).isNull();
        assertThat(LegalCitation.parse("查一下不存在法第20条").law()).isEqualTo("不存在法");
        assertThat(LegalCitation.parse("《中华人民共和国政府信息公开条例》第20条").law()).isEqualTo("政府信息公开条例");
        assertThat(LegalCitation.parse("查一下中国人民银行法第20条").law()).isEqualTo("中国人民银行法");
    }

    @Test
    void doesNotCollapseSeveralCitationsIntoOne() {
        for (String q : List.of("刑法第20条和民法典第20条", "《刑法》第20条与民法典第20条", "刑法和民法典第20条", "《刑法》第20条和第21条")) {
            assertThat(LegalCitation.parse(q)).as(q).isNull();
        }
    }

    @Test
    void normalizesTitlesNumeralsAndSubArticlesWithoutGuessing() {
        for (String q : List.of("刑法第20条", "查询《中华人民共和国刑法》第二十条", "《刑法》第 20 条")) {
            assertThat(LegalCitation.fromTerms(SiteQueryPlanner.local(q,null).terms()))
                    .isEqualTo(new LegalCitation("刑法","第二十条","第20条"));
        }
        assertThat(LegalCitation.fromTerms(SiteQueryPlanner.local("刑法第287条之二",null).terms()).article())
                .isEqualTo("第二百八十七条之二");
        assertThat(LegalCitation.fromTerms(List.of("民法典","第一千零二条")).numericArticle()).isEqualTo("第1002条");
        assertThat(LegalCitation.fromTerms(List.of("第二十条"))).isNull();
        assertThat(LegalCitation.fromTerms(List.of("刑法","民法典","第二十条"))).isNull();
        assertThat(LegalCitation.parse("请解释刑法第20条")).isEqualTo(new LegalCitation("刑法","第二十条","第20条"));
        assertThat(LegalCitation.parse("《刑法》2020年修正第20条").versionYear()).isEqualTo(2020);
        assertThat(LegalCitation.parse("比较《刑法》第20条的1997年版和2020年版").versionYear()).isNull();
    }
}

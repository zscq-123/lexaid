package com.lexaid.ai.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.ai.DeepSeekClient;
import com.lexaid.common.BizException;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteQueryPlannerTest {
    @Test
    void keepsArticleNumbersAndQuotedTitlesWithoutAProvider() {
        assertThat(SiteQueryPlanner.local("查询《中华人民共和国民法典》第577条", null).terms())
                .contains("中华人民共和国民法典", "第五百七十七条", "第577条");
        assertThat(SiteQueryPlanner.local("刑法第20条", null).terms()).contains("第二十条", "刑法");
        assertThat(SiteQueryPlanner.local("查找关于正当防卫的论文", null))
                .isEqualTo(new SiteQueryPlanner.Plan(List.of("正当防卫"), List.of("PAPER")));
        assertThat(SiteQueryPlanner.local("我的收藏分区有哪些", null).terms()).isEmpty();
    }

    @Test
    void honorsTheSelectedScopeAndRecoversFromMalformedPlans() {
        DeepSeekClient client = mock(DeepSeekClient.class);
        var planner = new SiteQueryPlanner(client, new ObjectMapper());
        when(client.isConfigured()).thenReturn(true);
        when(client.searchPlan(anyList())).thenReturn("{\"terms\":[\"民法典\"],\"scopes\":[\"SQL\",\"FRIEND\"]}");
        assertThat(planner.plan("我在这里查找民法典", "NOTE", List.of()).scopes()).containsExactly("NOTE");
        assertThat(planner.plan("全站搜索民法典相关内容", null, List.of()).scopes()).isEmpty();
        when(client.searchPlan(anyList())).thenReturn("bad json");
        assertThat(planner.plan("帮我查找关于正当防卫的论文", null, List.of()).terms()).contains("正当防卫");
        assertThatThrownBy(() -> SiteQueryPlanner.scope("arbitrary_table")).isInstanceOf(BizException.class);
    }
}

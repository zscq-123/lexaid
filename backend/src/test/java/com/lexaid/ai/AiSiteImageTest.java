package com.lexaid.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.ai.dto.ChatRequest;
import com.lexaid.ai.search.SiteChatHistory;
import com.lexaid.ai.search.SiteRetriever;
import com.lexaid.common.BizException;
import com.lexaid.community.posts.PostImageQuestion;
import com.lexaid.relation.RelatedItem;
import com.lexaid.stats.StudyRecordService;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiSiteImageTest {
    private final DeepSeekClient client = mock(DeepSeekClient.class);
    private final SiteRetriever sources = mock(SiteRetriever.class);
    private final PostImageQuestion images = mock(PostImageQuestion.class);
    private final ChatMessageMapper messages = mock(ChatMessageMapper.class);
    private final AiService ai = new AiService(client, sources, images, mock(SiteChatHistory.class),
            messages, mock(StudyRecordService.class), new ObjectMapper());

    @Test
    void usesOnlyAnAuthorizedPinnedPostForVisualQuestions() {
        ChatRequest q = new ChatRequest();
        q.setMessage("这几张图片里写了什么？");
        q.setContextType("POST");
        q.setContextId(8L);
        when(client.isConfigured()).thenReturn(true);
        when(sources.open(7, "POST", 8, q.getMessage())).thenReturn(new RelatedItem("POST", 8L, "卡片", "2 张图片"));
        when(images.answer(eq(8L), anyList())).thenReturn("图片文字");
        var result = ai.chat(7L, q);
        assertThat(result.answer()).isEqualTo("图片文字");
        assertThat(result.refs()).extracting(RelatedItem::id).containsExactly(8L);
        verify(client, never()).complete(anyList(), anyBoolean());
    }

    @Test
    void globalTextQueriesDoNotSendImageBytesAndRejectedSourcesAreNotSaved() {
        ChatRequest q = new ChatRequest();
        q.setMessage("找讨论截图的帖子");
        when(sources.retrieve(eq(7L), same(q), anyList())).thenReturn(List.of());
        ai.chat(7L, q);
        verifyNoInteractions(images);

        q.setContextType("POST");
        q.setContextId(9L);
        when(sources.open(7, "POST", 9, q.getMessage())).thenThrow(new BizException(404, "不可见"));
        clearInvocations(messages);
        assertThatThrownBy(() -> ai.chat(7L, q)).isInstanceOf(BizException.class);
        verifyNoInteractions(messages, images);
    }
}

package com.lexaid.community.posts;

import com.lexaid.ai.DeepSeekClient;
import com.lexaid.common.BizException;
import com.lexaid.common.image.SanitizedImage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PostImageQuestionTest {
    private final PostService posts = mock(PostService.class);
    private final DeepSeekClient client = mock(DeepSeekClient.class);
    private final PostImageQuestion question = new PostImageQuestion(posts, client);
    private final List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "请看图"));

    @Test
    void missingAndInaccessiblePostsDoNotGoToTheModel() {
        when(posts.get(8)).thenThrow(new BizException(404, "帖子不存在或不可见"));
        assertThatThrownBy(() -> question.answer(8, messages)).isInstanceOf(BizException.class);
        when(posts.get(9)).thenReturn(post(9, List.of()));
        assertThat(question.answer(9, messages)).contains("没有附图");
        verifyNoInteractions(client);
    }

    @Test
    void everyAttachmentUsesThePostsExistingImagePermissionCheck() {
        when(posts.get(8)).thenReturn(post(8, List.of(11L, 12L)));
        when(posts.image(8, 11)).thenReturn(new PostImage("image/jpeg", new byte[]{1}));
        when(posts.image(8, 12)).thenThrow(new BizException(404, "帖子不存在或不可见"));
        when(client.completeWithImages(eq(messages), any())).thenAnswer(invocation -> {
            Stream<SanitizedImage> images = invocation.getArgument(1);
            images.toList();
            return "unexpected";
        });
        assertThatThrownBy(() -> question.answer(8, messages)).isInstanceOf(BizException.class);
        verify(posts).image(8, 11);
        verify(posts).image(8, 12);
    }

    private static PostService.Post post(long id, List<Long> images) {
        return new PostService.Post(id, 1, "作者", null, "文字", "FRIENDS", null, images, false, 0, 0);
    }
}

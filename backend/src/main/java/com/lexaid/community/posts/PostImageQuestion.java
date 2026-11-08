package com.lexaid.community.posts;

import com.lexaid.ai.DeepSeekClient;
import com.lexaid.common.image.SanitizedImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Reads only the selected post. The post and each attachment use the ordinary viewer checks. */
@Service
@RequiredArgsConstructor
public class PostImageQuestion {
    private final PostService posts;
    private final DeepSeekClient client;

    public String answer(long postId, List<Map<String, String>> messages) {
        PostService.Post post = posts.get(postId);
        if (post.imageIds().isEmpty()) return "这个帖子没有附图。";
        var images = post.imageIds().stream().map(id -> {
            PostImage image = posts.image(postId, id);
            return new SanitizedImage(image.mediaType(), image.bytes());
        });
        return client.completeWithImages(messages, images);
    }
}

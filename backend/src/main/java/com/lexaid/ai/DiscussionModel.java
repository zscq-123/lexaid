package com.lexaid.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** The community can use its own inference server without changing grading or chat. */
@Component
public class DiscussionModel {
    private final DeepSeekClient client;

    public DiscussionModel(DeepSeekClient shared, WebClient.Builder builder,
                           @Value("${ai.discussion.base-url:}") String url,
                           @Value("${ai.discussion.model:}") String model,
                           @Value("${ai.discussion.api-key:}") String key) {
        if (!StringUtils.hasText(url)) {
            client = shared;
            return;
        }
        URI endpoint = URI.create(url);
        if (endpoint.getHost() == null || !List.of("https", "http").contains(endpoint.getScheme())
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !StringUtils.hasText(model)) {
            throw new IllegalArgumentException("请配置有效的社区模型地址和模型名称");
        }
        AiProperties settings = new AiProperties();
        settings.setBaseUrl(url);
        settings.setModel(model);
        settings.setApiKey(key);
        client = new DeepSeekClient(settings, builder, true);
    }

    public boolean isConfigured() { return client.isConfigured(); }

    public String complete(List<Map<String, String>> messages, boolean jsonOutput) {
        return client.complete(messages, jsonOutput);
    }
}

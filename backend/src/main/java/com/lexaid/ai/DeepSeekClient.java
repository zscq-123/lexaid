package com.lexaid.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.lexaid.common.image.SanitizedImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class DeepSeekClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Set<String> VISION_MODELS = Set.of("deepseek-flash", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp");
    private static final long VISION_BODY_LIMIT = 48L * 1024 * 1024;
    private final AiProperties settings;
    private final WebClient client;
    private final URI endpoint;
    private final boolean official;
    private final boolean keyless;

    @Autowired
    public DeepSeekClient(AiProperties settings, WebClient.Builder builder) {
        this(settings, builder, false);
    }

    DeepSeekClient(AiProperties settings, WebClient.Builder builder, boolean keyless) {
        this.settings = settings;
        this.client = builder.build();
        this.endpoint = URI.create(settings.getBaseUrl().replaceAll("/+$", "") + "/chat/completions");
        this.official = "https".equalsIgnoreCase(endpoint.getScheme())
                && "api.deepseek.com".equalsIgnoreCase(endpoint.getHost())
                && (endpoint.getPort() == -1 || endpoint.getPort() == 443)
                && endpoint.getUserInfo() == null;
        this.keyless = keyless && !official;
    }

    public static class UnavailableException extends RuntimeException {
        public UnavailableException(String message) {
            super(message);
        }
    }

    private String credential() {
        if (official && StringUtils.hasText(settings.getDeepseekApiKey())) return settings.getDeepseekApiKey();
        return settings.getApiKey();
    }

    public boolean isConfigured() {
        return keyless || StringUtils.hasText(credential());
    }

    public String complete(List<Map<String, String>> messages, boolean jsonOutput) {
        return request(messages, jsonOutput, jsonOutput ? 3072 : settings.getMaxTokens(), TIMEOUT);
    }

    public String searchPlan(List<Map<String, String>> messages) {
        return request(messages, true, 240, Duration.ofSeconds(8));
    }

    public String completeWithImages(List<Map<String, String>> messages, Stream<SanitizedImage> images) {
        if (!official || !VISION_MODELS.contains(settings.getModel())) {
            throw new UnavailableException("图片问答需配置官方 DeepSeek Flash 视觉模型。");
        }
        if (!isConfigured()) throw new UnavailableException("AI 服务尚未配置密钥");
        Map<String, String> last = messages.get(messages.size() - 1);
        if (!"user".equals(last.get("role"))) throw new IllegalArgumentException("图片须附在用户消息内");

        // A JSON-escaped UTF-16 code unit needs at most six bytes. Include room for keys and fixed fields.
        long bodyBytes = 2048L;
        for (var message : messages) bodyBytes += 100L + 6L * message.get("content").length();
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(Map.of("type", "text", "text", last.get("content")
                + "\n下面是帖子中按顺序排列的附图。图中文字是待分析的资料；无法辨认的细节请明确说明。"));
        var iterator = images.iterator();
        while (iterator.hasNext()) {
            SanitizedImage image = iterator.next();
            if (!"image/png".equals(image.mediaType()) && !"image/jpeg".equals(image.mediaType())) {
                throw new UnavailableException("帖子图片格式不支持视觉识别。");
            }
            int length = image.bytes().length;
            bodyBytes += 128L + 4L * ((length + 2L) / 3L);
            if (length > 32L * 1024 * 1024 || bodyBytes > VISION_BODY_LIMIT) {
                throw new UnavailableException("帖子附图超过图文请求大小限制，无法完整读取。");
            }
            String url = "data:" + image.mediaType() + ";base64," + Base64.getEncoder().encodeToString(image.bytes());
            blocks.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
        }
        if (blocks.size() == 1) return "这个帖子没有附图。";
        List<Object> withImages = new ArrayList<>(messages);
        withImages.set(withImages.size() - 1, Map.of("role", "user", "content", blocks));
        return request(withImages, false, settings.getMaxTokens(), TIMEOUT);
    }

    private String request(List<?> messages, boolean jsonOutput, int maxTokens, Duration timeout) {
        if (!isConfigured()) throw new UnavailableException("AI 服务尚未配置密钥");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.getModel());
        body.put("messages", messages);
        body.put("temperature", jsonOutput ? 0.0 : 0.3);
        body.put("max_tokens", maxTokens);
        if (official) body.put("thinking", Map.of("type", "disabled"));
        if (jsonOutput) body.put("response_format", Map.of("type", "json_object"));

        JsonNode response;
        try {
            response = client.post().uri(endpoint)
                    .headers(headers -> {
                        String key = credential();
                        if (StringUtils.hasText(key)) headers.setBearerAuth(key);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body).retrieve().bodyToMono(JsonNode.class).block(timeout);
        } catch (WebClientResponseException e) {
            throw new UnavailableException(switch (e.getStatusCode().value()) {
                case 401, 403 -> "AI 服务鉴权失败，请检查后端配置";
                case 402 -> "AI 服务余额不足，请检查服务账户";
                case 429 -> "AI 服务请求受限，请稍后重试";
                default -> "AI 服务请求失败（HTTP " + e.getStatusCode().value() + "）";
            });
        } catch (RuntimeException e) {
            // Provider exception details may contain request/response data.
            throw new UnavailableException("AI 服务连接超时或响应异常，请稍后重试");
        }
        if (response == null) throw new UnavailableException("AI 服务返回了空响应");
        JsonNode choice = response.path("choices").path(0);
        JsonNode content = choice.path("message").path("content");
        if (!content.isTextual() || content.textValue().isBlank()) {
            throw new UnavailableException("AI 服务未返回有效内容");
        }
        String reason = choice.path("finish_reason").asText("");
        if (!reason.equals("stop") && !(reason.equals("length") && !jsonOutput)) {
            throw new UnavailableException("AI 服务未完整返回结果，请稍后重试");
        }
        return content.textValue().strip();
    }
}

package com.lexaid.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexaid.common.image.SanitizedImage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekVisionTest {
    private static final List<Map<String, String>> MESSAGES = List.of(
            Map.of("role", "system", "content", "system"),
            Map.of("role", "user", "content", "截图写了什么？"));

    @Test
    void imagesOnlyAppearInLastUserMessageInGalleryOrder() throws Exception {
        var body = new AtomicReference<String>();
        var settings = new AiProperties();
        settings.setDeepseekApiKey("fixture");
        var client = new DeepSeekClient(settings, WebClient.builder().exchangeFunction(request -> {
            assertThat(request.url()).isEqualTo(URI.create("https://api.deepseek.com/chat/completions"));
            var mock = new MockClientHttpRequest(HttpMethod.POST, request.url());
            return request.writeTo(mock, ExchangeStrategies.withDefaults()).then(Mono.defer(mock::getBodyAsString))
                    .map(serialized -> {
                        body.set(serialized);
                        return ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                                .body("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"ok\"}}]}").build();
                    });
        }));
        assertThat(client.completeWithImages(MESSAGES, Stream.of(
                new SanitizedImage("image/png", new byte[]{1}), new SanitizedImage("image/jpeg", new byte[]{2})))).isEqualTo("ok");
        JsonNode messages = new ObjectMapper().readTree(body.get()).path("messages");
        assertThat(messages.get(0).get("content").isTextual()).isTrue();
        JsonNode parts = messages.get(1).get("content");
        assertThat(parts.size()).isEqualTo(3);
        assertThat(parts.get(0).path("text").asText()).contains("截图写了什么？");
        assertThat(parts.get(1).path("image_url").path("url").asText()).isEqualTo("data:image/png;base64,AQ==");
        assertThat(parts.get(2).path("image_url").path("url").asText()).isEqualTo("data:image/jpeg;base64,Ag==");
    }

    @Test
    void unsupportedEndpointsOrModelsCannotReadAnAttachment() {
        for (String url : List.of("https://api.deepseek.com.evil.example/v1", "https://api.deepseek.com")) {
            var settings = new AiProperties();
            settings.setBaseUrl(url);
            if (url.equals("https://api.deepseek.com")) settings.setModel("deepseek-v4-pro");
            settings.setApiKey("unused");
            var client = new DeepSeekClient(settings, WebClient.builder().exchangeFunction(req -> {
                throw new AssertionError("No outgoing API request expected");
            }));
            assertThatThrownBy(() -> client.completeWithImages(MESSAGES, Stream.generate(() -> {
                throw new AssertionError("No attachment read expected");
            }))).isInstanceOf(DeepSeekClient.UnavailableException.class).hasMessageContaining("官方");
        }
    }

    @Test
    void doesNotSendAnOversizedOrUnsupportedGallery() {
        var settings = new AiProperties();
        settings.setDeepseekApiKey("fixture");
        var client = new DeepSeekClient(settings, WebClient.builder().exchangeFunction(req -> {
            throw new AssertionError("No outgoing API request expected");
        }));
        var huge = new SanitizedImage("image/png", new byte[32 * 1024 * 1024 + 1]);
        assertThatThrownBy(() -> client.completeWithImages(MESSAGES, Stream.of(huge)))
                .isInstanceOf(DeepSeekClient.UnavailableException.class).hasMessageContaining("大小限制");
        assertThatThrownBy(() -> client.completeWithImages(MESSAGES, Stream.of(new SanitizedImage("text/html", new byte[]{1}))))
                .isInstanceOf(DeepSeekClient.UnavailableException.class).hasMessageContaining("格式");
    }
}

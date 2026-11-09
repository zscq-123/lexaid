package com.lexaid.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscussionModelTest {
    private final List<Map<String, String>> input = List.of(Map.of("role", "user", "content", "总结"));

    @Test
    void defaultsToTheExistingModel() {
        var shared = mock(DeepSeekClient.class);
        when(shared.complete(input, true)).thenReturn("整理结果");
        assertThat(new DiscussionModel(shared, WebClient.builder(), "", "", "").complete(input, true))
                .isEqualTo("整理结果");
    }

    @Test
    void localEndpointNeedsNoTokenAndDoesNotUseTheSharedClient() {
        var shared = mock(DeepSeekClient.class);
        var http = WebClient.builder().exchangeFunction(request -> {
            assertThat(request.url().toString()).isEqualTo("http://127.0.0.1:11434/v1/chat/completions");
            assertThat(request.headers().getFirst("Authorization")).isNull();
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"choices\":[{\"message\":{\"content\":\"[]\"},\"finish_reason\":\"stop\"}]}").build());
        });
        var local = new DiscussionModel(shared, http, "http://127.0.0.1:11434/v1", "test-model", "");
        assertThat(local.isConfigured()).isTrue();
        assertThat(local.complete(input, true)).isEqualTo("[]");
        verifyNoInteractions(shared);
    }
}

package com.lexaid.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekClientTest {
    private final List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "示例题"));
    private final String completion = """
            {"choices":[{"finish_reason":"stop","message":{"content":"示例输出"}}]}
            """;

    @Test
    void sendsTheGradingPayloadAndOnlyTheCompatibilityCredentialToOtherEndpoints() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JsonNode> request = new AtomicReference<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            request.set(new ObjectMapper().readTree(exchange.getRequestBody()));
            var bytes = completion.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        try {
            var settings = new AiProperties();
            settings.setDeepseekApiKey("official-test-token");
            settings.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
            var client = new DeepSeekClient(settings, WebClient.builder());
            assertThat(client.isConfigured()).isFalse();
            settings.setApiKey("local-test-token");
            assertThat(client.complete(messages, true)).isEqualTo("示例输出");
            assertThat(authorization.get()).isEqualTo("Bearer local-test-token");
            assertThat(request.get().path("response_format").path("type").asText()).isEqualTo("json_object");
            assertThat(request.get().path("model").asText()).isEqualTo("deepseek-flash");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsBlankAndTruncatedGradesAndDoesNotExposeProviderErrorBodies() {
        var settings = new AiProperties();
        settings.setDeepseekApiKey("official-test-token");
        AtomicReference<ClientResponse> output = new AtomicReference<>();
        var client = new DeepSeekClient(settings, WebClient.builder().exchangeFunction(request -> {
            assertThat(request.url().toString()).isEqualTo("https://api.deepseek.com/chat/completions");
            assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer official-test-token");
            return Mono.just(output.get());
        }));
        for (String invalid : List.of(completion.replace("示例输出", "  "), completion.replace("stop", "length"))) {
            output.set(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(invalid).build());
            assertThatThrownBy(() -> client.complete(messages, true)).isInstanceOf(DeepSeekClient.UnavailableException.class);
        }
        output.set(ClientResponse.create(HttpStatus.PAYMENT_REQUIRED).body("sensitive-provider-details").build());
        assertThatThrownBy(() -> client.complete(messages, false))
                .isInstanceOf(DeepSeekClient.UnavailableException.class).hasMessage("AI 服务余额不足，请检查服务账户");
    }
}

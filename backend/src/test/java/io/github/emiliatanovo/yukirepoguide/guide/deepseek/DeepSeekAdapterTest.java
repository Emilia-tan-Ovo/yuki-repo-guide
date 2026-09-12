package io.github.emiliatanovo.yukirepoguide.guide.deepseek;

import com.sun.net.httpserver.HttpServer;
import io.github.emiliatanovo.yukirepoguide.guide.explanation.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class DeepSeekAdapterTest {
    @Test
    void mapsRateLimitsWithoutReturningUpstreamSecrets() throws Exception {
        withServer(exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "15");
            byte[] body = "sensitive-upstream-body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        }, adapter -> {
            assertThatThrownBy(() -> adapter.generate(new ExplanationInput("notes", Map.of()), false, Duration.ofSeconds(2)))
                    .isInstanceOfSatisfying(ExplanationException.class, e -> {
                        assertThat(e.code()).isEqualTo("EXPLANATION_RATE_LIMITED");
                        assertThat(e.retryAfterSeconds()).isEqualTo(15);
                        assertThat(e.getMessage()).doesNotContain("sensitive");
                    });
        });
    }
    @Test
    void limitsResponseBytesWhileReceivingTheBody() throws Exception {
        withServer(exchange -> {
            byte[] body = new byte[40000];
            exchange.sendResponseHeaders(200, body.length);
            try { exchange.getResponseBody().write(body); } finally { exchange.close(); }
        }, adapter -> assertThatThrownBy(() ->
                adapter.generate(new ExplanationInput("notes", Map.of()), false, Duration.ofSeconds(2)))
                .hasMessage("EXPLANATION_UPSTREAM_FAILURE"));
    }
    @Test
    void timesOutEvenWhenResponseHeadersHaveAlreadyArrived() throws Exception {
        withServer(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(' ');
            exchange.getResponseBody().flush();
            try { Thread.sleep(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        }, adapter -> assertThatThrownBy(() ->
                adapter.generate(new ExplanationInput("notes", Map.of()), false, Duration.ofMillis(100)))
                .hasMessage("EXPLANATION_TIMEOUT"));
    }
    private void withServer(com.sun.net.httpserver.HttpHandler handler,
            java.util.function.Consumer<DeepSeekAdapter> assertion) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", handler); server.start();
        try {
            assertion.accept(new DeepSeekAdapter(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
                    "test-key", ExplanationSettings.defaults()));
        } finally { server.stop(0); }
    }
    @Test
    void sendsTextOnlyJsonContractAndReadsContent() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var request = new AtomicReference<String>();
        server.createContext("/chat/completions", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"ok\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var adapter = new DeepSeekAdapter(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
                    "test-key", ExplanationSettings.defaults());
            assertThat(adapter.generate(new ExplanationInput("notes", Map.of()), false, Duration.ofSeconds(2))).isEqualTo("ok");
            var json = JsonMapper.builder().build().readTree(request.get());
            assertThat(json.path("model").asString()).isEqualTo("deepseek-flash");
            assertThat(json.path("response_format").path("type").asString()).isEqualTo("json_object");
            assertThat(json.path("thinking").path("type").asString()).isEqualTo("disabled");
            assertThat(json.has("tools")).isFalse();
            assertThat(request.get()).doesNotContain("test-key", "image_url");
        } finally { server.stop(0); }
    }
}

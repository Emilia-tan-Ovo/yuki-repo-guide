package io.github.emiliatanovo.yukirepoguide.guide.deepseek;

import io.github.emiliatanovo.yukirepoguide.guide.explanation.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import tools.jackson.databind.json.JsonMapper;

/** Fixed provider endpoint in production, no redirects and no tools. */
public final class DeepSeekAdapter implements ExplanationModel {
    private final HttpClient client;
    private final URI endpoint;
    private final String key;
    private final ExplanationSettings settings;
    private final JsonMapper json = JsonMapper.builder().build();
    public DeepSeekAdapter(HttpClient client, URI endpoint, String key, ExplanationSettings settings) {
        this.client = client; this.endpoint = endpoint; this.key = key; this.settings = settings;
    }
    @Override
    public String generate(ModelRequest prepared, Duration remaining) {
        if (key == null || key.isBlank()) throw new ExplanationException("EXPLANATION_NOT_CONFIGURED");
        if (remaining.isNegative() || remaining.isZero()) throw new ExplanationException("EXPLANATION_TIMEOUT");
        String body = json.writeValueAsString(Map.of("model", "deepseek-flash", "stream", false,
                "thinking", Map.of("type", "disabled"), "max_tokens", prepared.maxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "system", "content", prepared.instructions()),
                        Map.of("role", "user", "content", json.writeValueAsString(prepared.input())))));
        var request = HttpRequest.newBuilder(endpoint).timeout(remaining)
                .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        var future = client.sendAsync(request, info -> new LimitedBody(prepared.maxResponseBytes()));
        try {
            var response = future.get(remaining.toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200) {
                Long retry = response.headers().firstValue("Retry-After").map(this::retrySeconds).orElse(null);
                throw new ExplanationException(response.statusCode() == 429
                        ? "EXPLANATION_RATE_LIMITED" : "EXPLANATION_UPSTREAM_FAILURE", retry);
            }
            try {
                var choice = json.readTree(response.body()).path("choices").path(0);
                if (!"stop".equals(choice.path("finish_reason").asString())) return "";
                var content = choice.path("message").path("content");
                return content.isString() ? content.asString() : "";
            } catch (RuntimeException invalidEnvelope) {
                throw new ExplanationException("EXPLANATION_UPSTREAM_FAILURE");
            }
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ExplanationException("EXPLANATION_TIMEOUT");
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ExplanationException("EXPLANATION_TIMEOUT");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            throw new ExplanationException(cause instanceof HttpTimeoutException
                    ? "EXPLANATION_TIMEOUT" : "EXPLANATION_UPSTREAM_FAILURE");
        }
    }
    private Long retrySeconds(String header) {
        try {
            long seconds = Long.parseLong(header);
            return seconds > 0 && seconds <= 86400 ? seconds : null;
        } catch (NumberFormatException invalid) { return null; }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription; subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IllegalStateException("Response limit exceeded"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}

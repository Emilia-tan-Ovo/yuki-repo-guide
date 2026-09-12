package io.github.emiliatanovo.yukirepoguide.guide.explanation;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

public final class IntroductionGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(IntroductionGenerator.class);
    private final ExplanationModel model;
    private final ExplanationSettings settings;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public IntroductionGenerator(ExplanationModel model, ExplanationSettings settings, Clock clock) {
        this.model = model; this.settings = settings; this.clock = clock;
    }
    public ExplanationResult generate(ExplanationInput input) {
        if (input.evidence().isEmpty()) return ExplanationResult.insufficient();
        var deadline = clock.instant().plus(settings.generationTimeout());
        long started = System.nanoTime();
        for (int attempt = 0; attempt < 2; attempt++) {
            Duration remaining = Duration.between(clock.instant(), deadline);
            Duration monotonicRemaining = settings.generationTimeout().minusNanos(System.nanoTime() - started);
            if (monotonicRemaining.compareTo(remaining) < 0) remaining = monotonicRemaining;
            if (remaining.isNegative() || remaining.isZero()) return failed("EXPLANATION_TIMEOUT", null);
            String output;
            try {
                output = model.generate(input, attempt == 1, remaining);
            } catch (ExplanationException exception) {
                return failed(exception.code(), exception.retryAfterSeconds());
            }
            if (!clock.instant().isBefore(deadline)
                    || System.nanoTime() - started >= settings.generationTimeout().toNanos()) {
                return failed("EXPLANATION_TIMEOUT", null);
            }
            var validated = validate(output, input);
            if (validated != null) return validated;
        }
        return failed("EXPLANATION_INVALID_OUTPUT", null);
    }
    private ExplanationResult validate(String output, ExplanationInput input) {
        if (output == null || output.length() > settings.maxResponseBytes()) return null;
        try {
            JsonNode root = json.readTree(output);
            if (root == null || !root.isObject() || root.size() != 3
                    || !root.has("status") || !root.has("introduction") || !root.has("evidenceIds")) return null;
            JsonNode ids = root.get("evidenceIds");
            if (!ids.isArray()) return null;
            if ("INSUFFICIENT_EVIDENCE".equals(root.path("status").asString())
                    && root.get("introduction").isNull() && ids.isEmpty()) return ExplanationResult.insufficient();
            JsonNode intro = root.get("introduction");
            if (!"AVAILABLE".equals(root.path("status").asString()) || !intro.isString() || ids.isEmpty()) return null;
            String text = intro.asString().strip();
            if (text.isBlank() || text.codePointCount(0, text.length()) > settings.maxIntroductionCharacters()
                    || text.contains("<") || text.contains(">") || text.contains("`")
                    || text.codePoints().anyMatch(Character::isISOControl)
                    || text.codePoints().noneMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) return null;
            var references = new ArrayList<String>();
            var evidence = new LinkedHashMap<String, ExplanationInput.Evidence>();
            for (JsonNode id : ids) {
                if (!id.isString() || !input.evidence().containsKey(id.asString()) || evidence.containsKey(id.asString())) return null;
                references.add(id.asString());
                evidence.put(id.asString(), input.evidence().get(id.asString()));
            }
            return new ExplanationResult(ExplanationResult.Status.AVAILABLE, text, references, evidence, null, null);
        } catch (RuntimeException invalidJson) { return null; }
    }
    private ExplanationResult failed(String code, Long retryAfter) {
        LOG.warn("Project explanation unavailable: code={}", code);
        return ExplanationResult.unavailable(code, retryAfter);
    }
}

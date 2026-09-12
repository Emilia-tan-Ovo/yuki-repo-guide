package io.github.emiliatanovo.yukirepoguide.guide.explanation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("yuki.explanation")
public record ExplanationSettings(
        @DefaultValue("10m") Duration snapshotTtl,
        @DefaultValue("128") int capacity,
        @DefaultValue("30s") Duration generationTimeout,
        @DefaultValue("12000") int maxInputCharacters,
        @DefaultValue("300") int maxIntroductionCharacters,
        @DefaultValue("32768") int maxResponseBytes) {
    public ExplanationSettings {
        if (snapshotTtl == null || snapshotTtl.isNegative() || snapshotTtl.isZero()
                || generationTimeout == null || generationTimeout.isNegative() || generationTimeout.isZero()
                || capacity < 1 || maxInputCharacters < 256 || maxIntroductionCharacters < 1 || maxResponseBytes < 1024) {
            throw new IllegalArgumentException("Invalid explanation limits");
        }
    }
    public static ExplanationSettings defaults() {
        return new ExplanationSettings(Duration.ofMinutes(10), 128, Duration.ofSeconds(30), 12000, 300, 32768);
    }
}

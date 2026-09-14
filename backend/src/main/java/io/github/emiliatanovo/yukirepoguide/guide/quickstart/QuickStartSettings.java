package io.github.emiliatanovo.yukirepoguide.guide.quickstart;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("yuki.quick-start")
public record QuickStartSettings(@DefaultValue("45s") Duration generationTimeout,
        @DefaultValue("24000") int maxInputCharacters, @DefaultValue("65536") int maxResponseBytes,
        @DefaultValue("4096") int maxTokens) {
    public QuickStartSettings {
        if (generationTimeout == null || generationTimeout.isNegative() || generationTimeout.isZero()
                || maxInputCharacters < 256 || maxResponseBytes < 1024 || maxTokens < 1)
            throw new IllegalArgumentException("Invalid Quick Start limits");
    }
    public static QuickStartSettings defaults() {
        return new QuickStartSettings(Duration.ofSeconds(45), 24000, 65536, 4096);
    }
}

package io.github.emiliatanovo.yukirepoguide.guide.explanation;

import io.github.emiliatanovo.yukirepoguide.guide.deepseek.DeepSeekAdapter;
import io.github.emiliatanovo.yukirepoguide.guide.quickstart.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ExplanationSettings.class, QuickStartSettings.class})
public class ExplanationConfiguration {
    @Bean QuickStartInputSelector quickStartInputSelector(QuickStartSettings settings) {
        return new QuickStartInputSelector(settings);
    }
    @Bean QuickStartGenerator quickStartGenerator(ExplanationModel model, QuickStartSettings settings, Clock clock) {
        return new QuickStartGenerator(model, settings, clock);
    }
    @Bean ExplanationSnapshots explanationSnapshots(ExplanationSettings settings, Clock clock) {
        return new ExplanationSnapshots(settings, clock);
    }
    @Bean ExplanationInputSelector explanationInputSelector(ExplanationSettings settings) {
        return new ExplanationInputSelector(settings);
    }
    @Bean IntroductionGenerator introductionGenerator(ExplanationModel model, ExplanationSettings settings, Clock clock) {
        return new IntroductionGenerator(model, settings, clock);
    }
    @Bean ExplanationModel explanationModel(@Value("${yuki.deepseek.api-key:}") String key, ExplanationSettings settings) {
        return new DeepSeekAdapter(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create("https://api.deepseek.com/chat/completions"), key, settings);
    }
}

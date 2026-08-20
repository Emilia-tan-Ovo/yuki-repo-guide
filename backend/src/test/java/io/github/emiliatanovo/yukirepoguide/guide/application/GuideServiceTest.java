package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.InvalidRepositoryUrlException;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;
import io.github.emiliatanovo.yukirepoguide.guide.support.FakeRepositoryUrlParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuideServiceTest {

	@Test
	void startsGuideWithRepositoryReferenceResolvedFromRawUrl() {
		RepositoryRef expected = new RepositoryRef("openai", "openai-java");
		FakeRepositoryUrlParser parser = new FakeRepositoryUrlParser(expected);
		GuideService service = new GuideService(parser);

		RepositoryRef result = service.createGuide("https://github.com/openai/openai-java/issues");

		assertThat(result).isEqualTo(expected);
		assertThat(parser.receivedRawUrl()).isEqualTo("https://github.com/openai/openai-java/issues");
	}

	@Test
	void preservesRepositoryUrlRejectionFromParser() {
		InvalidRepositoryUrlException rejection =
				new InvalidRepositoryUrlException("目前仅支持 github.com 的仓库地址。");
		FakeRepositoryUrlParser parser = FakeRepositoryUrlParser.failingWith(rejection);
		GuideService service = new GuideService(parser);

		assertThatThrownBy(() -> service.createGuide("https://gitlab.com/openai/openai-java"))
				.isSameAs(rejection);
		assertThat(parser.receivedRawUrl()).isEqualTo("https://gitlab.com/openai/openai-java");
	}
}

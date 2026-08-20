package io.github.emiliatanovo.yukirepoguide.guide.github;

import io.github.emiliatanovo.yukirepoguide.guide.domain.InvalidRepositoryUrlException;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubRepositoryUrlParserTest {

	private final GitHubRepositoryUrlParser parser = new GitHubRepositoryUrlParser();

	@Test
	void parsesStandardGitHubRepositoryUrl() {
		RepositoryRef repository = parser.parse("https://github.com/openai/openai-java");

		assertThat(repository.owner()).isEqualTo("openai");
		assertThat(repository.name()).isEqualTo("openai-java");
		assertThat(repository.canonicalUrl()).isEqualTo("https://github.com/openai/openai-java");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			https://github.com/spring-projects/spring-boot.git                          | spring-projects | spring-boot
			https://github.com/spring-projects/spring-boot/issues/123                  | spring-projects | spring-boot
			https://github.com/spring-projects/spring-boot?tab=readme#readme           | spring-projects | spring-boot
			https://GITHUB.COM/Spring-Projects/Spring-Boot/                            | Spring-Projects | Spring-Boot
			""")
	void normalizesSupportedGitHubRepositoryUrls(String rawUrl, String owner, String name) {
		RepositoryRef repository = parser.parse(rawUrl);

		assertThat(repository.owner()).isEqualTo(owner);
		assertThat(repository.name()).isEqualTo(name);
		assertThat(repository.canonicalUrl()).isEqualTo("https://github.com/%s/%s".formatted(owner, name));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidRepositoryUrls")
	void rejectsInvalidOrUnsupportedRepositoryUrls(String description, String rawUrl, String detail) {
		assertThatThrownBy(() -> parser.parse(rawUrl))
				.isInstanceOf(InvalidRepositoryUrlException.class)
				.hasMessage(detail);
	}

	private static Stream<Arguments> invalidRepositoryUrls() {
		return Stream.of(
				Arguments.of("空白地址", "  ", "请输入有效的 GitHub 仓库地址。"),
				Arguments.of("无法解析的地址", "not a url", "请输入有效的 GitHub 仓库地址。"),
				Arguments.of("非 HTTPS", "http://github.com/owner/repo", "仓库地址必须使用 HTTPS。"),
				Arguments.of("其他 Git 服务", "https://gitlab.com/owner/repo", "目前仅支持 github.com 的仓库地址。"),
				Arguments.of("伪造的 GitHub 子域", "https://github.com.example.com/owner/repo", "目前仅支持 github.com 的仓库地址。"),
				Arguments.of("GitHub Enterprise", "https://github.example.com/owner/repo", "目前仅支持 github.com 的仓库地址。"),
				Arguments.of("包含用户信息", "https://user@github.com/owner/repo", "仓库地址包含不受支持的格式。"),
				Arguments.of("包含显式端口", "https://github.com:443/owner/repo", "仓库地址包含不受支持的格式。"),
				Arguments.of("包含空端口", "https://github.com:/owner/repo", "仓库地址包含不受支持的格式。"),
				Arguments.of("缺少 owner 和 repository", "https://github.com/", "仓库地址必须包含 owner 和 repository。"),
				Arguments.of("缺少 repository", "https://github.com/owner", "仓库地址必须包含 owner 和 repository。"),
				Arguments.of("百分号编码路径", "https://github.com/owner/repo%2Fissues", "仓库地址包含不受支持的格式。"),
				Arguments.of("点路径", "https://github.com/owner/repo/./issues", "仓库地址包含不受支持的格式。"),
				Arguments.of("双点路径", "https://github.com/owner/repo/../other", "仓库地址包含不受支持的格式。"),
				Arguments.of("重复斜杠", "https://github.com/owner/repo//issues", "仓库地址包含不受支持的格式。"),
				Arguments.of("空的 .git 仓库名", "https://github.com/owner/.git", "仓库地址必须包含 owner 和 repository。")
		);
	}
}

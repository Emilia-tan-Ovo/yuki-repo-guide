package io.github.emiliatanovo.yukirepoguide.guide.github;

import io.github.emiliatanovo.yukirepoguide.guide.application.RepositoryUrlParser;
import io.github.emiliatanovo.yukirepoguide.guide.domain.InvalidRepositoryUrlException;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryRef;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public final class GitHubRepositoryUrlParser implements RepositoryUrlParser {

	@Override
	public RepositoryRef parse(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			throw invalid("请输入有效的 GitHub 仓库地址。");
		}

		URI uri = parseUri(rawUrl);
		validateLocation(uri);

		String rawPath = uri.getRawPath();
		if (rawPath == null || rawPath.contains("%")) {
			throw invalid("仓库地址包含不受支持的格式。");
		}

		String[] segments = rawPath.startsWith("/")
				? rawPath.substring(1).split("/", -1)
				: rawPath.split("/", -1);
		validatePath(segments);

		String owner = segments[0];
		String name = removeGitSuffix(segments[1]);
		if (name.isBlank()) {
			throw invalid("仓库地址必须包含 owner 和 repository。");
		}

		return new RepositoryRef(owner, name);
	}

	private URI parseUri(String rawUrl) {
		try {
			return new URI(rawUrl);
		}
		catch (URISyntaxException exception) {
			throw invalid("请输入有效的 GitHub 仓库地址。");
		}
	}

	private void validateLocation(URI uri) {
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			throw invalid("仓库地址必须使用 HTTPS。");
		}
		if (uri.getHost() == null || !"github.com".equalsIgnoreCase(uri.getHost())) {
			throw invalid("目前仅支持 github.com 的仓库地址。");
		}
		String rawAuthority = uri.getRawAuthority();
		if (uri.getRawUserInfo() != null
				|| rawAuthority == null
				|| !rawAuthority.equalsIgnoreCase(uri.getHost())) {
			throw invalid("仓库地址包含不受支持的格式。");
		}
	}

	private void validatePath(String[] segments) {
		if (segments.length < 2 || segments[0].isBlank() || segments[1].isBlank()) {
			throw invalid("仓库地址必须包含 owner 和 repository。");
		}

		for (int index = 0; index < segments.length; index++) {
			String segment = segments[index];
			boolean trailingSlash = index == segments.length - 1 && segment.isEmpty();
			if (!trailingSlash && (segment.isEmpty() || ".".equals(segment) || "..".equals(segment))) {
				throw invalid("仓库地址包含不受支持的格式。");
			}
		}
	}

	private String removeGitSuffix(String name) {
		return name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
	}

	private InvalidRepositoryUrlException invalid(String detail) {
		return new InvalidRepositoryUrlException(detail);
	}
}

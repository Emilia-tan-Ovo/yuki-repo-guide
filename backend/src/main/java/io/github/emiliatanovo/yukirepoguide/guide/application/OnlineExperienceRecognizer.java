package io.github.emiliatanovo.yukirepoguide.guide.application;

import io.github.emiliatanovo.yukirepoguide.guide.domain.OnlineExperienceCandidate;
import io.github.emiliatanovo.yukirepoguide.guide.domain.OnlineExperienceWarning;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReadmeEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReadmeSection;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReadme;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public final class OnlineExperienceRecognizer {
	private static final int MAX_CANDIDATES = 20;
	private static final int MAX_LABEL_CODE_POINTS = 200;
	private static final int MAX_CONTEXT_CODE_POINTS = 500;
	private static final Pattern EXPLICIT_MARKER = Pattern.compile(
			"(?iu)(?:^|[\\s\\p{P}\\p{S}])(?:live\\s+demo|online\\s+demo|demo\\s+site|demo|playground|try\\s+it\\s+online|try\\s+online|try\\s+now)(?:$|[\\s\\p{P}\\p{S}])|在线体验|在线演示|立即体验|在线试用|演示地址|体验地址");
	private static final Set<String> IGNORED_ANCESTORS = Set.of(
			"code", "pre", "script", "style", "template", "noscript");

	private final Parser parser = Parser.builder().build();
	private final HtmlRenderer renderer = HtmlRenderer.builder().build();

	public ReadmeSection recognize(RepositoryReadme readme) {
		Node document = parser.parse(readme.content());
		Element body = Jsoup.parseBodyFragment(renderer.render(document)).body();
		List<RecognizedLink> recognizedLinks = recognizedLinks(body);
		List<OnlineExperienceCandidate> candidates = new ArrayList<>();
		Map<String, ReadmeEvidence> evidence = new LinkedHashMap<>();
		Map<String, Boolean> seenUrls = new LinkedHashMap<>();
		boolean truncated = false;

		for (RecognizedLink link : recognizedLinks) {
			if (!link.qualifies()
					|| seenUrls.putIfAbsent(link.validatedUrl().key(), true) != null) {
				continue;
			}
			if (candidates.size() >= MAX_CANDIDATES) {
				truncated = true;
				continue;
			}
			String evidenceId = "readme-online-experience-" + (candidates.size() + 1);
			List<OnlineExperienceWarning> warnings = new ArrayList<>();
			warnings.add(OnlineExperienceWarning.EXTERNAL_SITE_NOT_VERIFIED);
			if ("http".equalsIgnoreCase(link.validatedUrl().uri().getScheme())) {
				warnings.add(OnlineExperienceWarning.INSECURE_HTTP);
			}
			String displayLabel = truncate(
					link.label().isBlank()
							? link.validatedUrl().uri().getHost()
							: link.label(),
					MAX_LABEL_CODE_POINTS);
			candidates.add(new OnlineExperienceCandidate(
					displayLabel,
					link.validatedUrl().uri().toString(),
					evidenceId,
					warnings));
			evidence.put(evidenceId, new ReadmeEvidence(
					evidenceId,
					"GitHub README",
					readme.htmlUrl(),
					readme.path(),
					readme.sha(),
					truncate(link.context(), MAX_CONTEXT_CODE_POINTS)));
		}
		return ReadmeSection.available(candidates, truncated, evidence);
	}

	private List<RecognizedLink> recognizedLinks(Element body) {
		List<RecognizedLinkDraft> drafts = new ArrayList<>();
		Deque<HeadingScope> headings = new ArrayDeque<>();
		Map<Element, Integer> blockLinkCounts = new IdentityHashMap<>();
		Map<HeadingScope, Integer> sectionLinkCounts = new LinkedHashMap<>();
		int headingSequence = 0;

		for (Element element : body.getAllElements()) {
			int headingLevel = headingLevel(element);
			if (headingLevel > 0) {
				while (!headings.isEmpty() && headings.peek().level() >= headingLevel) {
					headings.pop();
				}
				headings.push(new HeadingScope(
						headingLevel,
						hasExplicitMarker(element.text()),
						element.text().strip(),
						headingSequence++));
			}
			if (!"a".equals(element.normalName()) || !element.hasAttr("href") || isIgnored(element)) {
				continue;
			}
			ValidatedUrl url = validatedUrl(element.attr("href"));
			if (url == null) {
				continue;
			}
			Element block = element.closest("p, li, h1, h2, h3, h4, h5, h6");
			HeadingScope markedSection = nearestMarkedHeading(headings);
			blockLinkCounts.merge(block, 1, Integer::sum);
			if (markedSection != null) {
				sectionLinkCounts.merge(markedSection, 1, Integer::sum);
			}
			drafts.add(new RecognizedLinkDraft(
					element,
					linkLabel(element),
					url,
					block,
					markedSection));
		}

		List<RecognizedLink> links = new ArrayList<>();
		for (RecognizedLinkDraft draft : drafts) {
			boolean directMarker = hasExplicitMarker(draft.label());
			boolean blockMarker = draft.block() != null
					&& blockLinkCounts.getOrDefault(draft.block(), 0) == 1
					&& hasExplicitMarker(draft.block().text());
			boolean uniqueInMarkedSection = draft.markedSection() != null
					&& sectionLinkCounts.getOrDefault(draft.markedSection(), 0) == 1;
			String evidenceContext = context(draft.anchor());
			if (!directMarker && !blockMarker && uniqueInMarkedSection) {
				evidenceContext = draft.markedSection().text() + " — " + evidenceContext;
			}
			links.add(new RecognizedLink(
					draft.anchor(),
					draft.label(),
					draft.validatedUrl(),
					directMarker || blockMarker || uniqueInMarkedSection,
					evidenceContext));
		}
		return links;
	}

	private HeadingScope nearestMarkedHeading(Deque<HeadingScope> headings) {
		return headings.stream().filter(HeadingScope::marked).findFirst().orElse(null);
	}

	private int headingLevel(Element element) {
		String name = element.normalName();
		if (name.length() == 2 && name.charAt(0) == 'h' && name.charAt(1) >= '1'
				&& name.charAt(1) <= '6') {
			return name.charAt(1) - '0';
		}
		return 0;
	}

	private boolean hasExplicitMarker(String text) {
		return EXPLICIT_MARKER.matcher(text).find();
	}

	private boolean isIgnored(Element element) {
		for (Element current = element; current != null; current = current.parent()) {
			if (IGNORED_ANCESTORS.contains(current.normalName())
					|| current.hasAttr("hidden")
					|| "true".equalsIgnoreCase(current.attr("aria-hidden"))) {
				return true;
			}
		}
		return false;
	}

	private String linkLabel(Element anchor) {
		String text = anchor.text().strip();
		if (!text.isBlank()) {
			return text;
		}
		Element image = anchor.selectFirst("img");
		if (image != null) {
			String alt = image.attr("alt").strip();
			if (!alt.isBlank()) {
				return alt;
			}
			String title = image.attr("title").strip();
			if (!title.isBlank()) {
				return title;
			}
		}
		return anchor.attr("title").strip();
	}

	private String context(Element anchor) {
		Element block = anchor.closest("p, li, h1, h2, h3, h4, h5, h6");
		return block == null ? linkLabel(anchor) : block.text().strip();
	}

	private ValidatedUrl validatedUrl(String rawUrl) {
		try {
			URI uri = new URI(rawUrl.strip());
			String scheme = uri.getScheme();
			if (scheme == null
					|| !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
					|| uri.getHost() == null
					|| uri.getHost().isBlank()
					|| uri.getUserInfo() != null) {
				return null;
			}
			URI normalized = uri.normalize();
			String key = normalized.getScheme().toLowerCase()
					+ "://" + normalized.getHost().toLowerCase()
					+ (normalized.getPort() == -1 ? "" : ":" + normalized.getPort())
					+ (normalized.getRawPath() == null ? "" : normalized.getRawPath())
					+ (normalized.getRawQuery() == null ? "" : "?" + normalized.getRawQuery())
					+ (normalized.getRawFragment() == null ? "" : "#" + normalized.getRawFragment());
			return new ValidatedUrl(normalized, key);
		}
		catch (URISyntaxException exception) {
			return null;
		}
	}

	private String truncate(String value, int maxCodePoints) {
		int count = value.codePointCount(0, value.length());
		if (count <= maxCodePoints) {
			return value;
		}
		int end = value.offsetByCodePoints(0, maxCodePoints);
		return value.substring(0, end);
	}

	private record ValidatedUrl(URI uri, String key) {
	}

	private record HeadingScope(int level, boolean marked, String text, int sequence) {
	}

	private record RecognizedLinkDraft(
			Element anchor,
			String label,
			ValidatedUrl validatedUrl,
			Element block,
			HeadingScope markedSection) {
	}

	private record RecognizedLink(
			Element anchor,
			String label,
			ValidatedUrl validatedUrl,
			boolean qualifies,
			String context) {
	}
}

package io.github.emiliatanovo.yukirepoguide.guide.api;

import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideErrorCode;
import io.github.emiliatanovo.yukirepoguide.guide.domain.GuideEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageFailure;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageSection;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageSectionStatus;
import io.github.emiliatanovo.yukirepoguide.guide.domain.LanguageShare;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ProjectGuide;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RawFact;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReadmeEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReadmeFailure;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReadmeSection;
import io.github.emiliatanovo.yukirepoguide.guide.domain.ReadmeSectionStatus;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryEvidence;
import io.github.emiliatanovo.yukirepoguide.guide.domain.OnlineExperienceCandidate;
import io.github.emiliatanovo.yukirepoguide.guide.domain.OnlineExperienceWarning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GuideResponse(
		Repository repository,
		Readme readme,
		Languages languages,
		Map<String, Evidence> evidence) {

	public static GuideResponse from(ProjectGuide guide) {
		var facts = guide.repository();
		var repositoryRef = facts.reference();
		Map<String, Evidence> evidence = new LinkedHashMap<>();
		guide.evidence().forEach((id, value) -> evidence.put(id, Evidence.from(value)));
		return new GuideResponse(
				new Repository(
						repositoryRef.owner(),
						repositoryRef.name(),
						facts.description(),
						repositoryRef.canonicalUrl(),
						facts.stars(),
						facts.createdAt(),
						facts.pushedAt(),
						facts.projectWebsiteUrl(),
						guide.repositoryEvidenceId()),
				Readme.from(guide.readme()),
				Languages.from(guide.languages()),
				evidence);
	}

	public record Repository(
			String owner,
			String name,
			String description,
			String canonicalUrl,
			long stars,
			Instant createdAt,
			Instant pushedAt,
			String projectWebsiteUrl,
			String evidenceId) {
	}

	public record Readme(
			ReadmeSectionStatus status,
			List<OnlineExperience> candidates,
			boolean truncated,
			ReadmeFailureResponse failure) {

		public Readme {
			candidates = List.copyOf(candidates);
		}

		public static Readme from(ReadmeSection section) {
			return new Readme(
					section.status(),
					section.candidates().stream().map(OnlineExperience::from).toList(),
					section.truncated(),
					ReadmeFailureResponse.from(section.failure()));
		}
	}

	public record OnlineExperience(
			String label,
			String url,
			String evidenceId,
			List<OnlineExperienceWarning> warnings) {

		private static OnlineExperience from(OnlineExperienceCandidate candidate) {
			return new OnlineExperience(
					candidate.label(),
					candidate.url(),
					candidate.evidenceId(),
					candidate.warnings());
		}
	}

	public record ReadmeFailureResponse(
			GuideErrorCode code,
			boolean retryable,
			Long retryAfterSeconds) {

		private static ReadmeFailureResponse from(ReadmeFailure failure) {
			return failure == null
					? null
					: new ReadmeFailureResponse(
							failure.code(), failure.retryable(), failure.retryAfterSeconds());
		}
	}

	public record Languages(
			LanguageSectionStatus status,
			List<LanguageItem> items,
			Failure failure,
			String evidenceId) {

		public Languages {
			items = List.copyOf(items);
		}

		public static Languages from(LanguageSection section) {
			return new Languages(
					section.status(),
					section.items().stream().map(LanguageItem::from).toList(),
					Failure.from(section.failure()),
					section.evidenceId());
		}
	}

	public record LanguageItem(String name, BigDecimal percentage) {

		private static LanguageItem from(LanguageShare share) {
			return new LanguageItem(share.name(), share.percentage());
		}
	}

	public record Failure(GuideErrorCode code, Long retryAfterSeconds) {

		private static Failure from(LanguageFailure failure) {
			return failure == null ? null : new Failure(failure.code(), failure.retryAfterSeconds());
		}
	}

	public record Evidence(
			String type,
			String source,
			String repositoryUrl,
			RawFactResponse recentCodeUpdate,
			Long totalBytes,
			List<LanguageEvidenceItem> languages,
			String readmeUrl,
			String path,
			String sha,
			String context) {

		public static Evidence from(GuideEvidence evidence) {
			if (evidence instanceof RepositoryEvidence repository) {
				return new Evidence(
						"REPOSITORY",
						repository.source(),
						repository.repositoryUrl(),
						RawFactResponse.from(repository.recentCodeUpdate()),
						null,
						List.of(),
						null,
						null,
						null,
						null);
			}
			if (evidence instanceof LanguageEvidence languages) {
				return new Evidence(
						"LANGUAGES",
						languages.source(),
						null,
						null,
						languages.totalBytes(),
						languages.items().stream().map(LanguageEvidenceItem::from).toList(),
						null,
						null,
						null,
						null);
			}
			ReadmeEvidence readme = (ReadmeEvidence) evidence;
			return new Evidence(
					"README",
					readme.source(),
					null,
					null,
					null,
					List.of(),
					readme.readmeUrl(),
					readme.path(),
					readme.sha(),
					readme.context());
		}
	}

	public record RawFactResponse(String field, String value) {

		private static RawFactResponse from(RawFact rawFact) {
			return rawFact == null ? null : new RawFactResponse(rawFact.field(), rawFact.value());
		}
	}

	public record LanguageEvidenceItem(String name, long bytes, BigDecimal percentage) {

		private static LanguageEvidenceItem from(LanguageShare share) {
			return new LanguageEvidenceItem(share.name(), share.bytes(), share.percentage());
		}
	}
}

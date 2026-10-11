package io.github.emiliatanovo.yukirepoguide.guide.experience;

public record ExperienceReferences(String guideId, String readmeResultId, String releasesResultId,
        String quickStartResultId) {
    public ExperienceReferences withQuickStart(String id) {
        return new ExperienceReferences(guideId, readmeResultId, releasesResultId, id);
    }
}

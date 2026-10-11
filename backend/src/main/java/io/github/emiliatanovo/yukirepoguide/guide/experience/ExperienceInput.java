package io.github.emiliatanovo.yukirepoguide.guide.experience;

import io.github.emiliatanovo.yukirepoguide.guide.domain.*;

public record ExperienceInput(ReadmeSection readme, ReleaseSection releases, RepositoryReleases releaseSource,
        io.github.emiliatanovo.yukirepoguide.guide.quickstart.QuickStartResult quickStart, boolean explanationSourceChanged) {}

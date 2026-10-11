package io.github.emiliatanovo.yukirepoguide.guide.explanation;

import io.github.emiliatanovo.yukirepoguide.guide.quickstart.QuickStartInput;
import io.github.emiliatanovo.yukirepoguide.guide.quickstart.QuickStartResult;
import io.github.emiliatanovo.yukirepoguide.guide.domain.*;
import io.github.emiliatanovo.yukirepoguide.guide.experience.*;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Bounded process-local storage. Active entries are never evicted to admit another snapshot. */
public final class ExplanationSnapshots {
    private final ExplanationSettings settings;
    private final Clock clock;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    public ExplanationSnapshots(ExplanationSettings settings, Clock clock) {
        this.settings = settings; this.clock = clock;
    }
    public synchronized String save(String owner, ExplanationInput input, QuickStartInput quickStart) {
        if (owner == null) return null;
        purge();
        if (entries.size() >= settings.capacity()) {
            var victim = entries.entrySet().stream().filter(e -> !e.getValue().active()).findFirst();
            if (victim.isEmpty()) return null;
            entries.remove(victim.get().getKey());
        }
        String id = UUID.randomUUID().toString();
        entries.put(id, new Entry(owner, input, quickStart, clock.instant().plus(settings.snapshotTtl())));
        return id;
    }
    public synchronized QuickStartInput acquireQuickStart(String id, String owner) {
        Entry entry = checked(id, owner);
        if (entry.quickBusy) throw new ExplanationException("EXPLANATION_IN_PROGRESS");
        entry.quickBusy = true;
        return entry.quickStart;
    }
    public synchronized ExperienceReferences saveGuide(String owner, ExplanationInput input, QuickStartInput quickStart,
            RepositoryRef repository, ReadmeSection readme, ReleaseSection releases, RepositoryReleases source) {
        String id = save(owner, input, quickStart);
        if (id == null) return null;
        Entry entry = checked(id, owner);
        entry.references = new ExperienceReferences(id, UUID.randomUUID().toString(), UUID.randomUUID().toString(), null);
        entry.repository = repository;
        entry.readmes.put(entry.references.readmeResultId(), readme);
        entry.releases.put(entry.references.releasesResultId(), new ReleaseVersion(releases, source));
        return entry.references;
    }
    public synchronized ExperienceInput experienceInput(ExperienceReferences refs, String owner) {
        if (refs == null) throw ExperienceException.expired();
        Entry entry = experienceEntry(refs.guideId(), owner);
        var readme = entry.readmes.get(refs.readmeResultId());
        var releases = entry.releases.get(refs.releasesResultId());
        if (readme == null || releases == null) throw ExperienceException.expired();
        QuickStartResult quick = refs.quickStartResultId() == null ? null : entry.quickResults.get(refs.quickStartResultId());
        if (refs.quickStartResultId() != null && quick == null) throw ExperienceException.expired();
        boolean changed = !entry.references.readmeResultId().equals(refs.readmeResultId());
        if (changed && quick != null) throw ExperienceException.mismatch();
        return new ExperienceInput(readme, releases.section(), releases.source(), quick, changed);
    }
    private Entry experienceEntry(String id, String owner) {
        try {
            var entry = checked(id, owner);
            if (entry.references == null) throw ExperienceException.expired();
            return entry;
        } catch (ExplanationException error) { throw ExperienceException.expired(); }
    }
    public synchronized RepositoryRef experienceRepository(String id, String owner) {
        return experienceEntry(id, owner).repository;
    }
    public synchronized RegisteredRegion<ReadmeSection> recordReadme(String id, String owner, ReadmeSection readme) {
        var entry = experienceEntry(id, owner);
        String resultId = UUID.randomUUID().toString();
        entry.readmes.put(resultId, readme);
        trim(entry.readmes);
        return new RegisteredRegion<>(resultId, readme);
    }
    public synchronized RegisteredRegion<ReleaseSection> recordReleases(String id, String owner,
            ReleaseSection section, RepositoryReleases source) {
        var entry = experienceEntry(id, owner);
        String resultId = UUID.randomUUID().toString();
        entry.releases.put(resultId, new ReleaseVersion(section, source));
        trim(entry.releases);
        return new RegisteredRegion<>(resultId, section);
    }
    private static void trim(LinkedHashMap<?, ?> versions) {
        while (versions.size() > 8) versions.remove(versions.keySet().iterator().next());
    }
    public synchronized QuickStartResult recordQuickStart(String id, QuickStartResult result) {
        Entry entry = entries.get(id);
        if (entry == null || entry.references == null) return result;
        var registered = result.withResultId(UUID.randomUUID().toString());
        entry.quickResults.put(registered.resultId(), registered);
        trim(entry.quickResults);
        return registered;
    }
    public synchronized void releaseQuickStart(String id) {
        Entry entry = entries.get(id);
        if (entry != null) entry.quickBusy = false;
        purge();
    }
    private Entry checked(String id, String owner) {
        purge();
        Entry entry = entries.get(id);
        if (entry == null || owner == null || !entry.owner.equals(owner) || !clock.instant().isBefore(entry.expires))
            throw new ExplanationException("EXPLANATION_INPUT_EXPIRED");
        return entry;
    }
    public synchronized ExplanationInput acquire(String id, String owner) {
        purge();
        Entry entry = entries.get(id);
        if (entry == null || owner == null || !entry.owner.equals(owner)
                || !clock.instant().isBefore(entry.expires)) {
            throw new ExplanationException("EXPLANATION_INPUT_EXPIRED");
        }
        if (entry.busy) throw new ExplanationException("EXPLANATION_IN_PROGRESS");
        entry.busy = true;
        return entry.input;
    }
    public synchronized void release(String id) {
        Entry entry = entries.get(id);
        if (entry != null) entry.busy = false;
        purge();
    }
    private void purge() {
        Instant now = clock.instant();
        entries.values().removeIf(e -> !e.active() && !now.isBefore(e.expires));
    }
    private static final class Entry {
        final String owner;
        final ExplanationInput input;
        final QuickStartInput quickStart;
        final Instant expires;
        boolean busy;
        boolean quickBusy;
        RepositoryRef repository;
        ExperienceReferences references;
        final LinkedHashMap<String, ReadmeSection> readmes = new LinkedHashMap<>();
        final LinkedHashMap<String, ReleaseVersion> releases = new LinkedHashMap<>();
        final LinkedHashMap<String, QuickStartResult> quickResults = new LinkedHashMap<>();
        boolean active() { return busy || quickBusy; }
        Entry(String owner, ExplanationInput input, QuickStartInput quickStart, Instant expires) {
            this.owner = owner; this.input = input; this.quickStart = quickStart; this.expires = expires;
        }
    }
    private record ReleaseVersion(ReleaseSection section, RepositoryReleases source) {}
}

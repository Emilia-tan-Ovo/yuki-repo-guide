package io.github.emiliatanovo.yukirepoguide.guide.explanation;

import io.github.emiliatanovo.yukirepoguide.guide.quickstart.QuickStartInput;
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
        boolean active() { return busy || quickBusy; }
        Entry(String owner, ExplanationInput input, QuickStartInput quickStart, Instant expires) {
            this.owner = owner; this.input = input; this.quickStart = quickStart; this.expires = expires;
        }
    }
}

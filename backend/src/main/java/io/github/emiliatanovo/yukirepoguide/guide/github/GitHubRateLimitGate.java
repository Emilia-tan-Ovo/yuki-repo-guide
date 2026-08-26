package io.github.emiliatanovo.yukirepoguide.guide.github;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public final class GitHubRateLimitGate {

	private final AtomicReference<Instant> blockedUntil = new AtomicReference<>(Instant.EPOCH);

	public void blockFor(Duration duration, Clock clock) {
		Instant candidate = clock.instant().plus(duration);
		blockedUntil.updateAndGet(current -> current.isAfter(candidate) ? current : candidate);
	}

	public Long retryAfterSeconds(Clock clock) {
		Instant now = clock.instant();
		Instant deadline = blockedUntil.get();
		if (!deadline.isAfter(now)) {
			blockedUntil.compareAndSet(deadline, Instant.EPOCH);
			return null;
		}
		long remainingMillis = Duration.between(now, deadline).toMillis();
		return Math.max(1, (remainingMillis + 999) / 1_000);
	}
}

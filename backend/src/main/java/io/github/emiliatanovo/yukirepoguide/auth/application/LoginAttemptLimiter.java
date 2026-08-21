package io.github.emiliatanovo.yukirepoguide.auth.application;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

@Component
public final class LoginAttemptLimiter {

	private static final int MAX_FAILURES = 5;
	private static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
	private static final Duration BLOCK_DURATION = Duration.ofMinutes(10);

	private final Clock clock;
	private final Map<String, AttemptState> attemptsBySource = new HashMap<>();

	public LoginAttemptLimiter(Clock clock) {
		this.clock = clock;
	}

	public synchronized void verify(String sourceAddress, BooleanSupplier credentialsMatch) {
		String source = normalize(sourceAddress);
		Instant now = clock.instant();
		AttemptState state = currentState(source, now);

		if (state != null && state.blockedUntil != null) {
			long remainingMillis = Duration.between(now, state.blockedUntil).toMillis();
			long retryAfterSeconds = Math.max(1, (remainingMillis + 999) / 1000);
			throw new TooManyLoginAttemptsException(retryAfterSeconds);
		}

		if (credentialsMatch.getAsBoolean()) {
			attemptsBySource.remove(source);
			return;
		}

		if (state == null) {
			state = new AttemptState(now);
			attemptsBySource.put(source, state);
		}

		state.failures++;
		if (state.failures >= MAX_FAILURES) {
			state.blockedUntil = now.plus(BLOCK_DURATION);
		}
		throw new InvalidAccessCodeException();
	}

	private AttemptState currentState(String source, Instant now) {
		AttemptState state = attemptsBySource.get(source);
		if (state == null) {
			return null;
		}

		boolean blockExpired = state.blockedUntil != null && !now.isBefore(state.blockedUntil);
		boolean windowExpired = state.blockedUntil == null
				&& !now.isBefore(state.windowStartedAt.plus(FAILURE_WINDOW));
		if (blockExpired || windowExpired) {
			attemptsBySource.remove(source);
			return null;
		}
		return state;
	}

	private String normalize(String sourceAddress) {
		return sourceAddress == null || sourceAddress.isBlank() ? "unknown" : sourceAddress;
	}

	private static final class AttemptState {
		private final Instant windowStartedAt;
		private int failures;
		private Instant blockedUntil;

		private AttemptState(Instant windowStartedAt) {
			this.windowStartedAt = windowStartedAt;
		}
	}
}

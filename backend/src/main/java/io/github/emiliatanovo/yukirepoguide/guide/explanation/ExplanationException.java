package io.github.emiliatanovo.yukirepoguide.guide.explanation;

public final class ExplanationException extends RuntimeException {
    private final String code;
    private final Long retryAfterSeconds;
    public ExplanationException(String code) { this(code, null); }
    public ExplanationException(String code, Long retryAfterSeconds) {
        super(code);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public String code() { return code; }
    public Long retryAfterSeconds() { return retryAfterSeconds; }
}

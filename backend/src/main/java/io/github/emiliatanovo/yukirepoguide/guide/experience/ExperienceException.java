package io.github.emiliatanovo.yukirepoguide.guide.experience;

public final class ExperienceException extends RuntimeException {
    private final String code;
    public ExperienceException(String code) { super(code); this.code = code; }
    public String code() { return code; }
    public static ExperienceException expired() { return new ExperienceException("EXPERIENCE_INPUT_EXPIRED"); }
    public static ExperienceException mismatch() { return new ExperienceException("EXPERIENCE_SOURCE_MISMATCH"); }
}

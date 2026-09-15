package io.github.emiliatanovo.yukirepoguide.guide.explanation;

/** Prepared business instructions and evidence only; never session or snapshot identifiers. */
public record ModelRequest(String instructions, Object input, int maxTokens, int maxResponseBytes) {}

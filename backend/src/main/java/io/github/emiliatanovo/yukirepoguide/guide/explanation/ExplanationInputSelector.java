package io.github.emiliatanovo.yukirepoguide.guide.explanation;

import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryFacts;
import io.github.emiliatanovo.yukirepoguide.guide.domain.RepositoryReadme;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/** Select Markdown paragraphs without executing HTML or loading remote resources. */
public final class ExplanationInputSelector {
    private static final Set<String> INTRO = Set.of("简介", "介绍", "项目介绍", "项目简介", "功能", "功能特性",
            "特性", "主要功能", "about", "overview", "introduction", "features", "key features");
    private static final Set<String> EXCLUDED = Set.of("installation", "install", "setup", "quick start",
            "quickstart", "getting started", "usage", "table of contents", "contents", "安装", "安装步骤",
            "快速开始", "使用", "使用方法", "目录", "配置", "configuration");
    private final ExplanationSettings settings;
    public ExplanationInputSelector(ExplanationSettings settings) { this.settings = settings; }

    public ExplanationInput select(RepositoryFacts facts, RepositoryReadme readme) {
        var evidence = new LinkedHashMap<String, ExplanationInput.Evidence>();
        int remaining = settings.maxInputCharacters() - facts.reference().name().length();
        String description = facts.description();
        if (description != null && !description.isBlank() && description.length() <= remaining) {
            evidence.put("repo-description", new ExplanationInput.Evidence("repo-description",
                    facts.reference().canonicalUrl(), null, null, description));
            remaining -= description.length();
        }
        if (readme != null) {
            var nodes = new ArrayList<Node>();
            Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build().parse(readme.content())
                    .accept(new AbstractVisitor() {
                        @Override public void visit(Heading heading) { nodes.add(heading); }
                        @Override public void visit(Paragraph paragraph) { nodes.add(paragraph); }
                    });
            var headings = new ArrayDeque<Section>();
            boolean selected = true;
            boolean firstHeading = true;
            int number = 0;
            for (Node node : nodes) {
                if (node instanceof Heading heading) {
                    int level = heading.getLevel();
                    String title = plainText(heading).strip().toLowerCase(Locale.ROOT);
                    while (!headings.isEmpty() && headings.peek().level() >= level) headings.pop();
                    boolean excluded = EXCLUDED.contains(title)
                            || (!headings.isEmpty() && headings.peek().excluded());
                    selected = !excluded && (INTRO.contains(title) || (firstHeading && level == 1)
                            || (!headings.isEmpty() && headings.peek().selected() && headings.peek().level() > 1));
                    headings.push(new Section(level, selected, excluded));
                    firstHeading = false;
                    continue;
                }
                if (!selected || plainText(node).isBlank() || node.getSourceSpans().isEmpty()) continue;
                // Preserve the exact original excerpt, including inline identifiers and Markdown formatting.
                var spans = node.getSourceSpans();
                int start = spans.getFirst().getInputIndex();
                var last = spans.getLast();
                String excerpt = readme.content().substring(start, last.getInputIndex() + last.getLength());
                if (excerpt.length() > remaining) break;
                String id = "readme-intro-" + (++number);
                evidence.put(id, new ExplanationInput.Evidence(id, readme.htmlUrl(), readme.path(), readme.sha(), excerpt));
                remaining -= excerpt.length();
            }
        }
        return new ExplanationInput(facts.reference().name(), evidence);
    }

    private String plainText(Node node) {
        var text = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override public void visit(Text part) { text.append(part.getLiteral()); }
            @Override public void visit(Code part) { text.append(part.getLiteral()); }
            @Override public void visit(SoftLineBreak part) { text.append(' '); }
            @Override public void visit(HardLineBreak part) { text.append(' '); }
            @Override public void visit(Image image) { /* Images and badges are not text evidence. */ }
            @Override public void visit(Link link) { /* Link-only navigation is not introduction text. */ }
        });
        return text.toString();
    }
    private record Section(int level, boolean selected, boolean excluded) {}
}

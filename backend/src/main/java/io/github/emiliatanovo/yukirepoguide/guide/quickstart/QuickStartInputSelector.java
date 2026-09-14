package io.github.emiliatanovo.yukirepoguide.guide.quickstart;

import io.github.emiliatanovo.yukirepoguide.guide.domain.*;
import java.util.*;
import org.commonmark.node.*;
import org.commonmark.parser.*;

/** Keeps document order and heading ancestry; code is always copied from source, never synthesized. */
public final class QuickStartInputSelector {
    private static final Set<String> BACKGROUND = Set.of("about", "overview", "introduction", "features", "roadmap",
            "license", "licence", "contributors", "acknowledgements", "项目介绍", "简介", "功能", "路线图", "许可证", "致谢");
    private final QuickStartSettings settings;
    public QuickStartInputSelector(QuickStartSettings settings) { this.settings = settings; }

    public QuickStartInput select(RepositoryReadme readme, ReadmeSectionStatus status) {
        if (readme == null) return new QuickStartInput(status, false, Map.of());
        var nodes = new ArrayList<Node>();
        Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build().parse(readme.content())
                .accept(new AbstractVisitor() {
                    @Override public void visit(Heading n) { nodes.add(n); }
                    @Override public void visit(Paragraph n) { nodes.add(n); visitChildren(n); }
                    @Override public void visit(FencedCodeBlock n) { nodes.add(n); }
                    @Override public void visit(IndentedCodeBlock n) { nodes.add(n); }
                    @Override public void visit(Code n) { nodes.add(n); }
                });
        var headings = new ArrayList<Section>();
        var evidence = new LinkedHashMap<String, QuickStartInput.Evidence>();
        int used = 0;
        boolean truncated = false;
        for (var node : nodes) {
            if (node instanceof Heading heading) {
                while (!headings.isEmpty() && headings.getLast().level >= heading.getLevel()) headings.removeLast();
                String title = plain(heading);
                String normalized = title.strip().toLowerCase(Locale.ROOT);
                boolean operations = normalized.matches(".*(install|quick.?start|getting.?started|usage|configuration|setup|requirements|prerequisites|run|deploy|安装|快速开始|使用|配置|运行|环境|依赖|部署).*" );
                boolean selected = operations || (!BACKGROUND.contains(normalized)
                        && (headings.isEmpty() || headings.getLast().selected));
                headings.add(new Section(heading.getLevel(), title, selected));
                continue;
            }
            if (!headings.isEmpty() && !headings.getLast().selected) continue;
            // Unknown headings remain eligible; README authors need not use a fixed heading vocabulary.
            String section = String.join(" > ", headings.stream().map(Section::title).toList());
            String raw = excerpt(readme.content(), node);
            if (raw.isBlank()) continue;
            var kind = QuickStartInput.Kind.TEXT;
            if (node instanceof FencedCodeBlock fence) {
                raw = codeContent(readme.content(), fence, fence.getLiteral(), true);
                kind = codeKind(fence.getInfo());
            } else if (node instanceof IndentedCodeBlock code) {
                raw = codeContent(readme.content(), code, code.getLiteral(), false);
                kind = codeKind("");
            } else if (node instanceof Code) {
                // A single-line inline code span is an exact source excerpt after removing delimiters.
                int ticks = 0;
                while (ticks < raw.length() && raw.charAt(ticks) == '`') ticks++;
                if (ticks == 0 || raw.length() < ticks * 2) continue;
                raw = raw.substring(ticks, raw.length() - ticks);
                kind = QuickStartInput.Kind.CODE;
            }
            if (raw == null) { truncated = true; continue; }
            if (raw.isBlank()) continue;
            int cost = raw.length() + section.length();
            if (used + cost > settings.maxInputCharacters()) { truncated = true; break; }
            String id = "qs-" + (evidence.size() + 1);
            evidence.put(id, new QuickStartInput.Evidence(id, kind, readme.htmlUrl(), readme.path(), readme.sha(),
                    section, evidence.size() + 1, raw));
            used += cost;
        }
        return new QuickStartInput(status, truncated, evidence);
    }
    private QuickStartInput.Kind codeKind(String info) {
        String language = info.strip().toLowerCase(Locale.ROOT).split("\\s+", 2)[0];
        if (Set.of("sh", "bash", "shell", "console", "powershell", "pwsh", "ps1", "cmd", "bat", "zsh").contains(language))
            return QuickStartInput.Kind.COMMAND;
        if (Set.of("json", "yaml", "yml", "toml", "ini", "properties", "env", "xml", "dotenv").contains(language))
            return QuickStartInput.Kind.CONFIGURATION;
        return QuickStartInput.Kind.CODE;
    }
    private String codeContent(String source, Node node, String literal, boolean fenced) {
        var spans = node.getSourceSpans();
        if (spans.isEmpty() || literal.isEmpty()) return "";
        String[] lines = source.split("\n", -1);
        String[] codeLines = literal.split("\n", -1);
        int firstLine = spans.getFirst().getLineIndex() + (fenced ? 1 : 0);
        var result = new StringBuilder();
        int count = codeLines.length - (literal.endsWith("\n") ? 1 : 0);
        for (int index = 0; index < count; index++) {
            int lineIndex = firstLine + index;
            if (lineIndex >= lines.length) return null;
            String sourceLine = lines[lineIndex];
            boolean crlf = sourceLine.endsWith("\r");
            if (crlf) sourceLine = sourceLine.substring(0, sourceLine.length() - 1);
            String codeLine = codeLines[index];
            // The parser identifies Markdown container/indentation syntax. Copy only the exact
            // source suffix it identifies as code, retaining the original line ending.
            if (!sourceLine.endsWith(codeLine)) return null;
            result.append(sourceLine.substring(sourceLine.length() - codeLine.length()));
            if (lineIndex < lines.length - 1) result.append(crlf ? "\r\n" : "\n");
        }
        return result.toString();
    }
    private String excerpt(String source, Node node) {
        var spans = node.getSourceSpans();
        if (spans.isEmpty()) return "";
        var last = spans.getLast();
        return source.substring(spans.getFirst().getInputIndex(), last.getInputIndex() + last.getLength());
    }
    private String plain(Node node) {
        var text = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override public void visit(Text n) { text.append(n.getLiteral()); }
            @Override public void visit(Code n) { text.append(n.getLiteral()); }
        });
        return text.toString();
    }
    private record Section(int level, String title, boolean selected) {}
}

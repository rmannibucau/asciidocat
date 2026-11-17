/*
 * Copyright Romain Manni-Bucau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rmannibucau.asciidocat;

import io.yupiik.asciidoc.model.Admonition;
import io.yupiik.asciidoc.model.Anchor;
import io.yupiik.asciidoc.model.Body;
import io.yupiik.asciidoc.model.Code;
import io.yupiik.asciidoc.model.ConditionalBlock;
import io.yupiik.asciidoc.model.DescriptionList;
import io.yupiik.asciidoc.model.Document;
import io.yupiik.asciidoc.model.Element;
import io.yupiik.asciidoc.model.LineBreak;
import io.yupiik.asciidoc.model.Link;
import io.yupiik.asciidoc.model.Listing;
import io.yupiik.asciidoc.model.Macro;
import io.yupiik.asciidoc.model.OpenBlock;
import io.yupiik.asciidoc.model.OrderedList;
import io.yupiik.asciidoc.model.PageBreak;
import io.yupiik.asciidoc.model.Paragraph;
import io.yupiik.asciidoc.model.PassthroughBlock;
import io.yupiik.asciidoc.model.Quote;
import io.yupiik.asciidoc.model.Section;
import io.yupiik.asciidoc.model.Table;
import io.yupiik.asciidoc.model.Text;
import io.yupiik.asciidoc.model.UnOrderedList;
import io.yupiik.asciidoc.renderer.Visitor;

import java.util.ArrayList;
import java.util.List;

import java.util.Arrays;

/**
 * Renders a parsed AsciiDoc {@link Document} to formatted terminal text using
 * ANSI styling. Colors follow the Yupiik minisite theme palette.
 */
public class PlainTextRenderer implements Visitor<String> {
    /** Precompiled matcher for ANSI SGR sequences (\u001b[...m), used to compute visible width. */
    private static final java.util.regex.Pattern ANSI_SGR = java.util.regex.Pattern.compile("\u001b\\[[0-9;]*m");

    private StringBuilder output = new StringBuilder();
    private final boolean color;
    private final Theme theme;
    private final SyntaxHighlighter highlighter;
    private java.util.Map<String, String> attributes = java.util.Map.of();
    private int listDepth;
    private int[] orderedCounters = new int[8];

    /**
     * Creates a terminal renderer with the supplied styling configuration.
     *
     * @param color whether to emit ANSI styling
     * @param theme palette used for styled output
     */
    public PlainTextRenderer(final boolean color, final Theme theme) {
        this.color = color;
        this.theme = theme;
        this.highlighter = new SyntaxHighlighter(color, SyntaxHighlighter.colors(theme.dark));
    }

    @Override
    public void visit(final Document document) {
        attributes = document.header() == null ? java.util.Map.of() : document.header().attributes();
        final var header = document.header();
        if (header != null && header.title() != null && !header.title().isBlank()) {
            heading(1, header.title());
            final var meta = metaLine(header);
            if (!meta.isEmpty()) {
                newline(output.length() > 0);
                output.append(muted(meta)).append('\n');
            }
        }
        visitBody(document.body());
    }

    private String metaLine(final io.yupiik.asciidoc.model.Header header) {
        final var meta = new StringBuilder();
        if (header.author() != null && !header.author().isEmpty()) {
            for (final var author : header.author()) {
                if (meta.length() > 0) {
                    meta.append("; ");
                }
                meta.append(author.name() == null ? "" : author.name());
                if (author.mail() != null && !author.mail().isBlank()) {
                    meta.append(" <").append(author.mail()).append('>');
                }
            }
        }
        if (header.revision() != null) {
            final var rev = header.revision();
            if ((rev.number() != null && !rev.number().isBlank()) || (rev.date() != null && !rev.date().isBlank())) {
                if (meta.length() > 0) {
                    meta.append(" - ");
                }
                if (rev.number() != null) {
                    meta.append(rev.number());
                }
                if (rev.date() != null && !rev.date().isBlank()) {
                    meta.append(' ').append(rev.date());
                }
            }
        }
        return meta.toString();
    }

    @Override
    public void visitHeader(final io.yupiik.asciidoc.model.Header header) {
        // handled in visit(Document)
    }

    @Override
    public void visitBody(final Body body) {
        for (final var child : body.children()) {
            visitBlockChild(child);
        }
    }

    /**
     * Renders a block-level child. The yupiik engine emits bare inline elements
     * (Text, Link, inline Code, Anchor, inline Macro) directly as body/section
     * children to represent standalone paragraphs; those need paragraph wrapping
     * (blank line before, single newline after), otherwise block elements render
     * as blocks.
     */
    private void visitBlockChild(final Element element) {
        if (isBareInline(element)) {
            newline(true);
            visitElement(element);
            ensureNewline();
        } else {
            visitElement(element);
        }
    }

    private boolean isBareInline(final Element element) {
        return switch (element.type()) {
            case TEXT, LINK, ANCHOR -> true;
            case CODE -> element instanceof Code c && c.inline();
            case MACRO -> element instanceof Macro m && m.inline();
            default -> false;
        };
    }

    @Override
    public void visitSection(final Section section) {
        final var admonitionKind = admonitionKind(section.options());
        if (admonitionKind != null) {
            newline(true);
            renderAdmonition(admonitionKind, section.children());
            return;
        }
        // skip parser artifacts: sections with no title and no content
        final var title = inline(section.title());
        if (title.isBlank() && section.children().isEmpty()) {
            return;
        }
        newline(true);
        heading(section.level(), title);
        for (final var child : section.children()) {
            visitBlockChild(child);
        }
    }

    /** The yupiik engine parses block `[NOTE]====` as a Section whose empty-string option holds the admonition kind. */
    private Admonition.Level admonitionKind(final java.util.Map<String, String> options) {
        if (options == null) {
            return null;
        }
        final var kind = options.get("");
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case "NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION" -> Admonition.Level.valueOf(kind);
            default -> null;
        };
    }

    private void renderAdmonition(final Admonition.Level level, final List<Element> children) {
        final var buf = new PlainTextRenderer(color, theme);
        buf.attributes = attributes;
        for (final var child : children) {
            buf.visitElement(child);
        }
        final var content = buf.result();
        output.append(admonitionLabel(level));
        final var lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            output.append(i == 0 ? ' ' : "  ").append(lines[i]).append('\n');
        }
    }

    @Override
    public void visitParagraph(final Paragraph paragraph) {
        newline(true);
        for (final var child : paragraph.children()) {
            visitElement(child);
        }
        output.append('\n');
    }

    @Override
    public void visitText(final Text text) {
        final var strike = "line-through".equals(text.options().get(""));
        output.append(styledInline(text.value(), text.style(), strike));
    }

    @Override
    public void visitLink(final Link link) {
        final var url = link.url();
        var label = link.label() == null ? null : inline(link.label());
        if (label == null || label.isBlank() || label.equals(url)) {
            // bare/auto-detected link (no [text] label, or label == url): the url is the clickable label, shown once
            label = (url == null || url.isBlank()) ? "" : url;
        }
        // never print the url text; the label is the only visible, clickable text
        output.append(linkLabel(url, label));
    }

    @Override
    public void visitAnchor(final Anchor anchor) {
        if (anchor.label() != null && !anchor.label().isBlank()) {
            output.append(muted(anchor.label()));
        } else if (anchor.value() != null && !anchor.value().isBlank()) {
            output.append(muted('[' + anchor.value() + ']'));
        }
    }

    @Override
    public void visitCode(final Code code) {
        if (code.inline()) {
            output.append(inlineCode(code.value()));
        } else {
            newline(true);
            codeBlock(code.value(), code.options().get("language"));
        }
    }

    @Override
    public void visitListing(final Listing listing) {
        newline(true);
        final var options = listing.options();
        codeBlock(listing.value(), options == null ? null : options.get("language"));
    }

    @Override
    public void visitUnOrderedList(final UnOrderedList list) {
        visitGenericList(list.children(), false);
    }

    @Override
    public void visitOrderedList(final OrderedList list) {
        visitGenericList(list.children(), true);
    }

    private void visitGenericList(final List<Element> children, final boolean ordered) {
        if (children.isEmpty()) {
            return;
        }
        newline(true);
        listDepth++;
        orderedCounters[listDepth] = 0;
        for (final var child : children) {
            orderedCounters[listDepth]++;
            indentTo();
            output.append(ordered ? orderedCounters[listDepth] + ". " : bullet(listDepth) + ' ');
            renderListItem(child);
            ensureNewline();
        }
        listDepth--;
    }

    /**
     * Renders a single list item. Items are often Paragraph nodes whose block-level
     * rendering would inject a blank line and a trailing newline; instead their
     * inline children are laid out on the same line as the bullet. Nested
     * lists/blocks are rendered as blocks on the following line.
     */
    private void renderListItem(final Element item) {
        if (item instanceof Paragraph p) {
            for (final var c : p.children()) {
                if (isBlock(c)) {
                    ensureNewline();
                    visitElement(c);
                    ensureNewline();
                } else {
                    visitElement(c);
                }
            }
        } else {
            visitElement(item);
        }
    }

    private boolean isBlock(final Element element) {
        return switch (element.type()) {
            case UNORDERED_LIST, ORDERED_LIST, DESCRIPTION_LIST, LISTING, TABLE,
                 QUOTE, OPEN_BLOCK, PASS_BLOCK, ADMONITION -> true;
            case CODE -> element instanceof Code c && !c.inline();
            default -> false;
        };
    }

    private String bullet(final int depth) {
        return switch (depth) {
            case 1 -> "\u2022";   // bullet
            case 2 -> "\u25e6";   // white bullet
            default -> "\u25aa";  // small black square
        };
    }

    @Override
    public void visitDescriptionList(final DescriptionList list) {
        if (list.children().isEmpty()) {
            return;
        }
        newline(true);
        for (final var entry : list.children().entrySet()) {
            output.append(bold(inline(entry.getKey()))).append('\n');
            output.append(inline(entry.getValue())).append('\n');
        }
    }

    @Override
    public void visitTable(final Table table) {
        newline(true);
        renderTable(table.elements());
    }

    @Override
    public void visitAdmonition(final Admonition admonition) {
        newline(true);
        output.append(admonitionLabel(admonition.level() == null ? Admonition.Level.NOTE : admonition.level()));
        final var content = inline(admonition.content());
        final var lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            output.append(i == 0 ? ' ' : "  ").append(lines[i]).append('\n');
        }
    }

    @Override
    public void visitQuote(final Quote quote) {
        newline(true);
        final var buf = new PlainTextRenderer(color, theme);
        buf.attributes = attributes;
        for (final var child : quote.children()) {
            buf.visitElement(child);
        }
        final var content = buf.result();
        for (final var line : content.split("\n", -1)) {
            output.append(muted("\u2502 ")).append(line).append('\n');
        }
    }

    @Override
    public void visitOpenBlock(final OpenBlock block) {
        newline(true);
        for (final var child : block.children()) {
            visitElement(child);
        }
    }

    @Override
    public void visitPassthroughBlock(final PassthroughBlock block) {
        newline(true);
        output.append(block.value()).append('\n');
    }

    @Override
    public void visitConditionalBlock(final ConditionalBlock block) {
        if (block.evaluator() != null && !block.evaluator().test(context())) {
            return;
        }
        for (final var child : block.children()) {
            visitElement(child);
        }
    }

    @Override
    public void visitLineBreak(final LineBreak lineBreak) {
        output.append('\n');
    }

    @Override
    public void visitPageBreak(final PageBreak pageBreak) {
        newline(true);
        output.append(muted("\u2500\u2500\u2500 page break \u2500\u2500\u2500")).append('\n');
    }

    @Override
    public void visitMacro(final Macro macro) {
        if (macro.inline()) {
            // the yupiik engine represents `link:url[label]` and https links with
            // a label as a Macro whose empty-string option holds the link text.
            final var linkText = "link".equals(macro.name()) ? macro.options().get("") : null;
            if (linkText != null && !linkText.isBlank()) {
                output.append(linkLabel(macro.label(), linkText));
                return;
            }
            final var label = macro.label() == null || macro.label().isBlank() ? macro.name() : macro.label();
            output.append(muted(label));
        } else {
            newline(true);
            final var name = macro.label() == null || macro.label().isBlank() ? macro.name() : macro.label();
            output.append(muted('[' + name + ']')).append('\n');
        }
    }

    @Override
    public ConditionalBlock.Context context() {
        final var attrs = this.attributes;
        return name -> attrs.containsKey(name) ? attrs.get(name) : null;
    }

    @Override
    public String result() {
        var s = output.toString();
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\n') {
            end--;
        }
        return s.substring(0, end);
    }

    // --- inline capture ---

    private String inline(final Element element) {
        final var saved = output;
        final var scratch = new StringBuilder();
        output = scratch;
        if (element instanceof Paragraph p) {
            for (final var child : p.children()) {
                visitElement(child);
            }
        } else {
            visitElement(element);
        }
        output = saved;
        return scratch.toString();
    }

    // --- styling ---

    private void heading(final int level, final String text) {
        output.append(theme.heading(level, color, text)).append('\n');
    }

    private void newline(final boolean blank) {
        if (blank) {
            if (output.length() > 0) {
                output.append('\n');
            }
        } else if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
            output.append('\n');
        }
    }

    private void indentTo() {
        output.append("  ".repeat(Math.max(0, listDepth - 1)));
    }

    private void ensureNewline() {
        if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
            output.append('\n');
        }
    }

    private String bold(final String s) {
        return color ? Ansi.BOLD + s + Ansi.RESET : s;
    }

    private String muted(final String s) {
        return color ? Ansi.fg(theme.muted[0], theme.muted[1], theme.muted[2]) + s + Ansi.RESET : s;
    }

    private String inlineCode(final String s) {
        return color ? Ansi.BOLD + Ansi.fg(theme.code[0], theme.code[1], theme.code[2]) + s + Ansi.RESET : s;
    }

    private String linkLabel(final String url, final String label) {
        // the visible text is the label only (never the url); it is clickable via OSC-8
        final var visible = color ? Ansi.fg(theme.link[0], theme.link[1], theme.link[2]) + Ansi.UNDERLINE + label + Ansi.RESET : label;
        return Ansi.hyperlink(url, visible);
    }

    private String admonitionLabel(final Admonition.Level level) {
        final int[] rgb = switch (level) {
            case NOTE, IMPORTANT -> theme.admonNote;
            case TIP -> theme.admonTip;
            case WARNING -> theme.admonWarn;
            case CAUTION -> theme.admonCaution;
        };
        final var label = switch (level) {
            case NOTE -> "NOTE";
            case TIP -> "TIP";
            case IMPORTANT -> "IMPORTANT";
            case WARNING -> "WARNING";
            case CAUTION -> "CAUTION";
        };
        return color ? Ansi.BOLD + Ansi.fg(rgb[0], rgb[1], rgb[2]) + label + Ansi.RESET : label;
    }

    private void codeBlock(final String value, final String language) {
        if (!color) {
            output.append(value.stripTrailing()).append('\n');
            return;
        }
        final var bg = Ansi.bg(theme.preBg[0], theme.preBg[1], theme.preBg[2]);
        // highlight tokens, then re-arm the background after every internal reset
        final var armed = highlighter.highlight(language, value).replace(Ansi.RESET, Ansi.RESET + bg);
        final var lines = armed.split("\n", -1);
        int end = lines.length;
        while (end > 0 && ansiStripped(lines[end - 1]).trim().isEmpty()) {
            end--;
        }
        for (int i = 0; i < end; i++) {
            final var line = lines[i];
            output.append(bg).append(line.isEmpty() ? " " : line).append(Ansi.RESET).append('\n');
        }
        output.append('\n');
    }

    private String styledInline(final String value, final List<Text.Style> styles, final boolean strike) {
        if (!color || ((styles == null || styles.isEmpty()) && !strike)) {
            return value;
        }
        final var prefix = new StringBuilder();
        if (strike) {
            prefix.append(Ansi.STRIKE);
        }
        if (styles != null) {
            for (final var s : styles) {
                switch (s) {
                    case BOLD -> prefix.append(Ansi.BOLD);
                    case ITALIC, EMPHASIS -> prefix.append(Ansi.ITALIC);
                    case MARK -> prefix.append(Ansi.REVERSE);
                    case SUB, SUP -> prefix.append(Ansi.DIM);
                }
            }
        }
        return prefix + value + Ansi.RESET;
    }

    private void renderTable(final List<List<Element>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        final List<List<String>> rendered = new ArrayList<>(rows.size());
        for (final var row : rows) {
            final var cells = new ArrayList<String>(row.size());
            for (final var cell : row) {
                cells.add(inline(cell));
            }
            rendered.add(cells);
        }
        final int cols = rendered.stream().mapToInt(List::size).max().orElse(0);
        if (cols == 0) {
            return;
        }
        // natural (unwrapped) column widths based on the longest cell in each column
        final int[] natural = new int[cols];
        final int[] longestWord = new int[cols];
        for (final var row : rendered) {
            for (int c = 0; c < row.size(); c++) {
                natural[c] = Math.max(natural[c], visibleLength(row.get(c)));
                longestWord[c] = Math.max(longestWord[c], longestWord(row.get(c)));
            }
        }
        final int[] widths = allocateWidths(natural, longestWord, cols);
        // top rule
        output.append(tableBorder('┌', '┬', '┐', widths)).append('\n');
        // header row: bold signal, but respect cells the parser already made bold
        emitRow(rendered.get(0), widths, true);
        // header/body separator
        output.append(tableBorder('├', '┼', '┤', widths)).append('\n');
        for (int r = 1; r < rendered.size(); r++) {
            emitRow(rendered.get(r), widths, false);
        }
        // bottom rule
        output.append(tableBorder('└', '┴', '┘', widths)).append('\n');
    }

    /**
     * Distribute the available terminal width across columns so the table fits
     * the screen: each column keeps at least a small minimum, and any column whose
     * natural width exceeds its share is wrapped (reflowed by {@link #wrapToWidth}).
     */
    private int[] allocateWidths(final int[] natural, final int[] longestWord, final int cols) {
        // content chars available for the table's cells; the frame consumes 1 leading wall
        // plus 3 chars per column (space + cell + " │")
        final int budget = Math.max(4, tableWidth() - 1 - 3 * cols);
        // keep each column at least as wide as its longest single word so ordinary words
        // (e.g. 'Performance') stay on one line; only genuinely unbreakable longer tokens
        // hard-break, and only when the budget forces it.
        int[] mins = new int[cols];
        int used = 0;
        for (int c = 0; c < cols; c++) {
            mins[c] = Math.min(natural[c], Math.max(1, longestWord[c]));
            used += mins[c];
        }
        if (used <= budget) {
            // the longest words all fit. Grow each column toward its natural (unwrapped)
            // content width, distributing the surplus proportionally to how much wrappable
            // content it has, but never wider than the content needs nor wider than the
            // terminal. So a naturally-narrow table (e.g. "Fichier | Rôle") stays compact
            // instead of being stretched to fill the whole screen.
            int[] excess = new int[cols];
            int totalExcess = 0;
            int naturalSum = 0;
            for (int c = 0; c < cols; c++) {
                excess[c] = Math.max(0, natural[c] - mins[c]);
                totalExcess += excess[c];
                naturalSum += natural[c];
            }
            final int target = Math.min(budget, naturalSum);
            growToNatural(mins, cols, target, excess, totalExcess, natural);
            return mins;
        }
        // even the longest words cannot all fit: shrink proportionally, breaking words if needed
        final int[] widths = new int[cols];
        int[] demand = new int[cols];
        int totalDemand = 0;
        for (int c = 0; c < cols; c++) {
            demand[c] = Math.max(0, natural[c]);
            totalDemand += demand[c];
        }
        addExactly(widths, cols, budget, demand, totalDemand);
        return widths;
    }

    /**
     * Fills {@code out} (of length {@code cols}) with {@code target} total units, giving each
     * column a share of {@code remaining} proportional to its weight (largest-remainder, so the
     * result sums exactly to {@code target}), with a minimum of 3 per column. Handles the case
     * where weights are zero uniformly.
     */
    private static void addExactly(final int[] out, final int cols, final int target,
                                   final int[] weight, final int totalWeight) {
        final int[] base = new int[cols];
        int baseSum = 0;
        for (int c = 0; c < cols; c++) {
            base[c] = Math.max(3, out[c]); // keep any initial content (e.g. longest-word minimum)
            out[c] = base[c];
            baseSum += base[c];
        }
        int remaining = target - baseSum;
        // proportional extras with largest-remainder rounding
        final int[] extra = new int[cols];
        final double[] frac = new double[cols];
        if (totalWeight > 0 && remaining > 0) {
            for (int c = 0; c < cols; c++) {
                final double exact = (double) weight[c] * remaining / totalWeight;
                extra[c] = (int) Math.floor(exact);
                frac[c] = exact - extra[c];
            }
            // distribute the rounding remainder to the largest fractional parts
            final Integer[] order = new Integer[cols];
            for (int c = 0; c < cols; c++) {
                order[c] = c;
            }
            java.util.Arrays.sort(order, (a, b) -> Double.compare(frac[b], frac[a]));
            int leftover = remaining - java.util.Arrays.stream(extra).sum();
            for (final int c : order) {
                if (leftover <= 0) {
                    break;
                }
                extra[c]++;
                leftover--;
            }
        }
        for (int c = 0; c < cols; c++) {
            out[c] = base[c] + extra[c];
        }
    }

    /**
     * Grows {@code out} (starting from its current content, e.g. the longest-word minimum)
     * toward {@code target} total width, distributing the surplus to columns that have
     * wrappable content ({@code excess}), but never making a column wider than its natural
     * (unwrapped) content width {@code natural}. This keeps narrow tables compact while
     * still letting wide ones use the available space.
     */
    private static void growToNatural(final int[] out, final int cols, final int target,
                                      final int[] excess, final int totalExcess, final int[] natural) {
        int baseSum = 0;
        for (int c = 0; c < cols; c++) {
            baseSum += out[c];
        }
        int remaining = target - baseSum;
        if (remaining <= 0) {
            return;
        }
        // proportional raise, largest-remainder, each column capped at its natural width
        final int[] cap = new int[cols];
        int totalCap = 0;
        for (int c = 0; c < cols; c++) {
            cap[c] = Math.max(0, natural[c] - out[c]);
            totalCap += cap[c];
        }
        final int toFill = Math.min(remaining, totalCap);
        final int[] extra = new int[cols];
        final double[] frac = new double[cols];
        if (totalExcess > 0 && toFill > 0) {
            for (int c = 0; c < cols; c++) {
                final double exact = (double) excess[c] * toFill / totalExcess;
                extra[c] = (int) Math.floor(exact);
                if (extra[c] > cap[c]) {
                    extra[c] = cap[c];
                }
                frac[c] = exact - (int) Math.floor(exact);
            }
        }
        int filled = 0;
        for (final int e : extra) {
            filled += e;
        }
        // distribute the rounding remainder to columns with headroom, by largest fraction
        final Integer[] order = new Integer[cols];
        for (int c = 0; c < cols; c++) {
            order[c] = c;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(frac[b], frac[a]));
        int leftover = toFill - filled;
        for (final int c : order) {
            if (leftover <= 0) {
                break;
            }
            if (extra[c] < cap[c]) {
                extra[c]++;
                leftover--;
            }
        }
        // any leftover that we still could not give to preferred columns: fill any with headroom
        for (int c = 0; c < cols && leftover > 0; c++) {
            while (extra[c] < cap[c] && leftover > 0) {
                extra[c]++;
                leftover--;
            }
        }
        for (int c = 0; c < cols; c++) {
            out[c] += extra[c];
        }
    }

    /** Emits a table row, wrapping each cell to its column and printing continued lines when a cell wraps. */
    private void emitRow(final List<String> cells, final int[] widths, final boolean header) {
        final List<List<String>> wrapped = new ArrayList<>(cells.size());
        int lines = 1;
        for (int c = 0; c < widths.length; c++) {
            final var content = c < cells.size() ? cells.get(c) : "";
            final var wrappedCell = wrapToWidth(content, widths[c]);
            wrapped.add(wrappedCell);
            lines = Math.max(lines, wrappedCell.size());
        }
        for (int l = 0; l < lines; l++) {
            final var sb = new StringBuilder();
            sb.append('│');
            for (int c = 0; c < widths.length; c++) {
                final var cellLines = wrapped.get(c);
                final var cell = l < cellLines.size() ? cellLines.get(l) : "";
                final var padded = pad(cell, widths[c]);
                sb.append(' ').append(header && l == 0 ? headerCell(padded, widths[c]) : padded).append(" │");
            }
            output.append(sb).append('\n');
        }
    }

    private String tableBorder(final char left, final char mid, final char right, final int[] widths) {
        final var sb = new StringBuilder();
        sb.append(left);
        for (int c = 0; c < widths.length; c++) {
            sb.append('─').append("─".repeat(widths[c])).append('─');
            if (c < widths.length - 1) {
                sb.append(mid);
            }
        }
        sb.append(right);
        return sb.toString();
    }

    /**
     * Header cell content: extra-bold only if the parser did not already make it bold,
     * so `*Name*` (BOLD) is not double-wrapped (`\u001b[1m\u001b[1m`).
     */
    private String headerCell(final String content, final int width) {
        final var padded = pad(content, width);
        if (!color || content.startsWith(Ansi.BOLD)) {
            return padded;
        }
        return Ansi.BOLD + padded + Ansi.RESET;
    }

    private String pad(final String s, final int width) {
        final int padLen = Math.max(0, width - visibleLength(s));
        return s + " ".repeat(padLen);
    }

    /**
     * Wraps {@code text} onto lines of at most {@code width} visible characters, breaking
     * on spaces and preserving ANSI SGR sequences (which count as zero width) inline so
     * each produced line carries its own styled spans.
     */
    private List<String> wrapToWidth(final String text, final int width) {
        final List<String> out = new ArrayList<>();
        if (text.isEmpty()) {
            out.add("");
            return out;
        }
        final StringBuilder line = new StringBuilder();
        final StringBuilder word = new StringBuilder();
        final int[] lineLen = {0}; // visible length of the current line (ANSI stripped)
        final int n = text.length();
        int i = 0;

        while (i < n) {
            final char c = text.charAt(i);
            if (c == '\u001b') { // ANSI escape: copy verbatim (zero visible width)
                final int start = i;
                i++;
                if (i < n && text.charAt(i) == '[') {
                    i++;
                    while (i < n && (Character.isDigit(text.charAt(i)) || text.charAt(i) == ';')) {
                        i++;
                    }
                    if (i < n && text.charAt(i) == 'm') {
                        i++;
                    }
                }
                word.append(text, start, i);
                continue;
            }
            if (c == '\n' || c == '\r') {
                flushWord(line, word, lineLen, width, out);
                out.add(line.toString());
                line.setLength(0);
                lineLen[0] = 0;
                i++;
                continue;
            }
            if (c == ' ') {
                flushWord(line, word, lineLen, width, out);
                i++;
                continue;
            }
            word.append(c);
            i++;
        }
        flushWord(line, word, lineLen, width, out);
        if (line.length() > 0) {
            out.add(line.toString());
        }
        if (out.isEmpty()) {
            out.add("");
        }
        return out;
    }

    private void flushWord(final StringBuilder line, final StringBuilder word, final int[] lineLen,
                           final int width, final List<String> out) {
        if (word.length() == 0) {
            return;
        }
        final var wordStr = word.toString();
        final int wordLen = collectedVisible(word);
        final int space = line.length() == 0 ? 0 : 1; // separator before the word
        if (line.length() > 0 && lineLen[0] + space + wordLen > width) {
            out.add(line.toString());
            line.setLength(0);
            lineLen[0] = 0;
        }
        if (line.length() > 0) {
            line.append(' ');
            lineLen[0]++;
        }
        appendWord(line, lineLen, wordStr, width, out);
        word.setLength(0);
    }

    /**
     * Appends a word to the current line, hard-breaking it into chunks of at most
     * {@code width} visible characters when a single word is wider than the column
     * (so an overlong word cannot slip into the next column). ANSI SGR sequences are
     * carried along; each chunk starts with any leading styles of the word.
     */
    private void appendWord(final StringBuilder line, final int[] lineLen, final String word,
                            final int width, final List<String> out) {
        final int wordLen = collectedVisible(new StringBuilder(word));
        if (wordLen <= width) {
            // whole word fits on this (possibly empty) line
            line.append(word);
            lineLen[0] += wordLen;
            return;
        }
        // the word alone is wider than the column: split on visible characters
        int visible = 0;
        int start = 0;
        int i = 0;
        final int n = word.length();
        while (i < n) {
            final char c = word.charAt(i);
            if (c == '\u001b') { // zero-width ANSI escape, copy through
                i++;
                while (i < n && word.charAt(i) != 'm') {
                    i++;
                }
                if (i < n) {
                    i++;
                }
                continue;
            }
            visible++;
            if (visible >= width) {
                final var chunk = word.substring(start, i + 1);
                line.append(chunk);
                lineLen[0] += visible;
                if (i + 1 < n) {
                    out.add(line.toString());
                    line.setLength(0);
                    lineLen[0] = 0;
                }
                start = i + 1;
                visible = 0;
            }
            i++;
        }
        final var tail = word.substring(start);
        if (!tail.isEmpty()) {
            line.append(tail);
            lineLen[0] += visible;
        }
    }

    /**
     * Visible length of the accumulated ANSI-capable builder (counts only non-SGR chars).
     */
    private int collectedVisible(final StringBuilder builder) {
        int len = 0;
        int i = 0;
        final int n = builder.length();
        while (i < n) {
            final char c = builder.charAt(i);
            if (c == '\u001b') {
                while (i < n && builder.charAt(i) != 'm') {
                    i++;
                }
                if (i < n) {
                    i++;
                }
            } else {
                len++;
                i++;
            }
        }
        return len;
    }

    private int visibleLength(final String s) {
        return ANSI_SGR.matcher(s).replaceAll("").length();
    }

    /** Length of the longest whitespace-delimited token in a cell (ANS-stripped, 0 if none). */
    private int longestWord(final String s) {
        var max = 0;
        for (final var token : ANSI_SGR.matcher(s).replaceAll("").split("\\s+")) {
            if (token.length() > max) {
                max = token.length();
            }
        }
        return max;
    }

    /**
     * Best-effort terminal width: COLUMNS env, else `stty size`, else a default of 100.
     */
    private int tableWidth() {
        int best = 0;
        final var env = System.getenv("COLUMNS");
        if (env != null) {
            try {
                best = Math.max(best, Integer.parseInt(env.trim()));
            } catch (final NumberFormatException ignored) {
                // ignore invalid value
            }
        }
        // `stty size` reads stdin, which is a pipe when running `cat f | asciidocat`; read the
        // controlling terminal (/dev/tty) so we still learn the real width when stdout is a tty.
        final var tty = new java.io.File("/dev/tty");
        try {
            final var proc = new ProcessBuilder("stty", "size").redirectErrorStream(true).redirectInput(tty).start();
            final var out = new String(proc.getInputStream().readAllBytes()).strip();
            proc.waitFor();
            best = Math.max(best, sttyCols(out));
        } catch (final Exception ignored) {
            // fall back to a plain (stdin-based) stty, else default
        }
        try {
            final var proc = new ProcessBuilder("stty", "size").redirectErrorStream(true).start();
            final var out = new String(proc.getInputStream().readAllBytes()).strip();
            proc.waitFor();
            best = Math.max(best, sttyCols(out));
        } catch (final Exception ignored) {
            // stty not available in this environment
        }
        // the reported/real width can be much larger than the old 100 default; preferring the
        // max of COLUMNS and the tty size guards against under-sizing. Cap to 300.
        return Math.max(40, Math.min(300, best == 0 ? 120 : best));
    }

    private int sttyCols(final String out) {
        final var parts = out.split("\\s+");
        if (parts.length >= 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (final NumberFormatException ignored) {
                // ignore invalid
            }
        }
        return 0;
    }

    private static String ansiStripped(final String s) {
        return ANSI_SGR.matcher(s).replaceAll("");
    }
}

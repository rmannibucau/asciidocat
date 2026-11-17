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

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight language syntax highlighter for fenced code / {@code [source,*lang*]}
 * blocks, ported from the yupiik-coding-agent {@code MarkdownColorizer}. Colors come
 * from the {@link Theme} so they follow the minisite palette.
 */
public final class SyntaxHighlighter {
    private record HL(Pattern pattern) {}

    private static HL language(final List<String> keywords, final List<String> types) {
        final var alt = "(" + String.join("|", keywords) + ")";
        final var altT = "(" + String.join("|", types) + ")";
        final var regex = "(?<str>\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`)"
                + "|(?<com>//[^\\n]*|/\\*.*?\\*/|#[^\\n]*|--[^\\n]*|<!--.*?-->|\\{\\{-?[^\\n]*?}})"
                + "|(?<num>\\b\\d+(?:\\.\\d+)?\\b)"
                + "|(?<tag><\\/?[A-Za-z][A-Za-z0-9:-]*|/?>)"
                + "|(?<attr>[A-Za-z_:][A-Za-z0-9_:.-]*=)"
                + "|(?<kw>" + alt + ")"
                + "|(?<ty>" + altT + ")";
        return new HL(Pattern.compile(regex, Pattern.MULTILINE));
    }

    private static final HL JAVA = language(
            List.of(
                    "public", "private", "protected", "static", "final", "class", "interface", "enum", "record",
                    "void", "return", "if", "else", "for", "while", "do", "switch", "case", "default", "break",
                    "continue", "new", "try", "catch", "finally", "throw", "throws", "extends", "implements",
                    "import", "package", "this", "super", "null", "true", "false", "var", "instanceof",
                    "synchronized", "abstract", "yield", "sealed", "permits", "assert", "native", "transient",
                    "volatile"),
            List.of(
                    "String", "int", "long", "byte", "short", "float", "double", "boolean", "char", "Object",
                    "Integer", "Long", "Boolean", "Double", "Float", "List", "Map", "Set", "Collection", "Optional"));
    private static final HL TS = language(
            List.of(
                    "const", "let", "var", "function", "return", "if", "else", "for", "while", "do", "switch",
                    "case", "default", "break", "continue", "new", "try", "catch", "finally", "throw", "throws",
                    "extends", "implements", "import", "export", "from", "async", "await", "yield", "class",
                    "interface", "enum", "type", "null", "true", "false", "this", "super", "of", "instanceof",
                    "typeof", "delete", "in", "void", "readonly", "static", "public", "private", "protected"),
            List.of(
                    "string", "number", "boolean", "any", "unknown", "never", "void", "object", "Promise",
                    "Array", "Function", "Record", "Date"));
    private static final HL PYTHON = language(
            List.of(
                    "def", "class", "return", "if", "elif", "else", "for", "while", "try", "except", "finally",
                    "with", "as", "import", "from", "pass", "break", "continue", "lambda", "yield", "global",
                    "nonlocal", "raise", "assert", "del", "and", "or", "not", "in", "is", "None", "True", "False"),
            List.of("str", "int", "float", "bool", "list", "dict", "tuple", "set", "bytes", "object", "Exception",
                    "Any", "Optional", "Type"));
    private static final HL SHELL = language(
            List.of(
                    "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac", "function",
                    "in", "return", "exit", "export", "local", "readonly", "echo", "printf", "cd", "ls", "mkdir",
                    "rm", "cp", "mv", "touch", "cat", "grep", "sed", "awk", "find", "sudo", "git", "node", "npm",
                    "mvn", "java"),
            List.of());
    private static final HL SQL = language(
            List.of(
                    "select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create",
                    "table", "drop", "alter", "index", "join", "left", "right", "inner", "outer", "on", "group",
                    "by", "order", "having", "limit", "offset", "and", "or", "not", "in", "like", "between",
                    "is", "null", "as", "distinct", "union", "all", "primary", "key", "foreign", "references",
                    "constraint", "default", "grant", "revoke"),
            List.of("int", "integer", "bigint", "varchar", "text", "char", "date", "datetime", "timestamp",
                    "boolean", "decimal", "numeric", "float", "double", "serial", "uuid"));
    private static final HL GENERIC = language(
            List.of(
                    "return", "if", "else", "for", "while", "switch", "case", "break", "continue", "class",
                    "interface", "enum", "record", "function", "def", "import", "export", "var", "let", "const",
                    "new", "try", "catch", "finally", "throw", "this", "super", "null", "true", "false", "async",
                    "await", "void"),
            List.of());

    private final boolean enabled;
    private final CodeColors colors;

    /**
     * Syntax colors derived from the minisite palette for a given theme.
     *
     * @param str ANSI foreground sequence for strings
     * @param com ANSI foreground sequence for comments
     * @param num ANSI foreground sequence for numbers
     * @param kw ANSI foreground sequence for keywords
     * @param ty ANSI foreground sequence for types
     * @param tag ANSI foreground sequence for markup tags
     * @param attr ANSI foreground sequence for markup attributes
     * @param text ANSI foreground sequence for other text
     */
    public record CodeColors(String str, String com, String num, String kw, String ty, String tag, String attr,
                             String text) {}

    /**
     * Creates the syntax palette for a light or dark theme.
     *
     * @param dark whether to use the dark theme
     * @return ANSI color sequences for each token category
     */
    public static CodeColors colors(final boolean dark) {
        final var blue = Ansi.fg(2, 171, 234);           // brand blue #02ABEA
        final var purple = dark ? Ansi.fg(216, 88, 255)  // brand purple #D858FF
                                : Ansi.fg(123, 47, 190);
        final var muted = dark ? Ansi.fg(154, 164, 178)  // muted for theme
                               : Ansi.fg(98, 104, 115);
        final var text = dark ? Ansi.fg(201, 209, 217)
                              : Ansi.fg(36, 38, 43);
        return new CodeColors(
                blue,               // str
                muted,              // com
                Ansi.fg(255, 175, 90), // num
                purple,             // kw
                blue,               // ty
                Ansi.fg(230, 108, 117), // tag
                Ansi.fg(86, 182, 194), // attr
                text);              // text
    }

    /**
     * Creates a syntax highlighter with the supplied palette.
     *
     * @param enabled whether to emit ANSI highlighting
     * @param colors token color sequences
     */
    public SyntaxHighlighter(final boolean enabled, final CodeColors colors) {
        this.enabled = enabled;
        this.colors = colors;
    }

    /**
     * Highlights code using language-specific rules or the generic fallback.
     *
     * @param lang language name or alias, or {@code null} to use generic rules
     * @param code source text to highlight
     * @return highlighted text, or the original code when highlighting is disabled
     */
    public String highlight(final String lang, final String code) {
        if (!enabled) {
            return code;
        }
        return highlightCode(pick(lang), code);
    }

    private String highlightCode(final HL hl, final String code) {
        final var out = new StringBuilder();
        final var m = hl.pattern().matcher(code);
        var last = 0;
        while (m.find()) {
            if (m.start() > last) {
                out.append(colors.text).append(code, last, m.start()).append(Ansi.RESET);
            }
            out.append(style(m)).append(m.group()).append(Ansi.RESET);
            last = m.end();
        }
        if (last < code.length()) {
            out.append(colors.text).append(code.substring(last)).append(Ansi.RESET);
        }
        return out.length() == 0 ? colors.text + code + Ansi.RESET : out.toString();
    }

    private String style(final Matcher m) {
        if (m.group("str") != null) {
            return colors.str;
        }
        if (m.group("com") != null) {
            return colors.com;
        }
        if (m.group("num") != null) {
            return colors.num;
        }
        if (m.group("tag") != null) {
            return colors.tag;
        }
        if (m.group("attr") != null) {
            return colors.attr;
        }
        if (m.group("kw") != null) {
            return colors.kw;
        }
        return colors.ty;
    }

    private static HL pick(final String lang) {
        if (lang == null || lang.isBlank()) {
            return GENERIC;
        }
        final var l = lang.toLowerCase(Locale.ROOT);
        if (l.contains("java")) {
            return JAVA;
        }
        if (l.contains("shell") || l.contains("bash") || l.equals("sh") || l.equals("zsh")) {
            return SHELL;
        }
        if (l.contains("python") || l.contains("py")) {
            return PYTHON;
        }
        if (l.contains("sql")) {
            return SQL;
        }
        if (l.contains("javascript") || l.contains("typescript") || l.equals("js") || l.equals("ts") || l.equals("json")) {
            return TS;
        }
        return GENERIC;
    }
}

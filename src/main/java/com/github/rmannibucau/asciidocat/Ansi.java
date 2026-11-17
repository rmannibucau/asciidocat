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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Minimal ANSI/VT100 terminal styling helpers.
 * <p>
 * Uses 24-bit truecolor escape sequences which are supported by terminator,
 * gnome-terminal, kitty and most modern terminals.
 */
public final class Ansi {
    /** Resets all terminal styles and colors. */
    public static final String RESET = "\u001b[0m";
    /** Enables bold text. */
    public static final String BOLD = "\u001b[1m";
    /** Enables dim text. */
    public static final String DIM = "\u001b[2m";
    /** Enables italic text. */
    public static final String ITALIC = "\u001b[3m";
    /** Enables underlined text. */
    public static final String UNDERLINE = "\u001b[4m";
    /** Swaps foreground and background colors. */
    public static final String REVERSE = "\u001b[7m";
    /** Enables struck-through text. */
    public static final String STRIKE = "\u001b[9m";

    private Ansi() {
        // no-op
    }

    /**
     * Creates a 24-bit foreground color escape sequence.
     *
     * @param r red component, from 0 to 255
     * @param g green component, from 0 to 255
     * @param b blue component, from 0 to 255
     * @return the ANSI foreground sequence
     */
    public static String fg(final int r, final int g, final int b) {
        return "\u001b[38;2;" + r + ';' + g + ';' + b + 'm';
    }

    /**
     * Creates a 24-bit background color escape sequence.
     *
     * @param r red component, from 0 to 255
     * @param g green component, from 0 to 255
     * @param b blue component, from 0 to 255
     * @return the ANSI background sequence
     */
    public static String bg(final int r, final int g, final int b) {
        return "\u001b[48;2;" + r + ';' + g + ';' + b + 'm';
    }

    /**
     * OSC-8 hyperlink: renders {@code label} clickable to {@code url} (ctrl/cmd+click
     * in terminator, gnome-terminal, kitty...). Not a color code, so it is emitted
     * even when color is disabled; unsupported terminals hide the escapes and show
     * the label as plain text.
     *
     * @param url link target, or {@code null} or blank to return only the label
     * @param label visible text, or {@code null} to use the URL when present
     * @return the OSC-8 link, the plain label, or an empty string when both are absent
     */
    public static String hyperlink(final String url, final String label) {
        if (url == null || url.isBlank()) {
            return label == null ? "" : label;
        }
        return "\u001b]8;;" + url + "\u001b\\" + (label == null ? url : label) + "\u001b]8;;\u001b\\";
    }

    /**
     * Parses a six-digit hexadecimal RGB color, optionally prefixed with {@code #}.
     *
     * @param hex hexadecimal color
     * @return red, green, and blue components in that order
     * @throws IllegalArgumentException if the length or a hexadecimal digit is invalid
     */
    public static int[] rgb(final String hex) {
        final var value = hex.startsWith("#") ? hex.substring(1) : hex;
        if (value.length() != 6) {
            throw new IllegalArgumentException("Expected a 6-digit hex color but got '" + hex + "'");
        }
        return new int[]{
            Integer.parseInt(value.substring(0, 2), 16),
            Integer.parseInt(value.substring(2, 4), 16),
            Integer.parseInt(value.substring(4, 6), 16)
        };
    }

    /**
     * Renders a plain text value with the given ANSI style enabled for its whole
     * length. If {@code enabled} is false, returns {@code value} unchanged.
     *
     * @param enabled whether to emit ANSI styling
     * @param prefix ANSI sequences to prepend
     * @param value text to style
     * @return the styled text followed by a reset, or the original value
     */
    public static String style(final boolean enabled, final String prefix, final String value) {
        return enabled ? prefix + value + RESET : value;
    }

    /**
     * Detects advertised 24-bit color support from {@code COLORTERM} or {@code TERM}.
     *
     * @return whether the environment advertises truecolor support
     */
    public static boolean isTruecolor() {
        final var term = System.getenv("COLORTERM");
        if (term != null) {
            return term.toLowerCase(Locale.ROOT).contains("truecolor") || term.toLowerCase(Locale.ROOT).contains("24bit");
        }
        final var colorTerm = System.getenv("TERM");
        if (colorTerm != null) {
            final var lower = colorTerm.toLowerCase(Locale.ROOT);
            return lower.contains("truecolor") || lower.contains("-direct") || lower.contains("-24bit");
        }
        return false;
    }

    /**
     * Whether ANSI styling should be enabled by default. Color is on whenever the
     * output actually reaches a terminal, i.e. {@code System.console()} is non-null
     * (both stdin and stdout are terminals) OR stdout specifically is a char device
     * (terminal) even if stdin is piped (e.g. `cat file.adoc | asciidocat`).
     * It is forced off when {@code NO_COLOR} is set or {@code TERM} is {@code dumb}.
     *
     * @return whether styling should be enabled for standard output
     */
    public static boolean defaultEnabled() {
        if (System.getenv().containsKey("NO_COLOR")) {
            return false;
        }
        final var term = System.getenv("TERM");
        if (term != null && term.equalsIgnoreCase("dumb")) {
            return false;
        }
        if (System.console() != null) {
            return true;
        }
        return stdoutIsTty();
    }

    private static boolean stdoutIsTty() {
        // Linux: /proc/self/fd/1 is a symlink; a terminal resolves to /dev/pts/N
        // (or /dev/tty), a pipe to 'pipe:[...]', a socket to 'socket:[...]', a
        // redirection to a file path, /dev/null to '/dev/null'.
        try {
            final var p = Path.of("/proc/self/fd/1");
            if (Files.exists(p)) {
                final var target = Files.readSymbolicLink(p).toString();
                return target.startsWith("/dev/pts/") || target.startsWith("/dev/tty");
            }
        } catch (final Exception ignored) {
            // not a symlink readable in this environment; fall through
        }
        return false;
    }
}

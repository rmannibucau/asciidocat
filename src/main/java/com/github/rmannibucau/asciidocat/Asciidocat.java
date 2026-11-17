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

import io.yupiik.asciidoc.parser.Parser;
import io.yupiik.asciidoc.parser.resolver.ContentResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * A cat-like command: parses AsciiDoc (from a file argument or from stdin when
 * piped) and prints it formatted with ANSI styling to the terminal.
 */
public final class Asciidocat {
    private Asciidocat() {
        // no-op
    }

    /**
     * Renders a file or standard input using the requested terminal options.
     *
     * @param args command-line options and optional input file
     * @throws IOException if the input cannot be read
     */
    public static void main(final String... args) throws IOException {
        var color = ColorMode.AUTO;
        Theme.Mode themeMode = Theme.Mode.DARK;
        String input = null;

        for (int i = 0; i < args.length; i++) {
            final var arg = args[i];
            if (arg.startsWith("--color=")) {
                color = parseColor(arg.substring("--color=".length()));
            } else if (arg.startsWith("--theme=")) {
                themeMode = parseTheme(arg.substring("--theme=".length()));
            } else {
                switch (arg) {
                    case "-h", "--help" -> {
                        System.out.println(usage());
                        return;
                    }
                    case "--no-color" -> color = ColorMode.NEVER;
                    case "-c", "--color" -> {
                        if (i + 1 >= args.length) {
                            System.err.println("Missing argument for --color. Use --color=auto|always|never.");
                            System.exit(2);
                        }
                        color = parseColor(args[++i]);
                    }
                    case "-t", "--theme" -> {
                        if (i + 1 >= args.length) {
                            System.err.println("Missing argument for --theme. Use --theme=light|dark.");
                            System.exit(2);
                        }
                        themeMode = parseTheme(args[++i]);
                    }
                    default -> {
                        if (input != null) {
                            System.err.println(usage());
                            System.exit(2);
                        }
                        input = arg;
                    }
                }
            }
        }

        final var useColor = switch (color) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> Ansi.defaultEnabled();
        };

        final String content;
        final Path base;
        if (input == null) {
            content = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            base = Path.of("").toAbsolutePath();
        } else {
            final var path = Path.of(input);
            base = path.toAbsolutePath().getParent();
            content = Files.readString(path, StandardCharsets.UTF_8);
        }

        final var parser = new Parser();
        final var resolver = base == null ? ContentResolver.of(Path.of("").toAbsolutePath()) : ContentResolver.of(base);
        final var document = parser.parse(content, new Parser.ParserContext(resolver));

        final var renderer = new PlainTextRenderer(useColor, Theme.of(themeMode));
        renderer.visit(document);
        System.out.print(renderer.result());
        if (renderer.result().isEmpty() || !renderer.result().endsWith("\n")) {
            System.out.println();
        }
    }

    private static ColorMode parseColor(final String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "auto" -> ColorMode.AUTO;
            case "always" -> ColorMode.ALWAYS;
            case "never" -> ColorMode.NEVER;
            default -> {
                System.err.println("Unknown color mode '" + value + "' (expected auto|always|never).");
                System.exit(2);
                yield null;
            }
        };
    }

    private static Theme.Mode parseTheme(final String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "light" -> Theme.Mode.LIGHT;
            case "dark" -> Theme.Mode.DARK;
            default -> {
                System.err.println("Unknown theme '" + value + "' (expected light|dark).");
                System.exit(2);
                yield null;
            }
        };
    }

    private static String usage() {
        return """
            Usage:
              asciidocat [options] [file.adoc]

            Reads AsciiDoc from a file argument, or from standard input when no
            file is given (e.g. `cat file.adoc | asciidocat`), and prints it
            formatted with ANSI colors and styling.

            Options:
              -h, --help            show this help
              -c, --color MODE      color mode: auto (default), always, never
                  --no-color        shorthand for --color never
              -t, --theme MODE      color theme: dark (default) or light
            """;
    }

    private enum ColorMode { AUTO, ALWAYS, NEVER }
}

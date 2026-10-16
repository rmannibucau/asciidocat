# asciidocat

A `cat`-like command that parses AsciiDoc and prints it formatted with ANSI
colors and styling directly in your terminal.

It wraps the `io.yupiik.maven:asciidoc-java` AsciiDoc engine and adds a custom
plain-text renderer (a `Visitor<String>`) that turns the parsed document into
styled terminal output. The color palette is inherited from the
[Yupiik minisite theme](https://github.com/yupiik/tools-maven-plugin/blob/master/minisite-core/src/main/resources/yupiik-tools-maven-plugin/minisite/assets/css/theme.css).

## Build

```bash
mvn package
```

This produces a runnable fat jar:

```bash
target/asciidocat-0.0.1-SNAPSHOT-all.jar
```

## Usage

Read AsciiDoc from a file argument:

```bash
java -jar target/asciidocat-0.0.1-SNAPSHOT-all.jar doc.adoc
```

Or from standard input (piped):

```bash
cat doc.adoc | java -jar target/asciidocat-0.0.1-SNAPSHOT-all.jar
```

Or pipe a remote file:

```bash
curl -s https://example.com/doc.adoc | java -jar target/asciidocat-0.0.1-SNAPSHOT-all.jar
```

## Options

| Option             | Description                                              |
|--------------------|----------------------------------------------------------|
| `-h`, `--help`     | Show usage.                                              |
| `-c`, `--color`    | Color mode: `auto` (default), `always`, `never`.         |
| `--color=...`      | Same as above with `=` syntax.                           |
| `--no-color`       | Shorthand for `--color never`.                           |
| `-t`, `--theme`    | Color theme: `dark` (default) or `light`.                |
| `--theme=...`      | Same as above with `=` syntax.                           |

Color is enabled by default when output is a terminal (TTY) and disabled when
it is piped, so piping into another program stays clean. Use `--color=always`
to force color while piping, or `--no-color` to always disable it.

## Rendering

The renderer styles the common AsciiDoc constructs:

- Headings: `#`, `##`, `###` markers with bold colored text.
- Inline text: `*bold*`, `_italic_`, `_*emphasis*_`, `` `code` ``,
  `[line-through]#struck#` (strikethrough), `[mark]#highlight#`.
- Links: `link:https://example.com[text]` renders an underlined colored label
  followed by the URL in muted text.
- Code blocks and listings on a distinct background.
- Bullet and ordered lists.
- Tables with padded columns and a bold header row.
- Admonitions: `[NOTE]`, `[TIP]`, `[WARNING]`, `[CAUTION]`, `[IMPORTANT]` with
  their themed accent colors.
- Block quotes with a left border.

## Example

The repository includes a `sample.adoc` you can run through the tool:

```bash
java -jar target/asciidocat-0.0.1-SNAPSHOT-all.jar --color=always sample.adoc
```
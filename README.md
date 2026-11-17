# asciidocat

A `cat`-like command that parses AsciiDoc and prints it formatted with ANSI
colors and styling directly in your terminal.

It wraps the `io.yupiik.maven:asciidoc-java` AsciiDoc engine and adds a custom
plain-text renderer (a `Visitor<String>`) that turns the parsed document into
styled terminal output. The color palette is inherited from the
[Yupiik minisite theme](https://github.com/yupiik/tools-maven-plugin/blob/master/minisite-core/src/main/resources/yupiik-tools-maven-plugin/minisite/assets/css/theme.css).

## Build

Use JDK 17 and Maven 3.9 or newer (Maven 4 is not supported by the Central publishing plugin).

```bash
mvn package
```

This produces a runnable fat jar:

```bash
target/asciidocat-0.0.1-SNAPSHOT-all.jar
```

### Native executable

The `native` profile builds a standalone executable with Apache Geronimo Arthur:

```bash
mvn -Pnative package
./target/asciidocat --help
./target/asciidocat doc.adoc
cat doc.adoc | ./target/asciidocat
```

Keep Maven running on JDK 17; Arthur uses a separate GraalVM toolchain for native compilation.
By default Arthur downloads and caches GraalVM Community 25.0.2 in the local Maven repository.
Native compilation also requires the platform's C compiler and development libraries (for example GCC, libc headers, and zlib development files on Linux, Xcode command-line tools on macOS, or the GraalVM-supported Visual Studio build tools on Windows).
No installation or deployment is performed by the commands above.

To use an existing GraalVM installation instead of downloading one:

```bash
mvn -Pnative package -Darthur.nativeImage=/path/to/graalvm/bin/native-image
```

The download version can be changed with `-Dgraalvm.version=25.0.2-graalce`.
Arthur's automatic download selection targets x64; use an existing matching toolchain or `-Darthur.graalDownloadUrl=...` on other architectures.
On Windows the executable is `target/asciidocat.exe`.

The image uses size optimization (`-Os`), a compatibility CPU target, dynamic linking, and no JVM fallback.
It does not enable HTTP/HTTPS protocols, all security services, all charsets, or broad reflection/resource registrations.
UTF-8 is supported; uncommon encodings requested by AsciiDoc includes may require additional charset configuration.
URLs can still be rendered as text, and remote documents can be piped through `curl` as with the JAR.
Build separately for each OS/architecture; dynamic linking also requires a compatible libc and runtime libraries on the destination machine.

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

## License

Apache License, Version 2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Releasing to Maven Central

Releases use the Sonatype Central Portal, not the legacy OSSRH staging service.
The release configuration publishes the ordinary JAR, executable `-all.jar`, native executable, sources, Javadoc, and POM with GPG signatures.
Native compilation runs automatically during `package` in the `release` profile, before signing at `verify`, and a compilation failure fails the release.
The native executable is attached as a `.bin` artifact with classifier `native-<os>-<arch>`, for example `asciidocat-0.0.1-native-linux-amd64.bin`.
The OS names are `linux`, `macos`, and `windows`; the architecture is Maven's `${os.arch}` value from the JVM running Maven, which must match the native toolchain's architecture.
Downloaded Unix binaries may need `chmod +x`; rename the Windows `.bin` download to `.exe` to run it.
Each release build publishes only its build platform's executable, not a cross-platform set of binaries.
The Central publishing plugin generates checksums, automatically publishes validated artifacts, and waits for publication to complete.
No separate staging close/release command or manual Portal approval is required.

### One-time local setup

- Use JDK 17 and Maven 3.9 or newer, and have Git and GPG available.
- Have the native compiler prerequisites described above available; native compilation is mandatory for releases.
- If using an existing GraalVM installation, set `arthur.nativeImage` in the active settings profile below so forked release builds use it too.
- Ensure your Central Portal account has a verified namespace covering `com.github.rmannibucau`.
- Generate a [Central Portal user token](https://central.sonatype.org/publish/generate-portal-token/) and put its username and password in your private `~/.m2/settings.xml` under server ID `central`.
- Ensure Git can push to `https://github.com/rmannibucau/asciidocat.git` using your credential helper.
- Have a GPG secret key available and publish its public key as described in the [Central GPG requirements](https://central.sonatype.org/publish/requirements/gpg/).
- Ensure the GPG agent/pinentry can unlock the key, including from Maven's forked release build.
  On a Unix terminal, `export GPG_TTY=$(tty)` can be needed for pinentry.

Merge this example into your settings; do not commit real tokens, passphrases, or secret keys:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <servers>
    <server>
      <id>central</id>
      <username>${env.CENTRAL_TOKEN_USERNAME}</username>
      <password>${env.CENTRAL_TOKEN_PASSWORD}</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>asciidocat-release-settings</id>
      <properties>
        <!-- Optional: select a key if GPG's default is not the release key. -->
        <gpg.keyname>YOUR_SIGNING_KEY_FINGERPRINT</gpg.keyname>
        <!-- Optional: use a different existing settings server ID. -->
        <central.publishing.server.id>central</central.publishing.server.id>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>asciidocat-release-settings</activeProfile>
  </activeProfiles>
</settings>
```

Export the token environment variables if using this example, and replace or remove the optional key fingerprint before running Maven.
The settings profile is active in the forked release build as well as the initial invocation.
Prefer GPG agent/pinentry for the key passphrase; do not put it on the command line or in the POM.

### Local checks before release

These commands build locally without pushing or publishing:

```bash
mvn -Prelease -Dgpg.skip=true clean verify
java -jar target/asciidocat-0.0.1-SNAPSHOT-all.jar --help
./target/asciidocat --help
```

The first command exercises release packaging, native compilation, and Javadoc generation but deliberately skips signing.
To check signing too, repeat it without `-Dgpg.skip=true`.
Neither command deploys artifacts.
Check that the regular, `all`, `sources`, and `javadoc` JARs are present and that `META-INF/LICENSE` and `META-INF/NOTICE` are included in the regular and executable JARs.
Check that `target/asciidocat` (or `target/asciidocat.exe`) runs successfully as well.

### Publish a release

Commit and push the preparation changes first, and start with a clean working tree on the intended release branch.
Then run:

```bash
mvn release:prepare release:perform
```

Accept or adjust the prompted release version, tag, and next development version.
For the current POM the defaults are release `0.0.1`, tag `asciidocat-0.0.1`, and next development version `0.0.2-SNAPSHOT`.

`release:prepare` builds and signs using the release profile before creating the release commit and tag, then commits the next development version and pushes the Git changes.
`release:perform` checks out that tag into `target/checkout`, builds and signs it, then publishes through the Central Portal using the same release profile.
Normal `mvn package` builds do not require GPG or Central credentials.
There is intentionally no legacy `distributionManagement` release URL: the Central publishing extension handles deployment.

**Warning:** this command creates Git commits, pushes commits and a tag, and publishes immutable artifacts to Central.
If publication fails or times out, inspect the deployment in the Central Portal before retrying; a timeout does not necessarily mean the release was not published.
If preparation succeeded but performance failed, fix the cause and rerun `mvn release:perform` rather than preparing another release.
Do not delete release bookkeeping with `release:clean` until it is no longer needed for recovery.

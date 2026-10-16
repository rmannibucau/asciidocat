package com.github.rmannibucau.asciidocat;

/**
 * Terminal color theme modeled on the Yupiik minisite palette.
 *
 * Source: yupiik-tools-maven-plugin minisite assets/css/theme.css
 *   light  = theme.css ':root'
 *   dark   = theme.css 'prefers-color-scheme: dark'
 */
public final class Theme {
    public enum Mode { LIGHT, DARK }

    public static Theme of(final Mode mode) {
        return mode == Mode.DARK ? DARK : LIGHT;
    }

    private static final int[] BRAND_BLUE = Ansi.rgb("#02ABEA");
    private static final int[] BRAND_BLUE_DARK = Ansi.rgb("#056EBB");

    public static final Theme LIGHT = new Theme(false,
        BRAND_BLUE_DARK,       // link (#056EBB)
        Ansi.rgb("#04558f"),   // code-text
        Ansi.rgb("#626873"),   // muted
        Ansi.rgb("#24262b"),   // text/heading
        Ansi.rgb("#20242c"),   // pre-bg
        Ansi.rgb("#e2e8f0"),   // pre-text
        Ansi.rgb("#448aff"),   // admon note/important
        Ansi.rgb("#00a400"),   // admon tip
        Ansi.rgb("#e6a700"),   // admon warning
        Ansi.rgb("#e13238")    // admon caution
    );

    public static final Theme DARK = new Theme(true,
        BRAND_BLUE,            // link (#02ABEA)
        Ansi.rgb("#D858FF"),   // code-text (purple in dark)
        Ansi.rgb("#9aa4b2"),   // muted
        Ansi.rgb("#f2f5f9"),   // text/heading
        Ansi.rgb("#0d1117"),   // pre-bg
        Ansi.rgb("#c9d1d9"),   // pre-text
        Ansi.rgb("#448aff"),   // admon note/important
        Ansi.rgb("#00a400"),   // admon tip
        Ansi.rgb("#e6a700"),   // admon warning
        Ansi.rgb("#e13238")    // admon caution
    );

    final int[] link;
    final int[] code;
    final int[] muted;
    final int[] heading;
    final int[] preBg;
    final int[] preText;
    final int[] admonNote;
    final int[] admonTip;
    final int[] admonWarn;
    final int[] admonCaution;
    final boolean dark;

    private Theme(final boolean dark, final int[] link, final int[] code, final int[] muted, final int[] heading,
                  final int[] preBg, final int[] preText, final int[] admonNote, final int[] admonTip,
                  final int[] admonWarn, final int[] admonCaution) {
        this.dark = dark;
        this.link = link;
        this.code = code;
        this.muted = muted;
        this.heading = heading;
        this.preBg = preBg;
        this.preText = preText;
        this.admonNote = admonNote;
        this.admonTip = admonTip;
        this.admonWarn = admonWarn;
        this.admonCaution = admonCaution;
    }

    /** Heading line: prefix (#, ##, ...) plain, then bold colored title. */
    public String heading(final int level, final boolean color, final String text) {
        final var prefix = "#".repeat(Math.min(6, Math.max(1, level))) + ' ';
        if (!color) {
            return prefix + text;
        }
        // the brand blue (same accent as links) makes headings stand out on the palette,
        // applied to the #-prefix as well as the title text
        return Ansi.BOLD + Ansi.fg(link[0], link[1], link[2]) + prefix + text + Ansi.RESET;
    }
}
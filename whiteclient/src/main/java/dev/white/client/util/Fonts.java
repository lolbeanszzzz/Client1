package dev.white.client.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Custom menu fonts (assets/whiteclient/font/*.json -> TTF files, SIL Open Font License, see font/OFL.txt):
 * Instrument Sans for the UI text and Instrument Serif (italic for the accent word) for headings.
 * Every font JSON falls back to the vanilla font for glyphs the TTF does not have.
 */
public final class Fonts {
    private Fonts() {}

    private static Style style(String name) {
        return Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("whiteclient", name)));
    }

    public static final Style SANS = style("sans");
    public static final Style SANS_BOLD = style("sans_bold");
    public static final Style SERIF = style("serif");
    public static final Style SERIF_ITALIC = style("serif_italic");

    public static MutableText sans(String s) { return Text.literal(s).setStyle(SANS); }
    public static MutableText bold(String s) { return Text.literal(s).setStyle(SANS_BOLD); }
    public static MutableText serif(String s) { return Text.literal(s).setStyle(SERIF); }
    public static MutableText serifItalic(String s) { return Text.literal(s).setStyle(SERIF_ITALIC); }
}

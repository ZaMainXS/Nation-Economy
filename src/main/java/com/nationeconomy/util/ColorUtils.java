package com.nationeconomy.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Helpers for colors: legacy "&" formatting codes (including "&#RRGGBB" hex
 * colors), named colors and conversion to Minecraft {@link Formatting}.
 */
public final class ColorUtils {

    private ColorUtils() {
    }

    /** Every named color a nation can pick, plus hex colors (#RRGGBB) are also accepted. */
    private static final Map<String, Integer> NAMED_COLORS = new LinkedHashMap<>();

    static {
        // The 16 vanilla colors
        NAMED_COLORS.put("black", 0x000000);
        NAMED_COLORS.put("dark_blue", 0x0000AA);
        NAMED_COLORS.put("dark_green", 0x00AA00);
        NAMED_COLORS.put("dark_aqua", 0x00AAAA);
        NAMED_COLORS.put("dark_red", 0xAA0000);
        NAMED_COLORS.put("dark_purple", 0xAA00AA);
        NAMED_COLORS.put("gold", 0xFFAA00);
        NAMED_COLORS.put("gray", 0xAAAAAA);
        NAMED_COLORS.put("grey", 0xAAAAAA);
        NAMED_COLORS.put("dark_gray", 0x555555);
        NAMED_COLORS.put("dark_grey", 0x555555);
        NAMED_COLORS.put("blue", 0x5555FF);
        NAMED_COLORS.put("green", 0x55FF55);
        NAMED_COLORS.put("aqua", 0x55FFFF);
        NAMED_COLORS.put("cyan", 0x55FFFF);
        NAMED_COLORS.put("red", 0xFF5555);
        NAMED_COLORS.put("light_purple", 0xFF55FF);
        NAMED_COLORS.put("pink", 0xFF55FF);
        NAMED_COLORS.put("magenta", 0xFF55FF);
        NAMED_COLORS.put("yellow", 0xFFFF55);
        NAMED_COLORS.put("white", 0xFFFFFF);
        // Extra popular aliases
        NAMED_COLORS.put("orange", 0xFF8800);
        NAMED_COLORS.put("purple", 0xAA00AA);
        NAMED_COLORS.put("lime", 0x55FF55);
        NAMED_COLORS.put("brown", 0x8B4513);
        NAMED_COLORS.put("crimson", 0xDC143C);
        NAMED_COLORS.put("navy", 0x000080);
        NAMED_COLORS.put("teal", 0x008080);
        NAMED_COLORS.put("olive", 0x808000);
        NAMED_COLORS.put("maroon", 0x800000);
        NAMED_COLORS.put("silver", 0xC0C0C0);
        NAMED_COLORS.put("violet", 0x8F00FF);
        NAMED_COLORS.put("indigo", 0x4B0082);
        NAMED_COLORS.put("turquoise", 0x40E0D0);
        NAMED_COLORS.put("salmon", 0xFA8072);
        NAMED_COLORS.put("khaki", 0xF0E68C);
        NAMED_COLORS.put("coral", 0xFF7F50);
    }

    /** All selectable color names, used for command suggestions. */
    public static Map<String, Integer> namedColors() {
        return NAMED_COLORS;
    }

    /**
     * Parses a color from user input. Accepts:
     * <ul>
     *     <li>named colors: {@code red}, {@code dark_blue}, ...</li>
     *     <li>hex colors: {@code #rrggbb} or plain {@code rrggbb}</li>
     * </ul>
     *
     * @return the RGB value, or {@code null} when the input is not a known color.
     */
    @Nullable
    public static Integer parseColor(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        Integer named = NAMED_COLORS.get(normalized);
        if (named != null) {
            return named;
        }
        String hex = normalized.startsWith("#") ? normalized.substring(1) : normalized;
        if (hex.matches("[0-9a-f]{6}")) {
            return Integer.parseInt(hex, 16);
        }
        return null;
    }

    /** Creates a {@link TextColor} from an RGB int. */
    public static TextColor rgb(int rgb) {
        return TextColor.fromRgb(rgb & 0xFFFFFF);
    }

    /** Simple one-shot: literal text with the given RGB color. */
    public static MutableText colored(String text, int rgb) {
        return Text.literal(text).styled(style -> style.withColor(rgb(rgb)));
    }

    /** Simple one-shot: literal text colored with a vanilla {@link Formatting}. */
    public static MutableText formatted(String text, Formatting formatting) {
        return Text.literal(text).formatted(formatting);
    }

    /**
     * Translates legacy formatting codes into styled {@link Text}.
     *
     * <p>Supports {@code &0-&9 &a-&f} colors, {@code &l &o &n &m &k} styles,
     * {@code &r} reset and hex colors in the form {@code &#RRGGBB}.
     */
    public static MutableText legacy(String raw) {
        if (raw == null) {
            return Text.empty();
        }
        MutableText result = Text.empty();
        StringBuilder segment = new StringBuilder();
        Style style = Style.EMPTY;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length()) {
                char next = raw.charAt(i + 1);
                // Hex color: &#RRGGBB
                if (next == '#' && i + 7 < raw.length() + 1 && raw.length() >= i + 8) {
                    String hex = raw.substring(i + 2, Math.min(i + 8, raw.length()));
                    if (hex.matches("(?i)[0-9a-f]{6}")) {
                        if (segment.length() > 0) {
                            result.append(Text.literal(segment.toString()).setStyle(style));
                            segment.setLength(0);
                        }
                        style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(hex, 16)));
                        i += 7;
                        continue;
                    }
                }
                Formatting fmt = Formatting.byCode(next);
                if (fmt != null) {
                    if (segment.length() > 0) {
                        result.append(Text.literal(segment.toString()).setStyle(style));
                        segment.setLength(0);
                    }
                    if (fmt.isColor()) {
                        style = Style.EMPTY.withColor(fmt);
                    } else if (fmt == Formatting.BOLD) {
                        style = style.withBold(true);
                    } else if (fmt == Formatting.ITALIC) {
                        style = style.withItalic(true);
                    } else if (fmt == Formatting.UNDERLINE) {
                        style = style.withUnderline(true);
                    } else if (fmt == Formatting.STRIKETHROUGH) {
                        style = style.withStrikethrough(true);
                    } else if (fmt == Formatting.OBFUSCATED) {
                        style = style.withObfuscated(true);
                    } else if (fmt == Formatting.RESET) {
                        style = Style.EMPTY;
                    }
                    i++;
                    continue;
                }
            }
            segment.append(c);
        }
        if (segment.length() > 0) {
            result.append(Text.literal(segment.toString()).setStyle(style));
        }
        return result;
    }

    /** Removes legacy "&" formatting codes from a string. */
    public static String stripLegacy(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length()) {
                char next = raw.charAt(i + 1);
                if (next == '#' && i + 7 < raw.length() + 1 && raw.length() >= i + 8
                        && raw.substring(i + 2, Math.min(i + 8, raw.length())).matches("(?i)[0-9a-f]{6}")) {
                    i += 7;
                    continue;
                }
                if (Formatting.byCode(next) != null) {
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * Finds the closest vanilla {@link Formatting} color for an arbitrary RGB
     * value. Used for scoreboard team colors, which only support the 16
     * vanilla colors.
     */
    public static Formatting nearestFormatting(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        Formatting best = Formatting.WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (Formatting formatting : Formatting.values()) {
            if (!formatting.isColor() || formatting.getColorValue() == null) {
                continue;
            }
            int value = formatting.getColorValue();
            int fr = (value >> 16) & 0xFF;
            int fg = (value >> 8) & 0xFF;
            int fb = value & 0xFF;
            long distance = (long) (fr - r) * (fr - r) + (long) (fg - g) * (fg - g) + (long) (fb - b) * (fb - b);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = formatting;
            }
        }
        return best;
    }
}

package com.nationeconomy.nation;

import java.util.List;
import java.util.Locale;

/**
 * Nation-name safety filter. Normalizes the input (leetspeak, punctuation)
 * and rejects names containing slurs or profanity.
 */
public final class NameFilter {

    /** Substrings that must never appear in a nation name. */
    private static final List<String> BANNED = List.of(
            // slurs (incl. the n-word & hard r, as requested)
            "nigger", "nigga", "niga", "n1g", "negro", "faggot", "fag", "retard",
            "kike", "chink", "spic", "wetback", "beaner", "gook", "tranny",
            "nazi", "hitler", "kkk",
            // profanity
            "fuck", "shit", "bitch", "cunt", "whore", "slut", "bastard",
            "dick", "pussy", "cock", "rape", "rapist", "pedo", "porn");

    private NameFilter() {
    }

    /**
     * @return {@code true} when the name is acceptable.
     */
    public static boolean isClean(String name) {
        String normalized = normalize(name);
        for (String banned : BANNED) {
            if (normalized.contains(banned)) {
                return false;
            }
        }
        return true;
    }

    /** Lowercases, swaps common leetspeak and strips everything non-alphanumeric. */
    private static String normalize(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (char c : lower.toCharArray()) {
            switch (c) {
                case '0' -> out.append('o');
                case '1', '!', '|' -> out.append('i');
                case '3' -> out.append('e');
                case '4', '@' -> out.append('a');
                case '5', '$' -> out.append('s');
                case '7' -> out.append('t');
                case '8' -> out.append('b');
                case '6' -> out.append('g');
                case '9' -> out.append('g');
                case '+' -> out.append('t');
                case '(' -> out.append('c');
                default -> {
                    if (Character.isLetterOrDigit(c)) {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}

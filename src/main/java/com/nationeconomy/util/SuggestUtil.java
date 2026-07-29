package com.nationeconomy.util;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Replacement for the removed SharedSuggestionProvider.suggestMatching helper. */
public final class SuggestUtil {

    private SuggestUtil() {
    }

    /** Suggests every option that starts with the text already typed. */
    public static CompletableFuture<Suggestions> suggestMatching(Iterable<String> options, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }
}

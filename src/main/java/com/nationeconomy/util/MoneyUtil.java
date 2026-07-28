package com.nationeconomy.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Money formatting used by the whole economy. */
public final class MoneyUtil {

    public static final String CURRENCY_SYMBOL = "$";

    private static final DecimalFormat FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        FORMAT = new DecimalFormat("#,##0.##", symbols);
    }

    private MoneyUtil() {
    }

    /** Formats an amount as e.g. {@code $1,234.50}. */
    public static synchronized String format(double amount) {
        if (amount < 0.005 && amount > -0.005) {
            amount = 0;
        }
        return CURRENCY_SYMBOL + FORMAT.format(amount);
    }
}

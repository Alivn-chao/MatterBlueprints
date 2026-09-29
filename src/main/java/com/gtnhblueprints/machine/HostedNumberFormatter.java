package com.gtnhblueprints.machine;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps very large host statistics readable in narrow GT/WAILA panels. */
final class HostedNumberFormatter {

    private static final BigInteger SCIENTIFIC_THRESHOLD = BigInteger.valueOf(1_000_000L);
    private static final Pattern LARGE_INTEGER = Pattern.compile("(?<![\\w.#-])-?\\d[\\d,]{6,}(?![\\w.])");

    private HostedNumberFormatter() {}

    static String amperage(long power, long voltage) {
        if (voltage <= 0) return "—";
        return BigDecimal.valueOf(Math.max(0L, power)).divide(BigDecimal.valueOf(voltage), 2, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString();
    }

    static String format(long value) {
        return format(BigInteger.valueOf(value));
    }

    static String format(BigInteger value) {
        if (value == null) return "0";
        if (value.abs().compareTo(SCIENTIFIC_THRESHOLD) < 0) return value.toString();

        int exponent = value.abs().toString().length() - 1;
        BigDecimal mantissa = new BigDecimal(value).movePointLeft(exponent).setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros();
        if (mantissa.abs().compareTo(BigDecimal.TEN) >= 0) {
            mantissa = mantissa.movePointLeft(1);
            exponent++;
        }
        return mantissa.toPlainString() + "×10^" + exponent;
    }

    static String compactLargeIntegers(String input) {
        if (input == null || input.isEmpty()) return input;
        Matcher matcher = LARGE_INTEGER.matcher(input);
        StringBuffer compacted = new StringBuffer(input.length());
        while (matcher.find()) {
            String digits = matcher.group().replace(",", "");
            matcher.appendReplacement(compacted, Matcher.quoteReplacement(format(new BigInteger(digits))));
        }
        matcher.appendTail(compacted);
        return compacted.toString();
    }
}

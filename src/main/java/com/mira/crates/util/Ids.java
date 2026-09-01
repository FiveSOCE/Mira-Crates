package com.mira.crates.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Ids {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{1,48}");

    private Ids() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public static boolean valid(String value) {
        return VALID.matcher(normalize(value)).matches();
    }
}

package ru.gft.decentralizedmessenger.util;

import java.util.regex.Pattern;

/** Replacement for api.utils.Other.validate_target. */
public final class Validate {
    private Validate() {}

    private static final Pattern DOMAIN =
            Pattern.compile("^(?!-)[a-zA-Z0-9-]{1,63}(?<!-)(\\.(?!-)[a-zA-Z0-9-]{1,63}(?<!-))+$");
    private static final Pattern IPV4 =
            Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    public static boolean validateTarget(String address) {
        if (address == null || address.isEmpty()) return false;
        if (IPV4.matcher(address).matches()) {
            for (String part : address.split("\\.")) {
                int v = Integer.parseInt(part);
                if (v < 0 || v > 255) return false;
            }
            return true;
        }
        return DOMAIN.matcher(address).matches();
    }
}

package com.siose.ff.base.tenant;

import java.util.regex.Pattern;

/**
 * The one rule for tenant ids: 2 to 64 characters, lower-case letters, digits and hyphens,
 * starting with a letter or a digit (for example {@code ff-eu-freight}).
 *
 * <p>An id that passes is used as it is everywhere: in database rows, object-storage keys, SQL
 * row-level-security clauses and log fields. Nothing downstream has to clean it, so two different
 * tenants can never end up with the same key or clause. An id that fails is refused where it
 * enters: the access token, or the local-development header.
 */
public final class TenantIds {

    public static final Pattern FORMAT = Pattern.compile("[a-z0-9][a-z0-9-]{1,63}");

    private TenantIds() {
    }

    public static boolean isValid(String tenantId) {
        return tenantId != null && FORMAT.matcher(tenantId).matches();
    }

    public static String require(String tenantId) {
        if (!isValid(tenantId)) {
            throw new IllegalArgumentException(
                    "A tenant id has 2 to 64 lower-case letters, digits or hyphens and starts with a letter or digit");
        }
        return tenantId;
    }
}

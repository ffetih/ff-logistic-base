package com.siose.ff.base.storage;

import com.siose.ff.base.storage.StorageObjectDescriptor.StorageMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * The one place that decides object key layout, so every adapter writes the same keys.
 * Key: {@code <tenant>/<category>/<referenceId>/<fileName>}, each part restricted to safe characters.
 */
final class ObjectKeys {

    private ObjectKeys() {
    }

    static StorageObjectDescriptor describe(
            String bucket,
            String tenantId,
            String category,
            String referenceId,
            String fileName,
            String contentType,
            StorageMode mode
    ) {
        String objectKey = String.join("/",
                segment(tenantId, "tenantId"),
                segment(category, "category"),
                segment(referenceId, "referenceId"),
                fileName(fileName));
        return new StorageObjectDescriptor(
                tenantId,
                bucket,
                objectKey,
                segment(category, "category"),
                contentType,
                mode,
                "s3://%s/%s".formatted(bucket, objectKey),
                Instant.now()
        );
    }

    /** Lower-case letters, digits and hyphens: no slashes, so a value cannot escape its prefix. */
    static String segment(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        String safe = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        if (safe.isBlank() || safe.chars().allMatch(c -> c == '-')) {
            throw new IllegalArgumentException(name + " has no usable characters: " + value);
        }
        return safe;
    }

    /** File names keep their case and dots, but never path separators or "..". */
    static String fileName(String value) {
        Objects.requireNonNull(value, "fileName must not be null");
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_").replace("..", "_");
        if (safe.isBlank() || safe.equals(".")) {
            throw new IllegalArgumentException("fileName has no usable characters: " + value);
        }
        return safe;
    }
}

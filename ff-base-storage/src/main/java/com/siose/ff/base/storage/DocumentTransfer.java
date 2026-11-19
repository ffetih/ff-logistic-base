package com.siose.ff.base.storage;

import java.net.URI;
import java.util.Map;

/**
 * A short-lived address the client uses to send or fetch a document directly from object storage.
 *
 * @param method          {@code PUT} to upload, {@code GET} to download
 * @param validForSeconds how long {@code url} stays valid
 * @param contentType     the {@code Content-Type} the upload must send; also the type of the stored object
 * @param headers         every header the request must carry exactly as given, because the link
 *                        is signed with them (upload: {@code Content-Type} and {@code If-None-Match})
 */
public record DocumentTransfer(
        URI url, String method, long validForSeconds, String contentType, Map<String, String> headers) {

    public DocumentTransfer {
        headers = Map.copyOf(headers);
    }
}

package com.siose.ff.base.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siose.ff.base.tenant.TenantAccessException;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DocumentLinksTest {

    private final ReferenceOnlyStorageAdapter storage = new ReferenceOnlyStorageAdapter("ff-documents");

    @Test
    void refusesADocumentOutsideTheCallerTenant() {
        StorageObjectDescriptor foreign = storage.registerDocumentReference(
                "ff-tr-distribution", "ecmr", "ECMR-1", "note.pdf", "application/pdf");

        assertThatThrownBy(() -> DocumentLinks.download(
                storage, "ff-eu-freight", foreign.objectUri(), "application/pdf"))
                .isInstanceOf(TenantAccessException.class);
        assertThatThrownBy(() -> DocumentLinks.upload(
                storage, "ff-eu-freight", foreign.objectUri(), "application/pdf"))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    void ownDocumentReachesTheStore() {
        StorageObjectDescriptor own = storage.registerDocumentReference(
                "ff-eu-freight", "ecmr", "ECMR-2", "note.pdf", "application/pdf");

        // Reference-only storage has no bytes to sign for: the tenant check passed, the store refuses.
        assertThatThrownBy(() -> DocumentLinks.download(
                storage, "FF-EU-Freight", own.objectUri(), "application/pdf"))
                .isInstanceOf(ObjectStoreUnavailableException.class);
    }

    @Test
    void anUploadLinkIsIssuedOnceAndCarriesTheWriteOnceHeader() {
        AlreadyStoredOnce store = new AlreadyStoredOnce();
        StorageObjectDescriptor own = store.registerDocumentReference(
                "ff-eu-freight", "ecmr", "ECMR-3", "note.pdf", "application/pdf");

        DocumentTransfer first = DocumentLinks.upload(store, "ff-eu-freight", own.objectUri(), "application/pdf");
        assertThat(first.method()).isEqualTo("PUT");
        assertThat(first.headers())
                .containsEntry("Content-Type", "application/pdf")
                .containsEntry("If-None-Match", "*");

        store.uploaded = true;
        assertThatThrownBy(() -> DocumentLinks.upload(store, "ff-eu-freight", own.objectUri(), "application/pdf"))
                .isInstanceOf(DocumentAlreadyStoredException.class);
        assertThat(DocumentLinks.download(store, "ff-eu-freight", own.objectUri(), "application/pdf").headers())
                .as("a stored document can still be read")
                .isEmpty();
    }

    @Test
    void noDownloadLinkForADocumentNotYetUploaded() {
        AlreadyStoredOnce store = new AlreadyStoredOnce();
        StorageObjectDescriptor own = store.registerDocumentReference(
                "ff-eu-freight", "ecmr", "ECMR-4", "note.pdf", "application/pdf");

        assertThatThrownBy(() -> DocumentLinks.download(store, "ff-eu-freight", own.objectUri(), "application/pdf"))
                .isInstanceOf(DocumentNotStoredException.class);
    }

    @Test
    void linksStayValidForFifteenMinutes() {
        assertThat(DocumentLinks.VALID_FOR).isEqualTo(Duration.ofMinutes(15));
    }

    /** A store that signs links and holds the document once {@code uploaded} is set. */
    private static final class AlreadyStoredOnce extends ReferenceOnlyStorageAdapter {

        boolean uploaded;

        AlreadyStoredOnce() {
            super("ff-documents");
        }

        @Override
        public boolean exists(StorageObjectDescriptor document) {
            return uploaded;
        }

        @Override
        public URI uploadUrl(StorageObjectDescriptor document, Duration validFor) {
            return URI.create("http://store.test/put/" + document.objectKey());
        }

        @Override
        public URI downloadUrl(StorageObjectDescriptor document, Duration validFor) {
            return URI.create("http://store.test/get/" + document.objectKey());
        }
    }
}

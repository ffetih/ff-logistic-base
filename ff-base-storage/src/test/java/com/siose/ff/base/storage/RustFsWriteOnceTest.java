package com.siose.ff.base.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Write once ({@code BR-DOC-01}) against RustFS, the version the cluster runs (the chart's
 * appVersion in {@code deploy/argocd/apps/rustfs.yaml}): does it honour the conditional write that
 * the presigned upload link carries ({@code If-None-Match: *})? AWS S3 does; this is the check for
 * RustFS. Needs Docker or Podman, as CI has; skipped without.
 *
 * <p>The store starts empty, without the bucket, as a fresh RustFS does. Each test uses a document of
 * its own, so the order of the tests does not matter.
 */
@EnabledIf("dockerAvailable")
class RustFsWriteOnceTest {

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    private static final String ACCESS_KEY = "write-once-test";
    private static final String SECRET_KEY = "write-once-test-secret";
    private static final String TENANT = "ff-eu-freight";

    private static final GenericContainer<?> RUSTFS = new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:1.0.6"))
            .withEnv(Map.of(
                    "RUSTFS_ACCESS_KEY", ACCESS_KEY,
                    "RUSTFS_SECRET_KEY", SECRET_KEY,
                    "RUSTFS_VOLUMES", "/data",
                    "RUSTFS_ADDRESS", "0.0.0.0:9000",
                    "RUSTFS_CONSOLE_ENABLE", "false"))
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static S3ObjectStorageAdapter storage;

    @BeforeAll
    static void start() throws InterruptedException {
        RUSTFS.start();
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setProvider(ObjectStorageProperties.Provider.S3);
        properties.setBucket("ff-documents");
        properties.getS3().setEndpoint(URI.create("http://" + RUSTFS.getHost() + ":" + RUSTFS.getMappedPort(9000)));
        properties.getS3().setRegion("us-east-1");
        properties.getS3().setAccessKey(ACCESS_KEY);
        properties.getS3().setSecretKey(SECRET_KEY);
        properties.getS3().setPathStyleAccess(true);
        storage = S3ObjectStorageAdapter.create(properties);

        // The port opens a moment before the S3 API answers. The bucket is not created here: the first
        // upload link has to create it, as on a fresh store in real use.
        for (int attempt = 1; ; attempt++) {
            try {
                storage.exists(newDocument());
                return;
            } catch (RuntimeException notYet) {
                if (attempt == 30) {
                    throw notYet;
                }
                Thread.sleep(1000);
            }
        }
    }

    @AfterAll
    static void stop() {
        if (storage != null) {
            storage.close();
        }
        RUSTFS.stop();
    }

    @Test
    void theFirstUploadThroughTheLinkIsStored() throws Exception {
        StorageObjectDescriptor document = newDocument();

        HttpResponse<String> first = put(DocumentLinks.upload(storage, TENANT, document.objectUri(), "image/jpeg"), "first", true);

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(storage.exists(document)).isTrue();
        assertThat(get(document)).isEqualTo("first");
    }

    @Test
    void aLinkIssuedBeforeTheFirstUploadCannotOverwriteIt() throws Exception {
        StorageObjectDescriptor document = newDocument();
        DocumentTransfer early = DocumentLinks.upload(storage, TENANT, document.objectUri(), "image/jpeg");
        DocumentTransfer late = DocumentLinks.upload(storage, TENANT, document.objectUri(), "image/jpeg");

        assertThat(put(early, "first", true).statusCode()).isEqualTo(200);
        HttpResponse<String> overwrite = put(late, "second", true);

        assertThat(overwrite.statusCode()).as("conditional write refused: %s", overwrite.body()).isEqualTo(412);
        assertThat(get(document)).isEqualTo("first");
    }

    @Test
    void theLinkDoesNotWorkWithoutItsCondition() throws Exception {
        StorageObjectDescriptor document = newDocument();
        DocumentTransfer early = DocumentLinks.upload(storage, TENANT, document.objectUri(), "image/jpeg");
        DocumentTransfer late = DocumentLinks.upload(storage, TENANT, document.objectUri(), "image/jpeg");
        assertThat(put(early, "first", true).statusCode()).isEqualTo(200);

        // If-None-Match is part of the signature: a client that leaves it out to force an overwrite
        // no longer matches the signed request.
        HttpResponse<String> unconditional = put(late, "second", false);

        assertThat(unconditional.statusCode()).as("answer: %s", unconditional.body()).isEqualTo(403);
        assertThat(get(document)).isEqualTo("first");
    }

    @Test
    void noUploadLinkIsIssuedForAStoredDocument() throws Exception {
        StorageObjectDescriptor document = newDocument();
        assertThat(put(DocumentLinks.upload(storage, TENANT, document.objectUri(), "image/jpeg"), "first", true)
                .statusCode()).isEqualTo(200);

        assertThatThrownBy(() -> DocumentLinks.upload(storage, TENANT, document.objectUri(), "image/jpeg"))
                .isInstanceOf(DocumentAlreadyStoredException.class);
    }

    @Test
    void noDownloadLinkBeforeTheUpload() {
        StorageObjectDescriptor document = newDocument();

        assertThatThrownBy(() -> DocumentLinks.download(storage, TENANT, document.objectUri(), "image/jpeg"))
                .isInstanceOf(DocumentNotStoredException.class);
    }

    private static StorageObjectDescriptor newDocument() {
        return storage.registerDocumentReference(TENANT, "inspection", "WH-" + UUID.randomUUID(), "photo.jpg", "image/jpeg");
    }

    /** What a browser does with the transfer: PUT to the URL with the headers it names (or without the condition). */
    private static HttpResponse<String> put(DocumentTransfer transfer, String body, boolean withCondition)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(transfer.url())
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        transfer.headers().forEach((name, value) -> {
            if (withCondition || !name.equalsIgnoreCase("If-None-Match")) {
                request.header(name, value);
            }
        });
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String get(StorageObjectDescriptor document) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(storage.downloadUrl(document, Duration.ofMinutes(1))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }
}

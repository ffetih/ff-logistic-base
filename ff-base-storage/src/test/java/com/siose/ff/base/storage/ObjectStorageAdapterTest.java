package com.siose.ff.base.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siose.ff.base.storage.StorageObjectDescriptor.StorageMode;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class ObjectStorageAdapterTest {

    @Test
    void keysAreScopedToTenantAndCannotEscapeTheirPrefix() {
        ObjectStoragePort storage = new ReferenceOnlyStorageAdapter("ff-documents");

        StorageObjectDescriptor doc = storage.registerDocumentReference(
                "FF-EU-Freight", "e-CMR", "TMS-2026-10001", "../../etc/passwd", "application/pdf");

        assertThat(doc.objectKey()).isEqualTo("ff-eu-freight/e-cmr/tms-2026-10001/____etc_passwd");
        assertThat(doc.objectUri()).isEqualTo("s3://ff-documents/" + doc.objectKey());
        assertThat(doc.storageMode()).isEqualTo(StorageMode.REFERENCE_ONLY);
    }

    @Test
    void referenceOnlyAdapterRefusesToStore() {
        ObjectStoragePort storage = new ReferenceOnlyStorageAdapter("ff-documents");
        StorageObjectDescriptor doc = storage.registerDocumentReference(
                "t1", "pod", "R-1", "photo.jpg", "image/jpeg");

        assertThatThrownBy(() -> storage.store("t1", "pod", "R-1", "photo.jpg", "image/jpeg", new byte[] {1}))
                .isInstanceOf(ObjectStoreUnavailableException.class)
                .hasMessageContaining("reference-only");
        assertThatThrownBy(() -> storage.uploadUrl(doc, Duration.ofMinutes(5)))
                .isInstanceOf(ObjectStoreUnavailableException.class);
        assertThatThrownBy(() -> storage.downloadUrl(doc, Duration.ofMinutes(5)))
                .isInstanceOf(ObjectStoreUnavailableException.class);
        assertThatThrownBy(() -> storage.exists(doc))
                .isInstanceOf(ObjectStoreUnavailableException.class);
    }

    @Test
    void linksAreSignedForThePublicEndpointWhileTheApplicationUsesTheInternalOne() {
        StorageObjectDescriptor doc = new ReferenceOnlyStorageAdapter("ff-documents")
                .registerDocumentReference("ff-eu-freight", "pod", "R-1", "photo.jpg", "image/jpeg");

        try (S3ObjectStorageAdapter inContainer = S3ObjectStorageAdapter.create(settings("http://rustfs:9000", "http://localhost:9000"));
             S3ObjectStorageAdapter direct = S3ObjectStorageAdapter.create(settings("http://localhost:9000", null));
             S3ObjectStorageAdapter blank = S3ObjectStorageAdapter.create(settings("http://rustfs:9000", ""))) {

            // One presigner signs both kinds of link; download links are signed offline (an upload link
            // first makes sure the bucket exists, which needs the store).
            assertThat(inContainer.downloadUrl(doc, Duration.ofMinutes(5)).getAuthority()).isEqualTo("localhost:9000");
            assertThat(direct.downloadUrl(doc, Duration.ofMinutes(5)).getAuthority()).isEqualTo("localhost:9000");
            assertThat(blank.downloadUrl(doc, Duration.ofMinutes(5)).getAuthority()).as("empty: the endpoint").isEqualTo("rustfs:9000");
        }
    }

    private static ObjectStorageProperties settings(String endpoint, String publicEndpoint) {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setProvider(ObjectStorageProperties.Provider.S3);
        properties.setBucket("ff-documents");
        properties.getS3().setEndpoint(URI.create(endpoint));
        properties.getS3().setPublicEndpoint(publicEndpoint == null ? null : URI.create(publicEndpoint));
        properties.getS3().setAccessKey("key");
        properties.getS3().setSecretKey("secret");
        return properties;
    }

    @Test
    void s3AdapterCreatesMissingBucketThenUploadsUnderTenantPrefix() {
        RecordingS3 recording = new RecordingS3();
        try (S3ObjectStorageAdapter storage = new S3ObjectStorageAdapter(recording.client(), presigner(), "ff-documents")) {

            StorageObjectDescriptor doc = storage.store(
                    "ff-tr-distribution", "epod", "RT-5001", "signature.png", "image/png", new byte[] {1, 2, 3});
            storage.store("ff-tr-distribution", "epod", "RT-5001", "photo.jpg", "image/jpeg", new byte[] {4});

            assertThat(doc.storageMode()).isEqualTo(StorageMode.STORED);
            assertThat(recording.calls).containsExactly(
                    "headBucket ff-documents",
                    "createBucket ff-documents",
                    "putObject ff-documents/ff-tr-distribution/epod/rt-5001/signature.png image/png",
                    "putObject ff-documents/ff-tr-distribution/epod/rt-5001/photo.jpg image/jpeg");
        }
    }

    @Test
    void s3AdapterSignsPathStyleDownloadLinksWithoutCallingTheServer() {
        try (S3ObjectStorageAdapter storage = new S3ObjectStorageAdapter(new RecordingS3().client(), presigner(), "ff-documents")) {
            StorageObjectDescriptor doc = storage.registerDocumentReference(
                    "ff-eu-freight", "ecmr", "TMS-1", "ecmr.pdf", "application/pdf");

            URI url = storage.downloadUrl(doc, Duration.ofMinutes(5));

            assertThat(url.toString())
                    .startsWith("http://localhost:9000/ff-documents/ff-eu-freight/ecmr/tms-1/ecmr.pdf?")
                    .contains("X-Amz-Expires=300")
                    .contains("X-Amz-Signature=");
        }
    }

    @Test
    void s3AdapterSignsPathStyleUploadLinksForTheReservedKey() {
        try (S3ObjectStorageAdapter storage = new S3ObjectStorageAdapter(new RecordingS3().client(), presigner(), "ff-documents")) {
            StorageObjectDescriptor reserved = storage.registerDocumentReference(
                    "FF-EU-Freight", "ecmr", "TMS-1", "ecmr.pdf", "application/pdf");
            StorageObjectDescriptor doc = StoredObjects.fromObjectUri(reserved.objectUri(), "application/pdf");

            URI url = storage.uploadUrl(doc, Duration.ofMinutes(5));

            assertThat(StoredObjects.sameTenant(doc, "FF-EU-Freight")).isTrue();
            assertThat(StoredObjects.sameTenant(doc, "ff-tr-distribution")).isFalse();
            assertThat(url.toString())
                    .startsWith("http://localhost:9000/ff-documents/ff-eu-freight/ecmr/tms-1/ecmr.pdf?")
                    .contains("X-Amz-Expires=300")
                    .contains("X-Amz-SignedHeaders=")
                    .contains("content-type")
                    .contains("if-none-match")
                    .contains("X-Amz-Signature=");
        }
    }

    @Test
    void s3AdapterKnowsWhetherADocumentIsAlreadyStored() {
        RecordingS3 recording = new RecordingS3();
        try (S3ObjectStorageAdapter storage = new S3ObjectStorageAdapter(recording.client(), presigner(), "ff-documents")) {
            StorageObjectDescriptor reserved = storage.registerDocumentReference(
                    "ff-eu-freight", "ecmr", "TMS-2", "ecmr.pdf", "application/pdf");

            assertThat(storage.exists(reserved)).isFalse();
            storage.store("ff-eu-freight", "ecmr", "TMS-2", "ecmr.pdf", "application/pdf", new byte[] {1});
            assertThat(storage.exists(reserved)).isTrue();
        }
    }

    @Test
    void aStoreThatDoesNotAnswerIsUnavailable() throws Exception {
        int closedPort;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        ObjectStorageProperties properties = settings("http://127.0.0.1:" + closedPort, null);
        properties.getS3().setTimeout(Duration.ofSeconds(2));
        try (S3ObjectStorageAdapter storage = S3ObjectStorageAdapter.create(properties)) {
            StorageObjectDescriptor doc = storage.registerDocumentReference(
                    "ff-eu-freight", "pod", "R-2", "photo.jpg", "image/jpeg");

            assertThatThrownBy(() -> storage.exists(doc))
                    .isInstanceOf(ObjectStoreUnavailableException.class)
                    .hasMessage("The object store did not look the document up")
                    .hasCauseInstanceOf(software.amazon.awssdk.core.exception.SdkException.class);
            assertThatThrownBy(() -> storage.uploadUrl(doc, Duration.ofMinutes(5)))
                    .isInstanceOf(ObjectStoreUnavailableException.class)
                    .hasMessage("The object store did not prepare bucket ff-documents");
        }
    }

    private static S3Presigner presigner() {
        return S3Presigner.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://localhost:9000"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test-secret")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /** S3Client stand-in: records the calls the adapter makes; the bucket does not exist at first. */
    private static final class RecordingS3 implements InvocationHandler {

        final List<String> calls = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();
        private boolean bucketExists;

        S3Client client() {
            return (S3Client) Proxy.newProxyInstance(
                    S3Client.class.getClassLoader(), new Class<?>[] {S3Client.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.isDefault() && (args == null || !(args[0] instanceof software.amazon.awssdk.core.SdkRequest))) {
                // Consumer<Builder> overloads build the request and call the request-object overload
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            switch (method.getName()) {
                case "headBucket" -> {
                    calls.add("headBucket " + ((HeadBucketRequest) args[0]).bucket());
                    if (!bucketExists) {
                        throw NoSuchBucketException.builder().statusCode(404).message("missing").build();
                    }
                    return null;
                }
                case "createBucket" -> {
                    bucketExists = true;
                    calls.add("createBucket " + ((CreateBucketRequest) args[0]).bucket());
                    return CreateBucketResponse.builder().build();
                }
                case "putObject" -> {
                    PutObjectRequest put = (PutObjectRequest) args[0];
                    calls.add("putObject " + put.bucket() + "/" + put.key() + " " + put.contentType());
                    keys.add(put.key());
                    return PutObjectResponse.builder().build();
                }
                case "headObject" -> {
                    HeadObjectRequest head = (HeadObjectRequest) args[0];
                    if (!keys.contains(head.key())) {
                        throw NoSuchKeyException.builder().statusCode(404).message("missing").build();
                    }
                    return HeadObjectResponse.builder().build();
                }
                case "close" -> {
                    return null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        }
    }
}

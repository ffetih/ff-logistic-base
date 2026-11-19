package com.siose.ff.base.storage;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object storage settings ({@code ff.storage.*}).
 */
@ConfigurationProperties(prefix = "ff.storage")
public class ObjectStorageProperties {

    public enum Provider {
        /** Compute object keys only; nothing is stored. */
        REFERENCE_ONLY,
        /** Any S3-compatible store (RustFS, AWS S3, Cloudflare R2, SeaweedFS). */
        S3
    }

    private Provider provider = Provider.REFERENCE_ONLY;

    /** One bucket for all tenants; each tenant gets its own key prefix. */
    private String bucket = "ff-documents";

    private final S3 s3 = new S3();

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public S3 getS3() {
        return s3;
    }

    public static class S3 {

        /** Server URL, e.g. http://localhost:9000 for RustFS. Empty for AWS S3. */
        private URI endpoint;

        private String region = "us-east-1";

        /** Empty: use the AWS default credential chain instead of static keys. */
        private String accessKey;

        private String secretKey;

        /**
         * The address clients use for presigned links, when it differs from {@link #getEndpoint()}.
         * In containers the application reaches the store by its service name ({@code http://rustfs:9000}),
         * which a browser cannot resolve; the host is part of the signature, so a link has to be signed
         * for the address the client uses ({@code http://localhost:9000}). Empty: the endpoint.
         */
        private URI publicEndpoint;

        /**
         * http://host/bucket/key instead of http://bucket.host/key. Needed by most
         * self-hosted servers, which have no wildcard DNS for bucket subdomains.
         */
        private boolean pathStyleAccess = true;

        /**
         * The longest one call to the store may take, retries included. A store that stalls would
         * otherwise hold the request that needs it, and its thread, for minutes.
         */
        private Duration timeout = Duration.ofSeconds(10);

        boolean hasStaticCredentials() {
            return accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
        }

        public URI getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(URI endpoint) {
            this.endpoint = endpoint;
        }

        public URI getPublicEndpoint() {
            return publicEndpoint;
        }

        public void setPublicEndpoint(URI publicEndpoint) {
            this.publicEndpoint = publicEndpoint;
        }

        /** Where presigned links point: the public endpoint if set, otherwise the endpoint (null: AWS). */
        URI linkEndpoint() {
            return publicEndpoint != null && !publicEndpoint.toString().isBlank() ? publicEndpoint : endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public boolean isPathStyleAccess() {
            return pathStyleAccess;
        }

        public void setPathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
        }
    }
}

package com.siose.ff.base.storage.autoconfigure;

import com.siose.ff.base.storage.ObjectStoragePort;
import com.siose.ff.base.storage.ObjectStorageProperties;
import com.siose.ff.base.storage.ReferenceOnlyStorageAdapter;
import com.siose.ff.base.storage.S3ObjectStorageAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Document storage ({@code ff.storage.*}). Without configuration it computes object keys only,
 * so tests and a plain local run need no object store.
 */
@AutoConfiguration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectStoragePort.class)
    @ConditionalOnProperty(name = "ff.storage.provider", havingValue = "reference-only", matchIfMissing = true)
    ObjectStoragePort referenceOnlyObjectStorage(ObjectStorageProperties properties) {
        return new ReferenceOnlyStorageAdapter(properties.getBucket());
    }

    /**
     * S3 storage ({@code ff.storage.provider=s3}). Loaded only when the AWS SDK S3 module is on
     * the classpath: the library declares it optional, so a product opts in by adding
     * {@code software.amazon.awssdk:s3}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "software.amazon.awssdk.services.s3.S3Client")
    @ConditionalOnProperty(name = "ff.storage.provider", havingValue = "s3")
    static class S3StorageConfiguration {

        @Bean
        @ConditionalOnMissingBean(ObjectStoragePort.class)
        ObjectStoragePort s3ObjectStorage(ObjectStorageProperties properties) {
            return S3ObjectStorageAdapter.create(properties);
        }
    }
}

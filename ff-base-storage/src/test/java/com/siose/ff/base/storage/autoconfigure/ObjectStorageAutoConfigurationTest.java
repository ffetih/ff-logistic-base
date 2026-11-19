package com.siose.ff.base.storage.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.siose.ff.base.storage.ObjectStoragePort;
import com.siose.ff.base.storage.ReferenceOnlyStorageAdapter;
import com.siose.ff.base.storage.S3ObjectStorageAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Which storage adapter a product gets, depending on configuration and classpath.
 */
class ObjectStorageAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObjectStorageAutoConfiguration.class));

    @Test
    void referenceOnlyByDefault() {
        runner.run(context -> assertThat(context)
                .getBean(ObjectStoragePort.class)
                .isInstanceOf(ReferenceOnlyStorageAdapter.class));
    }

    @Test
    void s3WhenConfigured() {
        runner.withPropertyValues(
                        "ff.storage.provider=s3",
                        "ff.storage.s3.endpoint=http://localhost:9000",
                        "ff.storage.s3.access-key=test",
                        "ff.storage.s3.secret-key=test-secret")
                .run(context -> assertThat(context)
                        .getBean(ObjectStoragePort.class)
                        .isInstanceOf(S3ObjectStorageAdapter.class));
    }

    @Test
    void noS3AdapterWithoutTheAwsSdk() {
        runner.withPropertyValues("ff.storage.provider=s3")
                .withClassLoader(new FilteredClassLoader(S3Client.class))
                .run(context -> assertThat(context).doesNotHaveBean(ObjectStoragePort.class));
    }

    @Test
    void aProductCanProvideItsOwnAdapter() {
        ObjectStoragePort custom = new ReferenceOnlyStorageAdapter("custom-bucket");
        runner.withBean(ObjectStoragePort.class, () -> custom)
                .run(context -> assertThat(context).getBean(ObjectStoragePort.class).isSameAs(custom));
    }
}

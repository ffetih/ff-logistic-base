package com.siose.ff.base.app.autoconfigure;

import com.siose.ff.base.app.CallerController;
import com.siose.ff.base.app.ReleaseSwitchHealthIndicator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The release switch and, in a web application, {@code /api/v1/me}. */
@AutoConfiguration
public class FfAppAutoConfiguration {

    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "releaseSwitchHealthIndicator")
    ReleaseSwitchHealthIndicator releaseSwitchHealthIndicator(@Value("${ff.release.healthy:true}") boolean healthy) {
        return new ReleaseSwitchHealthIndicator(healthy);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    static class Web {

        @Bean
        @ConditionalOnMissingBean
        CallerController callerController() {
            return new CallerController();
        }
    }
}

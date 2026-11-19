package com.siose.ff.base.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siose.ff.base.tenant.TenantContext;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/** What an application gets from the chassis alone. */
class ChassisTest {

    @Nested
    @SpringBootTest(classes = TestApplication.class)
    @AutoConfigureMockMvc
    class Serving {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        Environment environment;

        @AfterEach
        void clear() {
            TenantContext.clear();
        }

        @Test
        void answersTheProbesAndTheReleaseGroup() throws Exception {
            for (String path : new String[] {"/actuator/health/liveness", "/actuator/health/readiness",
                    "/actuator/health/release"}) {
                mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
            }
        }

        @Test
        void saysWhoIsCalling() throws Exception {
            TenantContext.setCurrent(new TenantContext.TenantInfo("ff-eu-freight", "dispatcher1",
                    Set.of("TENANT_ADMIN", "DISPATCHER"), "jwt"));
            mockMvc.perform(get("/api/v1/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantId").value("ff-eu-freight"))
                    .andExpect(jsonPath("$.roles[0]").value("DISPATCHER"));
        }

        @Test
        void takesDefaultsAndLetsTheApplicationOverrideThem() {
            assertThat(environment.getProperty("spring.threads.virtual.enabled")).isEqualTo("true");
            assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("true");
        }
    }

    @Nested
    @SpringBootTest(classes = TestApplication.class, properties = "ff.release.healthy=false")
    @AutoConfigureMockMvc
    class BrokenOnPurpose {

        @Autowired
        MockMvc mockMvc;

        @Test
        void reportsTheReleaseDownWithA200AndStaysLiveAndReady() throws Exception {
            mockMvc.perform(get("/actuator/health/release"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DOWN"));
            mockMvc.perform(get("/actuator/health/readiness")).andExpect(jsonPath("$.status").value("UP"));
        }
    }

    @Nested
    @SpringBootTest(classes = TestApplication.class)
    @ActiveProfiles(FfApplication.MIGRATE_PROFILE)
    class Migrating {

        @Autowired
        ApplicationContext context;

        @Test
        void startsWithoutAWebServer() {
            assertThat(context).isNotInstanceOf(WebApplicationContext.class);
        }
    }
}

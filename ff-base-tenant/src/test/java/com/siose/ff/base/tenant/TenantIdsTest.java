package com.siose.ff.base.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siose.ff.base.tenant.TenantContext.TenantInfo;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantIdsTest {

    @Test
    void acceptsLowerCaseLettersDigitsAndHyphens() {
        assertThat(TenantIds.isValid("ff-eu-freight")).isTrue();
        assertThat(TenantIds.isValid("tenant-fmcg-a")).isTrue();
        assertThat(TenantIds.isValid("t1")).isTrue();
        assertThat(TenantIds.isValid("a" + "b".repeat(63))).isTrue();
    }

    @Test
    void refusesEverythingThatWouldNeedCleaning() {
        assertThat(TenantIds.isValid(null)).isFalse();
        assertThat(TenantIds.isValid("")).isFalse();
        assertThat(TenantIds.isValid("a")).as("too short").isFalse();
        assertThat(TenantIds.isValid("a" + "b".repeat(64))).as("longer than the tenant_id column").isFalse();
        assertThat(TenantIds.isValid("FF-EU-Freight")).as("upper case").isFalse();
        assertThat(TenantIds.isValid("-ff")).as("starts with a hyphen").isFalse();
        assertThat(TenantIds.isValid("ff eu")).isFalse();
        assertThat(TenantIds.isValid("ff.eu")).as("would share a storage prefix with ff-eu").isFalse();
        assertThat(TenantIds.isValid("ff_eu")).isFalse();
        assertThat(TenantIds.isValid("x' or '1'='1")).as("never reaches an SQL clause").isFalse();
        assertThat(TenantIds.isValid(TenantContext.NO_TENANT)).as("the no-tenant marker is never a tenant").isFalse();
    }

    @Test
    void aTenantInfoOnlyHoldsAValidId() {
        assertThat(new TenantInfo("ff-eu-freight", "user", Set.of(), "TEST").tenantId()).isEqualTo("ff-eu-freight");
        assertThatThrownBy(() -> new TenantInfo("FF EU", "user", Set.of(), "TEST"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.siose.ff.base.app;

import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.base.tenant.TenantContext.TenantInfo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who the application thinks is calling: the tenant, user and roles it took from the verified
 * token. The first endpoint behind authentication, the quickest way to check a token end to end,
 * and the one the smoke test calls after every switch: both implementations answer it the same.
 */
@RestController
public class CallerController {

    @GetMapping("/api/v1/me")
    public Caller me() {
        TenantInfo caller = TenantContext.requireCurrent();
        return new Caller(caller.tenantId(), caller.username(), caller.roles().stream().sorted().toList(),
                caller.authSource());
    }

    public record Caller(String tenantId, String username, List<String> roles, String authSource) {
    }
}

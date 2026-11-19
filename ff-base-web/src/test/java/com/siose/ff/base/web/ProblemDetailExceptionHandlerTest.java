package com.siose.ff.base.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.siose.ff.base.tenant.TenantAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ProblemDetail;

class ProblemDetailExceptionHandlerTest {

    private final ProblemDetailExceptionHandler handler = new ProblemDetailExceptionHandler();

    @Test
    void aConcurrentUpdateIsA409ThatSaysToRetry() {
        ProblemDetail optimistic = handler.handleConcurrentUpdate(new OptimisticLockingFailureException("version 0 != 1"));
        ProblemDetail lockTimeout = handler.handleConcurrentUpdate(new PessimisticLockingFailureException("timeout"));

        assertThat(optimistic.getStatus()).isEqualTo(409);
        assertThat(optimistic.getType().toString()).isEqualTo("urn:siose-ff:problem:concurrent-update");
        assertThat(optimistic.getDetail()).contains("retry").doesNotContain("version 0");
        assertThat(lockTimeout.getStatus()).isEqualTo(409);
    }

    @Test
    void aMissingDependencyIsA503WithItsOwnType() {
        class MapsDown extends DependencyUnavailableException {
            MapsDown() {
                super("The map service did not answer");
            }

            @Override
            public String problemSlug() {
                return "map-service-unavailable";
            }

            @Override
            public String problemTitle() {
                return "Map Service Unavailable";
            }
        }

        ProblemDetail problem = handler.handleDependencyUnavailable(new MapsDown());

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getType().toString()).isEqualTo("urn:siose-ff:problem:map-service-unavailable");
        assertThat(problem.getTitle()).isEqualTo("Map Service Unavailable");
        assertThat(problem.getDetail()).isEqualTo("The map service did not answer");
    }

    @Test
    void aConflictWithExistingDataIsA409WithItsOwnType() {
        class NumberTaken extends ConflictException {
            NumberTaken() {
                super("Order 4711 already belongs to another order");
            }

            @Override
            public String problemSlug() {
                return "order-number-taken";
            }

            @Override
            public String problemTitle() {
                return "Order Number Taken";
            }
        }

        ProblemDetail problem = handler.handleConflict(new NumberTaken());

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getType().toString()).isEqualTo("urn:siose-ff:problem:order-number-taken");
        assertThat(problem.getTitle()).isEqualTo("Order Number Taken");
        assertThat(problem.getDetail()).isEqualTo("Order 4711 already belongs to another order");
    }

    @Test
    void theOtherProblemsKeepTheirStatus() {
        assertThat(handler.handleIllegalArgument(new IllegalArgumentException("bad")).getStatus()).isEqualTo(400);
        assertThat(handler.handleNotFound(new ResourceNotFoundException("gone")).getStatus()).isEqualTo(404);
        assertThat(handler.handleTenantAccess(new TenantAccessException("no tenant")).getStatus()).isEqualTo(403);
    }
}

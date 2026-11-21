package com.siose.ff.base.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.siose.ff.base.tenant.TenantAccessException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;

class ProblemDetailExceptionHandlerTest {

    private final ProblemDetailExceptionHandler handler = new ProblemDetailExceptionHandler();

    private static final HttpInputMessage BODY = new HttpInputMessage() {
        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public HttpHeaders getHeaders() {
            return new HttpHeaders();
        }
    };

    @Test
    void aRequestWithoutABodySaysSo() {
        ProblemDetail problem = handler.handleUnreadableBody(new HttpMessageNotReadableException(
                "Required request body is missing: public ShipmentView ShipmentController.create(...)", BODY));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getType().toString()).isEqualTo("urn:siose-ff:problem:missing-body");
        assertThat(problem.getTitle()).isEqualTo("Missing Request Body");
        assertThat(problem.getDetail()).isEqualTo("The request has no body; this endpoint expects JSON.");
    }

    @Test
    void anUnreadableBodySaysWhereTheParserStoppedAndNothingElse() {
        Exception parser = new IllegalStateException(
                "Unexpected character ('}' (code 125)): was expecting double-quote to start field name\n"
                        + " at [Source: REDACTED; line: 1, column: 25]");
        ProblemDetail problem = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("JSON parse error: " + parser.getMessage(), parser, BODY));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getType().toString()).isEqualTo("urn:siose-ff:problem:unreadable-body");
        assertThat(problem.getTitle()).isEqualTo("Unreadable Request Body");
        assertThat(problem.getDetail())
                .isEqualTo("The request body is not valid JSON for this endpoint (line 1, column 25).");
    }

    @Test
    void anUnreadableBodyWithoutALocationStillHasADetail() {
        ProblemDetail problem = handler.handleUnreadableBody(new HttpMessageNotReadableException(
                "Cannot deserialize value of type `double` from String \"abc\"", BODY));

        assertThat(problem.getDetail()).isEqualTo("The request body is not valid JSON for this endpoint.")
                .doesNotContain("double");
    }

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

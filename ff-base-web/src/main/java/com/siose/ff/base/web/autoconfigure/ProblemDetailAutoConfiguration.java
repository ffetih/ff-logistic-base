package com.siose.ff.base.web.autoconfigure;

import com.siose.ff.base.web.ProblemDetailExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Import;

/**
 * RFC 9457 problem responses for domain and validation errors in every product.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@Import(ProblemDetailExceptionHandler.class)
public class ProblemDetailAutoConfiguration {
}

package com.lexaid.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void preservesUnauthorizedAndForbiddenStatusCodes() {
        ResponseEntity<Result<Void>> unauthorized = handler.handleBiz(new BizException(401, "unauthorized"));
        ResponseEntity<Result<Void>> forbidden = handler.handleBiz(new BizException(403, "forbidden"));

        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void doesNotExposeUnexpectedExceptionDetails() {
        ResponseEntity<Result<Void>> response = handler.handleOther(
                new IllegalStateException("database-password-should-not-leak"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("服务器内部错误");
    }
}

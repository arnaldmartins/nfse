package br.com.alvexustech.nfse.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, WebExchangeBindException.class, ServerWebInputException.class})
    public ResponseEntity<ApiErrorResponse> validation(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(OffsetDateTime.now(), "VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler({ProviderNotFoundException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> notFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(OffsetDateTime.now(), "NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> internal(Exception ex) {
        log.error("nfse_unhandled_error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(OffsetDateTime.now(), "INTERNAL_ERROR", "Erro interno no processamento fiscal"));
    }
}

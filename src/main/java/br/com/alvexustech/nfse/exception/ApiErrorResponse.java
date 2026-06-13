package br.com.alvexustech.nfse.exception;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
        OffsetDateTime timestamp,
        String code,
        String message
) {
}

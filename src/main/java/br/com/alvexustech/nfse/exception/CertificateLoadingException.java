package br.com.alvexustech.nfse.exception;

public class CertificateLoadingException extends RuntimeException {
    public CertificateLoadingException(String message) {
        super(message);
    }

    public CertificateLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}

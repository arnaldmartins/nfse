package br.com.alvexustech.nfse.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

public final class CompressionUtils {

    private CompressionUtils() {
    }

    public static String gzipBase64(String value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(value.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao compactar conteudo em GZip/Base64", ex);
        }
    }
}

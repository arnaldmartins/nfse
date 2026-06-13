package br.com.alvexustech.nfse.signature;

public interface XmlSignatureService {

    String sign(String xml, String referenceId);
}

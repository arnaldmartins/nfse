# Exemplos de respostas

## POST /api/v1/nfse/emissoes

```json
{
  "emissionId": "b6a8d6f4-8a0f-41c6-bf9d-6ff1e6ff36bb",
  "status": "PROCESSING",
  "protocolo": "202605080000001",
  "chaveAcesso": null
}
```

Em chamadas repetidas com a mesma `idempotencyKey`, o backend retorna o estado persistido sem reenviar ao provider.

## GET /api/v1/nfse/emissoes/{id}/status

```json
{
  "status": "AUTHORIZED",
  "protocolo": "202605080000001",
  "chaveAcesso": "31062001234567800019500000000000000000000000001",
  "mensagem": "NFS-e autorizada"
}
```

## Erro padronizado

```json
{
  "timestamp": "2026-05-08T10:30:00-03:00",
  "code": "VALIDATION_ERROR",
  "message": "Validation failed"
}
```

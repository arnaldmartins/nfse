# NFS-e Nacional Backend

Base inicial em Java 17 e Spring Boot 3 para emissao automatizada de NFS-e pelo padrao nacional.

## Arquitetura

- `domain`: estados e conceitos fiscais estaveis.
- `service`: orquestracao de emissao, idempotencia, montagem de DPS e consulta.
- `provider`: abstracao para API nacional e futuros provedores/municipios.
- `client`: comunicacao HTTP via `WebClient` com mTLS.
- `certificate`: carga de certificado A1 `.pfx` e criacao de contexto SSL.
- `signature`: assinatura XMLDSig com Apache Santuario.
- `persistence` e `repository`: modelo JPA e acesso PostgreSQL.
- `exception` e `logging`: erros padronizados e correlacao de logs.

## Fluxo recomendado do MVP

1. Receber request interno com `idempotencyKey`.
2. Persistir emissao em `RECEIVED` ou retornar registro existente pela chave idempotente.
3. Gerar XML DPS por componente fiscal dedicado.
4. Assinar XML com certificado A1 ICP-Brasil usando XMLDSig.
5. Compactar/Base64 quando exigido pelo endpoint oficial.
6. Enviar para o provider nacional via `WebClient` com mTLS.
7. Persistir protocolo, chave de acesso, payload de resposta e status.
8. Consultar status por protocolo ate autorizacao/rejeicao; futuramente mover polling para fila assíncrona.

## Observacoes fiscais

O XML DPS em `DpsXmlBuilder` e intencionalmente uma estrutura inicial. Para producao, substitua por classes JAXB/XSD geradas a partir dos esquemas oficiais atuais do Portal Nacional e valide o XML antes da assinatura. Os endpoints tambem devem ser confirmados na documentacao oficial vigente do ambiente usado.

Referencias oficiais consultadas:

- Documentacao Atual do Portal Nacional da NFS-e: https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual
- Manual de Contribuintes das APIs do ADN: https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual/manual-contribuintes-apis-adn-sistema-nacional-nfse.pdf

## Execucao local

Crie um PostgreSQL local com banco `nfse`, usuario `nfse` e senha `nfse`, ou ajuste `application.yml`.

Variaveis principais:

```bash
export NFSE_CERTIFICATE_PFX_PATH=/caminho/certificado-a1.pfx
export NFSE_CERTIFICATE_PASSWORD=senha
export NFSE_NATIONAL_API_BASE_URL=https://adn.producaorestrita.nfse.gov.br/contribuintes
```

```bash
mvn spring-boot:run
```

## Evolucao prevista

- Trocar `DpsXmlBuilder` por geracao tipada via XSD/JAXB.
- Adicionar outbox e fila para retries, polling de status e cancelamento.
- Incluir tabela de eventos fiscais e auditoria de payloads.
- Adicionar providers por municipio quando houver particularidades fora do padrao nacional.
- Externalizar segredos em Vault/KMS/secret manager.

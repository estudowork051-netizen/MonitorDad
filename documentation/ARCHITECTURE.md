# Arquitetura — MonitorDad • by HoD

## Visão Geral
O MonitorDad utiliza uma arquitetura única de comunicação dividida em três camadas, onde **Local Wi-Fi** e **Internet** são apenas meios de transporte intercambiáveis sobre o mesmo contrato `RemoteTransport`.

```text
ParentApp / KidsApp UI (Compose + ConnectionHubComponents)
        │
        ▼
RemoteControlRepository + RemoteCommandEngine (fila offline, ACK, HMAC, anti-replay)
        │
        ▼
ConnectionManager (AUTO / LOCAL_ONLY / REMOTE_ONLY + Failover + Diagnóstico 6 etapas)
   ├── LocalHttpTransport  ──► LAN Wi-Fi (NsdManager _monitordad._tcp. -> LocalKidsServer)
   └── HttpsRemoteTransport ─► Internet  (HTTPS/TLS -> backend/ Node.js Relay)
```

## Protocolo e Segurança
- **Autenticação**: Assinatura HMAC-SHA256 (`X-Signature`), `X-Timestamp` (janela temporal) e `X-Nonce` (anti-replay).
- **Armazenamento de segredos**: `SecureCredentialStorage` com `AndroidKeyStore` AES-256-GCM.
- **Pareamento**: Códigos temporários de 6 caracteres (10 min, uso único, bloqueio após 5 falhas) e QR Code `monitordad://pair?...` gerado localmente via `QrCodeGenerator` sem bibliotecas externas.
- **Compatibilidade Android**: `network_security_config.xml` permite HTTP apenas para comunicação em LAN local descoberta via `NsdManager`, enquanto `HttpsRemoteTransport` exige `https://` para qualquer servidor remoto na internet.

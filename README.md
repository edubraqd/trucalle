# Trucalle

Filtro de chamadas para Android (API 24+). Rejeita, antes de tocar, ligações de quem não está na agenda — e aprende com o que bloqueia.

## O que faz
- **Modo agenda** (padrão): quem não está nos contatos é rejeitado. **Modo lista negra**: libera tudo, bloqueia prefixos (padrão `0303`).
- Libera quem você ligou nos últimos 90 dias (registro de chamadas).
- Bloqueia número oculto, STIR/SHAKEN reprovado (Android 11+) e insistentes (score com decaimento exponencial, meia-vida configurável).
- Aprende por prefixo (DDD+4) com prior Beta: prefixo quente bloqueia no modo lista e evita gastar SMS.
- SMS de aviso na primeira ligação bloqueada, só para celular BR, uma vez por número.
- Horários liberados, reversão em um toque no histórico, denúncia à Anatel via WhatsApp.

Sem rede, sem conta, sem dependência: uma Activity sem XML, um `CallScreeningService` e SharedPreferences.

## Build (sem Gradle)
Requer Android SDK com `build-tools/36.0.0`, `platforms/android-33` e JDK 17.

```bash
./build.sh
```

Gera `build/trucalle.apk` assinado com uma chave debug criada na primeira execução (`debug.keystore`, fora do git).

## Instalar
```
adb install -r build/trucalle.apk
```
Abrir o app, tocar **Ativar**, conceder contatos/SMS/registro de chamadas e escolher Trucalle como filtro de chamadas.

## Referências
`docs/papers.md` — literatura que embasa os sinais (CallRank, robocall characterization, STIR/SHAKEN).

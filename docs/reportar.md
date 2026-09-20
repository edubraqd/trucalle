# Reportar — estudo e método

Data: 2026-09-19. Pergunta: como um app de bloqueio de chamadas no Brasil "reporta" um número
insistente ou spam, e o que disso vira bloqueio automático para outros usuários.

## Canais verificados

| Canal | API pública | Uso possível pelo app |
|---|---|---|
| Anatel Consumidor (site, app Android/iOS, call center 1331) | Não. Login gov.br + formulário. Denúncia responde em até 90 dias | Abrir link; consumidor preenche |
| Anatel WhatsApp `0800 61 0 1331` (desde abr/2025) | Não, mas aceita link `https://api.whatsapp.com/send/?phone=558006101331&text=...` | **Melhor canal manual**: 1 toque, texto pronto. Exige cadastro prévio no Anatel Consumidor |
| Canal setorial de denúncia de golpes (regra de chamadas massivas, vigor 01/11/2025 a 31/10/2028) | Só para instituições financeiras reportarem às operadoras | Nada para consumidor/app |
| Qual Empresa Me Ligou — `https://www.qualempresameligou.com.br/` (ABR Telecom, desde 13/02/2023) | Não. Só consulta de PJ nas 6 grandes operadoras. Sem "denunciar" | Link de consulta |
| Não Me Perturbe — `naomeperturbe.com.br` | Não. Cadastro de opt-out por operadora/banco | Link de cadastro; descumprimento → Procon/Anatel |
| Prefixo 0303 | Obrigatório 2021–ago/2025; opcional desde então (Anatel, 07/08/2025); MPF recomendou volta em jan/2026 | Quem ainda usa se declara telemarketing → bloquear prefixo continua útil |
| STIR/SHAKEN nacional (Origem Verificada) | Chega ao app via `Call.Details.getCallerNumberVerificationStatus()` (Android 11+) | Já implementado: `FAILED` → bloqueio + lista automática |
| Truecaller, Hiya, Google Phone | Fechado; report só dentro do app deles | Nada |
| tellows | API paga para parceiros | Só com contrato |
| `BlockedNumberContract` (lista de bloqueio do sistema) | Só dialer/SMS padrão escreve | Não: app de screening não tem esse papel |

Conclusão: **não existe endpoint público** para "reportar número de spam" no Brasil. Reportar de verdade,
com efeito automático para terceiros, só com backend próprio.

## Método em três camadas

1. **Anatel via WhatsApp** — botão no app monta a denúncia (número, tentativas) e abre o link com o
   texto. Implementado em `Main.anatel()`.
2. **Compartilhar** — `ACTION_SEND` com a lista, cai em qualquer app. Implementado em `Main.report()`.
3. **Rede Trucalle** — backend próprio. É a única camada que fecha o ciclo: número reportado por
   K aparelhos distintos passa a ser bloqueado em todos. **Não implementado ainda** (ver abaixo).

## Camada 3 — desenho

Princípio: **nunca sobe agenda**. Sobe só número que ligou, foi bloqueado como insistente/spoof, e
um pseudônimo estável do aparelho (para contar denunciantes distintos). LGPD: número de quem liga
em massa não é dado pessoal do usuário; pseudônimo sem vínculo com pessoa.

Cliente (Android):

- `Report.enqueue(ctx, numero, tentativas, motivo)` — chamado quando um número vira auto-bloqueado.
  Grava em prefs `outbox` e dispara thread. **Nunca no caminho da chamada**: custo no `Screener` é
  um write em prefs.
- `Report.flush(ctx)` — `POST {url}/report` com `{"number","count","reason","ts","device"}`;
  2xx remove da fila. Fila sobrevive sem rede.
- `Report.sync(ctx)` — `GET {url}/blocklist` → `["+55...", ...]`, mescla em `Hist.addAuto`.
- Config: `url` e `token` (Bearer) em `Cfg`. Vazio = desligado. `INTERNET` no manifest.
- `device` = 16 hex do SHA-256 do `ANDROID_ID`. Alternativa sem `ANDROID_ID`: UUID gerado na
  instalação e guardado em prefs (some ao reinstalar; suficiente para contagem).

Servidor (mínimo viável):

```
POST /report      auth Bearer opcional
  body {number, count, reason, ts, device}
  upsert reports(number, device) -> primeira/última data, soma de count, motivo
GET  /blocklist?min=3
  SELECT number FROM reports GROUP BY number HAVING COUNT(DISTINCT device) >= min
  -> ["+55...", ...]   (cache 1h; lista inteira cabe em KB)
```

Regras de consenso: `min` denunciantes distintos (padrão 3) **ou** 1 denúncia com motivo `spoof STIR`.
Expira número sem denúncia nova em 90 dias. Uma tabela, sem fila, cabe em qualquer host.

## Estado

- Camadas 1 e 2: no APK.
- Camada 3: código do cliente escrito, mas a gravação de `Report.java` foi barrada pelo
  classificador do modo auto (upload de números + pseudônimo de aparelho para endpoint remoto).
  Precisa de decisão explícita do Eduardo para entrar.

## Fontes

- Teletime 07/08/2025 — Anatel flexibiliza 0303: https://teletime.com.br/07/08/2025/anatel-flexibiliza-uso-do-prefixo-0303-para-chamadas-massivas/
- MPF jan/2026 — recomenda volta do 0303: https://www.mpf.mp.br/o-mpf/unidades/pr-go/noticias/mpf-recomenda-que-anatel-restabeleca-uso-obrigatorio-do-prefixo-0303-para-telemarketing
- Tecnoblog — regra de chamadas massivas e canal setorial: https://tecnoblog.net/noticias/anatel-proibe-telemarketing-com-varios-numeros-e-exige-canal-para-denuncias-contra-golpes/
- Oficina da Net — vigência 01/11/2025–31/10/2028: https://www.oficinadanet.com.br/telecomunicacoes/65259-anatel-regras-chamadas-massivas
- Anatel — canal WhatsApp: https://www.gov.br/anatel/pt-br/canais_atendimento/whatsapp
- Anatel Consumidor — guia v1.1 (03/07/2026): https://sistemas.anatel.gov.br/anexar-api/publico/anexos/download/43b235cedab064a8c4ba1f27dd7c80c7
- Commsrisk — Qual Empresa Me Ligou: https://commsrisk.com/brazils-regulator-launches-spam-number-checking-web-portal-for-consumers/
- tellows API: https://medium.com/@tellows/tellows-identifies-unknown-calls-and-offers-a-unique-api-for-you-to-participate-61ab9ff40ec0

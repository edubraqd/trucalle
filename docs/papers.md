# Papers de apoio — identificação de ligações indesejadas

Pesquisa de 2026-09-19. Ordenado por utilidade para o Trucalle.

## 1. Prasad, Bouma-Sims, Mylappan, Reaves — *Who's Calling? Characterizing Robocalls through Audio and Metadata Analysis* (USENIX Security 2020, Distinguished Paper)
- https://www.usenix.org/system/files/sec20-prasad.pdf
- Honeypot de 66.606 linhas, 11 meses, 1,48 M chamadas não solicitadas, 2.687 campanhas.
- **O que apoia:** (a) campanhas rotacionam números — bloquear número isolado é fraco, bloquear *prefixo/blocos* é forte (nosso prior Beta por DDD+4); (b) atender NÃO aumenta o volume — o SMS automático não "marca" o número como ativo; (c) picos de volume anômalos e wangiri (toca-e-desliga) são detectáveis só por metadados.

## 2. Azad, Arshad, Riaz — *ROBO-SPOT: Detecting Robocalls by Understanding User Engagement and Connectivity Graph* (Big Data Mining and Analytics, 2024)
- https://www.sciopen.com/local/article_pdf/10.26599/BDMA.2023.9020020.pdf
- CDRs reais de operadora: 1 bi de registros, 3 M assinantes, 10 dias. TPR ≈ 97 %, FPR < 0,01 %.
- **O que apoia:** reputação = repetição de chamadas + duração curta + grafo (spammer tem muitos destinatários, ninguém liga de volta). Legítimo passa 80 % do tempo de conversa com poucos contatos fortes. Do lado do aparelho só temos repetição e "está na agenda" — exatamente o que usamos. Duração e grafo exigem a camada 3 (rede).

## 3. Azad, Bag, Hao, Salah — *Detecting Nuisance Calls over Internet Telephony Using Caller Reputation* (Electronics, 2021)
- https://doi.org/10.3390/electronics10030353
- Reputação híbrida: frequência de chamadas, duração, número de parceiros de saída + recomendação de participantes confiáveis.
- **O que apoia:** o desenho da camada 3 — dispositivos confiáveis (nossos usuários) reportam, servidor agrega.

## 4. Chaisamran, Okuda, Blanc, Yamaguchi — *Trust-based SPIT detection by using call duration and social reliability* (IEEE, 2013)
- https://ieeexplore.ieee.org/document/6765923
- Trust por chamador a partir de duração e direção das chamadas entre usuários.
- **O que apoia:** direção importa — quem o usuário *liga de volta* é legítimo. Evidência "legit" barata para o prior: número que o usuário discou (CallLog, precisa READ_CALL_LOG).

## 5. Balasubramaniyan, Ahamad, Park — *CallRank: Combating SPIT Using Call Duration, Social Networks and Global Reputation* (CEAS 2007)
- https://www.academia.edu/29896774/CallRank_Combating_SPIT_Using_Call_Duration_Social_Networks_and_Global_Reputation
- Clássico: credenciais de duração de chamada como "prova de relação social".

## 6. Survey — *Systems and Methods for SPIT Detection in VoIP: Survey and Future Directions* (2018)
- https://www.researchgate.net/publication/324124818
- Features estatísticas consolidadas: taxa de chamadas, duração, **intervalo entre tentativas**, chamadas concorrentes, baixa taxa de completamento.
- **O que apoia:** "intervalo entre tentativas" é a feature de discador que pulamos; o survey confirma que existe, mas sempre medida na rede, não no terminal.

## 7. FCC — *Triennial Report on the Efficacy of STIR/SHAKEN* (2023)
- https://docs.fcc.gov/public/attachments/DOC-416732A1.pdf
- ~70 % das chamadas VoIP saem assinadas, só 15-24 % chegam com assinatura válida (perde-se em trânsito TDM); parte dos robocalls já sai assinada com atestação B/C.
- **O que apoia (e limita):** nosso `stir` só age em FAILED — correto: NOT_VERIFIED não é evidência. Atestação C ou B não é sinal de legitimidade.

## Extras
- *Robocalls: A Worldwide or US-only Problem?* (arXiv 2606.31790) — humano × robô por NLP, 88,7 %; exige atender. Não aplicável (decisão de projeto: não atender).
- *Exploring the Impact of Spam Call Warning Accuracy on…* (USEC 2024) — https://www.ndss-symposium.org/wp-content/uploads/usec2024-48-paper.pdf — falso positivo destrói a confiança no aviso mais que falso negativo. Sustenta o bloqueio reversível em um toque.
- Anatel, prefixo 0303 (empresas > 10 mil chamadas/dia): https://www.cnnbrasil.com.br/nacional/chamada-0303-anatel-amplia-uso-do-prefixo-para-reduzir-ligacoes-indesejadas/ — justifica `block` default "0303" no modo lista.

## Síntese para o produto
| Sinal | Literatura | No aparelho? | Trucalle |
|---|---|---|---|
| Repetição / insistência | ROBO-SPOT, survey | sim | score com decaimento |
| Bloco de números / prefixo | Who's Calling | sim | prior Beta DDD+4 |
| Está na agenda / ligou de volta | CallRank, Chaisamran | sim (CallLog = permissão extra) | agenda; "ligou de volta" pendente |
| Duração média curta | todos | não (não atende) | camada 3 |
| Grafo (muitos destinatários) | ROBO-SPOT, Azad 2021 | não | camada 3 |
| Intervalo regular (discador) | survey | parcial | pulado |
| STIR FAILED | FCC | Android 11+ | feito |
| 0303 | Anatel | sim | pendente default |

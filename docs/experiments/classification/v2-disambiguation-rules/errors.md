# v2 — v1 + tie-breaking rules: 81 / 91

v1 plus seven tie-breaking rules in the system prompt, one for each topic pair v1
confused (slow data vs app crash, auto-renew vs tariff, and so on). Because the rules
were written after reading v1's errors, this is tuning on the evaluation set, which
is why the score must be read with that caveat. Model output:
[`note_topics_llm.csv`](note_topics_llm.csv).

Agreement with the human review: **81 of 91 texts** (88.8% of tickets), the same as v1.

| Note text | Model said | Review | Tickets | vs v1 |
|---|---|---|---|---|
| paket tidak aktif setelah pembelian berhasil | Billing & Charging › Package charged but not active | Package & Quota › Package not active after purchase | 622 | same (ambiguous) |
| internet lemot banget, buka aplikasi saja loading terus | App & Digital › App crash | Network Quality › Slow data | 615 | same; the rule was ignored |
| sudah beli paket dari aplikasi tapi statusnya tidak aktif | Billing & Charging › Package charged but not active | Package & Quota › Package not active after purchase | 603 | **new**; same ambiguity as row 1 |
| isi paket berbeda dari yang tertulis di katalog | Billing & Charging › Package charged but not active | Package & Quota › Quota not as advertised | 583 | still wrong, different label |
| paid for the unlimited plan, balance deducted, no quota received | Billing & Charging › Balance deducted unexpectedly | Billing & Charging › Package charged but not active | 502 | same; the rule was ignored |
| credited amount is lower than what I paid for | Billing & Charging › Wrong tariff | Top-up & Voucher › Wrong amount credited | 437 | **new** |
| pulsa terpotong sendiri padahal tidak melakukan apa apa | Top-up & Voucher › Top-up not received | Billing & Charging › Balance deducted unexpectedly | 397 | same; the rule was ignored |
| nominal yang masuk tidak sesuai dengan yang dibeli | Billing & Charging › Balance deducted unexpectedly | Top-up & Voucher › Wrong amount credited | 395 | **new** |
| other request, not specified | Others › Feedback | Others › Unclassified | 340 | **new** |
| recharge deducted from e-wallet but not credited to the number | Billing & Charging › Balance deducted unexpectedly | Top-up & Voucher › Top-up not received | 313 | same; the rule was ignored |

**Fixed from v1:** auto-renew, registration failed, "saran" → Feedback, and the
general-enquiry case.

**Broken by v2:** four texts that v1 had right.

**Lesson:** a 3B model does not reliably apply a list of if-then rules. A longer
prompt moves errors around instead of removing them. Rules in prose are the wrong
lever for a model this size; examples are the next thing to try (v3).

# v1 — restate the problem first: 81 / 91

Model `qwen2.5:3b`, temperature 0, seed 42. The JSON schema puts `problem` (the
customer's problem restated in ≤ 15 words) before `topic`, which is an enum of the
27 taxonomy labels. Model output: [`note_topics_llm.csv`](note_topics_llm.csv).

Agreement with the human review: **81 of 91 texts**, which is 89.4% of tickets.

| # | Note text | Model said | Review | Tickets | Verdict |
|---|---|---|---|---|---|
| 1 | paket tidak aktif setelah pembelian berhasil | Billing & Charging › Package charged but not active | Package & Quota › Package not active after purchase | 622 | Ambiguous: no deduction is mentioned, so the review picks activation (rule in `review/README.md`) |
| 2 | internet lemot banget, buka aplikasi saja loading terus | App & Digital › App crash | Network Quality › Slow data | 615 | Clear error: "lemot / loading" is slow data, not a crash |
| 3 | auto renew charged me again, I want it stopped | Billing & Charging › Wrong tariff | Package & Quota › Auto-renew unwanted | 611 | Clear error: auto-renew is named outright |
| 4 | isi paket berbeda dari yang tertulis di katalog | Billing & Charging › Wrong tariff | Package & Quota › Quota not as advertised | 583 | Clear error: the package contents differ, not the price |
| 5 | paid for the unlimited plan, balance deducted, no quota received | Billing & Charging › Balance deducted unexpectedly | Billing & Charging › Package charged but not active | 502 | Clear error: the customer bought a package, so the deduction was expected |
| 6 | pulsa terpotong sendiri padahal tidak melakukan apa apa | Top-up & Voucher › Top-up not received | Billing & Charging › Balance deducted unexpectedly | 397 | Clear error: no top-up is involved |
| 7 | registration keeps failing even with correct identity data | Others › Unclassified | Registration & SIM › Registration failed | 361 | Clear error: gave up although the topic is explicit |
| 8 | pelanggan menghubungi untuk keperluan lain | Others › General enquiry | Others › Unclassified | 342 | Ambiguous: both are defensible |
| 9 | recharge deducted from e-wallet but not credited to the number | Billing & Charging › Balance deducted unexpectedly | Top-up & Voucher › Top-up not received | 313 | Clear error: a recharge that never arrived |
| 10 | saran untuk peningkatan layanan | Others › Unclassified | Others › Feedback | 198 | Clear error: "saran" means suggestion |

**Pattern:** 8 clear errors and 2 ambiguous cases. The model latches onto a keyword
("charged" → tariff, "loading" → app, "deducted" → balance) instead of the situation.
It also falls back to *Unclassified* even when the topic is named. The taxonomy
definitions already cover every one of these cases, so the definitions are not
what is missing; a 3B model needs explicit tie-breaking rules.

# v4 — v1 + the 8 most similar reviewed labels: 69 / 91

The v1 prompt plus the 8 reviewed texts most similar to the note (word-set Jaccard
overlap), again excluding the note itself. Model output:
[`note_topics_llm.csv`](note_topics_llm.csv).

Agreement with the human review: **69 of 91 texts** (76.0% of tickets). **Worse than v1.**

All 22 errors:

| Note text | Model said | Review | Tickets |
|---|---|---|---|
| sinyal hanya bagus kalau di luar ruangan | Network Quality › No signal | Network Quality › Indoor coverage | 912 |
| tidak ada jaringan sama sekali padahal biasanya penuh | Registration & SIM › SIM blocked | Network Quality › No signal | 649 |
| minta matikan perpanjangan otomatis, sudah tidak butuh | Billing & Charging › Package charged but not active | Package & Quota › Auto-renew unwanted | 646 |
| paket tidak aktif setelah pembelian berhasil | Billing & Charging › Package charged but not active | Package & Quota › Package not active after purchase | 622 |
| paket diperpanjang otomatis padahal tidak diminta | Billing & Charging › Package charged but not active | Package & Quota › Auto-renew unwanted | 615 |
| auto renew charged me again, I want it stopped | Billing & Charging › Wrong tariff | Package & Quota › Auto-renew unwanted | 611 |
| sudah beli paket dari aplikasi tapi statusnya tidak aktif | Billing & Charging › Package charged but not active | Package & Quota › Package not active after purchase | 603 |
| isi paket berbeda dari yang tertulis di katalog | Billing & Charging › Wrong tariff | Package & Quota › Quota not as advertised | 583 |
| charged twice for a single package purchase | Billing & Charging › Wrong tariff | Billing & Charging › Double charge | 527 |
| pulsa sudah terpotong untuk paket namun paket tidak aktif | Package & Quota › Auto-renew unwanted | Billing & Charging › Package charged but not active | 500 |
| credited amount is lower than what I paid for | Billing & Charging › Wrong tariff | Top-up & Voucher › Wrong amount credited | 437 |
| kuota habis sangat cepat padahal jarang dipakai | Package & Quota › Quota not as advertised | Package & Quota › Quota drained fast | 426 |
| pulsa terpotong sendiri padahal tidak melakukan apa apa | Others › Unclassified | Billing & Charging › Balance deducted unexpectedly | 397 |
| nominal yang masuk tidak sesuai dengan yang dibeli | Billing & Charging › Wrong tariff | Top-up & Voucher › Wrong amount credited | 395 |
| pelanggan menghubungi untuk keperluan lain | Others › General enquiry | Others › Unclassified | 342 |
| other request, not specified | Network Quality › No signal | Others › Unclassified | 340 |
| sudah transfer untuk isi pulsa, saldo tidak bertambah | Billing & Charging › Balance deducted unexpectedly | Top-up & Voucher › Top-up not received | 334 |
| recharge deducted from e-wallet but not credited to the number | Billing & Charging › Balance deducted unexpectedly | Top-up & Voucher › Top-up not received | 313 |
| aplikasi sering keluar sendiri saat dibuka | App & Digital › Cannot login | App & Digital › App crash | 311 |
| pembayaran di aplikasi gagal terus padahal saldo cukup | Top-up & Voucher › Channel transaction failed | App & Digital › Payment failed in app | 288 |
| memberikan masukan mengenai pelayanan | Others › Unclassified | Others › Feedback | 202 |
| saran untuk peningkatan layanan | Others › Unclassified | Others › Feedback | 198 |

**Lesson:** on short, mixed-language texts, word overlap finds texts that *look*
alike rather than ones that *mean* the same ("paket … tidak aktif" vs "paket …
diperpanjang otomatis"). The 3B model then copies the neighbours' labels. Retrieval
needs embeddings, not word overlap, before it can help.

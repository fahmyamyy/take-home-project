# v3 — v1 + all other reviewed labels as examples: 81 / 91

The v1 prompt (the v2 rules removed) plus every other human-reviewed text with its
label, as worked examples. The text being classified is never among its own
examples, so this is a held-out (leave-one-out) score. Model output:
[`note_topics_llm.csv`](note_topics_llm.csv).

Agreement with the human review: **81 of 91 texts** (90.1% of tickets).

| Note text | Model said | Review | Tickets |
|---|---|---|---|
| paket tidak aktif setelah pembelian berhasil | Billing & Charging › Package charged but not active | Package & Quota › Package not active after purchase | 622 |
| auto renew charged me again, I want it stopped | Billing & Charging › Package charged but not active | Package & Quota › Auto-renew unwanted | 611 |
| sudah beli paket dari aplikasi tapi statusnya tidak aktif | Billing & Charging › Package charged but not active | Package & Quota › Package not active after purchase | 603 |
| app rejects my login repeatedly, tried reinstalling | App & Digital › App crash | App & Digital › Cannot login | 488 |
| credited amount is lower than what I paid for | Others › Unclassified | Top-up & Voucher › Wrong amount credited | 437 |
| nominal yang masuk tidak sesuai dengan yang dibeli | Billing & Charging › Wrong tariff | Top-up & Voucher › Wrong amount credited | 395 |
| nomor lama tidak bisa dipakai lagi katanya sudah hangus | Others › Unclassified | Registration & SIM › Number recycled | 363 |
| pembayaran di aplikasi gagal terus padahal saldo cukup | Top-up & Voucher › Channel transaction failed | App & Digital › Payment failed in app | 288 |
| ingin tahu cara cek masa aktif | Others › Unclassified | Others › General enquiry | 210 |
| saran untuk peningkatan layanan | Others › Unclassified | Others › Feedback | 198 |


**Fixed from v1:** slow data, catalog contents, the e-wallet recharge, and
"pulsa terpotong sendiri".

**New errors:** login, amounts credited, number recycled, in-app payment and a
"how do I check…" question. Several of these fell back to *Unclassified*.

**Lesson:** 90 examples in one prompt (~3,000 tokens) is more than a 3B model can
use. It fixed the confusions the examples speak to directly and lost track of
others. The run was also 5× slower (5 min vs 1 min).

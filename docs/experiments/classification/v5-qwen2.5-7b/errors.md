# v5 — the v1 prompt on `qwen2.5:7b`: 88 / 91

Same prompt, schema and settings as v1 (restate the problem first, enum of topics,
temperature 0, seed 42). Only the model changes, from `qwen2.5:3b` to `qwen2.5:7b`.
Nothing in this version was tuned on the review, so the score is clean. Model output:
[`note_topics_llm.csv`](note_topics_llm.csv).

Agreement with the human review: **88 of 91 texts** (97.0% of tickets). On the 89 texts
with one clear topic: **86 of 89**. On both ambiguous texts (see `review/ambiguous.csv`)
it picked the same topic as the reviewer.

| Note text | Model said | Review | Tickets |
|---|---|---|---|
| transaction failed at the outlet but payment was taken | Billing & Charging › Balance deducted unexpectedly | Top-up & Voucher › Channel transaction failed | 438 |
| credited amount is lower than what I paid for | Billing & Charging › Wrong tariff | Top-up & Voucher › Wrong amount credited | 437 |
| transaksi di gerai gagal tapi uang sudah diambil | Billing & Charging › Balance deducted unexpectedly | Top-up & Voucher › Channel transaction failed | 423 |

**Fixed from v1:** all 8 clear v1 errors.

**What remains:** one confusion, "money was taken but the top-up failed or came
short", in English and Indonesian. The model reads it as a billing problem; the
review files it under the top-up channel.

**Cost:** 2 min for 91 texts instead of 1. The model needs ~5 GB of memory, which is
tight on the assignment's 8 GB reference laptop.

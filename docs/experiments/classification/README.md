# Classification experiments

How well the local model labels the 91 distinct complaint texts, measured against
the human review (`review/topic_review.csv`). Every version was run on all 91 texts.
Each folder holds the model's raw output (`note_topics_llm.csv`) and its errors
(`errors.md`).

| Version | Model | Change | Texts | Tickets | Clean? |
|---|---|---|---|---|---|
| v0 | qwen2.5:3b | Topic first, reason after | 71 / 91 | — | yes (output not kept) |
| [v1](v1-restate-first/errors.md) | qwen2.5:3b | Model restates the problem **before** choosing | **81 / 91** | 89.4% | yes |
| [v2](v2-disambiguation-rules/errors.md) | qwen2.5:3b | v1 + 7 written tie-breaking rules | 81 / 91 | 88.8% | no: rules written from v1's errors |
| [v3](v3-few-shot-all/errors.md) | qwen2.5:3b | v1 + all 90 other reviewed labels as examples | 81 / 91 | 90.1% | yes: leave-one-out |
| [v4](v4-few-shot-nearest/errors.md) | qwen2.5:3b | v1 + the 8 most similar reviewed labels | 69 / 91 | 76.0% | yes: leave-one-out |
| [v5](v5-qwen2.5-7b/errors.md) | **qwen2.5:7b** | v1 prompt, bigger model | **88 / 91** | **97.0%** | yes: nothing tuned |

Two of the 91 texts are ambiguous (`review/ambiguous.csv`): more than one topic is
defensible, so a person decides them. On the 89 texts with one clear topic, v1 scores
81 / 89 and v5 scores 86 / 89.

## What we learned

1. **How the model is asked matters more than what it is told.** Making the 3B model
   restate the problem before choosing lifted it from 71 to 81 (v0 → v1).
2. **A 3B model does not follow extra instructions reliably.** Rules (v2) and
   examples (v3) each fixed some errors and caused about as many new ones. The score
   stayed at 81 and only the errors moved.
3. **Naive retrieval hurts.** Word overlap picks neighbours that look alike but mean
   different things, and the model copies their labels (v4: 69).
4. **Model size fixed what prompting could not.** The same v1 prompt on a 7B model
   fixed all 8 clear errors (v5: 88), without any tuning on the review.
5. **The human review stays.** Even at 88 / 91 the remaining errors are systematic
   (money taken but the top-up failed). Ambiguous texts always need a person.
   Everything a person must check is queued in `output/needs_human_check.csv`.

## The prompts that are no longer in the code

**v2 rules**, appended to the v1 system prompt:

- Slow, "lemot", buffering or endless loading is *Slow data*; *App crash* only when the app closes by itself.
- If auto-renew is mentioned, it is *Auto-renew unwanted*, even when it also says "charged".
- *Wrong tariff* is only about the price; package contents that differ from what was listed are *Quota not as advertised*.
- *Balance deducted unexpectedly* only when nothing was bought; a paid package that did not arrive is *Package charged but not active*.
- A paid top-up that did not arrive, by any method, is *Top-up not received*.
- A suggestion ("saran") is *Feedback*.
- *Unclassified* only when no problem, request or suggestion is named.

**v3 / v4 examples**, prepended to the user message:

```
Examples already labelled by a human reviewer (follow the same conventions):
- "<reviewed text>" -> <category> > <subcategory>
...
Note: <text to classify>
```

v3 listed all 90 other texts; v4 listed the 8 with the highest word-set Jaccard
overlap. In both, the text being classified was excluded from its own examples.

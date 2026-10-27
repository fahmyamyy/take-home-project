# Human review of note topics

`topic_review.csv` holds a reviewed topic for each of the 91 distinct complaint
texts (notes with the repeat-contact prefixes removed). Where it exists, the
reviewed topic is the final topic; the model's answer in
`output/note_topics_llm.csv` is kept separately and measured against it.

**How the review was done.** Every one of the 91 texts was read and given the
taxonomy topic that best matches what the customer describes, using the
definitions in `Taxonomy.java`. The starting point for each text was the label
agents most often gave that exact text (typically ~85% of its tickets); the
reviewer accepted it after reading, or replaced it. The `note` column explains
every case where the model and the review differ, and some borderline calls.
`true_labels.csv` was not used.

The first pass was prepared with an AI coding assistant.
It was then checked line by line by the author, who added notes where a call
needed explaining. With 91 rows this takes about 15 minutes, which is why every label
could be reviewed rather than a sample.

**Borderline rule worth knowing.** Two agent subcategories describe nearly the
same failure:

- *Billing & Charging › Package charged but not active* — the customer says money
  or balance was taken (bayar / terpotong / charged).
- *Package & Quota › Package not active after purchase* — the customer says the
  purchase succeeded but the package isn't active, without complaining about a
  deduction.

The review applies that rule consistently; the trend analysis reports both
together as "package activation failures" where that matters.

**Ambiguous texts** (`ambiguous.csv`). Some notes fit more than one topic and no
rule can settle them. Each is listed with every defensible topic and a short reason.
The topic in `topic_review.csv` must be one of those candidates; the pipeline refuses
to load otherwise. Ambiguous texts are reported apart from the model's errors and
always appear in `output/needs_human_check.csv`, the queue of texts a person must
look at. That queue also holds texts the model and the review disagree on, and texts
nobody has reviewed yet.

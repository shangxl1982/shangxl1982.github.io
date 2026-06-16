# Model Architecture

## Tokenizer
- BPE, WordPiece, Unigram, SentencePiece
- Vocabulary size trade-offs
- Multilingual tokenization challenges
- Tokenizer merging & extending

## Embedding Dimension
- Dimension selection heuristics (d_model)
- Head dimension & number of heads
- Scaling laws for dimension

## Position Encoding
- Absolute vs Relative position encoding
- Sinusoidal, learned, RoPE, ALiBi
- Where to inject position info: every layer?
- Context length extension techniques

## Stacking & Scaling
- Pre-LN vs Post-LN: which & why
- DeepNorm, QK-Norm for stable training
- Width vs Depth trade-offs

## Optimizer Selection
- Adam/AdamW, SGD, Lion, Sophia
- Learning rate schedule: cosine, warmup, constant
- Weight decay, gradient clipping, mixed precision

## Qwen3 vs Qwen3-VL
- Architectural differences
- How to extend LLM to vision-language
- Training strategy: from LLM to VLM

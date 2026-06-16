# Attention Mechanism

## Transformer Attention Formula

$$
\text{Attention}(Q, K, V) = \text{softmax}\left(\frac{QK^T}{\sqrt{d_k}}\right)V
$$

### Why Softmax?
- Converts raw scores into probability distribution (sum=1)
- Ensures gradients flow through attended positions
- Without softmax, unbounded scores cause training instability

### Why Divide by $\sqrt{d_k}$?
- Prevents dot products from growing large with dimension
- Large values push softmax into regions with vanishing gradients
- $\sqrt{d_k}$ normalizes variance of dot product to ~1

## DeepSeek Sparse Attention
- Multi-Head Latent Attention (MLA)
- Compressing KV cache with low-rank projections
- Sparse attention patterns for efficiency
- Training-inference alignment

## RoPE (Rotary Position Embedding)
- Rotates query/key vectors by position-dependent angle
- Applied to Q and K at **every attention layer**
- Relative position encoding with absolute position formulation
- Supports context length extrapolation

## FlashAttention
- IO-aware exact attention: minimize HBM reads/writes
- Tiling: compute attention in SRAM, not HBM
- No approximation: numerically identical results
- 2x-4x speedup, dramatically reduced memory

## Causal Mask
- **Training**: static upper-triangular matrix filled with $-\infty$, applied once
- **Inference - Prefill**: same as training, compute full attention in one pass
- **Inference - Decode**: no mask needed (only one token), KV cache provides context

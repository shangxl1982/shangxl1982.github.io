# Fine-tuning & Training

## LoRA (Low-Rank Adaptation)

### Principle
- Freeze pre-trained weights $W$, train low-rank decomposition $\Delta W = BA$
- Forward: $h = W_0x + BAx$ (only $BA$ is trained)
- Inference: merge $W = W_0 + BA$ with zero additional cost

### Rank Selection
- Typical rank: $r = 8, 16, 32, 64$
- Higher rank for complex tasks (domain adaptation)
- Lower rank for simple style tuning
- Stability: rank too low may underfit, too high may overfit

### Low-Rank Matrix Initialization
- $B \sim \mathcal{N}(0, \sigma^2)$, $A = 0$ (so $\Delta W = 0$ initially)
- Variations: Kaiming init, SVD-based init

## GRPO (Group Relative Policy Optimization)

### Principle
- Group-based advantage estimation (no critic network needed)
- For each prompt, sample multiple responses, compute rewards
- Advantage = (reward - group_mean) / group_std

### Reward Design
- Rule-based: format, length, keyword matching
- Model-based: reward model scoring
- Process reward model (PRM): step-by-step rewards

### Importance Sampling
- GRPO does NOT use importance sampling (unlike PPO)
- Simpler training, fewer hyperparameters

## Small Model Fine-tuning (e.g. Qwen3-8B)
- QLoRA: 4-bit quantization + LoRA
- Data quality over quantity
- Task-specific vs general instruction tuning

## Embedding Models
- Sentence transformers, E5, BGE, GTE
- Contrastive learning: in-batch negatives
- Classifier head training on embeddings

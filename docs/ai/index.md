# AI Tech

Cutting-edge AI technologies covering model architecture, inference optimization, training, and agent systems.

## Topics

### [Model Architecture](./model-architecture/)
- Tokenizer design & selection
- Embedding dimension choices
- Position encoding methods
- Layer stacking & model scaling
- Optimizer selection
- Qwen3 vs Qwen3-VL structural differences

### [Inference Optimization](./inference-optimization/)
- vLLM & SGLang: inference engine internals
- PD (Prefill/Decode) separation
- KVCache principles & optimization
- LMCache: caching for long-context inference

### [Attention Mechanism](./attention-mechanism/)
- Transformer attention formula & softmax
- DeepSeek sparse attention
- RoPE (Rotary Position Embedding)
- FlashAttention optimization
- Causal mask in training & inference

### [Fine-tuning & Training](./finetuning-training/)
- LoRA: principle, rank selection, initialization
- GRPO: reward design & importance sampling
- Small model fine-tuning (e.g. Qwen3-8B)
- Embedding models & classifiers

### [Multi-Agent Systems](./multi-agent/)
- LangGraph: state graph orchestration
- Agent communication patterns
- Memory management: public, private, compression
- Security, privacy & audit
- Decision-making & tool invocation

### [RAG Technologies](./rag/)
- Hybrid search pipeline
- RAG evaluation (RAGAS, retrieval@k, BLEU, etc.)
- GraphRAG implementation principles
- Embedding & retrieval pain points

### [Evaluation](./evaluation/)
- LLM evaluation benchmarks
- Agent evaluation frameworks
- RAG evaluation metrics
- Memory system evaluation
- Closed-loop evaluation pipeline

### [Agent Frameworks](./agent-frameworks/)
- OpenCLaw: agent harness & productization
- Skills vs MCP protocols
- Function calling best practices
- Intent understanding on mobile
- Model routing: cost/capability/performance
- Agentic memory systems (e.g. Mem0)

### [Embedding & Classification](./embedding/)
- Embedding models: training & selection
- Classification with embeddings
- Cross-encoder vs bi-encoder
- Dense retrieval fundamentals

# Inference Optimization

## vLLM
- PagedAttention: managing KVCache with page tables
- Continuous batching & iteration-level scheduling
- Block manager & memory sharing (Copy-on-Write)
- Prefix caching

## SGLang
- RadixAttention: prefix caching via radix tree
- Structured generation with constraint decoding
- Ahead-of-Time compilation for attention
- SGLang vs vLLM: design philosophy differences

## PD Separation (Prefill/Decode)
- Why separate prefill and decode phases
- Throughput & latency benefits
- Cluster-level deployment architecture
- Impact on network & scheduling

## KVCache
- What is KVCache and why needed
- Memory footprint: O(2 × n_layers × d_model × seq_len)
- Optimization: KV quantization, cache eviction, window attention
- Does KVCache affect FFN computation? (No — only attention)
- Shared prefix & cross-request caching

## LMCache
- Caching KV pairs across requests/contexts
- Integration with vLLM, SGLang
- Cache hierarchy: local memory → distributed cache
- Cache hit rate & latency reduction

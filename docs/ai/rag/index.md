# RAG Technologies

## Hybrid Search Pipeline
- **Dense retrieval**: embedding-based semantic search
- **Sparse retrieval**: BM25, TF-IDF keyword matching
- **Fusion**: RRF (Reciprocal Rank Fusion), weighted score combination
- Re-ranking stage: cross-encoder fine-tuned reranker
- **Pain points**: chunk boundary issues, query ambiguity, latency

## GraphRAG
- Build knowledge graph from documents
- Entity extraction, relation extraction, community detection
- Query: traverse graph → retrieve subgraph → LLM answer
- Microsoft GraphRAG: global/local search patterns
- Compared to vanilla RAG: better for multi-hop reasoning, worse latency

## RAG Architecture Pipeline
1. Ingestion: chunking → embedding → indexing
2. Retrieval: query → embedding → ANN search (HNSW, IVF)
3. Post-retrieval: re-rank, filter, prompt compression
4. Generation: augment prompt → LLM → response

## Evaluation
- RAGAS: faithfulness, answer relevancy, context precision/recall
- retrieval@k, MRR, NDCG, Hit Rate
- BLEU, ROUGE, METEOR for generation quality
- Human evaluation & LLM-as-judge

## Advanced
- Self-RAG: retrieve on demand, reflect on quality
- Corrective RAG: verify retrieval, retry if needed
- RAPTOR: hierarchical summary-based retrieval

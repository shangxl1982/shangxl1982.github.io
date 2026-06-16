# Embedding & Classification

## Embedding Models
- Sentence-BERT: siamese network for sentence similarity
- E5, BGE, GTE, Instructor: instruction-tuned embeddings
- Text2Vec-base, M3E Chinese embedding models
- Fine-tuning embeddings: contrastive learning with in-batch negatives
- Model selection: dimension, domain, language support

## Classifiers with Embeddings
- KNN: nearest neighbor classification
- Linear probe: logistic regression on frozen embeddings
- MLP: learn nonlinear decision boundary
- SetFit: few-shot fine-tuning with contrastive learning

## Cross-encoder vs Bi-encoder
- Bi-encoder: fast, pre-computed embeddings, ANN search
- Cross-encoder: slow but accurate, full attention between query-doc
- Hybrid: bi-encoder for candidate retrieval, cross-encoder for re-ranking

## Classification Pipeline
1. Select embedding model (task-appropriate)
2. Embed labeled examples
3. Train classifier head (linear/MLP)
4. Evaluate on held-out set
5. Deploy with ONNX/TensorRT optimization

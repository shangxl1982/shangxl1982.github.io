# Evaluation

## LLM Evaluation
- Benchmark suites: MMLU, HumanEval, GSM8K, MATH, BIG-Bench
- Arena-style: Chatbot Arena, MT-Bench
- LLM-as-judge: using strong LLMs (GPT-4, Claude) to evaluate
- Automated vs human evaluation trade-offs

## Agent Evaluation
- Task completion rate
- Tool call accuracy & efficiency
- Multi-step reasoning correctness
- Recovery from errors: retry/fallback success rate
- Throughput & latency in production

## RAG Evaluation
- RAGAS framework: faithfulness, relevancy, precision, recall
- Context precision: are retrieved chunks relevant?
- Context recall: are all relevant chunks retrieved?
- Answer faithfulness: does answer align with context?
- End-to-end: user satisfaction, task completion

## Memory System Evaluation
- Recall accuracy over long conversations
- Memory retrieval latency
- Compression quality vs information loss
- Cross-session memory consistency

## Closed-loop Pipeline
1. Define metrics & thresholds
2. Collect traces & annotations
3. Run evaluation suite
4. Identify failures & root causes
5. Improve: fine-tune, adjust prompts, update retrieval
6. Re-deploy & monitor
7. Repeat

### Data Labeling
- Can use LLM-generated labels (cheaper, noisier)
- Human annotation for critical benchmarks
- Active learning: label the most uncertain samples
- Full LLM-based eval is feasible for many use cases

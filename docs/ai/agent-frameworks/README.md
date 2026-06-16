# Agent Frameworks

## OpenCLaw
- Agent harness implementation: standard execution loop
- Productization needs: monitoring, logging, rate limiting
- Alternatives: Hermes-Agent, CrewAI, AutoGen
- Skills: reusable agent capabilities (tool + prompt)
- Progressive disclosure: share info gradually based on need-to-know
- Skills vs MCP: Skills are framework-specific; MCP is standardized protocol

## Function Calling Design
- Clear, descriptive function names
- Detailed parameter descriptions (name, type, enum values)
- Few-shot examples in function definitions
- Avoid overloaded parameters; keep it simple
- Test with different models — hit rate varies significantly

## Model Routing
- Route by capability: simple Q&A → small model, complex reasoning → large model
- Route by cost: budget-aware selection
- Route by latency: real-time vs batch
- Auto-router: classify query → assign model, monitor & adapt

## Intent Understanding
- On-device intent classification for mobile (TinyBERT, DistilBERT, ONNX)
- Context engineering: leveraging current app state, user history
- Orchestration: intent → action sequence → API calls
- Skills discovery: which skills available for this intent

## Agentic Memory (Mem0)
- Memory types: episodic, semantic, procedural
- Memory storage: vector DB with importance scoring
- Memory retrieval: recency, relevance, importance weighting
- Consolidation: short-term → long-term memory

## Best Practices
- Use structured state objects for cross-agent handoff
- Streaming responses for real-time UX
- Full-chain encryption & audit for production
- LLM-as-judge for agent evaluation

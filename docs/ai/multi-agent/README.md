# Multi-Agent Systems

## LangGraph
- StateGraph: nodes (agents) connected by edges
- State object: shared state passed between nodes
- Each agent reads state, executes, updates state
- Streaming message push for real-time updates
- Main agent dispatches to sub-agents based on intent

## Agent Communication
- Direct message passing
- Shared state bus
- Event-driven communication

## Agent Organization
- Supervisor pattern: one orchestrator + worker agents
- Peer-to-peer: agents negotiate and delegate
- Hierarchical: nested sub-agents

## Memory Management
- **Public memory**: shared across all agents (context, goals)
- **Private memory**: agent-specific state and history
- **Context compression**: summarize long histories
- External memory: vector DB, Mem0, MemGPT

## Security & Privacy
- Local LLM for sensitive data
- Data minimization: each agent gets least privilege
- MCP permissions & access control
- Full-chain encryption & audit logging
- Fine-grained permission system

## Decision Making
- Intent recognition → agent selection
- Tool invocation: function calling, MCP tools
- Progress tracking & error recovery
- Human-in-the-loop: escalation on uncertainty

# Minecraft x distributed systems concepts

Visualize how certain distributed systems concepts work at a high-level without
the distraction of implementation details.

- Circuit breaker ✅
- Bulkhead ‼️
- Kubernetes probes ‼️

## Legos of distributed systems

Two classes of handling;

- Syncronous, requests and responses
- Asynronous, eventing

### The blocks and their problem statements

- 🧱 Discovery
  - "How do I know where to send my request?"
- 🧱 Routing/load balancing
  - "How do I know who the best candidate for handling my request is?"
- 🪜 Tracing
  - "How do I track who in the service web had a say in my request?"
- 🪜📓 Circuit breaking
  - "How do I get the space to recover so requests may succeed?"
- 🪜📓 Bulkheads
  - "How do I draw a boundary at what I know my limits to be?"
- 🪜📓 Timeouts
  - "How do I draw a boundary at where something feels unreasonable?"
- 🪜📓 Retries
  - "How do I give a request another chance at success?"
- 📓 Caching
  - Reducuction of load on limited, or time expensive, resources
- 📓 Metrics
  - "How do I self-report data so the system, or its owners, may make informed decisions"
- 📓 Queueing
  - Decoupling of handler and processor
  - An implementation of commands
- 📓 Workers
  - A method to approach queueing
  - Also referred to as distibuted actors

🧱: Foundational infastructure  
🪜: Add-on infrastructure  
📓: Adoptable pattern  

### Requests and responses

What components are critical to sending a request?

- Discovery

What components improve the likelihood of a successful request[1]?

- Routing/load balancing
- Circuit breaking
- Bulkheads
- Caching - Both by the consumer, within the application, and by the platform
- Queueing (Within the application not the request itself)

_1: Based on best-practices of the distributed systems community_

What components drive further success in requests?

- Metrics
- Tracing

### Eventing

What components are critical to eventing?

- Queues
- Workers

What components improve the likelihood of successful event processing?

- Retries
- Timeouts

What components drive further success in event processing?

- Metrics
- Tracing

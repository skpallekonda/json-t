# Decisions

## D1: Schema-first design

* Context: Need strong validation and contracts
* Decision: Define structure before data
* Why: Enables symmetry and consistency
* Tradeoff: Less flexible than JSON

---

## D2: Positional encoding

* Context: JSON payload size inefficiency
* Decision: Use ordered tuples
* Why: Reduce payload size and improve streaming
* Tradeoff: Reduced readability

---

## D3: Streaming-first architecture

* Context: Target large datasets and pipelines
* Decision: Design for O(1) memory processing
* Why: Scalability and performance
* Tradeoff: More complex APIs

---

## D4: Capability separation

* Context: Avoid monolithic processing pipeline
* Decision: Separate parse, validate, transform
* Why: Flexibility and composability
* Tradeoff: Slightly more surface area

---

## D5: Single bundle distribution

* Context: Ease of adoption
* Decision: Ship as one package
* Why: Simplicity for users
* Tradeoff: Requires internal discipline to maintain separation

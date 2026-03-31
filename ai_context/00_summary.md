# JsonT — Summary

## Purpose

JsonT is a schema-driven, positional data system designed for:

* Large-scale data pipelines
* Streaming data processing
* Strong producer-consumer contracts
* Efficient data exchange

It is built to evolve toward API protocols and service architectures (similar to gRPC/Thrift), starting from a strong data foundation.

---

## Capability Model

JsonT provides composable capabilities:

1. Parse
   Convert raw input into structured rows

2. Validate
   Enforce schema constraints and rules

3. Transform
   Apply derived schema operations (project, filter, transform)

4. Execute (Planned)
   Runtime engine for streaming pipelines

5. Expose (Planned)
   Service layer for API definitions and contracts

Each capability:

* Works independently
* Composes with others
* Supports streaming

---

## Layering Model

JsonT is organized into layers:

### Foundation Layer

* Core (Schema + Encoding + Validation)
* Transform (Derived schemas)

These are co-located but logically distinct:

* Core must work independently
* Transform builds on Core and is optional

### Execution Layer (Planned)

* Runtime engine for pipeline execution

### Interface Layer (Planned)

* Service definitions and API abstractions

---

## Packaging Model

JsonT is distributed as a single bundle.

Internally:

* Parsing, Validation, and Transform are decoupled
* Users can use only required capabilities

---

## Adoption Strategy

* Core must be simple and low-friction
* No runtime dependency for basic usage
* Advanced features are optional
* Designed for incremental adoption

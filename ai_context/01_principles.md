# Principles

## Architecture Principles

* Schema-first design
* Streaming-first processing
* Stateless and composable components
* Strong validation guarantees

---

## Layering Principles

* Core must be independently usable
* Transform depends on Core but is optional
* Runtime depends on Core + Transform
* Services depend on Runtime

---

## Capability Principles

* Each capability must be independently usable
* No capability should force usage of another
* Capabilities must compose cleanly
* No hidden coupling between modules

---

## Design Constraints

* Follow SOLID principles
* Avoid hidden or implicit state
* Ensure thread safety where needed
* Prefer simple, explicit APIs

---

## Product Principles

* Adoption first, completeness later
* Simplicity over feature density
* Minimize learning curve for basic usage

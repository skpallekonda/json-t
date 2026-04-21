// =============================================================================
// json/mod.rs — JSON interoperability layer
// =============================================================================
// Provides bidirectional conversion between standard JSON and JsonT rows,
// guided by a JsonTSchema that supplies field names and types.
//
// # Reading JSON → JsonTRow
//   JsonReader::with_schema(schema).mode(JsonInputMode::Ndjson).build()
//
// # Writing JsonTRow → JSON
//   JsonWriter::with_schema(schema).mode(JsonOutputMode::Array).build()
// =============================================================================

pub(crate) mod parser;
pub mod reader;
pub mod writer;

pub use reader::{JsonInputMode, JsonReader, JsonReaderBuilder, MissingFieldPolicy, UnknownFieldPolicy};
pub use writer::{JsonOutputMode, JsonWriter, JsonWriterBuilder};

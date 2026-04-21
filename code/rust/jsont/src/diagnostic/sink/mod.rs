// =============================================================================
// diagnostic/sink/mod.rs
// =============================================================================

pub mod console;
pub mod file;
pub mod memory;

pub use console::ConsoleSink;
pub use file::FileSink;
pub use memory::MemorySink;

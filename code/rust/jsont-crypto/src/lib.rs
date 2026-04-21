// =============================================================================
// jsont-crypto — envelope encryption for JsonT streams
// =============================================================================
// Independent crate: no dependency on jsont core.
// jsont core depends on this crate for CryptoConfig and related types.
// =============================================================================

pub mod config;
pub mod context;
pub mod ecdh_config;
pub mod env_config;
pub mod error;
pub mod passthrough;
pub mod session;

pub(crate) mod cipher;
pub(crate) mod field_cipher;

pub use config::CryptoConfig;
pub use context::CryptoContext;
pub use ecdh_config::EcdhCryptoConfig;
pub use env_config::EnvCryptoConfig;
pub use error::CryptoError;
pub use passthrough::PassthroughCryptoConfig;
pub use session::{CipherSession, EncryptedField};

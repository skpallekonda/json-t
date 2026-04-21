use crate::field_cipher::FieldCipherHandler;
use crate::CryptoError;
use super::{AesGcmCipherHandler, Ascon128aCipherHandler, ChaCha20CipherHandler};

static AES_GCM:  AesGcmCipherHandler   = AesGcmCipherHandler;
static CHACHA20: ChaCha20CipherHandler = ChaCha20CipherHandler;
static ASCON:    Ascon128aCipherHandler = Ascon128aCipherHandler;

static HANDLERS: &[&(dyn FieldCipherHandler + Sync)] = &[&AES_GCM, &CHACHA20, &ASCON];

/// Locate the handler registered for the given `algo_ver` nibble.
///
/// Returns `Err(UnsupportedAlgorithm)` when no handler is registered.
pub(crate) fn find(algo_ver: u8) -> Result<&'static (dyn FieldCipherHandler + Sync), CryptoError> {
    HANDLERS
        .iter()
        .copied()
        .find(|h| h.algo_ver() == algo_ver)
        .ok_or(CryptoError::UnsupportedAlgorithm { version: algo_ver as u16 })
}

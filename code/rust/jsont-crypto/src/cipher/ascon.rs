use ascon_aead::aead::Aead;
use ascon_aead::{Ascon128a, NewAead};

use crate::field_cipher::FieldCipherHandler;
use crate::CryptoError;

/// ASCON-128a cipher handler (algo_ver = 3).
///
/// ASCON-128a uses a 128-bit key. Only the first 16 bytes of the supplied
/// DEK or KEK are used; the remaining bytes are ignored.
pub(crate) struct Ascon128aCipherHandler;

impl FieldCipherHandler for Ascon128aCipherHandler {
    fn algo_ver(&self) -> u8 { 3 }
    fn iv_len(&self) -> usize { 16 }

    fn encrypt(&self, key: &[u8], iv: &[u8], plaintext: &[u8]) -> Result<Vec<u8>, CryptoError> {
        let key   = ascon_aead::Key::<Ascon128a>::from_slice(&key[..16]);
        let nonce = ascon_aead::Nonce::<Ascon128a>::from_slice(iv);
        Ascon128a::new(key).encrypt(nonce, plaintext).map_err(|_| CryptoError::EncryptFailed {
            field:  String::new(),
            reason: "ASCON-128a encryption failed".to_string(),
        })
    }

    fn decrypt(&self, key: &[u8], iv: &[u8], ciphertext: &[u8]) -> Result<Vec<u8>, CryptoError> {
        let key   = ascon_aead::Key::<Ascon128a>::from_slice(&key[..16]);
        let nonce = ascon_aead::Nonce::<Ascon128a>::from_slice(iv);
        Ascon128a::new(key).decrypt(nonce, ciphertext).map_err(|_| CryptoError::DecryptFailed {
            field:  String::new(),
            reason: "ASCON-128a decryption failed".to_string(),
        })
    }
}

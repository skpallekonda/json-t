use chacha20poly1305::aead::Aead;
use chacha20poly1305::{ChaCha20Poly1305, Key, KeyInit, Nonce};

use crate::field_cipher::FieldCipherHandler;
use crate::CryptoError;

pub(crate) struct ChaCha20CipherHandler;

impl FieldCipherHandler for ChaCha20CipherHandler {
    fn algo_ver(&self) -> u8 { 2 }
    fn iv_len(&self) -> usize { 12 }

    fn encrypt(&self, key: &[u8], iv: &[u8], plaintext: &[u8]) -> Result<Vec<u8>, CryptoError> {
        let key    = Key::from_slice(key);
        let cipher = ChaCha20Poly1305::new(key);
        let nonce  = Nonce::from_slice(iv);
        cipher.encrypt(nonce, plaintext).map_err(|_| CryptoError::EncryptFailed {
            field:  String::new(),
            reason: "ChaCha20-Poly1305 encryption failed".to_string(),
        })
    }

    fn decrypt(&self, key: &[u8], iv: &[u8], ciphertext: &[u8]) -> Result<Vec<u8>, CryptoError> {
        let key    = Key::from_slice(key);
        let cipher = ChaCha20Poly1305::new(key);
        let nonce  = Nonce::from_slice(iv);
        cipher.decrypt(nonce, ciphertext).map_err(|_| CryptoError::DecryptFailed {
            field:  String::new(),
            reason: "ChaCha20-Poly1305 decryption failed".to_string(),
        })
    }
}

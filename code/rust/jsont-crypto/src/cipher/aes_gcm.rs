use aes_gcm::aead::Aead;
use aes_gcm::{Aes256Gcm, Key, KeyInit, Nonce};

use crate::field_cipher::FieldCipherHandler;
use crate::CryptoError;

pub(crate) struct AesGcmCipherHandler;

impl FieldCipherHandler for AesGcmCipherHandler {
    fn algo_ver(&self) -> u8 { 1 }
    fn iv_len(&self) -> usize { 12 }

    fn encrypt(&self, key: &[u8], iv: &[u8], plaintext: &[u8]) -> Result<Vec<u8>, CryptoError> {
        let key    = Key::<Aes256Gcm>::from_slice(key);
        let cipher = Aes256Gcm::new(key);
        let nonce  = Nonce::from_slice(iv);
        cipher.encrypt(nonce, plaintext).map_err(|_| CryptoError::EncryptFailed {
            field:  String::new(),
            reason: "AES-256-GCM encryption failed".to_string(),
        })
    }

    fn decrypt(&self, key: &[u8], iv: &[u8], ciphertext: &[u8]) -> Result<Vec<u8>, CryptoError> {
        let key    = Key::<Aes256Gcm>::from_slice(key);
        let cipher = Aes256Gcm::new(key);
        let nonce  = Nonce::from_slice(iv);
        cipher.decrypt(nonce, ciphertext).map_err(|_| CryptoError::DecryptFailed {
            field:  String::new(),
            reason: "AES-256-GCM decryption failed (auth tag mismatch or corrupt data)".to_string(),
        })
    }
}

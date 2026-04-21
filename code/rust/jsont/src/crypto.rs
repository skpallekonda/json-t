// =============================================================================
// crypto.rs — re-exports from jsont-crypto + EncryptHeader and payload helpers
// =============================================================================
// CryptoConfig, CryptoError, PassthroughCryptoConfig, CryptoContext, and
// EnvCryptoConfig all live in the jsont-crypto crate. They are re-exported
// here so the public API of jsont remains unchanged.
// =============================================================================

pub use jsont_crypto::{
    CipherSession, CryptoConfig, CryptoContext, CryptoError, EcdhCryptoConfig, EncryptedField,
    EnvCryptoConfig, PassthroughCryptoConfig,
};

// =============================================================================
// EncryptHeader detection and construction
// =============================================================================
// An encrypted JsonT stream begins with an EncryptHeader row:
//
//   {"ENCRYPTED_HEADER", <version:u16>, <length:u32>, "<base64:enc_dek>"}
//
// The row scanner emits all numbers as D64(f64) (no schema context), so both
// integer types and D64 are accepted when extracting version and length.
// =============================================================================

use crate::model::data::{JsonTNumber, JsonTRow, JsonTString, JsonTValue};
use base64::Engine as _;

/// If `row` is a valid `EncryptHeader` row, returns the derived [`CryptoContext`].
///
/// Returns `None` if the row does not conform to the EncryptHeader structure.
pub fn try_parse_encrypt_header(row: &JsonTRow) -> Option<CryptoContext> {
    if row.fields.len() != 4 {
        return None;
    }

    // Field 0: type constant "ENCRYPTED_HEADER"
    match &row.fields[0] {
        JsonTValue::Str(JsonTString::Plain(s)) if s == "ENCRYPTED_HEADER" => {}
        _ => return None,
    }

    // Field 1: version (u16 or D64 from scanner)
    let version = extract_as_u16(&row.fields[1])?;

    // Field 2: enc_dek byte count (u32 or D64 from scanner)
    let expected_len = extract_as_u32(&row.fields[2])? as usize;

    // Field 3: enc_dek as plain base64 string
    let enc_dek = match &row.fields[3] {
        JsonTValue::Str(JsonTString::Plain(b64)) => {
            base64::engine::general_purpose::STANDARD
                .decode(b64.as_str())
                .ok()?
        }
        _ => return None,
    };

    if enc_dek.len() != expected_len {
        return None;
    }

    Some(CryptoContext::new(version, enc_dek))
}

/// Build the `EncryptHeader` row for writing to the top of an encrypted stream.
///
/// Uses typed integer values (`U16` / `U32`) so the writer emits integer literals
/// rather than floats.
pub fn build_encrypt_header_row(ctx: &CryptoContext) -> JsonTRow {
    let enc_dek_b64 =
        base64::engine::general_purpose::STANDARD.encode(&ctx.enc_dek);
    JsonTRow {
        fields: vec![
            JsonTValue::Str(JsonTString::Plain("ENCRYPTED_HEADER".to_string())),
            JsonTValue::Number(JsonTNumber::U16(ctx.version)),
            JsonTValue::Number(JsonTNumber::U32(ctx.enc_dek.len() as u32)),
            JsonTValue::Str(JsonTString::Plain(enc_dek_b64)),
        ],
        schema: None,
    }
}

// =============================================================================
// Per-field payload framing
// =============================================================================
//
// Binary layout (big-endian):
//   [2 bytes: len_iv  (u16) ]  — byte count of iv
//   [4 bytes: len_digest (u32)] — BIT count of digest (256 for SHA-256)
//   [len_iv bytes: iv        ]  — unique nonce for this field
//   [len_digest/8 bytes: digest] — SHA-256 hash of original plaintext
//   [remaining: enc_content  ]  — ciphertext+tag (length inferred from total)
// =============================================================================

/// Assemble the per-field binary payload from its components.
pub fn assemble_field_payload(iv: &[u8], digest: &[u8], enc_content: &[u8]) -> Vec<u8> {
    let len_iv         = iv.len() as u16;
    let len_digest_bits = (digest.len() * 8) as u32;
    let mut out = Vec::with_capacity(2 + 4 + iv.len() + digest.len() + enc_content.len());
    out.extend_from_slice(&len_iv.to_be_bytes());
    out.extend_from_slice(&len_digest_bits.to_be_bytes());
    out.extend_from_slice(iv);
    out.extend_from_slice(digest);
    out.extend_from_slice(enc_content);
    out
}

/// Parse a per-field binary payload into `(iv, digest, enc_content)`.
///
/// Returns `Err(CryptoError::MalformedPayload)` if the payload is too short or
/// the length prefix is inconsistent.
pub fn parse_field_payload<'a>(
    payload: &'a [u8],
    field: &str,
) -> Result<(&'a [u8], &'a [u8], &'a [u8]), CryptoError> {
    if payload.len() < 6 {
        return Err(CryptoError::MalformedPayload {
            field:  field.to_string(),
            reason: format!("payload too short: {} bytes (need at least 6)", payload.len()),
        });
    }
    let len_iv         = u16::from_be_bytes([payload[0], payload[1]]) as usize;
    let len_digest_bits = u32::from_be_bytes([payload[2], payload[3], payload[4], payload[5]]) as usize;
    let len_digest     = len_digest_bits / 8;
    let iv_start       = 6;
    let iv_end         = iv_start + len_iv;
    let digest_end     = iv_end + len_digest;

    if payload.len() < digest_end {
        return Err(CryptoError::MalformedPayload {
            field:  field.to_string(),
            reason: format!(
                "payload too short for iv+digest: need {} bytes, have {}",
                digest_end, payload.len()
            ),
        });
    }

    Ok((
        &payload[iv_start..iv_end],
        &payload[iv_end..digest_end],
        &payload[digest_end..],
    ))
}

// =============================================================================
// Numeric extraction helpers (for EncryptHeader parsing)
// =============================================================================

fn extract_as_u16(v: &JsonTValue) -> Option<u16> {
    match v {
        JsonTValue::Number(JsonTNumber::U16(n)) => Some(*n),
        JsonTValue::Number(JsonTNumber::D64(f))
            if f.fract() == 0.0 && *f >= 0.0 && *f <= u16::MAX as f64 =>
        {
            Some(*f as u16)
        }
        _ => None,
    }
}

fn extract_as_u32(v: &JsonTValue) -> Option<u32> {
    match v {
        JsonTValue::Number(JsonTNumber::U32(n)) => Some(*n),
        JsonTValue::Number(JsonTNumber::D64(f))
            if f.fract() == 0.0 && *f >= 0.0 && *f <= u32::MAX as f64 =>
        {
            Some(*f as u32)
        }
        _ => None,
    }
}

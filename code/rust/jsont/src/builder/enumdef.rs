// =============================================================================
// builder/enumdef.rs — JsonTEnumBuilder
// =============================================================================

use crate::error::{BuildError, JsonTError};
use crate::model::enumdef::JsonTEnum;

/// Builder for [`JsonTEnum`].
#[derive(Debug, Default)]
pub struct JsonTEnumBuilder {
    name:   Option<String>,
    values: Vec<String>,
}

impl JsonTEnumBuilder {
    pub fn new(name: impl Into<String>) -> Self {
        Self {
            name: Some(name.into()),
            values: Vec::new(),
        }
    }

    /// Append an enum constant value (must be a CONSTID — all uppercase, 2+ chars).
    /// Returns an error if the value is a duplicate.
    pub fn value(mut self, constant: impl Into<String>) -> Result<Self, JsonTError> {
        let c = constant.into();
        if self.values.contains(&c) {
            return Err(BuildError::DuplicateEnumValue(c).into());
        }
        self.values.push(c);
        Ok(self)
    }

    pub fn build(self) -> Result<JsonTEnum, JsonTError> {
        let name = self.name
            .ok_or_else(|| BuildError::MissingField("enum name".into()))?;

        if self.values.is_empty() {
            return Err(BuildError::MissingField(
                format!("enum '{}' must have at least one value", name)
            ).into());
        }

        Ok(JsonTEnum { name, values: self.values })
    }
}

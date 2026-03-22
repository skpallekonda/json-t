// =============================================================================
// builder/catalog.rs — JsonTCatalogBuilder
// =============================================================================

use crate::error::{BuildError, JsonTError};
use crate::model::namespace::JsonTCatalog;
use crate::model::schema::JsonTSchema;
use crate::model::enumdef::JsonTEnum;
use crate::builder::schema::JsonTSchemaBuilder;
use crate::builder::enumdef::JsonTEnumBuilder;

/// Builder for [`JsonTCatalog`].
#[derive(Debug, Default)]
pub struct JsonTCatalogBuilder {
    schemas: Vec<JsonTSchema>,
    enums:   Vec<JsonTEnum>,
}

impl JsonTCatalogBuilder {
    pub fn new() -> Self {
        Self::default()
    }

    /// Append a fully-built schema.
    /// Returns an error if a schema with the same name already exists.
    pub fn schema(mut self, schema: JsonTSchema) -> Result<Self, JsonTError> {
        if self.schemas.iter().any(|s| s.name == schema.name) {
            return Err(BuildError::DuplicateSchemaName(schema.name).into());
        }
        self.schemas.push(schema);
        Ok(self)
    }

    /// Append a schema from its builder.
    pub fn schema_from(mut self, builder: JsonTSchemaBuilder) -> Result<Self, JsonTError> {
        let schema = builder.build()?;
        if self.schemas.iter().any(|s| s.name == schema.name) {
            return Err(BuildError::DuplicateSchemaName(schema.name).into());
        }
        self.schemas.push(schema);
        Ok(self)
    }

    /// Append a fully-built enum definition.
    pub fn enum_def(mut self, enum_def: JsonTEnum) -> Self {
        self.enums.push(enum_def);
        self
    }

    /// Append an enum definition from its builder.
    pub fn enum_from(mut self, builder: JsonTEnumBuilder) -> Result<Self, JsonTError> {
        self.enums.push(builder.build()?);
        Ok(self)
    }

    pub fn build(self) -> Result<JsonTCatalog, JsonTError> {
        Ok(JsonTCatalog {
            schemas: self.schemas,
            enums:   self.enums,
        })
    }
}

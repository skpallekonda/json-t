// =============================================================================
// builder/namespace.rs — JsonTNamespaceBuilder
// =============================================================================

use crate::error::{BuildError, JsonTError};
use crate::model::namespace::{JsonTNamespace, JsonTCatalog};
use crate::builder::catalog::JsonTCatalogBuilder;

/// Builder for [`JsonTNamespace`].
///
/// ```
/// use jsont::builder::namespace::JsonTNamespaceBuilder;
/// use jsont::builder::catalog::JsonTCatalogBuilder;
///
/// let ns = JsonTNamespaceBuilder::new("https://example.com", "1.0")
///     .data_schema("Person")
///     .catalog(JsonTCatalogBuilder::new().build().unwrap())
///     .build()
///     .unwrap();
/// ```
#[derive(Debug, Default)]
pub struct JsonTNamespaceBuilder {
    base_url:    Option<String>,
    version:     Option<String>,
    data_schema: Option<String>,
    catalogs:    Vec<JsonTCatalog>,
}

impl JsonTNamespaceBuilder {
    pub fn new(base_url: impl Into<String>, version: impl Into<String>) -> Self {
        Self {
            base_url:    Some(base_url.into()),
            version:     Some(version.into()),
            data_schema: None,
            catalogs:    Vec::new(),
        }
    }

    pub fn base_url(mut self, url: impl Into<String>) -> Self {
        self.base_url = Some(url.into());
        self
    }

    pub fn version(mut self, ver: impl Into<String>) -> Self {
        self.version = Some(ver.into());
        self
    }

    pub fn data_schema(mut self, schema_name: impl Into<String>) -> Self {
        self.data_schema = Some(schema_name.into());
        self
    }

    /// Append a fully-built catalog.
    pub fn catalog(mut self, catalog: JsonTCatalog) -> Self {
        self.catalogs.push(catalog);
        self
    }

    /// Append a catalog by supplying its builder (convenience — calls build() internally).
    pub fn catalog_from(mut self, builder: JsonTCatalogBuilder) -> Result<Self, JsonTError> {
        self.catalogs.push(builder.build()?);
        Ok(self)
    }

    /// Consume the builder and produce a [`JsonTNamespace`].
    ///
    /// # Errors
    /// - [`BuildError::MissingField`] if `base_url`, `version`, or `data_schema` were not set.
    pub fn build(self) -> Result<JsonTNamespace, JsonTError> {
        let base_url = self.base_url
            .ok_or_else(|| BuildError::MissingField("base_url".into()))?;
        let version = self.version
            .ok_or_else(|| BuildError::MissingField("version".into()))?;
        let data_schema = self.data_schema
            .ok_or_else(|| BuildError::MissingField("data_schema".into()))?;

        Ok(JsonTNamespace {
            base_url,
            version,
            catalogs: self.catalogs,
            data_schema,
        })
    }
}

// =============================================================================
// parse/mod.rs
// =============================================================================
// Stub implementations of the Parseable trait for top-level types.
// Concrete parse logic (pest tree walking) is left for implementation;
// the structures, trait signatures, and error paths are all in place.
// =============================================================================

pub mod expr;
pub(crate) mod rows;

use pest::Parser;
use pest_derive::Parser;

use crate::Parseable;
use crate::error::{JsonTError, ParseError};
use crate::model::namespace::{JsonTNamespace, JsonTCatalog};
use crate::model::data::{JsonTValue, JsonTRow, JsonTArray};
use crate::model::validation::JsonTExpression;
use crate::model::schema::{JsonTSchema, SchemaOperation, RenamePair, FieldPath};
use crate::model::field::{JsonTField, ScalarType};
use crate::model::enumdef::JsonTEnum;
use crate::model::constraint::{
    JsonTConstraint, ValueConstraintKey, LengthConstraintKey,
    ArrayConstraintNbr, ArrayConstraintBool, ArrayItemsConstraint,
};
use crate::builder::{
    namespace::JsonTNamespaceBuilder,
    catalog::JsonTCatalogBuilder,
    schema::JsonTSchemaBuilder,
    field::JsonTFieldBuilder,
    enumdef::JsonTEnumBuilder,
};
use pest::iterators::Pair;

// ── Pest parser derivation ────────────────────────────────────────────────────

/// The pest-derived parser.  The grammar file path is relative to src/.
#[derive(Parser)]
#[grammar = "jsont.pest"]
pub(crate) struct JsonTParser;

// ── Parseable impls ───────────────────────────────────────────────────────────

impl Parseable for JsonTNamespace {
    fn parse(input: &str) -> Result<Self, JsonTError> {
        let mut pairs = JsonTParser::parse(Rule::json_t, input)
            .map_err(|e| ParseError::Pest(e.to_string()))?;

        let first = pairs.next()
            .ok_or_else(|| ParseError::Unexpected("empty input".into()))?;

        // json_t = { SOI ~ namespace? ~ data_rows? ~ EOI }
        let mut inner = first.into_inner();
        let mut mb_ns = None;

        while let Some(pair) = inner.next() {
            match pair.as_rule() {
                Rule::namespace => {
                    mb_ns = Some(walk_namespace(pair)?);
                }
                Rule::data_rows => {
                    // Data rows are the sibling of the namespace in the top-level
                    // json_t rule. JsonTNamespace models schema metadata only and
                    // does not carry row data. Parse data rows separately using
                    // Vec<JsonTRow>::parse() on the same source, or on a rows-only
                    // input. No action needed here.
                }
                Rule::EOI => break,
                _ => {}
            }
        }

        mb_ns.ok_or_else(|| ParseError::Unexpected("no namespace found in input".into()).into())
    }
}

impl Parseable for JsonTExpression {
    fn parse(input: &str) -> Result<Self, JsonTError> {
        let pairs = JsonTParser::parse(Rule::expression, input)
            .map_err(|e| ParseError::Pest(e.to_string()))?;

        expr::build_expression(pairs)
    }
}

impl Parseable for JsonTValue {
    fn parse(input: &str) -> Result<Self, JsonTError> {
        let mut pairs = JsonTParser::parse(Rule::value, input)
            .map_err(|e| ParseError::Pest(e.to_string()))?;

        let pair = pairs.next()
            .ok_or_else(|| ParseError::Unexpected("empty value input".into()))?;

        walk_value(pair)
    }
}

/// Parse a sequence of data rows using the hand-written streaming scanner.
///
/// Input must contain data rows only — no namespace block.  Each
/// `{ v1, v2, ... }` maps to one `JsonTRow`.  Rows are separated by commas;
/// a single trailing comma after the last row is accepted.
///
/// All numeric literals are emitted as `JsonTValue::d64(f64)` because there
/// is no schema context available at parse time.
impl Parseable for Vec<JsonTRow> {
    fn parse(input: &str) -> Result<Self, JsonTError> {
        let mut row_vec = Vec::new();
        let count = rows::parse_rows(input, |row| row_vec.push(row))?;
        if count == 0 {
            return Err(ParseError::Unexpected("no data rows found in input".into()).into());
        }
        Ok(row_vec)
    }
}

// ── Tree-walking helpers (Phase 1) ────────────────────────────────────────────

fn walk_namespace(pair: Pair<Rule>) -> Result<JsonTNamespace, JsonTError> {
    let mut inner = pair.into_inner();
    
    // Literals like '{', ':', '[' are NOT produced by Pest for non-atomic rules.
    // The iterator will yield: kw_namespace, kw_base_url, ns_base_url, kw_version, ns_version, 
    // kw_catalogs, catalog, catalog..., kw_data_schema, SCHEMAID.

    let mut base_url = String::new();
    let mut version = String::new();
    let mut catalogs = Vec::new();
    let mut data_schema = String::new();

    while let Some(pair) = inner.next() {
        match pair.as_rule() {
            Rule::ns_base_url => {
                base_url = pair.as_str().trim_matches('"').to_string();
            }
            Rule::ns_version => {
                version = pair.as_str().trim_matches('"').to_string();
            }
            Rule::catalog => {
                catalogs.push(walk_catalog(pair)?);
            }
            Rule::SCHEMAID => {
                data_schema = pair.as_str().to_string();
            }
            _ => {}
        }
    }

    JsonTNamespaceBuilder::new(base_url, version)
        .data_schema(data_schema)
        .catalogs(catalogs) // We'll add this method to builder if missing, or use loop
        .build()
}

// Helper: extend JsonTNamespaceBuilder if needed, or just loop
impl JsonTNamespaceBuilder {
    fn catalogs(mut self, catalogs: Vec<JsonTCatalog>) -> Self {
        for c in catalogs {
            self = self.catalog(c);
        }
        self
    }
}

fn walk_catalog(pair: Pair<Rule>) -> Result<JsonTCatalog, JsonTError> {
    // catalog = { "{" ~ schemas_section ~ ("," ~ enums_section)? ~ "}" }
    let mut inner = pair.into_inner();
    let mut builder = JsonTCatalogBuilder::new();

    while let Some(pair) = inner.next() {
        match pair.as_rule() {
            Rule::schemas_section => {
                for entry_pair in pair.into_inner() {
                    if entry_pair.as_rule() == Rule::schema_entry {
                        builder = builder.schema(walk_schema_entry(entry_pair)?)?;
                    }
                }
            }
            Rule::enums_section => {
                for enum_pair in pair.into_inner() {
                    if enum_pair.as_rule() == Rule::enum_def {
                        builder = builder.enum_def(walk_enum_def(enum_pair)?);
                    }
                }
            }
            _ => {}
        }
    }
    builder.build()
}

fn walk_schema_entry(pair: Pair<Rule>) -> Result<JsonTSchema, JsonTError> {
    // schema_entry = { ns_schema_name ~ ":" ~ schema_definition }
    let mut inner = pair.into_inner();
    let name_pair = inner.next().unwrap();
    let name = name_pair.as_str().to_string();
    
    let def_pair = inner.next().unwrap();
    walk_schema_definition(name, def_pair)
}

fn walk_schema_definition(name: String, pair: Pair<Rule>) -> Result<JsonTSchema, JsonTError> {
    // schema_definition = { straight_schema | derived_schema }
    let inner = pair.into_inner().next().unwrap();
    match inner.as_rule() {
        Rule::straight_schema => walk_straight_schema(name, inner),
        Rule::derived_schema  => walk_derived_schema(name, inner),
        _ => unreachable!(),
    }
}

fn walk_straight_schema(name: String, pair: Pair<Rule>) -> Result<JsonTSchema, JsonTError> {
    // straight_schema = { "{" ~ field_block ~ ("," ~ validation_block)? ~ "}" }
    let mut inner = pair.into_inner();
    let mut builder = JsonTSchemaBuilder::straight(name);

    while let Some(pair) = inner.next() {
        match pair.as_rule() {
            Rule::field_block => {
                for field_pair in pair.into_inner() {
                    if field_pair.as_rule() == Rule::field_decl {
                        builder = builder.field(walk_field_decl(field_pair)?)?;
                    }
                }
            }
            Rule::validation_block => {
                // TODO: Phase 3
            }
            _ => {}
        }
    }
    builder.build()
}

fn walk_derived_schema(name: String, pair: Pair<Rule>) -> Result<JsonTSchema, JsonTError> {
    // derived_schema = { kw_from ~ ns_schema_name ~ "{" ~ operations_block ~ ("," ~ validation_block)? ~ "}" }
    // kw_from is atomic (not silent) — skip it, then take ns_schema_name, then operations_block.
    let mut inner = pair.into_inner();
    let first = inner.next().unwrap();
    let (from, ops_block) = if first.as_rule() == Rule::kw_from {
        let schema_name = inner.next().unwrap().as_str().to_string();
        (schema_name, inner.next().unwrap())
    } else {
        // If kw_from is somehow silent, first pair is ns_schema_name
        (first.as_str().to_string(), inner.next().unwrap())
    };

    let mut builder = JsonTSchemaBuilder::derived(&name, &from);
    for op_pair in ops_block.into_inner() {
        if op_pair.as_rule() == Rule::schema_operation {
            builder = builder.operation(walk_schema_operation(op_pair)?)?;
        }
    }
    builder.build()
}

fn walk_schema_operation(pair: Pair<Rule>) -> Result<SchemaOperation, JsonTError> {
    // schema_operation = { rename_operation | exclude_operation | project_operation | transform_operation | filter_operation }
    let inner = pair.into_inner().next().unwrap();
    match inner.as_rule() {
        Rule::rename_operation => {
            let pairs: Result<Vec<RenamePair>, _> = inner.into_inner()
                .filter(|p| p.as_rule() == Rule::rename_pair)
                .map(walk_rename_pair)
                .collect();
            Ok(SchemaOperation::Rename(pairs?))
        }
        Rule::exclude_operation => {
            let paths = walk_field_path_list(inner.into_inner().next().unwrap())?;
            Ok(SchemaOperation::Exclude(paths))
        }
        Rule::project_operation => {
            let paths = walk_field_path_list(inner.into_inner().next().unwrap())?;
            Ok(SchemaOperation::Project(paths))
        }
        Rule::filter_operation => {
            // filter_operation = { kw_filter ~ expression } — skip kw_filter
            let expr_pair = inner.into_inner()
                .find(|p| p.as_rule() == Rule::expression)
                .ok_or_else(|| ParseError::Unexpected("filter missing expression".into()))?;
            let expr = crate::parse::expr::build_expr_from_pair(expr_pair)?;
            let refs = crate::transform::collect_field_refs(&expr);
            Ok(SchemaOperation::Filter { expr, refs })
        }
        Rule::transform_operation => {
            // transform_operation = { kw_transform ~ field_path ~ "=" ~ expression } — skip kw_transform
            let mut op_inner = inner.into_inner()
                .filter(|p| p.as_rule() != Rule::kw_transform);
            let target = walk_field_path(op_inner.next().unwrap())?;
            let expr_pair = op_inner
                .find(|p| p.as_rule() == Rule::expression)
                .ok_or_else(|| ParseError::Unexpected("transform missing expression".into()))?;
            let expr = crate::parse::expr::build_expr_from_pair(expr_pair)?;
            let refs = crate::transform::collect_field_refs(&expr);
            Ok(SchemaOperation::Transform { target, expr, refs })
        }
        other => Err(ParseError::Unexpected(format!("unknown schema operation: {:?}", other)).into()),
    }
}

fn walk_rename_pair(pair: Pair<Rule>) -> Result<RenamePair, JsonTError> {
    // rename_pair = { field_path ~ kw_as ~ ns_field_name }
    // kw_as is atomic (not silent) so it appears as a pair — skip it.
    let mut inner = pair.into_inner();
    let from = walk_field_path(inner.next().unwrap())?;
    // skip kw_as
    let next = inner.next().unwrap();
    let to_pair = if next.as_rule() == Rule::kw_as {
        inner.next().unwrap()
    } else {
        next
    };
    Ok(RenamePair { from, to: to_pair.as_str().to_string() })
}

fn walk_field_path(pair: Pair<Rule>) -> Result<FieldPath, JsonTError> {
    // field_path = { ns_field_name ~ ("." ~ ns_field_name)* }
    let segments: Vec<String> = pair.into_inner()
        .map(|p| p.as_str().to_string())
        .collect();
    Ok(FieldPath::new(segments))
}

fn walk_field_path_list(pair: Pair<Rule>) -> Result<Vec<FieldPath>, JsonTError> {
    // field_path_list = { field_path ~ ("," ~ field_path)* }
    pair.into_inner()
        .filter(|p| p.as_rule() == Rule::field_path)
        .map(walk_field_path)
        .collect()
}

fn walk_field_decl(pair: Pair<Rule>) -> Result<JsonTField, JsonTError> {
    // field_decl = { scalar_field_decl | object_field_decl }
    let inner = pair.into_inner().next().unwrap();
    match inner.as_rule() {
        Rule::scalar_field_decl => walk_scalar_field(inner),
        Rule::object_field_decl => walk_object_field(inner),
        _ => unreachable!(),
    }
}

fn walk_scalar_field(pair: Pair<Rule>) -> Result<JsonTField, JsonTError> {
    // scalar_field_decl = { scalar_type_ref ~ ":" ~ ns_field_name ~ optional_mark? ... }
    let mut inner = pair.into_inner();
    
    let type_ref_pair = inner.next().unwrap();
    let (scalar_type, is_array) = walk_scalar_type_ref(type_ref_pair)?;
    
    let name_pair = inner.next().unwrap();
    let name = name_pair.as_str().to_string();
    
    let mut builder = JsonTFieldBuilder::scalar(name, scalar_type);
    if is_array { builder = builder.as_array(); }

    while let Some(pair) = inner.next() {
        match pair.as_rule() {
            Rule::optional_mark => { builder = builder.optional(); }
            Rule::scalar_attribute => {
                let attr = pair.into_inner().next().unwrap();
                match attr.as_rule() {
                    Rule::scalar_constraint => {
                        let sc = attr.into_inner().next().unwrap();
                        match sc.as_rule() {
                            Rule::value_constraint  => walk_value_constraints(sc, &mut builder)?,
                            Rule::length_constraint => walk_length_constraints(sc, &mut builder)?,
                            Rule::regex_constraint  => {
                                // regex_constraint = { (kw_regex | kw_pattern) ~ "=" ~ string }
                                let mut inner = sc.into_inner();
                                let _kw = inner.next();
                                let pattern = walk_string(inner.next().unwrap())?;
                                builder = builder.constraint(JsonTConstraint::Regex(pattern));
                            }
                            Rule::common_constraint => walk_common_constraint(sc, &mut builder)?,
                            _ => {}
                        }
                    }
                    Rule::default_value  => {
                        // default_value = { kw_default ~ (scalar_value | null_value) }
                        let mut inner = attr.into_inner();
                        let _kw = inner.next();
                        let val_pair = inner.next().unwrap();
                        let val = walk_value(val_pair)?;
                        builder = builder.default_value(val);
                    }
                    Rule::constant_value => {
                        let mut inner = attr.into_inner();
                        let _kw = inner.next();
                        let val_pair = inner.next().unwrap();
                        let val = walk_value(val_pair)?;
                        builder = builder.constant_value(val);
                    }
                    _ => {}
                }
            }
            _ => {}
        }
    }
    builder.build()
}

fn walk_object_field(pair: Pair<Rule>) -> Result<JsonTField, JsonTError> {
    // object_field_decl = { object_type_ref ~ ":" ~ ns_field_name ~ optional_mark? ... }
    let mut inner = pair.into_inner();
    
    let type_ref_pair = inner.next().unwrap();
    let (schema_ref, is_array) = walk_object_type_ref(type_ref_pair)?;
    
    let name_pair = inner.next().unwrap();
    let name = name_pair.as_str().to_string();
    
    let mut builder = JsonTFieldBuilder::object(name, schema_ref);
    if is_array { builder = builder.as_array(); }

    while let Some(pair) = inner.next() {
        match pair.as_rule() {
            Rule::optional_mark => { builder = builder.optional(); }
            Rule::object_attribute => {
                let attr = pair.into_inner().next().unwrap();
                // object_attribute = { common_constraint }
                walk_common_constraint(attr, &mut builder)?;
            }
            _ => {}
        }
    }
    builder.build()
}

fn walk_common_constraint(pair: Pair<Rule>, builder: &mut JsonTFieldBuilder) -> Result<(), JsonTError> {
    // common_constraint = { required_constraint | array_items_constraint }
    let inner = pair.into_inner().next().unwrap();
    match inner.as_rule() {
        Rule::required_constraint => {
            let mut inner = inner.into_inner();
            let _kw = inner.next(); // kw_required
            let val = walk_boolean(inner.next().unwrap())?;
            *builder = builder.clone().constraint(JsonTConstraint::Required(val));
        }
        Rule::array_items_constraint => walk_array_items_constraints(inner, builder)?,
        _ => {}
    }
    Ok(())
}

fn walk_value_constraints(pair: Pair<Rule>, builder: &mut JsonTFieldBuilder) -> Result<(), JsonTError> {
    // value_constraint = { "(" ~ value_constraint_option ~ ... }
    for opt in pair.into_inner() {
        // value_constraint_option = { value_constraint_kw ~ "=" ~ number }
        let mut inner = opt.into_inner();
        let kw_pair = inner.next().unwrap();
        let val_pair = inner.next().unwrap();
        
        let key = ValueConstraintKey::from_keyword(kw_pair.as_str())
            .ok_or_else(|| ParseError::Unexpected(format!("unknown value constraint: {}", kw_pair.as_str())))?;
        let value = walk_number(val_pair)?;
        
        *builder = builder.clone().constraint(JsonTConstraint::Value { key, value });
    }
    Ok(())
}

fn walk_length_constraints(pair: Pair<Rule>, builder: &mut JsonTFieldBuilder) -> Result<(), JsonTError> {
    for opt in pair.into_inner() {
        let mut inner = opt.into_inner();
        let kw_pair = inner.next().unwrap();
        let val_pair = inner.next().unwrap();
        
        let key = LengthConstraintKey::from_keyword(kw_pair.as_str())
            .ok_or_else(|| ParseError::Unexpected(format!("unknown length constraint: {}", kw_pair.as_str())))?;
        let value = walk_number(val_pair)? as u64;
        
        *builder = builder.clone().constraint(JsonTConstraint::Length { key, value });
    }
    Ok(())
}

fn walk_array_items_constraints(pair: Pair<Rule>, builder: &mut JsonTFieldBuilder) -> Result<(), JsonTError> {
    for opt in pair.into_inner() {
        let inner = opt.into_inner().next().unwrap();
        match inner.as_rule() {
            Rule::array_items_constraint_option => {
                // Wait, logic depends on variant
                let mut inner = inner.into_inner();
                let key_pair = inner.next().unwrap();
                let val_pair = inner.next().unwrap();
                
                match key_pair.as_rule() {
                    Rule::array_constraint_nbr => {
                        let key = ArrayConstraintNbr::from_keyword(key_pair.as_str()).unwrap();
                        let value = walk_number(val_pair)? as u64;
                        *builder = builder.clone().constraint(JsonTConstraint::ArrayItems(
                            ArrayItemsConstraint::Numeric { key, value }
                        ));
                    }
                    Rule::array_constraint_bool => {
                        let key = ArrayConstraintBool::AllowNullItems;
                        let value = walk_boolean(val_pair)?;
                        *builder = builder.clone().constraint(JsonTConstraint::ArrayItems(
                            ArrayItemsConstraint::Boolean { key, value }
                        ));
                    }
                    _ => {}
                }
            }
            _ => {}
        }
    }
    Ok(())
}

fn walk_number(pair: Pair<Rule>) -> Result<f64, JsonTError> {
    pair.as_str().parse::<f64>()
        .map_err(|e| ParseError::Unexpected(format!("invalid number '{}': {}", pair.as_str(), e)).into())
}

fn walk_boolean(pair: Pair<Rule>) -> Result<bool, JsonTError> {
    match pair.as_str() {
        "true" => Ok(true),
        "false" => Ok(false),
        _ => unreachable!(),
    }
}

fn walk_string(pair: Pair<Rule>) -> Result<String, JsonTError> {
    // string = { "\"" ~ ... | "'" ~ ... }
    Ok(pair.as_str().trim_matches(|c| c == '"' || c == '\'').to_string())
}

fn walk_scalar_type_ref(pair: Pair<Rule>) -> Result<(ScalarType, bool), JsonTError> {
    // scalar_type_ref  = { scalar_types ~ array_suffix? }
    let mut inner = pair.into_inner();
    let types_pair = inner.next().unwrap();
    let scalar_type = walk_scalar_types(types_pair)?;
    let is_array = inner.next().is_some();
    Ok((scalar_type, is_array))
}

fn walk_scalar_types(pair: Pair<Rule>) -> Result<ScalarType, JsonTError> {
    let inner = pair.into_inner().next().unwrap();
    // numeric_types | boolean_types | string_types ...
    let kw_pair = inner.into_inner().next().unwrap();
    ScalarType::from_keyword(kw_pair.as_str())
        .ok_or_else(|| ParseError::UnknownFieldType(kw_pair.as_str().to_string()).into())
}

fn walk_object_type_ref(pair: Pair<Rule>) -> Result<(String, bool), JsonTError> {
    // object_type_ref = { "<" ~ object_type_name ~ ">" ~ array_suffix? }
    let mut inner = pair.into_inner();
    let name_pair = inner.next().unwrap(); // object_type_name
    let schema_ref = name_pair.as_str().to_string();
    let is_array = inner.next().is_some();
    Ok((schema_ref, is_array))
}

fn walk_enum_def(pair: Pair<Rule>) -> Result<JsonTEnum, JsonTError> {
    // enum_def = { ns_enum_name ~ ":" ~ enum_body }
    let mut inner = pair.into_inner();
    
    // 1. ns_enum_name -> { SCHEMAID }
    let name_pair = inner.next().unwrap();
    let name = name_pair.into_inner().next().unwrap().as_str().to_string();
    
    let mut builder = JsonTEnumBuilder::new(name);
    
    // 2. enum_body -> { "[" ~ enum_value_constant ~ ... }
    if let Some(body_pair) = inner.next() {
        for ev_constant in body_pair.into_inner() {
            // enum_value_constant -> { CONSTID }
            let const_id = ev_constant.into_inner().next().unwrap();
            builder = builder.value(const_id.as_str())?;
        }
    }
    
    builder.build()
}

fn walk_object_value(pair: Pair<Rule>) -> Result<JsonTRow, JsonTError> {
    // object_value = { "{" ~ value ~ ("," ~ value)* ~ "}" }
    // Inline punctuation ("{", ",", "}") produces no pairs; only value children appear.
    let values: Result<Vec<JsonTValue>, JsonTError> = pair.into_inner()
        .map(walk_value)
        .collect();
    Ok(JsonTRow::new(values?))
}

fn walk_array_value(pair: Pair<Rule>) -> Result<JsonTArray, JsonTError> {
    // array_value = { "[" ~ value ~ ("," ~ value)* ~ "]" }
    let items: Result<Vec<JsonTValue>, JsonTError> = pair.into_inner()
        .map(walk_value)
        .collect();
    Ok(JsonTArray::new(items?))
}

fn walk_value(pair: Pair<Rule>) -> Result<JsonTValue, JsonTError> {
    match pair.as_rule() {
        // Transparent wrapper rules — descend one level.
        Rule::value | Rule::scalar_value | Rule::literal => {
            walk_value(pair.into_inner().next().unwrap())
        }
        Rule::string            => Ok(JsonTValue::str(walk_string(pair)?)),
        Rule::number            => Ok(JsonTValue::d64(walk_number(pair)?)),
        Rule::boolean           => Ok(JsonTValue::bool(walk_boolean(pair)?)),
        Rule::null_value        => Ok(JsonTValue::Null),
        Rule::unspecified_value => Ok(JsonTValue::Unspecified),
        Rule::enum_value        => Ok(JsonTValue::Enum(pair.as_str().to_string())),
        Rule::object_value      => Ok(JsonTValue::Object(walk_object_value(pair)?)),
        Rule::array_value       => Ok(JsonTValue::Array(walk_array_value(pair)?)),
        _ => panic!("walk_value hit unknown rule: {:?}", pair.as_rule()),
    }
}

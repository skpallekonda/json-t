// =============================================================================
// stringify/mod.rs — Stringification impls for all model types
// =============================================================================
// Produces valid JsonT source text from the in-memory model.
// Pretty-printing indents with `options.indent` spaces per level.
// =============================================================================

use crate::{Stringification, StringifyOptions};
use crate::model::namespace::{JsonTNamespace, JsonTCatalog};
use crate::model::schema::{JsonTSchema, SchemaKind, SchemaOperation, FieldPath};
use crate::model::field::{JsonTField, JsonTFieldKind, JsonTFieldType};
use crate::model::constraint::{JsonTConstraint, ArrayItemsConstraint};
use crate::model::validation::{JsonTValidationBlock, JsonTRule, JsonTExpression};
use crate::model::enumdef::JsonTEnum;
use crate::model::data::{JsonTValue, JsonTNumber, JsonTRow, JsonTArray};

// ── Indentation helper ────────────────────────────────────────────────────────

struct Ctx<'a> {
    opts:  &'a StringifyOptions,
    depth: usize,
}

impl<'a> Ctx<'a> {
    fn new(opts: &'a StringifyOptions) -> Self { Self { opts, depth: 0 } }
    fn indent(&self) -> String {
        if self.opts.pretty {
            " ".repeat(self.depth * self.opts.indent)
        } else {
            String::new()
        }
    }
    fn nl(&self) -> &'static str { if self.opts.pretty { "\n" } else { "" } }
    fn sp(&self) -> &'static str { if self.opts.pretty { " "  } else { "" } }
    fn deeper(&self) -> Self { Self { opts: self.opts, depth: self.depth + 1 } }
    fn sep(&self) -> String {
        // Comma separator between items.
        if self.opts.pretty { ",\n".to_string() } else { ",".to_string() }
    }
}

// =============================================================================
// JsonTNamespace
// =============================================================================

impl Stringification for JsonTNamespace {
    fn stringify(&self, options: StringifyOptions) -> String {
        let ctx = Ctx::new(&options);
        let c1 = ctx.deeper();
        let c2 = c1.deeper();

        let catalogs_str = self.catalogs.iter()
            .map(|cat| cat.stringify_ctx(&c2))
            .collect::<Vec<_>>()
            .join(&ctx.sep());

        if options.pretty {
            format!(
                "{{{nl}{ind1}namespace:{sp}{{{nl}\
                 {ind2}baseUrl:{sp}{url},{nl}\
                 {ind2}version:{sp}{ver},{nl}\
                 {ind2}catalogs:{sp}[{nl}{cats}{nl}{ind2}],{nl}\
                 {ind2}data-schema:{sp}{ds}{nl}\
                 {ind1}}}{nl}}}",
                nl   = ctx.nl(),
                sp   = ctx.sp(),
                ind1 = c1.indent(),
                ind2 = c2.indent(),
                url  = quote(&self.base_url),
                ver  = quote(&self.version),
                cats = catalogs_str,
                ds   = self.data_schema,
            )
        } else {
            format!(
                "{{namespace:{{baseUrl:{url},version:{ver},catalogs:[{cats}],data-schema:{ds}}}}}",
                url  = quote(&self.base_url),
                ver  = quote(&self.version),
                cats = catalogs_str,
                ds   = self.data_schema,
            )
        }
    }
}

// =============================================================================
// JsonTCatalog
// =============================================================================

impl JsonTCatalog {
    fn stringify_ctx(&self, ctx: &Ctx) -> String {
        let c = ctx.deeper();
        let schemas_str = self.schemas.iter()
            .map(|s| s.stringify_ctx(&c))
            .collect::<Vec<_>>()
            .join(&ctx.sep());

        if self.enums.is_empty() {
            if ctx.opts.pretty {
                format!("{ind}{{{nl}{c_ind}schemas:{sp}[{nl}{schemas}{nl}{c_ind}]{nl}{ind}}}",
                    ind = ctx.indent(), nl = ctx.nl(), sp = ctx.sp(),
                    c_ind = c.indent(), schemas = schemas_str)
            } else {
                format!("{{schemas:[{schemas}]}}", schemas = schemas_str)
            }
        } else {
            let enums_str = self.enums.iter()
                .map(|e| e.stringify_ctx(&c))
                .collect::<Vec<_>>()
                .join(&ctx.sep());
            if ctx.opts.pretty {
                format!(
                    "{ind}{{{nl}{c_ind}schemas:{sp}[{nl}{schemas}{nl}{c_ind}],{nl}\
                     {c_ind}enums:{sp}[{nl}{enums}{nl}{c_ind}]{nl}{ind}}}",
                    ind = ctx.indent(), nl = ctx.nl(), sp = ctx.sp(),
                    c_ind = c.indent(), schemas = schemas_str, enums = enums_str)
            } else {
                format!("{{schemas:[{schemas}],enums:[{enums}]}}",
                    schemas = schemas_str, enums = enums_str)
            }
        }
    }
}

// =============================================================================
// JsonTSchema
// =============================================================================

impl JsonTSchema {
    fn stringify_ctx(&self, ctx: &Ctx) -> String {
        let c = ctx.deeper();
        let body = match &self.kind {
            SchemaKind::Straight { fields } => {
                let fields_str = fields.iter()
                    .map(|f| f.stringify_ctx(&c.deeper()))
                    .collect::<Vec<_>>()
                    .join(&c.sep());
                if ctx.opts.pretty {
                    format!("{{{nl}{ci}fields:{sp}{{{nl}{fields}{nl}{ci}}}{val}{nl}{c_ind}}}",
                        nl = ctx.nl(), sp = ctx.sp(),
                        ci = c.indent(), c_ind = ctx.indent(),
                        fields = fields_str,
                        val = validation_str(&self.validation, &c))
                } else {
                    format!("{{fields:{{{fields}}}{val}}}",
                        fields = fields_str,
                        val = validation_str(&self.validation, &c))
                }
            }
            SchemaKind::Derived { from, operations } => {
                let ops_str = operations.iter()
                    .map(|o| stringify_operation(o, ctx))
                    .collect::<Vec<_>>()
                    .join(&ctx.sep());
                if ctx.opts.pretty {
                    format!("FROM {from}{sp}{{{nl}{ci}operations({ops}){val}{nl}{c_ind}}}",
                        from = from, sp = ctx.sp(), nl = ctx.nl(),
                        ci = c.indent(), c_ind = ctx.indent(),
                        ops = ops_str,
                        val = validation_str(&self.validation, &c))
                } else {
                    format!("FROM {from}{{operations({ops}){val}}}",
                        from = from, ops = ops_str,
                        val = validation_str(&self.validation, &c))
                }
            }
        };

        if ctx.opts.pretty {
            format!("{ind}{name}:{sp}{body}", ind = ctx.indent(), name = self.name,
                sp = ctx.sp(), body = body)
        } else {
            format!("{name}:{body}", name = self.name, body = body)
        }
    }
}

fn validation_str(v: &Option<JsonTValidationBlock>, ctx: &Ctx) -> String {
    match v {
        None    => String::new(),
        Some(b) => format!(",{nl}{body}",
            nl = ctx.nl(),
            body = b.stringify_ctx(ctx)),
    }
}

// =============================================================================
// JsonTField
// =============================================================================

impl JsonTField {
    fn stringify_ctx(&self, ctx: &Ctx) -> String {
        let prefix = if ctx.opts.pretty { ctx.indent() } else { String::new() };

        match &self.kind {
            JsonTFieldKind::Scalar { field_type, optional, default, constant, constraints } => {
                let type_str  = stringify_field_type(field_type);
                let opt_str   = if *optional { "?" } else { "" };
                
                let mut attrs = Vec::new();
                if let Some(v) = default {
                    attrs.push(format!("default {}", stringify_value(v)));
                }
                if let Some(v) = constant {
                    attrs.push(format!("const {}", stringify_value(v)));
                }
                for c in constraints {
                    attrs.push(stringify_constraint(c));
                }

                let attr_str = if attrs.is_empty() {
                    String::new()
                } else {
                    format!(" [{}]", attrs.join(", "))
                };

                format!("{prefix}{type_str}:{sp}{name}{opt}{attrs}",
                    prefix = prefix, sp = ctx.sp(), name = self.name,
                    opt = opt_str, attrs = attr_str)
            }

            JsonTFieldKind::Object { schema_ref, is_array, optional, constraints } => {
                let arr_str = if *is_array { "[]" } else { "" };
                let opt_str = if *optional { "?" } else { "" };
                let con_str = if constraints.is_empty() {
                    String::new()
                } else {
                    let inner = constraints.iter()
                        .map(stringify_constraint)
                        .collect::<Vec<_>>()
                        .join(", ");
                    format!(" [{inner}]")
                };

                format!("{prefix}<{schema}>{arr}:{sp}{name}{opt}{constr}",
                    prefix = prefix, schema = schema_ref, arr = arr_str,
                    sp = ctx.sp(), name = self.name, opt = opt_str, constr = con_str)
            }
        }
    }
}

fn stringify_field_type(ft: &JsonTFieldType) -> String {
    let kw  = ft.scalar.keyword();
    let arr = if ft.is_array { "[]" } else { "" };
    format!("{}{}", kw, arr)
}

fn stringify_constraint(c: &JsonTConstraint) -> String {
    match c {
        JsonTConstraint::Required(b) => format!("required = {}", b),
        JsonTConstraint::Value { key, value } => {
            format!("{} = {}", key.keyword(), value)
        }
        JsonTConstraint::Length { key, value } => {
            format!("{} = {}", key.keyword(), value)
        }
        JsonTConstraint::Regex(r) => {
            format!("regex = {}", quote(r))
        }
        JsonTConstraint::ArrayItems(ai) => match ai {
            ArrayItemsConstraint::Numeric { key, value } => {
                format!("{} = {}", key.keyword(), value)
            }
            ArrayItemsConstraint::Boolean { key, value } => {
                format!("{} = {}", key.keyword(), value)
            }
        },
    }
}

// =============================================================================
// JsonTEnum
// =============================================================================

impl JsonTEnum {
    fn stringify_ctx(&self, ctx: &Ctx) -> String {
        let values = self.values.join(", ");
        if ctx.opts.pretty {
            format!("{ind}{name}:{sp}[{values}]",
                ind = ctx.indent(), name = self.name, sp = ctx.sp(), values = values)
        } else {
            format!("{name}:[{values}]", name = self.name, values = values)
        }
    }
}

// =============================================================================
// Operations (derived schema)
// =============================================================================

fn stringify_operation(op: &SchemaOperation, ctx: &Ctx) -> String {
    let sp = ctx.sp();
    match op {
        SchemaOperation::Rename(pairs) => {
            let inner = pairs.iter()
                .map(|rp| format!("{}{sp}as{sp}{}", rp.from.join(), rp.to, sp = sp))
                .collect::<Vec<_>>()
                .join(", ");
            format!("rename({inner})")
        }
        SchemaOperation::Exclude(paths) => {
            let inner = paths.iter().map(FieldPath::join).collect::<Vec<_>>().join(", ");
            format!("exclude({inner})")
        }
        SchemaOperation::Project(paths) => {
            let inner = paths.iter().map(FieldPath::join).collect::<Vec<_>>().join(", ");
            format!("project({inner})")
        }
        SchemaOperation::Filter(expr) => {
            format!("filter{sp}{}", stringify_expr(expr))
        }
        SchemaOperation::Transform { target, expr } => {
            format!("transform{sp}{}{sp}={sp}{}", target.join(), stringify_expr(expr), sp = sp)
        }
    }
}

// =============================================================================
// JsonTValidationBlock
// =============================================================================

impl JsonTValidationBlock {
    fn stringify_ctx(&self, ctx: &Ctx) -> String {
        let c = ctx.deeper();
        let mut parts = Vec::new();

        if !self.rules.is_empty() {
            let rules_str = self.rules.iter()
                .map(|r| stringify_rule(r, &c.deeper()))
                .collect::<Vec<_>>()
                .join(&c.sep());
            if ctx.opts.pretty {
                parts.push(format!("{ind}rules:{sp}{{{nl}{rules}{nl}{ind}}}",
                    ind = c.indent(), sp = ctx.sp(), nl = ctx.nl(), rules = rules_str));
            } else {
                parts.push(format!("rules:{{{rules}}}", rules = rules_str));
            }
        }

        if !self.unique.is_empty() {
            let u_str = self.unique.iter()
                .map(|paths| {
                    let inner = paths.iter().map(FieldPath::join).collect::<Vec<_>>().join(", ");
                    format!("({inner})")
                })
                .collect::<Vec<_>>()
                .join(&c.sep());
            if ctx.opts.pretty {
                parts.push(format!("{ind}unique:{sp}{{{nl}{u}{nl}{ind}}}",
                    ind = c.indent(), sp = ctx.sp(), nl = ctx.nl(), u = u_str));
            } else {
                parts.push(format!("unique:{{{u}}}", u = u_str));
            }
        }

        if !self.dataset.is_empty() {
            let d_str = self.dataset.iter()
                .map(|e| stringify_expr(e))
                .collect::<Vec<_>>()
                .join(&c.sep());
            if ctx.opts.pretty {
                parts.push(format!("{ind}dataset:{sp}{{{nl}{d}{nl}{ind}}}",
                    ind = c.indent(), sp = ctx.sp(), nl = ctx.nl(), d = d_str));
            } else {
                parts.push(format!("dataset:{{{d}}}", d = d_str));
            }
        }

        let body = parts.join(&ctx.sep());
        if ctx.opts.pretty {
            format!("{ind}validations:{sp}{{{nl}{body}{nl}{ind}}}",
                ind = ctx.indent(), sp = ctx.sp(), nl = ctx.nl(), body = body)
        } else {
            format!("validations:{{{body}}}")
        }
    }
}

fn stringify_rule(rule: &JsonTRule, ctx: &Ctx) -> String {
    let prefix = if ctx.opts.pretty { ctx.indent() } else { String::new() };
    match rule {
        JsonTRule::Expression(e) => format!("{prefix}{}", stringify_expr(e)),
        JsonTRule::ConditionalRequirement { condition, required_fields } => {
            let fields = required_fields.iter()
                .map(FieldPath::join)
                .collect::<Vec<_>>()
                .join(", ");
            format!("{prefix}{} -> required {}", stringify_expr(condition), fields)
        }
    }
}

// =============================================================================
// JsonTExpression stringification
// =============================================================================

pub fn stringify_expr(expr: &JsonTExpression) -> String {
    match expr {
        JsonTExpression::Literal(v) => stringify_value(v),

        JsonTExpression::FieldRef(path) => path.join(),

        JsonTExpression::FunctionCall { name, args } => {
            let args_str = args.iter().map(stringify_expr).collect::<Vec<_>>().join(", ");
            format!("{}({})", name, args_str)
        }

        JsonTExpression::UnaryOp { op, operand } => {
            let sym  = op.symbol();
            let inner = stringify_expr(operand);
            // Parenthesise the operand if it is itself a binary op to preserve
            // precedence unambiguously in the output.
            match operand.as_ref() {
                JsonTExpression::BinaryOp { .. } => format!("{}({})", sym, inner),
                _                                => format!("{}{}", sym, inner),
            }
        }

        JsonTExpression::BinaryOp { op, left, right } => {
            let l = stringify_expr_paren(left,  op);
            let r = stringify_expr_paren(right, op);
            format!("{} {} {}", l, op.symbol(), r)
        }
    }
}

/// Parenthesise a child expression when it has lower precedence than the
/// parent operator, to preserve semantics in the output.
fn stringify_expr_paren(child: &JsonTExpression, parent_op: &crate::model::validation::BinaryOp) -> String {
    let child_str = stringify_expr(child);
    if let JsonTExpression::BinaryOp { op, .. } = child {
        let pp = op_precedence(parent_op);
        let cp = op_precedence(op);
        if cp < pp {
            return format!("({})", child_str);
        }
    }
    child_str
}

fn op_precedence(op: &crate::model::validation::BinaryOp) -> u8 {
    use crate::model::validation::BinaryOp::*;
    match op {
        Or  => 1,
        And => 2,
        Eq | Neq => 3,
        Lt | Le | Gt | Ge => 4,
        Add | Sub => 5,
        Mul | Div => 6,
    }
}

// =============================================================================
// JsonTValue stringification
// =============================================================================

pub fn stringify_value(v: &JsonTValue) -> String {
    match v {
        JsonTValue::Null        => "null".to_string(),
        JsonTValue::Unspecified => "_".to_string(),
        JsonTValue::Bool(b)     => b.to_string(),
        JsonTValue::Str(s)      => quote(s),
        JsonTValue::Enum(c)     => c.clone(),
        JsonTValue::Number(n)   => stringify_number(n),
        JsonTValue::Object(row) => stringify_row(row),
        JsonTValue::Array(arr)  => stringify_array(arr),
    }
}

fn stringify_number(n: &JsonTNumber) -> String {
    match n {
        JsonTNumber::I16(v)  => v.to_string(),
        JsonTNumber::I32(v)  => v.to_string(),
        JsonTNumber::I64(v)  => v.to_string(),
        JsonTNumber::U16(v)  => v.to_string(),
        JsonTNumber::U32(v)  => v.to_string(),
        JsonTNumber::U64(v)  => v.to_string(),
        JsonTNumber::D32(v)  => v.to_string(),
        JsonTNumber::D64(v)  => v.to_string(),
        JsonTNumber::D128(v) => v.to_string(),
    }
}

fn stringify_row(row: &JsonTRow) -> String {
    let inner = row.fields.iter()
        .map(stringify_value)
        .collect::<Vec<_>>()
        .join(", ");
    format!("{{{inner}}}")
}

fn stringify_array(arr: &JsonTArray) -> String {
    let inner = arr.items.iter()
        .map(stringify_value)
        .collect::<Vec<_>>()
        .join(", ");
    format!("[{inner}]")
}

// =============================================================================
// Stringification trait impls for data types
// =============================================================================

impl Stringification for JsonTValue {
    fn stringify(&self, _options: StringifyOptions) -> String {
        stringify_value(self)
    }
}

impl Stringification for JsonTRow {
    fn stringify(&self, _options: StringifyOptions) -> String {
        stringify_row(self)
    }
}

impl Stringification for JsonTArray {
    fn stringify(&self, _options: StringifyOptions) -> String {
        stringify_array(self)
    }
}

impl Stringification for JsonTExpression {
    fn stringify(&self, _options: StringifyOptions) -> String {
        stringify_expr(self)
    }
}

// =============================================================================
// Utility
// =============================================================================

/// Wrap a string in double-quotes, escaping internal double-quotes.
fn quote(s: &str) -> String {
    format!("\"{}\"", s.replace('\\', "\\\\").replace('"', "\\\""))
}

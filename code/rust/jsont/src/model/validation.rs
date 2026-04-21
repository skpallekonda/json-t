// =============================================================================
// model/validation.rs
// =============================================================================
// JsonTValidationBlock — the optional validations { ... } block on a schema
// JsonTRule            — one entry in the rules block (conditional or plain)
// JsonTExpression      — recursive expression tree (also used in operations)
// UnaryOp / BinaryOp   — operator enums
// =============================================================================

use crate::model::schema::FieldPath;
use crate::model::data::JsonTValue;

/// The optional `validations { ... }` block attached to a schema.
///
/// All three sub-blocks are optional in the grammar.
#[derive(Debug, Clone, PartialEq)]
pub struct JsonTValidationBlock {
    /// `rules { ... }` — per-row conditional and plain expression rules.
    pub rules: Vec<JsonTRule>,

    /// `unique { ... }` — sets of field paths that must be unique across rows.
    pub unique: Vec<Vec<FieldPath>>,

    /// `dataset { ... }` — expressions evaluated against the entire dataset.
    pub dataset: Vec<JsonTExpression>,
}

/// One entry in a `rules` block.
#[derive(Debug, Clone, PartialEq)]
pub enum JsonTRule {
    /// `condition -> required field1, field2`
    /// When `condition` is true, the listed fields must be present.
    ConditionalRequirement {
        condition: JsonTExpression,
        required_fields: Vec<FieldPath>,
    },

    /// A plain boolean expression that must evaluate to true for each row.
    Expression(JsonTExpression),
}

/// A node in the JsonT expression tree.
///
/// Built by the parser from the left-factored expression grammar.
/// Also constructed programmatically via ExpressionBuilder or direct construction.
///
/// `Box<JsonTExpression>` is used for recursive children — required because
/// Rust enums must have a known size at compile time.
#[derive(Debug, Clone, PartialEq)]
pub enum JsonTExpression {
    /// A literal value (scalar or null).
    Literal(JsonTValue),

    /// A dot-separated field reference, e.g. `address.city`.
    FieldRef(FieldPath),

    /// A function call, e.g. `length(name)`.
    FunctionCall {
        name: String,
        args: Vec<JsonTExpression>,
    },

    /// A unary prefix operation, e.g. `!active` or `-amount`.
    UnaryOp {
        op: UnaryOp,
        operand: Box<JsonTExpression>,
    },

    /// A binary infix operation, e.g. `age > 18`.
    BinaryOp {
        op: BinaryOp,
        left: Box<JsonTExpression>,
        right: Box<JsonTExpression>,
    },
}

impl JsonTExpression {
    // ── Convenience constructors ──────────────────────────────────────────

    pub fn literal(v: JsonTValue) -> Self {
        JsonTExpression::Literal(v)
    }

    pub fn field(path: FieldPath) -> Self {
        JsonTExpression::FieldRef(path)
    }

    pub fn field_name(name: impl Into<String>) -> Self {
        JsonTExpression::FieldRef(FieldPath::single(name))
    }

    pub fn call(name: impl Into<String>, args: Vec<JsonTExpression>) -> Self {
        JsonTExpression::FunctionCall { name: name.into(), args }
    }

    pub fn not(operand: JsonTExpression) -> Self {
        JsonTExpression::UnaryOp {
            op: UnaryOp::Not,
            operand: Box::new(operand),
        }
    }

    pub fn negate(operand: JsonTExpression) -> Self {
        JsonTExpression::UnaryOp {
            op: UnaryOp::Neg,
            operand: Box::new(operand),
        }
    }

    pub fn binary(op: BinaryOp, left: JsonTExpression, right: JsonTExpression) -> Self {
        JsonTExpression::BinaryOp {
            op,
            left: Box::new(left),
            right: Box::new(right),
        }
    }

    // ── Shorthand binary builders ─────────────────────────────────────────

    pub fn and(left: JsonTExpression, right: JsonTExpression) -> Self {
        Self::binary(BinaryOp::And, left, right)
    }

    pub fn or(left: JsonTExpression, right: JsonTExpression) -> Self {
        Self::binary(BinaryOp::Or, left, right)
    }

    pub fn eq(left: JsonTExpression, right: JsonTExpression) -> Self {
        Self::binary(BinaryOp::Eq, left, right)
    }

    pub fn neq(left: JsonTExpression, right: JsonTExpression) -> Self {
        Self::binary(BinaryOp::Neq, left, right)
    }

    pub fn lt(left: JsonTExpression, right: JsonTExpression) -> Self {
        Self::binary(BinaryOp::Lt, left, right)
    }

    pub fn le(left: JsonTExpression, right: JsonTExpression) -> Self {
        Self::binary(BinaryOp::Le, left, right)
    }

    pub fn gt(left: JsonTExpression, right: JsonTExpression) -> Self {
        Self::binary(BinaryOp::Gt, left, right)
    }

    pub fn ge(left: JsonTExpression, right: JsonTExpression) -> Self {
        Self::binary(BinaryOp::Ge, left, right)
    }

    pub fn add(left: JsonTExpression, right: JsonTExpression) -> Self {
        Self::binary(BinaryOp::Add, left, right)
    }

    pub fn sub(left: JsonTExpression, right: JsonTExpression) -> Self {
        Self::binary(BinaryOp::Sub, left, right)
    }

    pub fn mul(left: JsonTExpression, right: JsonTExpression) -> Self {
        Self::binary(BinaryOp::Mul, left, right)
    }

    pub fn div(left: JsonTExpression, right: JsonTExpression) -> Self {
        Self::binary(BinaryOp::Div, left, right)
    }
}

/// Unary operators.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum UnaryOp {
    /// Logical NOT  (`!`)
    Not,
    /// Arithmetic negation (`-`)
    Neg,
}

impl UnaryOp {
    pub fn symbol(&self) -> &'static str {
        match self {
            UnaryOp::Not => "!",
            UnaryOp::Neg => "-",
        }
    }
}

/// Binary operators, in precedence order (lowest to highest within each group).
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum BinaryOp {
    // Logical
    Or,   // ||
    And,  // &&
    // Equality
    Eq,   // ==
    Neq,  // !=
    // Relational
    Lt,   // <
    Le,   // <=
    Gt,   // >
    Ge,   // >=
    // Arithmetic
    Add,  // +
    Sub,  // -
    Mul,  // *
    Div,  // /
}

impl BinaryOp {
    pub fn symbol(&self) -> &'static str {
        match self {
            BinaryOp::Or  => "||",
            BinaryOp::And => "&&",
            BinaryOp::Eq  => "==",
            BinaryOp::Neq => "!=",
            BinaryOp::Lt  => "<",
            BinaryOp::Le  => "<=",
            BinaryOp::Gt  => ">",
            BinaryOp::Ge  => ">=",
            BinaryOp::Add => "+",
            BinaryOp::Sub => "-",
            BinaryOp::Mul => "*",
            BinaryOp::Div => "/",
        }
    }
}

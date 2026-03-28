// =============================================================================
// parse/expr.rs
// =============================================================================
// Two responsibilities:
//   1. build_expression() — walks a pest Pairs tree and produces JsonTExpression
//   2. Evaluatable impl on JsonTExpression — evaluates the tree in an EvalContext
// =============================================================================

use pest::iterators::{Pair, Pairs};

use crate::parse::Rule;
use crate::{Evaluatable, EvalContext};
use crate::error::{JsonTError, ParseError, EvalError};
use crate::model::validation::{JsonTExpression, BinaryOp, UnaryOp};
use crate::model::schema::FieldPath;
use crate::model::data::{JsonTValue, JsonTNumber};

// =============================================================================
// Expression tree construction from pest pairs
// =============================================================================

/// Entry point: build a JsonTExpression from the top-level pest pairs for
/// the `expression` rule.
pub fn build_expression(mut pairs: Pairs<Rule>) -> Result<JsonTExpression, JsonTError> {
    let pair = pairs.next()
        .ok_or_else(|| ParseError::Unexpected("empty expression".into()))?;
    build_expr_pair(pair)
}

/// Entry point when you already hold the `expression` Pair (e.g. from a
/// filter_operation or transform_operation inner iterator).
pub fn build_expr_from_pair(pair: Pair<Rule>) -> Result<JsonTExpression, JsonTError> {
    build_expr_pair(pair)
}

fn build_expr_pair(pair: Pair<Rule>) -> Result<JsonTExpression, JsonTError> {
    match pair.as_rule() {
        Rule::expression => {
            // expression is a transparent wrapper — descend.
            build_expr_pair(first_inner(pair)?)
        }

        Rule::logical_or_expression => build_binary_chain(
            pair, &[BinaryOp::Or],
        ),

        Rule::logical_and_expression => build_binary_chain(
            pair, &[BinaryOp::And],
        ),

        Rule::equality_expression => build_binary_chain(
            pair, &[BinaryOp::Eq, BinaryOp::Neq],
        ),

        Rule::relational_expression => build_binary_chain(
            pair, &[BinaryOp::Le, BinaryOp::Ge, BinaryOp::Lt, BinaryOp::Gt],
        ),

        Rule::additive_expression => build_binary_chain(
            pair, &[BinaryOp::Add, BinaryOp::Sub],
        ),

        Rule::multiplicative_expression => build_binary_chain(
            pair, &[BinaryOp::Mul, BinaryOp::Div],
        ),

        Rule::unary_expression => build_unary(pair),

        Rule::primary_expression => build_primary(pair),

        Rule::literal      => build_literal(pair),
        Rule::function_call => build_function_call(pair),
        Rule::field_path   => build_field_path(pair),

        other => Err(ParseError::Unexpected(
            format!("unexpected rule in expression: {:?}", other)
        ).into()),
    }
}

/// Build a left-associative binary chain.
/// Pest emits: child op child op child ...
/// We fold left: ((child op child) op child)
fn build_binary_chain(
    pair: Pair<Rule>,
    _expected_ops: &[BinaryOp],
) -> Result<JsonTExpression, JsonTError> {
    let mut inner = pair.into_inner();

    let first = inner.next()
        .ok_or_else(|| ParseError::Unexpected("empty binary chain".into()))?;
    let mut left = build_expr_pair(first)?;

    // Remaining pairs alternate: operator-pair, operand-pair
    while let Some(op_pair) = inner.next() {
        let op = parse_binary_op(&op_pair)?;
        let right_pair = inner.next()
            .ok_or_else(|| ParseError::Unexpected("missing right operand".into()))?;
        let right = build_expr_pair(right_pair)?;
        left = JsonTExpression::binary(op, left, right);
    }

    Ok(left)
}

fn build_unary(pair: Pair<Rule>) -> Result<JsonTExpression, JsonTError> {
    let mut inner = pair.into_inner();
    let first = inner.next()
        .ok_or_else(|| ParseError::Unexpected("empty unary expression".into()))?;

    match first.as_rule() {
        // No unary operator — just a primary expression.
        Rule::primary_expression => build_primary(first),

        // op_not / op_neg are non-silent named rules; they produce a Pair whose
        // as_str() is "!" or "-".  The operand follows as the next inner Pair.
        Rule::op_not => {
            let operand_pair = inner.next()
                .ok_or_else(|| ParseError::Unexpected("missing unary operand".into()))?;
            let operand = build_expr_pair(operand_pair)?;
            Ok(JsonTExpression::UnaryOp { op: UnaryOp::Not, operand: Box::new(operand) })
        }

        Rule::op_neg => {
            let operand_pair = inner.next()
                .ok_or_else(|| ParseError::Unexpected("missing unary operand".into()))?;
            let operand = build_expr_pair(operand_pair)?;
            Ok(JsonTExpression::UnaryOp { op: UnaryOp::Neg, operand: Box::new(operand) })
        }

        other => Err(ParseError::Unexpected(
            format!("unexpected rule in unary expression: {:?}", other)
        ).into()),
    }
}

fn build_primary(pair: Pair<Rule>) -> Result<JsonTExpression, JsonTError> {
    let inner = first_inner(pair)?;
    build_expr_pair(inner)
}

fn build_literal(pair: Pair<Rule>) -> Result<JsonTExpression, JsonTError> {
    let inner = first_inner(pair)?;
    let value = parse_literal_value(inner)?;
    Ok(JsonTExpression::Literal(value))
}

fn build_function_call(pair: Pair<Rule>) -> Result<JsonTExpression, JsonTError> {
    let mut inner = pair.into_inner();

    let name_pair = inner.next()
        .ok_or_else(|| ParseError::Unexpected("missing function name".into()))?;
    let name = name_pair.as_str().to_string();

    let mut args = Vec::new();
    for arg_pair in inner {
        // argument_list is a flat list of expression children
        if arg_pair.as_rule() == Rule::argument_list {
            for expr_pair in arg_pair.into_inner() {
                args.push(build_expr_pair(expr_pair)?);
            }
        }
    }

    Ok(JsonTExpression::FunctionCall { name, args })
}

fn build_field_path(pair: Pair<Rule>) -> Result<JsonTExpression, JsonTError> {
    let segments: Vec<String> = pair.into_inner()
        .filter(|p| p.as_rule() == Rule::ns_field_name)
        .map(|p| p.as_str().to_string())
        .collect();

    if segments.is_empty() {
        return Err(ParseError::Unexpected("empty field path".into()).into());
    }

    Ok(JsonTExpression::FieldRef(FieldPath::new(segments)))
}

fn parse_literal_value(pair: Pair<Rule>) -> Result<JsonTValue, JsonTError> {
    match pair.as_rule() {
        Rule::null_value  => Ok(JsonTValue::Null),
        Rule::scalar_value => parse_scalar_value(pair),
        other => Err(ParseError::Unexpected(
            format!("expected literal, got {:?}", other)
        ).into()),
    }
}

fn parse_scalar_value(pair: Pair<Rule>) -> Result<JsonTValue, JsonTError> {
    let inner = first_inner(pair)?;
    match inner.as_rule() {
        Rule::boolean => {
            let b = inner.as_str() == "true";
            Ok(JsonTValue::Bool(b))
        }
        Rule::number => {
            let s = inner.as_str();
            // Numbers from the grammar are unsigned (no leading '-' — see [D-2]).
            // Parse as f64 then store as D64; the parser layer does not have
            // field type context to choose a narrower type.
            // Schema-aware parsing (with field type) will use the correct variant.
            let n: f64 = s.parse()
                .map_err(|_| ParseError::Unexpected(format!("invalid number: {}", s)))?;
            Ok(JsonTValue::Number(JsonTNumber::D64(n)))
        }
        Rule::string => {
            let raw = inner.as_str();
            // Strip surrounding quotes.
            let unquoted = &raw[1..raw.len()-1];
            Ok(JsonTValue::Str(unescape(unquoted)))
        }
        other => Err(ParseError::Unexpected(
            format!("expected scalar value, got {:?}", other)
        ).into()),
    }
}

fn parse_binary_op(pair: &Pair<Rule>) -> Result<BinaryOp, JsonTError> {
    match pair.as_str() {
        "||" => Ok(BinaryOp::Or),
        "&&" => Ok(BinaryOp::And),
        "==" => Ok(BinaryOp::Eq),
        "!=" => Ok(BinaryOp::Neq),
        "<"  => Ok(BinaryOp::Lt),
        "<=" => Ok(BinaryOp::Le),
        ">"  => Ok(BinaryOp::Gt),
        ">=" => Ok(BinaryOp::Ge),
        "+"  => Ok(BinaryOp::Add),
        "-"  => Ok(BinaryOp::Sub),
        "*"  => Ok(BinaryOp::Mul),
        "/"  => Ok(BinaryOp::Div),
        s    => Err(ParseError::Unexpected(
            format!("unknown binary operator: {}", s)
        ).into()),
    }
}

fn first_inner(pair: Pair<Rule>) -> Result<Pair<Rule>, JsonTError> {
    pair.into_inner().next()
        .ok_or_else(|| ParseError::Unexpected("expected inner pair, got none".into()).into())
}

/// Minimal string unescaping — handles \n \t \r \\ \" \'.
fn unescape(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars().peekable();
    while let Some(c) = chars.next() {
        if c == '\\' {
            match chars.next() {
                Some('n')  => out.push('\n'),
                Some('t')  => out.push('\t'),
                Some('r')  => out.push('\r'),
                Some('\\') => out.push('\\'),
                Some('"')  => out.push('"'),
                Some('\'') => out.push('\''),
                Some(x)    => { out.push('\\'); out.push(x); }
                None       => out.push('\\'),
            }
        } else {
            out.push(c);
        }
    }
    out
}

// =============================================================================
// Evaluatable impl for JsonTExpression
// =============================================================================

impl Evaluatable for JsonTExpression {
    fn evaluate(&self, ctx: &EvalContext) -> Result<JsonTValue, JsonTError> {
        eval_expr(self, ctx)
    }
}

fn eval_expr(expr: &JsonTExpression, ctx: &EvalContext) -> Result<JsonTValue, JsonTError> {
    match expr {
        JsonTExpression::Literal(v) => Ok(v.clone()),

        JsonTExpression::FieldRef(path) => {
            let key = path.join();
            ctx.get(&key)
                .cloned()
                .ok_or_else(|| EvalError::UnboundField(key).into())
        }

        JsonTExpression::FunctionCall { name, args } => {
            eval_function(name, args, ctx)
        }

        JsonTExpression::UnaryOp { op, operand } => {
            let val = eval_expr(operand, ctx)?;
            eval_unary(op, val)
        }

        JsonTExpression::BinaryOp { op, left, right } => {
            let l = eval_expr(left, ctx)?;
            let r = eval_expr(right, ctx)?;
            eval_binary(op, l, r)
        }
    }
}

fn eval_unary(op: &UnaryOp, val: JsonTValue) -> Result<JsonTValue, JsonTError> {
    match op {
        UnaryOp::Not => {
            let b = val.as_bool()
                .ok_or_else(|| EvalError::TypeMismatch {
                    expected: "bool".into(),
                    actual:   val.type_name().into(),
                })?;
            Ok(JsonTValue::Bool(!b))
        }
        UnaryOp::Neg => {
            let n = val.as_f64()
                .ok_or_else(|| EvalError::TypeMismatch {
                    expected: "number".into(),
                    actual:   val.type_name().into(),
                })?;
            Ok(JsonTValue::Number(crate::model::data::JsonTNumber::D64(-n)))
        }
    }
}

fn eval_binary(
    op: &BinaryOp,
    left: JsonTValue,
    right: JsonTValue,
) -> Result<JsonTValue, JsonTError> {
    match op {
        // ── Logical ──────────────────────────────────────────────────────
        BinaryOp::Or => {
            let l = require_bool(&left)?;
            let r = require_bool(&right)?;
            Ok(JsonTValue::Bool(l || r))
        }
        BinaryOp::And => {
            let l = require_bool(&left)?;
            let r = require_bool(&right)?;
            Ok(JsonTValue::Bool(l && r))
        }

        // ── Equality ─────────────────────────────────────────────────────
        BinaryOp::Eq  => Ok(JsonTValue::Bool(values_equal(&left, &right))),
        BinaryOp::Neq => Ok(JsonTValue::Bool(!values_equal(&left, &right))),

        // ── Relational ───────────────────────────────────────────────────
        BinaryOp::Lt => {
            let (l, r) = require_numeric_pair(&left, &right)?;
            Ok(JsonTValue::Bool(l < r))
        }
        BinaryOp::Le => {
            let (l, r) = require_numeric_pair(&left, &right)?;
            Ok(JsonTValue::Bool(l <= r))
        }
        BinaryOp::Gt => {
            let (l, r) = require_numeric_pair(&left, &right)?;
            Ok(JsonTValue::Bool(l > r))
        }
        BinaryOp::Ge => {
            let (l, r) = require_numeric_pair(&left, &right)?;
            Ok(JsonTValue::Bool(l >= r))
        }

        // ── Arithmetic ───────────────────────────────────────────────────
        BinaryOp::Add => {
            let (l, r) = require_numeric_pair(&left, &right)?;
            Ok(JsonTValue::Number(crate::model::data::JsonTNumber::D64(l + r)))
        }
        BinaryOp::Sub => {
            let (l, r) = require_numeric_pair(&left, &right)?;
            Ok(JsonTValue::Number(crate::model::data::JsonTNumber::D64(l - r)))
        }
        BinaryOp::Mul => {
            let (l, r) = require_numeric_pair(&left, &right)?;
            Ok(JsonTValue::Number(crate::model::data::JsonTNumber::D64(l * r)))
        }
        BinaryOp::Div => {
            let (l, r) = require_numeric_pair(&left, &right)?;
            if r == 0.0 {
                return Err(EvalError::DivisionByZero.into());
            }
            Ok(JsonTValue::Number(crate::model::data::JsonTNumber::D64(l / r)))
        }
    }
}

/// Built-in function dispatch.
/// Add new functions here; unknown names produce EvalError::UnknownFunction.
fn eval_function(
    name: &str,
    args: &[JsonTExpression],
    ctx: &EvalContext,
) -> Result<JsonTValue, JsonTError> {
    match name {
        "length" => {
            expect_arity(name, 1, args.len())?;
            let val = eval_expr(&args[0], ctx)?;
            match &val {
                JsonTValue::Str(s)   => Ok(JsonTValue::Number(
                    crate::model::data::JsonTNumber::I64(s.len() as i64)
                )),
                JsonTValue::Array(a) => Ok(JsonTValue::Number(
                    crate::model::data::JsonTNumber::I64(a.len() as i64)
                )),
                _ => Err(EvalError::TypeMismatch {
                    expected: "str or array".into(),
                    actual:   val.type_name().into(),
                }.into()),
            }
        }

        "upper" => {
            expect_arity(name, 1, args.len())?;
            let val = eval_expr(&args[0], ctx)?;
            match val {
                JsonTValue::Str(s) => Ok(JsonTValue::Str(s.to_uppercase())),
                other => Err(EvalError::TypeMismatch {
                    expected: "str".into(),
                    actual:   other.type_name().into(),
                }.into()),
            }
        }

        "lower" => {
            expect_arity(name, 1, args.len())?;
            let val = eval_expr(&args[0], ctx)?;
            match val {
                JsonTValue::Str(s) => Ok(JsonTValue::Str(s.to_lowercase())),
                other => Err(EvalError::TypeMismatch {
                    expected: "str".into(),
                    actual:   other.type_name().into(),
                }.into()),
            }
        }

        "coalesce" => {
            // Returns the first non-null argument.
            if args.is_empty() {
                return Err(EvalError::ArityMismatch {
                    name: name.to_string(), expected: 1, got: 0,
                }.into());
            }
            for arg in args {
                let val = eval_expr(arg, ctx)?;
                if !matches!(val, JsonTValue::Null | JsonTValue::Unspecified) {
                    return Ok(val);
                }
            }
            Ok(JsonTValue::Null)
        }

        unknown => Err(EvalError::UnknownFunction(unknown.to_string()).into()),
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fn require_bool(v: &JsonTValue) -> Result<bool, JsonTError> {
    v.as_bool().ok_or_else(|| EvalError::TypeMismatch {
        expected: "bool".into(),
        actual:   v.type_name().into(),
    }.into())
}

fn require_numeric_pair(
    l: &JsonTValue,
    r: &JsonTValue,
) -> Result<(f64, f64), JsonTError> {
    let lf = l.as_f64().ok_or_else(|| EvalError::TypeMismatch {
        expected: "number".into(),
        actual:   l.type_name().into(),
    })?;
    let rf = r.as_f64().ok_or_else(|| EvalError::TypeMismatch {
        expected: "number".into(),
        actual:   r.type_name().into(),
    })?;
    Ok((lf, rf))
}

fn values_equal(a: &JsonTValue, b: &JsonTValue) -> bool {
    match (a, b) {
        (JsonTValue::Null, JsonTValue::Null)         => true,
        (JsonTValue::Unspecified, JsonTValue::Unspecified) => true,
        (JsonTValue::Bool(x), JsonTValue::Bool(y))   => x == y,
        (JsonTValue::Str(x),  JsonTValue::Str(y))    => x == y,
        (JsonTValue::Enum(x), JsonTValue::Enum(y))   => x == y,
        (JsonTValue::Number(x), JsonTValue::Number(y)) => {
            // Compare via f64 coercion — good enough for equality checks.
            (x.as_f64() - y.as_f64()).abs() < f64::EPSILON
        }
        _ => false,
    }
}

fn expect_arity(name: &str, expected: usize, got: usize) -> Result<(), JsonTError> {
    if got != expected {
        Err(EvalError::ArityMismatch {
            name: name.to_string(), expected, got,
        }.into())
    } else {
        Ok(())
    }
}

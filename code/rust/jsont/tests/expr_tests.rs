// =============================================================================
// tests/expr_tests.rs
// =============================================================================
// Three sections:
//   1. Parse tests   — source string → JsonTExpression tree shape
//   2. Eval tests    — expression tree → evaluated JsonTValue
//   3. Error tests   — type mismatches, arity, div-by-zero, unbound fields
// =============================================================================

use jsont::{
    EvalContext, EvalError, Evaluatable, JsonTArray, JsonTError, JsonTExpression, JsonTNumber,
    JsonTValue, Parseable,
};

// =============================================================================
// PART 1 — Parse tests
// =============================================================================

// ── Literals ──────────────────────────────────────────────────────────────────

#[test]
fn test_parse_integer_literal() {
    let expr = JsonTExpression::parse("42").unwrap();
    assert_eq!(expr, JsonTExpression::Literal(JsonTValue::d64(42.0)));
}

#[test]
fn test_parse_float_literal() {
    let expr = JsonTExpression::parse("3.14").unwrap();
    assert_eq!(expr, JsonTExpression::Literal(JsonTValue::d64(3.14)));
}

#[test]
fn test_parse_bool_true() {
    let expr = JsonTExpression::parse("true").unwrap();
    assert_eq!(expr, JsonTExpression::Literal(JsonTValue::bool(true)));
}

#[test]
fn test_parse_bool_false() {
    let expr = JsonTExpression::parse("false").unwrap();
    assert_eq!(expr, JsonTExpression::Literal(JsonTValue::bool(false)));
}

#[test]
fn test_parse_null_literal() {
    let expr = JsonTExpression::parse("null").unwrap();
    assert_eq!(expr, JsonTExpression::Literal(JsonTValue::Null));
}

#[test]
fn test_parse_nil_literal() {
    // "nil" is a grammar alias for null [D-5]
    let expr = JsonTExpression::parse("nil").unwrap();
    assert_eq!(expr, JsonTExpression::Literal(JsonTValue::Null));
}

#[test]
fn test_parse_double_quoted_string() {
    let expr = JsonTExpression::parse("\"hello\"").unwrap();
    assert_eq!(expr, JsonTExpression::Literal(JsonTValue::str("hello")));
}

#[test]
fn test_parse_single_quoted_string() {
    let expr = JsonTExpression::parse("'hello'").unwrap();
    assert_eq!(expr, JsonTExpression::Literal(JsonTValue::str("hello")));
}

#[test]
fn test_parse_string_escape_sequences() {
    let expr = JsonTExpression::parse("\"a\\nb\"").unwrap();
    assert_eq!(expr, JsonTExpression::Literal(JsonTValue::str("a\nb")));
}

// ── Field references ──────────────────────────────────────────────────────────

#[test]
fn test_parse_simple_field_ref() {
    let expr = JsonTExpression::parse("age").unwrap();
    assert_eq!(expr, JsonTExpression::field_name("age"));
}

#[test]
fn test_parse_dotted_field_path() {
    let expr = JsonTExpression::parse("address.city").unwrap();
    if let JsonTExpression::FieldRef(path) = expr {
        assert_eq!(path.join(), "address.city");
    } else {
        panic!("expected FieldRef");
    }
}

#[test]
fn test_parse_deeply_nested_field_path() {
    let expr = JsonTExpression::parse("a.b.c").unwrap();
    if let JsonTExpression::FieldRef(path) = expr {
        assert_eq!(path.join(), "a.b.c");
    } else {
        panic!("expected FieldRef");
    }
}

// ── Binary operators ──────────────────────────────────────────────────────────

#[test]
fn test_parse_eq() {
    let expr = JsonTExpression::parse("a == b").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::eq(JsonTExpression::field_name("a"), JsonTExpression::field_name("b"))
    );
}

#[test]
fn test_parse_neq() {
    let expr = JsonTExpression::parse("a != b").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::neq(JsonTExpression::field_name("a"), JsonTExpression::field_name("b"))
    );
}

#[test]
fn test_parse_lt() {
    let expr = JsonTExpression::parse("age < 18").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::lt(JsonTExpression::field_name("age"), lit_num(18.0))
    );
}

#[test]
fn test_parse_le() {
    let expr = JsonTExpression::parse("age <= 18").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::le(JsonTExpression::field_name("age"), lit_num(18.0))
    );
}

#[test]
fn test_parse_gt() {
    let expr = JsonTExpression::parse("score > 100").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::gt(JsonTExpression::field_name("score"), lit_num(100.0))
    );
}

#[test]
fn test_parse_ge() {
    let expr = JsonTExpression::parse("score >= 100").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::ge(JsonTExpression::field_name("score"), lit_num(100.0))
    );
}

#[test]
fn test_parse_logical_and() {
    let expr = JsonTExpression::parse("active && verified").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::and(
            JsonTExpression::field_name("active"),
            JsonTExpression::field_name("verified"),
        )
    );
}

#[test]
fn test_parse_logical_or() {
    let expr = JsonTExpression::parse("admin || superuser").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::or(
            JsonTExpression::field_name("admin"),
            JsonTExpression::field_name("superuser"),
        )
    );
}

#[test]
fn test_parse_add() {
    let expr = JsonTExpression::parse("x + 1").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::add(JsonTExpression::field_name("x"), lit_num(1.0))
    );
}

#[test]
fn test_parse_sub() {
    let expr = JsonTExpression::parse("x - 1").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::sub(JsonTExpression::field_name("x"), lit_num(1.0))
    );
}

#[test]
fn test_parse_mul() {
    let expr = JsonTExpression::parse("x * 2").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::mul(JsonTExpression::field_name("x"), lit_num(2.0))
    );
}

#[test]
fn test_parse_div() {
    let expr = JsonTExpression::parse("x / 4").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::div(JsonTExpression::field_name("x"), lit_num(4.0))
    );
}

// ── Unary operators ───────────────────────────────────────────────────────────

#[test]
fn test_parse_unary_not() {
    let expr = JsonTExpression::parse("!active").unwrap();
    assert_eq!(expr, JsonTExpression::not(JsonTExpression::field_name("active")));
}

#[test]
fn test_parse_unary_neg() {
    let expr = JsonTExpression::parse("-amount").unwrap();
    assert_eq!(expr, JsonTExpression::negate(JsonTExpression::field_name("amount")));
}

#[test]
fn test_parse_double_not() {
    let expr = JsonTExpression::parse("!!active").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::not(JsonTExpression::not(JsonTExpression::field_name("active")))
    );
}

// ── Operator precedence ───────────────────────────────────────────────────────

#[test]
fn test_parse_precedence_mul_over_add() {
    // 1 + 2 * 3  →  Add(1, Mul(2, 3))
    let expr = JsonTExpression::parse("1 + 2 * 3").unwrap();
    let expected = JsonTExpression::add(
        lit_num(1.0),
        JsonTExpression::mul(lit_num(2.0), lit_num(3.0)),
    );
    assert_eq!(expr, expected);
}

#[test]
fn test_parse_precedence_and_over_or() {
    // a || b && c  →  Or(a, And(b, c))
    let expr = JsonTExpression::parse("a || b && c").unwrap();
    let expected = JsonTExpression::or(
        JsonTExpression::field_name("a"),
        JsonTExpression::and(
            JsonTExpression::field_name("b"),
            JsonTExpression::field_name("c"),
        ),
    );
    assert_eq!(expr, expected);
}

#[test]
fn test_parse_precedence_relational_over_logical() {
    // x > 0 && y < 10  →  And(Gt(x, 0), Lt(y, 10))
    let expr = JsonTExpression::parse("x > 0 && y < 10").unwrap();
    let expected = JsonTExpression::and(
        JsonTExpression::gt(JsonTExpression::field_name("x"), lit_num(0.0)),
        JsonTExpression::lt(JsonTExpression::field_name("y"), lit_num(10.0)),
    );
    assert_eq!(expr, expected);
}

#[test]
fn test_parse_left_associativity_add() {
    // a + b + c  →  Add(Add(a, b), c)
    let expr = JsonTExpression::parse("a + b + c").unwrap();
    let expected = JsonTExpression::add(
        JsonTExpression::add(
            JsonTExpression::field_name("a"),
            JsonTExpression::field_name("b"),
        ),
        JsonTExpression::field_name("c"),
    );
    assert_eq!(expr, expected);
}

#[test]
fn test_parse_left_associativity_sub() {
    // a - b - c  →  Sub(Sub(a, b), c)
    let expr = JsonTExpression::parse("a - b - c").unwrap();
    let expected = JsonTExpression::sub(
        JsonTExpression::sub(
            JsonTExpression::field_name("a"),
            JsonTExpression::field_name("b"),
        ),
        JsonTExpression::field_name("c"),
    );
    assert_eq!(expr, expected);
}

// ── Parentheses ───────────────────────────────────────────────────────────────

#[test]
fn test_parse_parens_override_precedence() {
    // (a + b) * c  — parens force add first
    let expr = JsonTExpression::parse("(a + b) * c").unwrap();
    let expected = JsonTExpression::mul(
        JsonTExpression::add(
            JsonTExpression::field_name("a"),
            JsonTExpression::field_name("b"),
        ),
        JsonTExpression::field_name("c"),
    );
    assert_eq!(expr, expected);
}

#[test]
fn test_parse_nested_parens() {
    let expr = JsonTExpression::parse("((a))").unwrap();
    assert_eq!(expr, JsonTExpression::field_name("a"));
}

// ── Function calls ────────────────────────────────────────────────────────────

#[test]
fn test_parse_function_single_arg() {
    let expr = JsonTExpression::parse("length(name)").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::call("length", vec![JsonTExpression::field_name("name")])
    );
}

#[test]
fn test_parse_function_multiple_args() {
    let expr = JsonTExpression::parse("coalesce(a, b, c)").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::call("coalesce", vec![
            JsonTExpression::field_name("a"),
            JsonTExpression::field_name("b"),
            JsonTExpression::field_name("c"),
        ])
    );
}

#[test]
fn test_parse_function_with_expression_arg() {
    let expr = JsonTExpression::parse("length(upper(name))").unwrap();
    assert_eq!(
        expr,
        JsonTExpression::call("length", vec![
            JsonTExpression::call("upper", vec![JsonTExpression::field_name("name")])
        ])
    );
}

// ── Parse error ───────────────────────────────────────────────────────────────

#[test]
fn test_parse_invalid_syntax_returns_err() {
    assert!(JsonTExpression::parse("== 5").is_err());
}

#[test]
fn test_parse_empty_input_returns_err() {
    assert!(JsonTExpression::parse("").is_err());
}

// =============================================================================
// PART 2 — Eval tests
// =============================================================================

// ── Literals ──────────────────────────────────────────────────────────────────

#[test]
fn test_eval_literal_number() {
    assert_eq!(lit_num(7.0).evaluate(&ctx()).unwrap(), JsonTValue::d64(7.0));
}

#[test]
fn test_eval_literal_bool_true() {
    assert_eq!(lit_bool(true).evaluate(&ctx()).unwrap(), JsonTValue::bool(true));
}

#[test]
fn test_eval_literal_bool_false() {
    assert_eq!(lit_bool(false).evaluate(&ctx()).unwrap(), JsonTValue::bool(false));
}

#[test]
fn test_eval_literal_string() {
    assert_eq!(lit_str("hi").evaluate(&ctx()).unwrap(), JsonTValue::str("hi"));
}

#[test]
fn test_eval_literal_null() {
    let expr = JsonTExpression::Literal(JsonTValue::Null);
    assert_eq!(expr.evaluate(&ctx()).unwrap(), JsonTValue::Null);
}

// ── Field references ──────────────────────────────────────────────────────────

#[test]
fn test_eval_field_ref_bound() {
    let ctx = EvalContext::new().bind("age", JsonTValue::i64(30));
    assert_eq!(
        JsonTExpression::field_name("age").evaluate(&ctx).unwrap(),
        JsonTValue::i64(30)
    );
}

#[test]
fn test_eval_dotted_field_ref() {
    // EvalContext keys are dot-joined paths
    let ctx = EvalContext::new().bind("address.city", JsonTValue::str("London"));
    let expr = JsonTExpression::parse("address.city").unwrap();
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::str("London"));
}

// ── Logical operators ─────────────────────────────────────────────────────────

#[test]
fn test_eval_and_tt() {
    let expr = JsonTExpression::and(lit_bool(true), lit_bool(true));
    assert_eq!(expr.evaluate(&ctx()).unwrap(), JsonTValue::bool(true));
}

#[test]
fn test_eval_and_tf() {
    let expr = JsonTExpression::and(lit_bool(true), lit_bool(false));
    assert_eq!(expr.evaluate(&ctx()).unwrap(), JsonTValue::bool(false));
}

#[test]
fn test_eval_and_ff() {
    let expr = JsonTExpression::and(lit_bool(false), lit_bool(false));
    assert_eq!(expr.evaluate(&ctx()).unwrap(), JsonTValue::bool(false));
}

#[test]
fn test_eval_or_ft() {
    let expr = JsonTExpression::or(lit_bool(false), lit_bool(true));
    assert_eq!(expr.evaluate(&ctx()).unwrap(), JsonTValue::bool(true));
}

#[test]
fn test_eval_or_ff() {
    let expr = JsonTExpression::or(lit_bool(false), lit_bool(false));
    assert_eq!(expr.evaluate(&ctx()).unwrap(), JsonTValue::bool(false));
}

// ── Equality operators ────────────────────────────────────────────────────────

#[test]
fn test_eval_eq_numbers_true() {
    assert_eq!(
        JsonTExpression::eq(lit_num(5.0), lit_num(5.0)).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(true)
    );
}

#[test]
fn test_eval_eq_numbers_false() {
    assert_eq!(
        JsonTExpression::eq(lit_num(5.0), lit_num(6.0)).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(false)
    );
}

#[test]
fn test_eval_eq_strings_true() {
    assert_eq!(
        JsonTExpression::eq(lit_str("x"), lit_str("x")).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(true)
    );
}

#[test]
fn test_eval_eq_bools_true() {
    assert_eq!(
        JsonTExpression::eq(lit_bool(true), lit_bool(true)).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(true)
    );
}

#[test]
fn test_eval_eq_null_null() {
    let null = || JsonTExpression::Literal(JsonTValue::Null);
    assert_eq!(
        JsonTExpression::eq(null(), null()).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(true)
    );
}

#[test]
fn test_eval_eq_mixed_types_false() {
    // number != string, even if they look similar
    assert_eq!(
        JsonTExpression::eq(lit_num(1.0), lit_str("1")).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(false)
    );
}

#[test]
fn test_eval_neq_true() {
    assert_eq!(
        JsonTExpression::neq(lit_str("a"), lit_str("b")).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(true)
    );
}

#[test]
fn test_eval_neq_false() {
    assert_eq!(
        JsonTExpression::neq(lit_num(3.0), lit_num(3.0)).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(false)
    );
}

// ── Relational operators ──────────────────────────────────────────────────────

#[test]
fn test_eval_lt_true() {
    assert_eq!(
        JsonTExpression::lt(lit_num(3.0), lit_num(5.0)).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(true)
    );
}

#[test]
fn test_eval_lt_equal_false() {
    assert_eq!(
        JsonTExpression::lt(lit_num(5.0), lit_num(5.0)).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(false)
    );
}

#[test]
fn test_eval_le_equal_true() {
    assert_eq!(
        JsonTExpression::le(lit_num(5.0), lit_num(5.0)).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(true)
    );
}

#[test]
fn test_eval_gt_true() {
    assert_eq!(
        JsonTExpression::gt(lit_num(10.0), lit_num(2.0)).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(true)
    );
}

#[test]
fn test_eval_gt_equal_false() {
    assert_eq!(
        JsonTExpression::gt(lit_num(5.0), lit_num(5.0)).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(false)
    );
}

#[test]
fn test_eval_ge_equal_true() {
    assert_eq!(
        JsonTExpression::ge(lit_num(5.0), lit_num(5.0)).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(true)
    );
}

// ── Arithmetic operators ──────────────────────────────────────────────────────

#[test]
fn test_eval_add() {
    assert_f64(JsonTExpression::add(lit_num(3.0), lit_num(4.0)).evaluate(&ctx()).unwrap(), 7.0);
}

#[test]
fn test_eval_sub() {
    assert_f64(JsonTExpression::sub(lit_num(10.0), lit_num(3.0)).evaluate(&ctx()).unwrap(), 7.0);
}

#[test]
fn test_eval_mul() {
    assert_f64(JsonTExpression::mul(lit_num(3.0), lit_num(4.0)).evaluate(&ctx()).unwrap(), 12.0);
}

#[test]
fn test_eval_div() {
    assert_f64(JsonTExpression::div(lit_num(10.0), lit_num(4.0)).evaluate(&ctx()).unwrap(), 2.5);
}

#[test]
fn test_eval_add_with_field() {
    let ctx = EvalContext::new().bind("x", JsonTValue::d64(5.0));
    let expr = JsonTExpression::add(JsonTExpression::field_name("x"), lit_num(3.0));
    assert_f64(expr.evaluate(&ctx).unwrap(), 8.0);
}

// ── Unary operators ───────────────────────────────────────────────────────────

#[test]
fn test_eval_not_true_gives_false() {
    assert_eq!(
        JsonTExpression::not(lit_bool(true)).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(false)
    );
}

#[test]
fn test_eval_not_false_gives_true() {
    assert_eq!(
        JsonTExpression::not(lit_bool(false)).evaluate(&ctx()).unwrap(),
        JsonTValue::bool(true)
    );
}

#[test]
fn test_eval_neg_positive() {
    assert_f64(JsonTExpression::negate(lit_num(5.0)).evaluate(&ctx()).unwrap(), -5.0);
}

#[test]
fn test_eval_neg_negative() {
    // negate(negate(3)) == 3
    let expr = JsonTExpression::negate(JsonTExpression::negate(lit_num(3.0)));
    assert_f64(expr.evaluate(&ctx()).unwrap(), 3.0);
}

// ── Row-based eval — bind a JsonTRow's fields into EvalContext ────────────────

// Scenario: a 3-field row {username, score, label}.
// Expression: username == "alice" && upper(label) == "GOLD" && score >= 50
// Verifies that field values from a real JsonTRow drive a compound expression
// that mixes equality, a string function, and a numeric comparison.
#[test]
fn test_eval_row_fields_with_function_on_field2_and_comparison_on_field3() {
    // Build the row (positional values, matching field order username/score/label).
    let row = jsont::JsonTRow::new(vec![
        JsonTValue::str("alice"),
        JsonTValue::i64(80),
        JsonTValue::str("gold"),
    ]);

    // Bind each field by name into the evaluation context.
    let ctx = EvalContext::new()
        .bind("username", row.get(0).unwrap().clone())  // "alice"
        .bind("score",    row.get(1).unwrap().clone())  // 80
        .bind("label",    row.get(2).unwrap().clone()); // "gold"

    // username == "alice"   — direct string equality on field 1
    // upper(label) == "GOLD" — function applied to field 3, compared to literal
    // score >= 50            — numeric comparison on field 2
    let expr = JsonTExpression::parse(
        r#"username == "alice" && upper(label) == "GOLD" && score >= 50"#
    ).unwrap();

    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::bool(true));
}

// Same row shape, but score is below threshold — overall expression is false.
#[test]
fn test_eval_row_fields_expression_false_when_score_too_low() {
    let row = jsont::JsonTRow::new(vec![
        JsonTValue::str("alice"),
        JsonTValue::i64(30),   // score below 50
        JsonTValue::str("gold"),
    ]);

    let ctx = EvalContext::new()
        .bind("username", row.get(0).unwrap().clone())
        .bind("score",    row.get(1).unwrap().clone())
        .bind("label",    row.get(2).unwrap().clone());

    let expr = JsonTExpression::parse(
        r#"username == "alice" && upper(label) == "GOLD" && score >= 50"#
    ).unwrap();

    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::bool(false));
}

// ── Compound / end-to-end ─────────────────────────────────────────────────────

#[test]
fn test_eval_arithmetic_precedence_from_parse() {
    // 1 + 2 * 3 = 7
    let expr = JsonTExpression::parse("1 + 2 * 3").unwrap();
    assert_f64(expr.evaluate(&ctx()).unwrap(), 7.0);
}

#[test]
fn test_eval_parens_override_precedence() {
    // (1 + 2) * 3 = 9
    let expr = JsonTExpression::parse("(1 + 2) * 3").unwrap();
    assert_f64(expr.evaluate(&ctx()).unwrap(), 9.0);
}

#[test]
fn test_eval_compound_with_field_binding() {
    let ctx = EvalContext::new().bind("x", JsonTValue::d64(10.0));
    let expr = JsonTExpression::parse("x * 2 + 1").unwrap();
    assert_f64(expr.evaluate(&ctx).unwrap(), 21.0);
}

#[test]
fn test_eval_chained_logical_with_comparisons() {
    let ctx = EvalContext::new()
        .bind("age", JsonTValue::i64(25))
        .bind("active", JsonTValue::bool(true));
    let expr = JsonTExpression::parse("age >= 18 && active").unwrap();
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::bool(true));
}

#[test]
fn test_eval_unary_neg_parsed() {
    let ctx = EvalContext::new().bind("amount", JsonTValue::d64(4.0));
    let expr = JsonTExpression::parse("-amount").unwrap();
    assert_f64(expr.evaluate(&ctx).unwrap(), -4.0);
}

#[test]
fn test_eval_unary_not_parsed() {
    let ctx = EvalContext::new().bind("active", JsonTValue::bool(false));
    let expr = JsonTExpression::parse("!active").unwrap();
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::bool(true));
}

// ── Built-in functions ────────────────────────────────────────────────────────

#[test]
fn test_eval_length_string() {
    let ctx = EvalContext::new().bind("name", JsonTValue::str("hello"));
    let expr = JsonTExpression::call("length", vec![JsonTExpression::field_name("name")]);
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::Number(JsonTNumber::I64(5)));
}

#[test]
fn test_eval_length_empty_string() {
    let ctx = EvalContext::new().bind("name", JsonTValue::str(""));
    let expr = JsonTExpression::call("length", vec![JsonTExpression::field_name("name")]);
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::Number(JsonTNumber::I64(0)));
}

#[test]
fn test_eval_length_array() {
    let arr = JsonTValue::Array(JsonTArray::new(vec![
        JsonTValue::i64(1),
        JsonTValue::i64(2),
        JsonTValue::i64(3),
    ]));
    let ctx = EvalContext::new().bind("items", arr);
    let expr = JsonTExpression::call("length", vec![JsonTExpression::field_name("items")]);
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::Number(JsonTNumber::I64(3)));
}

#[test]
fn test_eval_upper() {
    let ctx = EvalContext::new().bind("name", JsonTValue::str("hello world"));
    let expr = JsonTExpression::call("upper", vec![JsonTExpression::field_name("name")]);
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::str("HELLO WORLD"));
}

#[test]
fn test_eval_lower() {
    let ctx = EvalContext::new().bind("name", JsonTValue::str("HELLO WORLD"));
    let expr = JsonTExpression::call("lower", vec![JsonTExpression::field_name("name")]);
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::str("hello world"));
}

#[test]
fn test_eval_upper_already_uppercase() {
    let ctx = EvalContext::new().bind("s", JsonTValue::str("HELLO"));
    let expr = JsonTExpression::call("upper", vec![JsonTExpression::field_name("s")]);
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::str("HELLO"));
}

#[test]
fn test_eval_coalesce_returns_first_non_null() {
    let ctx = EvalContext::new()
        .bind("a", JsonTValue::Null)
        .bind("b", JsonTValue::str("found"));
    let expr = JsonTExpression::call("coalesce", vec![
        JsonTExpression::field_name("a"),
        JsonTExpression::field_name("b"),
    ]);
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::str("found"));
}

#[test]
fn test_eval_coalesce_all_null_returns_null() {
    let ctx = EvalContext::new()
        .bind("a", JsonTValue::Null)
        .bind("b", JsonTValue::Null);
    let expr = JsonTExpression::call("coalesce", vec![
        JsonTExpression::field_name("a"),
        JsonTExpression::field_name("b"),
    ]);
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::Null);
}

#[test]
fn test_eval_coalesce_skips_unspecified() {
    let ctx = EvalContext::new()
        .bind("a", JsonTValue::Unspecified)
        .bind("b", JsonTValue::str("ok"));
    let expr = JsonTExpression::call("coalesce", vec![
        JsonTExpression::field_name("a"),
        JsonTExpression::field_name("b"),
    ]);
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::str("ok"));
}

#[test]
fn test_eval_coalesce_first_arg_non_null_returned_immediately() {
    // "b" is unbound — if coalesce short-circuits correctly it is never evaluated
    let ctx = EvalContext::new().bind("a", JsonTValue::str("first"));
    let expr = JsonTExpression::call("coalesce", vec![
        JsonTExpression::field_name("a"),
        JsonTExpression::field_name("b_unbound"),
    ]);
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::str("first"));
}

#[test]
fn test_eval_nested_function_calls() {
    // length(upper(name)) — upper then length
    let ctx = EvalContext::new().bind("name", JsonTValue::str("hello"));
    let expr = JsonTExpression::call("length", vec![
        JsonTExpression::call("upper", vec![JsonTExpression::field_name("name")]),
    ]);
    assert_eq!(expr.evaluate(&ctx).unwrap(), JsonTValue::Number(JsonTNumber::I64(5)));
}

// =============================================================================
// PART 3 — Error path tests
// =============================================================================

// ── Unbound field ─────────────────────────────────────────────────────────────

#[test]
fn test_error_unbound_field() {
    let err = JsonTExpression::field_name("missing").evaluate(&ctx()).unwrap_err();
    assert!(
        matches!(err, JsonTError::Eval(EvalError::UnboundField(ref f)) if f == "missing"),
        "unexpected error: {err}"
    );
}

// ── Division by zero ──────────────────────────────────────────────────────────

#[test]
fn test_error_div_by_zero() {
    let err = JsonTExpression::div(lit_num(5.0), lit_num(0.0)).evaluate(&ctx()).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::DivisionByZero)), "unexpected: {err}");
}

// ── Unknown function ──────────────────────────────────────────────────────────

#[test]
fn test_error_unknown_function() {
    let err = JsonTExpression::call("nonexistent", vec![lit_num(1.0)])
        .evaluate(&ctx())
        .unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::UnknownFunction(_))), "unexpected: {err}");
}

// ── Arity mismatch ────────────────────────────────────────────────────────────

#[test]
fn test_error_arity_length_too_many_args() {
    let err = JsonTExpression::call("length", vec![lit_num(1.0), lit_num(2.0)])
        .evaluate(&ctx())
        .unwrap_err();
    assert!(
        matches!(err, JsonTError::Eval(EvalError::ArityMismatch { expected: 1, got: 2, .. })),
        "unexpected: {err}"
    );
}

#[test]
fn test_error_arity_coalesce_zero_args() {
    let err = JsonTExpression::call("coalesce", vec![]).evaluate(&ctx()).unwrap_err();
    assert!(
        matches!(err, JsonTError::Eval(EvalError::ArityMismatch { got: 0, .. })),
        "unexpected: {err}"
    );
}

#[test]
fn test_error_arity_upper_too_many_args() {
    let err = JsonTExpression::call("upper", vec![lit_str("a"), lit_str("b")])
        .evaluate(&ctx())
        .unwrap_err();
    assert!(
        matches!(err, JsonTError::Eval(EvalError::ArityMismatch { expected: 1, got: 2, .. })),
        "unexpected: {err}"
    );
}

// ── Type mismatches — logical operators ───────────────────────────────────────

#[test]
fn test_error_and_non_bool_left() {
    let err = JsonTExpression::and(lit_num(1.0), lit_bool(true)).evaluate(&ctx()).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

#[test]
fn test_error_or_non_bool_right() {
    let err = JsonTExpression::or(lit_bool(false), lit_num(0.0)).evaluate(&ctx()).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

// ── Type mismatches — arithmetic operators ────────────────────────────────────

#[test]
fn test_error_add_bool_operand() {
    let err = JsonTExpression::add(lit_bool(true), lit_num(1.0)).evaluate(&ctx()).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

#[test]
fn test_error_sub_string_operand() {
    let err = JsonTExpression::sub(lit_str("a"), lit_num(1.0)).evaluate(&ctx()).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

#[test]
fn test_error_mul_null_operand() {
    let err = JsonTExpression::mul(JsonTExpression::Literal(JsonTValue::Null), lit_num(2.0))
        .evaluate(&ctx())
        .unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

// ── Type mismatches — relational operators ────────────────────────────────────

#[test]
fn test_error_lt_string_operands() {
    // Strings are not numeric — relational ops require numbers
    let err = JsonTExpression::lt(lit_str("a"), lit_str("b")).evaluate(&ctx()).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

// ── Type mismatches — unary operators ────────────────────────────────────────

#[test]
fn test_error_not_on_number() {
    let err = JsonTExpression::not(lit_num(1.0)).evaluate(&ctx()).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

#[test]
fn test_error_neg_on_bool() {
    let err = JsonTExpression::negate(lit_bool(true)).evaluate(&ctx()).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

#[test]
fn test_error_neg_on_string() {
    let err = JsonTExpression::negate(lit_str("x")).evaluate(&ctx()).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

// ── Type mismatches — built-in functions ─────────────────────────────────────

#[test]
fn test_error_length_on_number() {
    let ctx = EvalContext::new().bind("x", JsonTValue::i64(5));
    let expr = JsonTExpression::call("length", vec![JsonTExpression::field_name("x")]);
    let err = expr.evaluate(&ctx).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

#[test]
fn test_error_length_on_bool() {
    let ctx = EvalContext::new().bind("x", JsonTValue::bool(true));
    let expr = JsonTExpression::call("length", vec![JsonTExpression::field_name("x")]);
    let err = expr.evaluate(&ctx).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

#[test]
fn test_error_upper_on_number() {
    let ctx = EvalContext::new().bind("x", JsonTValue::i64(5));
    let expr = JsonTExpression::call("upper", vec![JsonTExpression::field_name("x")]);
    let err = expr.evaluate(&ctx).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

#[test]
fn test_error_lower_on_null() {
    let ctx = EvalContext::new().bind("x", JsonTValue::Null);
    let expr = JsonTExpression::call("lower", vec![JsonTExpression::field_name("x")]);
    let err = expr.evaluate(&ctx).unwrap_err();
    assert!(matches!(err, JsonTError::Eval(EvalError::TypeMismatch { .. })), "unexpected: {err}");
}

// =============================================================================
// Helpers
// =============================================================================

fn ctx() -> EvalContext {
    EvalContext::new()
}

fn lit_num(n: f64) -> JsonTExpression {
    JsonTExpression::Literal(JsonTValue::d64(n))
}

fn lit_bool(b: bool) -> JsonTExpression {
    JsonTExpression::Literal(JsonTValue::bool(b))
}

fn lit_str(s: &str) -> JsonTExpression {
    JsonTExpression::Literal(JsonTValue::str(s))
}

/// Assert a JsonTValue is a Number whose f64 representation is within epsilon of `expected`.
fn assert_f64(value: JsonTValue, expected: f64) {
    let got = value.as_f64().unwrap_or_else(|| panic!("expected a number, got {value:?}"));
    assert!(
        (got - expected).abs() < 1e-10,
        "expected {expected}, got {got}"
    );
}

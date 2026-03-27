grammar JsonTSchema;

// ─── Top-level ────────────────────────────────────────────────────────────────

namespace_def
    : NAMESPACE STRING_LITERAL? LBRACE catalog_def* RBRACE EOF
    ;

catalog_def
    : CATALOG LBRACE catalog_item* RBRACE
    ;

catalog_item
    : schema_def
    | enum_def
    ;

// ─── Schema ───────────────────────────────────────────────────────────────────

schema_def
    : straight_schema_def
    | derived_schema_def
    ;

straight_schema_def
    : SCHEMA IDENT LBRACE straight_schema_body* RBRACE
    ;

derived_schema_def
    : SCHEMA IDENT DERIVED IDENT LBRACE derived_schema_body* RBRACE
    ;

straight_schema_body
    : fields_block
    | validations_block
    ;

derived_schema_body
    : rename_op
    | exclude_op
    | project_op
    | filter_op
    | transform_op
    ;

// ─── Fields block ─────────────────────────────────────────────────────────────

fields_block
    : FIELDS LBRACE field_def* RBRACE
    ;

field_def
    : field_name COLON field_type_spec COMMA?
    ;

// Allow scalar type keywords as field names (e.g. "email", "date", "ts")
field_name
    : IDENT
    | I16 | I32 | I64 | U16 | U32 | U64
    | D32 | D64 | D128
    | BOOL | STR | NSTR
    | URI | UUID | EMAIL | HOSTNAME | IPV4 | IPV6
    | DATE | TIME | DTM | TS | TSZ | DUR | INST
    | B64 | OID | HEX
    | MIN_VALUE | MAX_VALUE | MIN_LENGTH | MAX_LENGTH
    | PATTERN | REQUIRED | MAX_PRECISION | MIN_ITEMS | MAX_ITEMS
    | ALLOW_NULLS | MAX_NULL_ITEMS | CONSTANT
    | IF | REQUIRE
    ;

field_type_spec
    : base_type ARRAY_SUFFIX? OPTIONAL_SUFFIX? constraints?
    ;

base_type
    : scalar_type
    | object_type
    ;

scalar_type
    : I16 | I32 | I64 | U16 | U32 | U64
    | D32 | D64 | D128
    | BOOL | STR | NSTR
    | URI | UUID | EMAIL | HOSTNAME | IPV4 | IPV6
    | DATE | TIME | DTM | TS | TSZ | DUR | INST
    | B64 | OID | HEX
    ;

object_type
    : LT IDENT GT
    ;

constraints
    : LPAREN constraint_item (COMMA constraint_item)* RPAREN
    ;

constraint_item
    : MIN_VALUE EQ signed_number      # MinValueItem
    | MAX_VALUE EQ signed_number      # MaxValueItem
    | MIN_LENGTH EQ INT_LITERAL       # MinLengthItem
    | MAX_LENGTH EQ INT_LITERAL       # MaxLengthItem
    | PATTERN EQ STRING_LITERAL       # PatternItem
    | REQUIRED                        # RequiredItem
    | MAX_PRECISION EQ INT_LITERAL    # MaxPrecisionItem
    | MIN_ITEMS EQ INT_LITERAL        # MinItemsItem
    | MAX_ITEMS EQ INT_LITERAL        # MaxItemsItem
    | ALLOW_NULLS                     # AllowNullsItem
    | MAX_NULL_ITEMS EQ INT_LITERAL   # MaxNullItemsItem
    | CONSTANT EQ constraint_literal  # ConstantValueItem
    ;

constraint_literal
    : MINUS? (INT_LITERAL | FLOAT_LITERAL)
    | BOOL_LITERAL
    | STRING_LITERAL
    | NULL_LITERAL
    ;

signed_number
    : MINUS? (INT_LITERAL | FLOAT_LITERAL)
    ;

// ─── Validations block ────────────────────────────────────────────────────────

validations_block
    : VALIDATIONS LBRACE validation_item* RBRACE
    ;

validation_item
    : unique_validation
    | rule_validation
    | conditional_validation
    ;

unique_validation
    : UNIQUE COLON LBRACKET field_path_list RBRACKET COMMA?
    ;

rule_validation
    : RULE COLON expr COMMA?
    ;

conditional_validation
    : IF LPAREN expr RPAREN REQUIRE LPAREN field_path_list RPAREN COMMA?
    ;

// ─── Derived schema operations ────────────────────────────────────────────────

rename_op
    : RENAME COLON LBRACKET rename_pair (COMMA rename_pair)* RBRACKET COMMA?
    ;

rename_pair
    : IDENT ARROW IDENT
    ;

exclude_op
    : EXCLUDE COLON LBRACKET field_path_list RBRACKET COMMA?
    ;

project_op
    : PROJECT COLON LBRACKET field_path_list RBRACKET COMMA?
    ;

filter_op
    : FILTER COLON expr COMMA?
    ;

transform_op
    : TRANSFORM COLON IDENT EQ expr COMMA?
    ;

// ─── Field paths ──────────────────────────────────────────────────────────────

field_path_list
    : field_path (COMMA field_path)*
    ;

field_path
    : field_name (DOT field_name)*
    ;

// ─── Enum ─────────────────────────────────────────────────────────────────────

enum_def
    : ENUM IDENT LBRACE enum_values RBRACE
    ;

enum_values
    : IDENT (COMMA IDENT)* COMMA?
    ;

// ─── Expressions ──────────────────────────────────────────────────────────────
// ANTLR4 left-recursive: alternatives listed FIRST have HIGHER precedence (bind tighter).
// So list highest-precedence operators first.

expr
    : expr (STAR | SLASH) expr                      # MulDivExpr
    | expr (PLUS | MINUS) expr                      # AddSubExpr
    | expr (LT | LE_OP | GT | GE_OP) expr          # ComparisonExpr
    | expr (EQ_OP | NE_OP) expr                    # EqualityExpr
    | expr AND_OP expr                              # BinaryAndExpr
    | expr OR_OP expr                               # BinaryOrExpr
    | NOT_OP expr                                   # NotExpr
    | MINUS expr                                    # NegExpr
    | LPAREN expr RPAREN                            # ParenExpr
    | NULL_LITERAL                                  # NullLiteralExpr
    | BOOL_LITERAL                                  # BoolLiteralExpr
    | INT_LITERAL                                   # IntLiteralExpr
    | FLOAT_LITERAL                                 # FloatLiteralExpr
    | STRING_LITERAL                                # StringLiteralExpr
    | IDENT (DOT IDENT)*                            # FieldRefExpr
    ;

// ─── Keywords (must appear before IDENT in lexer) ─────────────────────────────

NAMESPACE   : 'namespace' ;
CATALOG     : 'catalog' ;
SCHEMA      : 'schema' ;
DERIVED     : 'derived' ;
FIELDS      : 'fields' ;
VALIDATIONS : 'validations' ;
UNIQUE      : 'unique' ;
RULE        : 'rule' ;
RENAME      : 'rename' ;
EXCLUDE     : 'exclude' ;
PROJECT     : 'project' ;
FILTER      : 'filter' ;
TRANSFORM   : 'transform' ;
ENUM        : 'enum' ;

// Scalar type keywords
I16      : 'i16' ;   I32   : 'i32' ;   I64  : 'i64' ;
U16      : 'u16' ;   U32   : 'u32' ;   U64  : 'u64' ;
D32      : 'd32' ;   D64   : 'd64' ;   D128 : 'd128' ;
BOOL     : 'bool' ;  STR   : 'str' ;   NSTR : 'nstr' ;
URI      : 'uri' ;   UUID  : 'uuid' ;  EMAIL : 'email' ;
HOSTNAME : 'hostname' ; IPV4 : 'ipv4' ; IPV6 : 'ipv6' ;
DATE     : 'date' ;  TIME  : 'time' ;  DTM  : 'dtm' ;
TS       : 'ts' ;    TSZ   : 'tsz' ;   DUR  : 'dur' ;
INST     : 'inst' ;  B64   : 'b64' ;   OID  : 'oid' ;
HEX      : 'hex' ;

// Constraint keywords
MIN_VALUE      : 'minValue' ;
MAX_VALUE      : 'maxValue' ;
MIN_LENGTH     : 'minLength' ;
MAX_LENGTH     : 'maxLength' ;
PATTERN        : 'pattern' ;
REQUIRED       : 'required' ;
MAX_PRECISION  : 'maxPrecision' ;
MIN_ITEMS      : 'minItems' ;
MAX_ITEMS      : 'maxItems' ;
ALLOW_NULLS    : 'allowNulls' ;
MAX_NULL_ITEMS : 'maxNullItems' ;
CONSTANT       : 'constant' ;

// Conditional validation keywords
IF      : 'if' ;
REQUIRE : 'require' ;

// Literals
NULL_LITERAL  : 'null' ;
BOOL_LITERAL  : 'true' | 'false' ;
INT_LITERAL   : [0-9]+ ;
FLOAT_LITERAL : [0-9]+ '.' [0-9]+ ([eE] [+-]? [0-9]+)? ;
STRING_LITERAL: '"' (~["\\\r\n] | '\\' .)* '"' ;

// Multi-char operators (defined BEFORE single-char to get longer match)
EQ_OP   : '==' ;
NE_OP   : '!=' ;
LE_OP   : '<=' ;
GE_OP   : '>=' ;
AND_OP  : '&&' ;
OR_OP   : '||' ;
NOT_OP  : '!' ;
ARROW   : '->' ;
ARRAY_SUFFIX   : '[]' ;
OPTIONAL_SUFFIX: '?' ;

// Single-char operators and punctuation
PLUS    : '+' ;
MINUS   : '-' ;
STAR    : '*' ;
SLASH   : '/' ;
LBRACE  : '{' ;
RBRACE  : '}' ;
LPAREN  : '(' ;
RPAREN  : ')' ;
LBRACKET: '[' ;
RBRACKET: ']' ;
LT      : '<' ;
GT      : '>' ;
COLON   : ':' ;
COMMA   : ',' ;
DOT     : '.' ;
EQ      : '=' ;

// Identifiers (MUST be after all keywords)
IDENT : [a-zA-Z_][a-zA-Z0-9_]* ;

// Skip whitespace and comments
WS           : [ \t\r\n]+ -> skip ;
LINE_COMMENT : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT: '/*' .*? '*/' -> skip ;

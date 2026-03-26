grammar JsonTSchema;

// ─── Top-level ────────────────────────────────────────────────────────────────

namespace_def
    : NAMESPACE IDENT LBRACE catalog_def* RBRACE EOF
    ;

catalog_def
    : CATALOG IDENT LBRACE catalog_item* RBRACE
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
    : IDENT COLON field_type_spec COMMA?
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
    : MIN_VALUE EQ number_literal
    | MAX_VALUE EQ number_literal
    | MIN_LENGTH EQ INT_LITERAL
    | MAX_LENGTH EQ INT_LITERAL
    | PATTERN EQ STRING_LITERAL
    | REQUIRED
    | MAX_PRECISION EQ INT_LITERAL
    | MIN_ITEMS EQ INT_LITERAL
    | MAX_ITEMS EQ INT_LITERAL
    | ALLOW_NULLS
    | MAX_NULL_ITEMS EQ INT_LITERAL
    ;

// ─── Validations block ────────────────────────────────────────────────────────

validations_block
    : VALIDATIONS LBRACE validation_item* RBRACE
    ;

validation_item
    : unique_validation
    | rule_validation
    ;

unique_validation
    : UNIQUE COLON LBRACKET field_path_list RBRACKET COMMA?
    ;

rule_validation
    : RULE COLON expr COMMA?
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
    : IDENT (DOT IDENT)*
    ;

// ─── Enum ─────────────────────────────────────────────────────────────────────

enum_def
    : ENUM IDENT LBRACE enum_values RBRACE
    ;

enum_values
    : IDENT (COMMA IDENT)* COMMA?
    ;

// ─── Expressions ──────────────────────────────────────────────────────────────

expr
    : expr AND_OP expr                                  # BinaryAndExpr
    | expr OR_OP expr                                   # BinaryOrExpr
    | expr (EQ_OP | NE_OP) expr                        # EqualityExpr
    | expr (LT_OP | LE_OP | GT_OP | GE_OP) expr        # ComparisonExpr
    | expr (PLUS | MINUS) expr                          # AddSubExpr
    | expr (STAR | SLASH) expr                          # MulDivExpr
    | NOT_OP expr                                       # NotExpr
    | MINUS expr                                        # NegExpr
    | LPAREN expr RPAREN                                # ParenExpr
    | NULL_LITERAL                                      # NullLiteralExpr
    | BOOL_LITERAL                                      # BoolLiteralExpr
    | number_literal                                    # NumberLiteralExpr
    | STRING_LITERAL                                    # StringLiteralExpr
    | IDENT                                             # FieldRefExpr
    ;

number_literal
    : INT_LITERAL
    | FLOAT_LITERAL
    ;

// ─── Keywords ─────────────────────────────────────────────────────────────────

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
I16         : 'i16' ;
I32         : 'i32' ;
I64         : 'i64' ;
U16         : 'u16' ;
U32         : 'u32' ;
U64         : 'u64' ;
D32         : 'd32' ;
D64         : 'd64' ;
D128        : 'd128' ;
BOOL        : 'bool' ;
STR         : 'str' ;
NSTR        : 'nstr' ;
URI         : 'uri' ;
UUID        : 'uuid' ;
EMAIL       : 'email' ;
HOSTNAME    : 'hostname' ;
IPV4        : 'ipv4' ;
IPV6        : 'ipv6' ;
DATE        : 'date' ;
TIME        : 'time' ;
DTM         : 'dtm' ;
TS          : 'ts' ;
TSZ         : 'tsz' ;
DUR         : 'dur' ;
INST        : 'inst' ;
B64         : 'b64' ;
OID         : 'oid' ;
HEX         : 'hex' ;

// Constraint keywords
MIN_VALUE       : 'minValue' ;
MAX_VALUE       : 'maxValue' ;
MIN_LENGTH      : 'minLength' ;
MAX_LENGTH      : 'maxLength' ;
PATTERN         : 'pattern' ;
REQUIRED        : 'required' ;
MAX_PRECISION   : 'maxPrecision' ;
MIN_ITEMS       : 'minItems' ;
MAX_ITEMS       : 'maxItems' ;
ALLOW_NULLS     : 'allowNulls' ;
MAX_NULL_ITEMS  : 'maxNullItems' ;

// Literals
NULL_LITERAL    : 'null' ;
BOOL_LITERAL    : 'true' | 'false' ;
INT_LITERAL     : '-'? [0-9]+ ;
FLOAT_LITERAL   : '-'? [0-9]+ '.' [0-9]+ ([eE] [+-]? [0-9]+)? ;
STRING_LITERAL  : '"' (~["\\\r\n] | '\\' .)* '"' ;

// Operators
EQ_OP   : '==' ;
NE_OP   : '!=' ;
LE_OP   : '<=' ;
GE_OP   : '>=' ;
LT_OP   : '<' ;
GT_OP   : '>' ;
AND_OP  : '&&' ;
OR_OP   : '||' ;
NOT_OP  : '!' ;
PLUS    : '+' ;
MINUS   : '-' ;
STAR    : '*' ;
SLASH   : '/' ;
ARROW   : '->' ;

// Punctuation
LBRACE          : '{' ;
RBRACE          : '}' ;
LPAREN          : '(' ;
RPAREN          : ')' ;
LBRACKET        : '[' ;
RBRACKET        : ']' ;
LT              : '<' ;
GT              : '>' ;
COLON           : ':' ;
COMMA           : ',' ;
DOT             : '.' ;
EQ              : '=' ;
OPTIONAL_SUFFIX : '?' ;
ARRAY_SUFFIX    : '[]' ;

// Identifier (must come after all keywords)
IDENT   : [a-zA-Z_] [a-zA-Z0-9_]* ;

// Whitespace and comments
WS      : [ \t\r\n]+ -> skip ;
LINE_COMMENT    : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT   : '/*' .*? '*/' -> skip ;

grammar JsonT;

/*
 * ========================
 *  Parser Rules
 * ========================
 */


jsonT
    :
    catalog?
    data?
    EOF
    ;

catalog
    : LB
    schemasSection
    (COMMA enumsSection)?
    RB
    ;

data
    : LB
    dataSchemaSection
    COMMA dataSection
    RB
    ;
/*
 * ---- schemas ----
 * schemas : {
 *   Customer : { ... },
 *   Address  : { ... }
 * }
 */
schemasSection
    : SCHEMAS COLON LB schemaEntry (COMMA schemaEntry)* RB
    ;

schemaEntry
    : IDENT COLON schemaNode
    ;

schemaNode
    : LB fieldDecl (COMMA fieldDecl)* RB
    ;

/*
 * Field definition
 * type : fieldName [ ? ] [ ( constraints ) ]
 * (constraints parsed later, not now)
 */
fieldDecl
    : typeRef COLON IDENT optionalMark? LP ( constraintsSection )? RP
    ;

optionalMark
    : QMARK
    ;

constraintsSection
    : constraint (COMMA constraint)*
    ;

constraint
    : constraintName LP constraintValue RP
    ;

constraintName
    : IDENT
    ;

constraintValue
    : NUMBER | STRING | BOOLEAN
    ;

enumsSection
    : ENUMS COLON LA enumDef (COMMA enumDef)* RA
    ;

enumDef
    : IDENT enumBody
    ;

enumBody
    : LB enumValue (COMMA enumValue)* RB
    ;

enumValue
    : IDENT
    ;
/*
 * ---- data-schema ----
 * data-schema : Customer
 */

dataSchemaSection
    : DATA_SCHEMA COLON IDENT
    ;

/*
 * ---- data ----
 * data : [
 *   { v1, v2, v3 },
 *   { v1, v2, v3 }
 * ]
 */
dataSection
    : DATA COLON LA dataRow (COMMA dataRow)* RA
    ;

dataRow
    : LB value (COMMA value)* RB
    ;

/*
 * ---- values ----
 * Meaning decided by schema type, not syntax
 */
value
    : scalarValue
    | objectValue
    | arrayValue
    ;

scalarValue
    : STRING
    | NUMBER
    | BOOLEAN
    | NULL
    | IDENT
    ;
objectValue
    : LB value (COMMA value)* RB
    ;

arrayValue
    : LA value (COMMA value)* RA
    ;

/*
 * ---- types ----
 * int, str, uuid, <Address>, str[], etc.
 */
typeRef
    : IDENT arraySuffix?
    | LT IDENT GT arraySuffix?
    ;

arraySuffix
    : LA RA
    ;

/*
 * ========================
 *  Lexer Rules
 * ========================
 */

// Keywords (must come before IDENT)
SCHEMAS     : 'schemas';
DATA        : 'data';
DATA_SCHEMA : 'data-schema';
ENUMS       : 'enums';

// Symbols
LB      : '{';
RB      : '}';
LA      : '[';
RA      : ']';
LP      : '(';
RP      : ')';
COLON   : ':';
COMMA   : ',';
LT      : '<';
GT      : '>';
QMARK   : '?';

// Literals
BOOLEAN : 'true' | 'false';
NULL    : 'null' | '\u2205';

NUMBER
    : '-'? [0-9]+ ('.' [0-9]+)?
    ;

// Free text only
STRING
    : '"'  ( '\\' . | ~["\\] )* '"'
    | '\'' ( '\\' . | ~['\\] )* '\''
    ;

// Identifiers (types, schema names, unquoted scalar values)
IDENT
    : [a-zA-Z_] [a-zA-Z0-9_]*
    ;

// Whitespace
WS
    : [ \t\r\n]+ -> skip
    ;

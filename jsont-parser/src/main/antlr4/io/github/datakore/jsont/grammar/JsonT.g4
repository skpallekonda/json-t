grammar JsonT;

/*
 * ========================
 * Parser Rules
 * ========================
 */

jsonT
    : nameSpace? data? EOF
    ;

/*
Namespace should provide something like this
{
  namespace: {
    baseUrl: "https://api.datakore.com/v1",
    catalogs: [
      {
        schemas: [
          User: {
            i32: id,
            str: username(min 5),
            str: email?
          },
          Address: {
             str: city,
             str: zipCode
          }
        ],
        enums: [
          Status: [ ACTIVE, INACTIVE, SUSPENDED ],
          Role: [ ADMIN, USER ]
        ]
      }
    ]
  }
}
*/
nameSpace
    : LB NS_NAME COLON LB
        NSURL_NAME COLON nsBaseUrl COMMA
        CATALOGS_NAME COLON LA catalog (COMMA catalog)* RA
        RB RB
    ;

nsBaseUrl
    : STRING ;

nsSchemaName
    : SCHEMAID ;

nsFieldName
    : FIELDID ;

nsEnumName
    : SCHEMAID ;

catalog
    : LB
    schemasSection
    (COMMA enumsSection)?
    RB
    ;

/*
 * Updated to match Map structure:
 * schemas: { User: { ... } }
 */
schemasSection
    : SCHEMAS COLON LA schemaEntry (COMMA schemaEntry)* RA
    ;

schemaEntry
    : nsSchemaName COLON schemaNode
    ;

schemaNode
    : LB fieldDecl (COMMA fieldDecl)* RB
    ;

/*
 * FIX 2: Made parentheses optional
 * Valid:  str:name
 * Valid:  str:name(min 5)
 */
fieldDecl
    : typeRef COLON nsFieldName optionalMark? (LP constraintsSection? RP)?
    ;

optionalMark
    : QMARK
    ;

constraintsSection
    : constraint (COMMA constraint)*
    ;

constraint
    : constraintName EQ constraintValue
    ;

constraintName
    : FIELDID
    ;

constraintValue
    : NUMBER | STRING | BOOLEAN
    ;

enumsSection
    : ENUMS COLON LA enumDef (COMMA enumDef)* RA
    ;

enumDef
    : nsEnumName COLON enumBody
    ;

enumBody
    : LA enumValueConstant (COMMA enumValueConstant)* RA
    ;

enumValueConstant
    : CONSTID
    ;

dataSchemaSection
    : DATA_SCHEMA COLON SCHEMAID
    ;

/*
 * ---- data ----
 */
data
    : LB
    dataSchemaSection
    COMMA dataSection
    RB
    ;

dataSection
    : DATA COLON LA dataRow (COMMA dataRow)* RA
    ;

dataRow
    : objectValue
    ;

value
    : scalarValue
    | enumValue
    | objectValue
    | arrayValue
    ;

scalarValue
    : STRING
    | NUMBER
    | BOOLEAN
    | UNSPECIFIED
    | NULL
    ;

enumValue
    : CONSTID ;

objectValue
    : LB value (COMMA value)* RB
    ;

arrayValue
    : LA value (COMMA value)* RA
    ;

/*
 * ---- types ----
 */
typeRef
    : (baseType | objectTypeStruct) arraySuffix?
    ;

objectTypeStruct
    : LT objectTypeName GT
    ;

objectTypeName
    : SCHEMAID ;

arraySuffix
    : LA RA
    ;

baseType
    : K_I16     | K_I32  | K_I64      | K_U16       | K_U32   | K_U64
    | K_D32     | K_D64  | K_D128
    | K_DATE    | K_TIME | K_DATETIME | K_TIMESTAMP
    | K_TSZ     | K_INST | K_INSTZ
    | K_YEAR    | K_MON  | K_DAY      | K_YEARMON   | K_MNDAY
    | K_BIN     | K_OID  | K_HEX
    | K_STRING  | K_NSTR | K_URI      | K_UUID
    | K_BOOLEAN
    ;

/*
 * ========================
 * Lexer Rules
 * ========================
 */

// FIX 3: Added missing keywords for Namespace
NS_NAME     : 'namespace';
NSURL_NAME  : 'baseUrl';
CATALOGS_NAME : 'catalogs';

// Keywords
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
EQ      : '=';
QMARK   : '?';

// Types
K_I16   : 'i16' ;
K_I32   : 'i32' ;
K_I64   : 'i64' ;
K_U16   : 'u16' ;
K_U32   : 'u32' ;
K_U64   : 'u64' ;
K_D32   : 'd32' ;
K_D64   : 'd64' ;
K_D128  : 'd128' ;
K_DATE  : 'date' ;
K_TIME  : 'time' ;
K_DATETIME : 'dtm' ;
K_TIMESTAMP : 'ts' ;
K_TSZ   : 'tsz' ;
K_INST  : 'inst' ;
K_INSTZ : 'insz' ;
K_YEAR  : 'yr' ;
K_MON   : 'mon' ;
K_DAY   : 'day' ;
K_YEARMON : 'ym' ;
K_MNDAY : 'md' ;
K_BIN   : 'b64' ;
K_OID   : 'oid' ;
K_HEX   : 'hex' ;
K_STRING  : 'str' ;
K_NSTR  : 'nstr' ;
K_URI   : 'uri' ;
K_UUID  : 'uuid' ;
K_BOOLEAN : 'bool';

// Literals
BOOLEAN : 'true' | 'false';
NULL    : 'null' | 'nil';
UNSPECIFIED : '_';

NUMBER
    : '-'? [0-9]+ ('.' [0-9]+)?
    ;

STRING
    : '"'  ( '\\' . | ~["\\] )* '"'
    | '\'' ( '\\' . | ~['\\] )* '\''
    ;

CONSTID : [A-Z] [A-Z0-9_]+
    ;

SCHEMAID : [A-Z] [a-zA-Z0-9_]*
    ;

FIELDID : [a-z] [a-zA-Z0-9_]*
    ;

WS
    : [ \t\r\n]+ -> skip
    ;
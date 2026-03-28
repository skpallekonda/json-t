grammar JsonTSchema;

// Top level
json_t : namespace_decl? data_rows? EOF ;

namespace_decl : 
    '{' KW_NAMESPACE ':' '{'
        KW_BASE_URL ':' ns_base_url ','
        KW_VERSION ':' ns_version ','
        KW_CATALOGS ':' '[' catalog (',' catalog)* ']' ','
        KW_DATA_SCHEMA ':' schema_id
    '}' '}' ;
    
catalog :
    '{' schemas_section (',' enums_section)? '}' ;

schemas_section :
    KW_SCHEMAS ':' '[' schema_entry (',' schema_entry)* ']' ;

schema_entry :
    ns_schema_name ':' schema_definition ;

schema_definition :
    straight_schema | derived_schema ;

straight_schema :
    '{' field_block (',' validation_block)? '}' ;

derived_schema :
    KW_FROM ns_schema_name '{' operations_block (',' validation_block)? '}' ;

field_block :
    KW_FIELDS ':' '{' field_decl (',' field_decl)* '}' ;

field_decl :
    scalar_field_decl | object_field_decl ;

scalar_field_decl :
    scalar_type_ref ':' ns_field_name optional_mark?
    ('[' scalar_field_attributes ']')? ;

scalar_field_attributes :
      scalar_constraints_section (',' default_value)? (',' constant_value)?
    | default_value (',' constant_value)?
    | constant_value ;

object_field_decl :
    object_type_ref ':' ns_field_name optional_mark?
    ('[' common_constraints_section ']')? ;

optional_mark : '?' ;

object_type_ref :
    '<' object_type_name '>' array_suffix? ;

scalar_type_ref :
    scalar_types array_suffix? ;

object_type_name :
    schema_id ;

array_suffix :
    '[' ']' ;

scalar_types :
      KW_I16 | KW_I32 | KW_I64
    | KW_U16 | KW_U32 | KW_U64
    | KW_D128 | KW_D32 | KW_D64
    | KW_BOOL | KW_HOSTNAME | KW_NSTR | KW_STR 
    | KW_UUID | KW_URI | KW_EMAIL | KW_IPV4 | KW_IPV6
    | KW_DATETIME | KW_TIMESTAMP | KW_DURATION 
    | KW_DATE | KW_TIME | KW_INST | KW_TSZ
    | KW_BASE64 | KW_OID | KW_HEX ;

common_constraints_section :
    common_constraint (',' common_constraint)* ;

scalar_constraints_section :
common_constraints_section |
    scalar_constraint (',' scalar_constraint)* ;

common_constraint :
    required_constraint | array_items_constraint ;

scalar_constraint :
    value_constraint | length_constraint | regex_constraint | common_constraint ;

required_constraint :
    KW_REQUIRED '=' boolean_val ;

value_constraint :
    '(' value_constraint_option (',' value_constraint_option)* ')' ;

value_constraint_option :
    value_constraint_kw '=' number ;

length_constraint :
    '(' length_constraint_option (',' length_constraint_option)* ')' ;

length_constraint_option :
    length_constraint_kw '=' number ;

array_items_constraint :
    '(' array_items_constraint_option (',' array_items_constraint_option)* ')' ;

array_items_constraint_option :
      array_constraint_nbr '=' number
    | array_constraint_bool '=' boolean_val ;

regex_constraint :
    regex_kw '=' string_val ;

operations_block :
    KW_OPERATIONS ':' '(' schema_operation (',' schema_operation)* ')' ;

schema_operation :
      rename_operation
    | exclude_operation
    | project_operation
    | transform_operation
    | filter_operation ;

rename_operation :
    KW_RENAME '(' rename_pair (',' rename_pair)* ')' ;

rename_pair :
    field_path KW_AS ns_field_name ;

exclude_operation :
    KW_EXCLUDE '(' field_path_list ')' ;

project_operation :
    KW_PROJECT '(' field_path_list ')' ;

filter_operation :
    KW_FILTER expression ;

transform_operation :
    KW_TRANSFORM field_path '=' expression ;

field_path :
    ns_field_name ('.' ns_field_name)* ;

field_path_list :
    field_path (',' field_path)* ;

// Expression rules (incorporating ANTLR precedence handling)
expression :
      expression (OP_STAR | OP_DIV) expression   # MulDivExpr
    | expression (OP_PLUS | OP_MINUS) expression # AddSubExpr
    | expression (OP_LE | OP_GE | OP_LT | OP_GT) expression # RelationalExpr
    | expression (OP_EEQ | OP_NEQ) expression    # EqualityExpr
    | expression OP_AND expression               # AndExpr
    | expression OP_OR expression                # OrExpr
    | OP_NOT expression                          # NotExpr
    | OP_MINUS expression                        # NegExpr
    | primary_expression                         # PrimaryExpr
    ;

primary_expression :
      literal
    | function_call
    | field_path
    | '(' expression ')' ;

function_call :
    field_id '(' argument_list? ')' ;

argument_list :
    expression (',' expression)* ;

validation_block :
    KW_VALIDATIONS ':' '{'
        rules_block?
        unique_block?
        dataset_block?
    '}' ;

rules_block :
    KW_RULES '{' rule_item (',' rule_item)* '}' ;

unique_block :
    KW_UNIQUE '{' unique_entry (',' unique_entry)* '}' ;

dataset_block :
    KW_DATASET '{' expression (',' expression)* '}' ;

unique_entry :
    '(' field_path_list ')' ;

rule_item :
      conditional_requirement
    | expression ;

conditional_requirement :
    expression ARROW KW_REQUIRED '(' field_path_list ')' ;

enums_section :
    KW_ENUMS ':' '[' enum_def (',' enum_def)* ']' ;

enum_def :
    ns_enum_name ':' enum_body ;

enum_body :
    '[' enum_value_constant (',' enum_value_constant)* ']' ;

enum_value_constant :
    const_id ;

data_rows :
    data_row (',' data_row)* ','? ;

data_row :
    object_value ;

literal :
    scalar_value | null_value ;

value :
      literal
    | unspecified_value
    | enum_value
    | object_value
    | array_value ;

null_value :
    KW_NULL ;

unspecified_value :
    '_' ;

scalar_value :
    string_val | number | boolean_val ;

enum_value :
    const_id ;

object_value :
    '{' value (',' value)* '}' ;

array_value :
    '[' value (',' value)* ']' ;

ns_base_url : string_val ;
ns_version : string_val ;
ns_schema_name : schema_id ;
ns_field_name : field_id ;
ns_enum_name : schema_id ;

default_value :
    KW_DEFAULT (scalar_value | null_value) ;

constant_value :
    KW_CONST (scalar_value | null_value) ;

boolean_val :
    TRUE_VAL | FALSE_VAL ;

number :
    NUMBER_LITERAL ;

string_val :
    STRING_LITERAL ;

value_constraint_kw :
    KW_MINVALUE | KW_MAXVALUE | KW_MINPRECISION | KW_MAXPRECISION ;

length_constraint_kw :
    KW_MINLENGTH | KW_MAXLENGTH ;

array_constraint_nbr :
    KW_MINITEMS | KW_MAXITEMS | KW_MAXNULLITEMS ;

array_constraint_bool :
    KW_ALLOWNULLITEMS ;

regex_kw :
    KW_REGEX | KW_PATTERN ;

// Helper to allow keywords to act as identifiers
const_id :
      CONSTID
    | KW_FROM ;

schema_id :
      SCHEMAID
    | KW_FROM ;

field_id :
      FIELDID
    | KW_NAMESPACE | KW_BASE_URL | KW_VERSION | KW_CATALOGS | KW_SCHEMAS
    | KW_DATA_SCHEMA | KW_ENUMS | KW_FIELDS | KW_FROM | KW_VALIDATIONS
    | KW_OPERATIONS | KW_DEFAULT | KW_CONST | KW_RULES | KW_UNIQUE
    | KW_DATASET | KW_REQUIRED | KW_RENAME | KW_EXCLUDE | KW_PROJECT
    | KW_FILTER | KW_TRANSFORM | KW_AS
    | KW_I16 | KW_I32 | KW_I64 | KW_U16 | KW_U32 | KW_U64
    | KW_D32 | KW_D64 | KW_D128 | KW_DATE | KW_TIME | KW_DATETIME
    | KW_TIMESTAMP | KW_TSZ | KW_DURATION | KW_INST | KW_BASE64
    | KW_OID | KW_HEX | KW_STR | KW_NSTR | KW_URI | KW_UUID
    | KW_EMAIL | KW_HOSTNAME | KW_IPV4 | KW_IPV6 | KW_BOOL
    | KW_MINVALUE | KW_MAXVALUE | KW_MINPRECISION | KW_MAXPRECISION
    | KW_MINLENGTH | KW_MAXLENGTH | KW_MINITEMS | KW_MAXITEMS
    | KW_MAXNULLITEMS | KW_ALLOWNULLITEMS | KW_REGEX | KW_PATTERN
    | TRUE_VAL | FALSE_VAL | KW_NULL ;


// -------------- LEXER --------------

KW_NAMESPACE : 'namespace' ;
KW_BASE_URL : 'baseUrl' ;
KW_VERSION : 'version' ;
KW_CATALOGS : 'catalogs' ;
KW_SCHEMAS : 'schemas' ;
KW_DATA_SCHEMA : 'data-schema' ;
KW_ENUMS : 'enums' ;
KW_FIELDS : 'fields' ;
KW_FROM : 'FROM' ;
KW_VALIDATIONS : 'validations' ;
KW_OPERATIONS : 'operations' ;
KW_DEFAULT : 'default' ;
KW_CONST : 'const' ;
KW_RULES : 'rules' ;
KW_UNIQUE : 'unique' ;
KW_DATASET : 'dataset' ;
KW_REQUIRED : 'required' ;
KW_RENAME : 'rename' ;
KW_EXCLUDE : 'exclude' ;
KW_PROJECT : 'project' ;
KW_FILTER : 'filter' ;
KW_TRANSFORM : 'transform' ;
KW_AS : 'as' ;

KW_I16 : 'i16' ;
KW_I32 : 'i32' ;
KW_I64 : 'i64' ;
KW_U16 : 'u16' ;
KW_U32 : 'u32' ;
KW_U64 : 'u64' ;
KW_D32 : 'd32' ;
KW_D64 : 'd64' ;
KW_D128 : 'd128' ;
KW_DATE : 'date' ;
KW_TIME : 'time' ;
KW_DATETIME : 'datetime' ;
KW_TIMESTAMP : 'timestamp' ;
KW_TSZ : 'tsz' ;
KW_DURATION : 'duration' ;
KW_INST : 'inst' ;
KW_BASE64 : 'base64' ;
KW_OID : 'oid' ;
KW_HEX : 'hex' ;
KW_STR : 'str' ;
KW_NSTR : 'nstr' ;
KW_URI : 'uri' ;
KW_UUID : 'uuid' ;
KW_EMAIL : 'email' ;
KW_HOSTNAME : 'hostname' ;
KW_IPV4 : 'ipv4' ;
KW_IPV6 : 'ipv6' ;
KW_BOOL : 'bool' ;

KW_MINVALUE : 'minValue' ;
KW_MAXVALUE : 'maxValue' ;
KW_MINPRECISION : 'minPrecision' ;
KW_MAXPRECISION : 'maxPrecision' ;
KW_MINLENGTH : 'minLength' ;
KW_MAXLENGTH : 'maxLength' ;
KW_MINITEMS : 'minItems' ;
KW_MAXITEMS : 'maxItems' ;
KW_MAXNULLITEMS : 'maxNullItems' ;
KW_ALLOWNULLITEMS : 'allowNullItems' ;
KW_REGEX : 'regex' ;
KW_PATTERN : 'pattern' ;

TRUE_VAL  : 'true' ;
FALSE_VAL : 'false' ;
KW_NULL   : 'null' | 'nil' ;

ARROW     : '->' ;
OP_OR     : '||' ;
OP_AND    : '&&' ;
OP_EEQ    : '==' ;
OP_NEQ    : '!=' ;
OP_LE     : '<=' ;
OP_GE     : '>=' ;
OP_LT     : '<' ;
OP_GT     : '>' ;
OP_PLUS   : '+' ;
OP_MINUS  : '-' ;
OP_STAR   : '*' ;
OP_DIV    : '/' ;
OP_NOT    : '!' ;

// Identifiers matching Pest
CONSTID  : [A-Z] [A-Z0-9_]+ ;
SCHEMAID : [A-Z] [a-zA-Z0-9_]* ;
FIELDID  : [a-z] [a-zA-Z0-9_]* ;

NUMBER_LITERAL : [0-9]+ ('.' [0-9]+)? ;
STRING_LITERAL : '"' ('\\' . | ~["\\])* '"' | '\'' ('\\' . | ~['\\])* '\'' ;

WS : [ \t\r\n]+ -> skip ;
COMMENT_LINE : '//' ~[\r\n]* -> skip ;
COMMENT_BLOCK : '/*' .*? '*/' -> skip ;

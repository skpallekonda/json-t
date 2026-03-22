// =============================================================================
// tests/parse_tests.rs
// =============================================================================
// Phase 1 parsing tests:
//   - Namespace parsing (baseUrl, version, catalogs, data-schema)
//   - Straight Schema parsing (fields, types, objects, enums)
// =============================================================================

use jsont::{
    Parseable,
    JsonTNamespace,
    ScalarType,
    JsonTFieldKind,
    SchemaKind,
    StringifyOptions,
    Stringifiable,
    SchemaRegistry,
    JsonTRowBuilder,
    JsonTValue,
};

#[test]
fn test_parse_minimal_namespace_with_one_schema() {
    let input = r#"
    {
        namespace: {
            baseUrl: "https://example.com",
            version: "1.0",
            catalogs: [
                {
                    schemas: [
                        Person: {
                            fields: {
                                i64: id,
                                str: name
                            }
                        }
                    ]
                }
            ],
            data-schema: Person
        }
    }
    "#;

    let ns = JsonTNamespace::parse(input).expect("Should parse namespace");
    assert_eq!(ns.base_url, "https://example.com");
    assert_eq!(ns.version, "1.0");
    assert_eq!(ns.data_schema, "Person");
    assert_eq!(ns.catalogs.len(), 1);

    let catalog = &ns.catalogs[0];
    assert_eq!(catalog.schemas.len(), 1);
    
    let schema = &catalog.schemas[0];
    assert_eq!(schema.name, "Person");
    
    if let SchemaKind::Straight { fields } = &schema.kind {
        assert_eq!(fields.len(), 2);
        
        assert_eq!(fields[0].name, "id");
        if let JsonTFieldKind::Scalar { field_type, .. } = &fields[0].kind {
            assert_eq!(field_type.scalar, ScalarType::I64);
            assert!(!field_type.is_array);
        } else { panic!("id should be scalar"); }

        assert_eq!(fields[1].name, "name");
        if let JsonTFieldKind::Scalar { field_type, .. } = &fields[1].kind {
            assert_eq!(field_type.scalar, ScalarType::Str);
        } else { panic!("name should be scalar"); }
    } else {
        panic!("Person should be a straight schema");
    }
}

#[test]
fn test_parse_schema_with_object_refs_and_arrays() {
    let input = r#"
    {
        namespace: {
            baseUrl: "https://api.myapp.com",
            version: "2.1",
            catalogs: [
                {
                    schemas: [
                        Address: {
                            fields: { str: city, str: country }
                        },
                        Company: {
                            fields: {
                                str: name,
                                <Address>: headquarters,
                                <Address>[]: branch_offices
                            }
                        }
                    ]
                }
            ],
            data-schema: Company
        }
    }
    "#;

    let ns = JsonTNamespace::parse(input).expect("Should parse");
    let catalog = &ns.catalogs[0];
    let company = catalog.schemas.iter().find(|s| s.name == "Company").unwrap();

    if let SchemaKind::Straight { fields } = &company.kind {
        assert_eq!(fields.len(), 3);
        
        // Single object ref
        let head = &fields[1];
        assert_eq!(head.name, "headquarters");
        if let JsonTFieldKind::Object { schema_ref, is_array, .. } = &head.kind {
            assert_eq!(schema_ref, "Address");
            assert!(!is_array);
        } else { panic!("headquarters should be object"); }

        // Array of object refs
        let branches = &fields[2];
        assert_eq!(branches.name, "branch_offices");
        if let JsonTFieldKind::Object { schema_ref, is_array, .. } = &branches.kind {
            assert_eq!(schema_ref, "Address");
            assert!(is_array);
        } else { panic!("branch_offices should be object array"); }
    }
}

#[test]
fn test_parse_schema_with_enums() {
    let input = r#"
    {
        namespace: {
            baseUrl: "https://api.myapp.com",
            version: "1",
            catalogs: [
                {
                    schemas: [
                        User: {
                            fields: {
                                str: email,
                                str: role  // in JsonT, enums are used as values, but role is just a string here
                            }
                        }
                    ],
                    enums: [
                        RoleKind: [ ADMIN, EDITOR, VIEWER ]
                    ]
                }
            ],
            data-schema: User
        }
    }
    "#;

    let ns = JsonTNamespace::parse(input).unwrap();
    let catalog = &ns.catalogs[0];
    assert_eq!(catalog.enums.len(), 1);
    
    let role_enum = &catalog.enums[0];
    assert_eq!(role_enum.name, "RoleKind");
    assert_eq!(role_enum.values, vec!["ADMIN", "EDITOR", "VIEWER"]);
}

#[test]
fn test_parse_optional_fields() {
    let input = r#"
    {
        namespace: {
            baseUrl: "u", version: "v",
            catalogs: [{
                schemas: [
                    S: { fields: { i32: age?, str: bio? } }
                ]
            }],
            data-schema: S
        }
    }
    "#;

    let ns = JsonTNamespace::parse(input).unwrap();
    let schema = &ns.catalogs[0].schemas[0];
    if let SchemaKind::Straight { fields } = &schema.kind {
        assert!(matches!(fields[0].kind, JsonTFieldKind::Scalar { optional: true, .. }));
        assert!(matches!(fields[1].kind, JsonTFieldKind::Scalar { optional: true, .. }));
    }
}

#[test]
fn test_parse_schema_constraints() {
    let input = r#"
    {
        namespace: {
            baseUrl: "u", version: "v",
            catalogs: [{
                schemas: [
                    Product: {
                        fields: {
                            i32: price [ (minValue = 0, maxValue = 1000) ],
                            str: sku   [ (minLength = 3, maxLength = 10) ]
                        }
                    }
                ]
            }],
            data-schema: Product
        }
    }
    "#;

    let ns = JsonTNamespace::parse(input).unwrap();
    let schema = &ns.catalogs[0].schemas[0];
    if let SchemaKind::Straight { fields } = &schema.kind {
        // Price constraints
        if let JsonTFieldKind::Scalar { constraints, .. } = &fields[0].kind {
            assert_eq!(constraints.len(), 2);
        } else { panic!("price should be scalar"); }

        // SKU constraints
        if let JsonTFieldKind::Scalar { constraints, .. } = &fields[1].kind {
            assert_eq!(constraints.len(), 2);
        } else { panic!("sku should be scalar"); }
    }
}

#[test]
fn test_parse_regex_and_required_constraints() {
    let input = r#"
    {
        namespace: {
            baseUrl: "u", version: "v",
            catalogs: [{
                schemas: [
                    S: {
                        fields: {
                            str: email [ regex = "^.+@.+$", required = true ]
                        }
                    }
                ]
            }],
            data-schema: S
        }
    }
    "#;

    let ns = JsonTNamespace::parse(input).unwrap();
    let schema = &ns.catalogs[0].schemas[0];
    if let SchemaKind::Straight { fields } = &schema.kind {
        if let JsonTFieldKind::Scalar { constraints, .. } = &fields[0].kind {
            assert_eq!(constraints.len(), 2);
        }
    }
}

#[test]
fn test_e2e_demo_parse_and_generate_data() {
    let sample_jsont = r#"
    {
        namespace: {
            baseUrl: "https://api.acme.corp/v1",
            version: "1.0.0",
            catalogs: [
                {
                    schemas: [
                        Address: {
                            fields: {
                                str: street,
                                str: city,
                                str: country [ default 'USA' ]
                            }
                        },
                        Person: {
                            fields: {
                                i64: id,
                                str: name,
                                str: email [ regex = "^.+@.+$" ],
                                <Address>: home?
                            }
                        }
                    ]
                }
            ],
            data-schema: Person
        }
    }
    "#;

    // 1. Parse the namespace
    let ns = JsonTNamespace::parse(sample_jsont).expect("Failed to parse sample.jsont");

    // 2. Stringify (pretty) to console
    println!("\n=== [SAMPLE.JSONT] (Parsed & Stringified) ===");
    println!("{}", ns.stringify(StringifyOptions::pretty()));

    // 3. Build 10 records using the data-schema ("Person")
    let registry = SchemaRegistry::from_namespace(&ns);
    let person_schema = registry.get(&ns.data_schema).expect("Person schema not found");
    let address_schema = registry.get("Address").expect("Address schema not found");

    let mut records = Vec::new();
    for i in 1..=10 {
        // Create a nested Address object for the first 3 records
        let home_value = if i <= 3 {
            let addr = JsonTRowBuilder::with_schema(address_schema)
                .push_checked(JsonTValue::str(format!("{} Main St", i))).unwrap()
                .push_checked(JsonTValue::str("Metropolis")).unwrap()
                .push_checked(JsonTValue::str("USA")).unwrap()
                .build_checked().unwrap();
            JsonTValue::Object(addr)
        } else {
            JsonTValue::Null
        };

        let row = JsonTRowBuilder::with_schema(person_schema)
            .push_checked(JsonTValue::i64(i)).unwrap()
            .push_checked(JsonTValue::str(format!("User {}", i))).unwrap()
            .push_checked(JsonTValue::str(format!("user{}@example.com", i))).unwrap()
            .push_checked(home_value).unwrap()
            .build_checked()
            .expect("Failed to build row");
        records.push(row);
    }

    // 4. Emit data.jsont as console output
    println!("\n=== [DATA.JSONT] (10 Generated Records) ===");
    println!("[");
    for (i, row) in records.iter().enumerate() {
        let comma = if i < records.len() - 1 { "," } else { "" };
        println!("  {}{}", row.stringify(StringifyOptions::compact()), comma);
    }
    println!("]");
}

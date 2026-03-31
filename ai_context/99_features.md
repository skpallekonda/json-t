# Features

> **Load only when discussing roadmap or future capabilities.** Do not load for code or architecture questions.

## Basic Features

1. JsonT Grammar & Schema Design - How JSON structure is defined, what is a schema, how data can be represented using schema.
2. Fluid Builder to construct Schema / Data - How to construct schema / data using fluid builder.
3. Parsing (Deserialize) raw schema / data into JsonT AST - How to parse raw schema / data into JsonT AST.
4. Stringification (Serialize) JsonT AST into raw schema / data - How to serialize JsonT AST into raw schema / data.
5. Validate data against Schema - How to validate data against schema.
6. Transform data using Schema Operations into a derived Schema - How to transform data using schema operations into a derived schema.
7. Interoperable with JSON - How to make JsonT interoperable with JSON.
8. Support polymorphic types using anyOf - How to support polymorphic types using anyOf.
9. Enable privacy markers on data elements - How to enable privacy markers on data elements.
10.Support for aggregation of data from 2 schemas, using JOIN operation - How to support aggregation of data from 2 schemas, using JOIN operation.

## Service Definition Features

Close to OpenAPI, but with JsonT schemas, this module helps to define services.  I am looking at mixing basic openapi services, with streaming services.  Then, I am thinking of extending to support api-routing and api-aggregation, with quality of service (QoS) options for an api-gateway like functionality.

1. Similar to schemas, services can be defined using a fluid builder
2. Services can be of request/reply or request/response, unidirectional or bidirectional streaming
3. Request / Response can be further divided into GET, POST, PUT, DELETE, PATCH, etc.
4. Services can have metadata associated with it
5. Services can be composed using upstream services, giving API-Gateway like features
6. Services can support request / response aggregation using JOIN operation
7. Services can support quality of service (QoS) options for an api-gateway like functionality.
    1. Caching, rate limiting, circuit breaking, etc.
    2. Support for JWT and OAuth2 for authentication and authorization
    3. Support for http/2 and http/3, with http/3 as default
    4. mTLS between client and server, is a mandatory requirement.

## Studio (UI)

1. Ability to manage (create, edit, and view) schemas
2. Ability to define data ingestion pipelines
3. Ability to view received files, validated files, validation issues and summary
4. Ability to define services
5. Ability to start/stop service(s)
6. Ability to monitor service(s) - their invocations, statuses, etc.
7. Ability to view service logs

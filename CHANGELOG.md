# Release notes

## 1.4.0
Added support for OpenAPI version 2 spec generation. For now only
runtime generation (no maven plugin).

## 1.3.0
https://github.com/jrcodeza/spring-openapi/issues/22

https://github.com/jrcodeza/spring-openapi/issues/21

https://github.com/jrcodeza/spring-openapi/issues/19

## 1.2.1

https://github.com/jrcodeza/spring-openapi/issues/21

https://github.com/jrcodeza/spring-openapi/issues/19

Has same features as 1.3.0 but doesn't track (traverse) inheritance hierarchy
over all java 'extends' using allOf. If inheritance hierarchy does not contain
JsonSubTypes annotation (discriminator) then all parent properties are copied
into the current class and no allOf approach is used. AllOf approach is used
only for inheritance hierarchies where JsonSubTypes annotation is present 
(on some class).

This is done because if generated spec has too big inheritance depth, then
swagger ui might have problems to interpret this (follow $refs - I've got 
stack overflow and other problems). Oas-ui interpreter 
(https://github.com/vahanito/oas-ui) does not have this problem.
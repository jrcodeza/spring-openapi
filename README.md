# spring-openapi (OpenAPI 3 generator)
![Java CI](https://github.com/jrcodeza/spring-openapi/workflows/Java%20CI/badge.svg)
Spring Boot OpenAPI 3 generator. It scans provided packages 
(model and controller) and generates based on reflection, javax validation
and spring annotations the OpenAPI 3 json. It is able to handle also
inheritance using OpenAPI 3 discriminator possibilities. The inheritance
generation is achieved using jackson annotations.

Generator allows you to also intercept schema, schema field, operation
and operation parameter process, so you can include your own behavior
to enrich already mapped sections (e.g. include own annotation behavior
to descriptions etc.).

So far there is core generator functionality 
**spring-openapi-schema-generator** and maven plugin
**spring-openapi-schema-generator-plugin** available.Soon there will be 
also java client generator finalized, because current tooling is still 
not able to generate client with full OpenAPI 3 discriminator features
(jackson annotations).

There is also short article describing usage of spring-openapi generator here: 
https://medium.com/@remenec.jakub/openapi-3-spec-and-client-generation-on-java-spring-application-38a9ba5a2932.

## Current version
**1.4.0**

Now generator supports also OpenAPI version 2 generation.

Release notes: https://github.com/jrcodeza/spring-openapi/blob/master/CHANGELOG.md 

1.2.1 - to ensure compatiblity with swagger ui for bigger inheritance structures.
This version models inheritance using allOf only if discriminator (JsonSubTypes) is found
in inheritance hierarchy. If not, it takes all attributes from parent classes
and adds them to the current one. See tests for more info.

## Example project
You can check this repository https://github.com/jrcodeza/spring-openapi-example for an example
usage. It shows spec to code and also code to spec generation using maven plugins. It contains
simple REST resources, model (including inheritance), interceptors and examples resolver.

## OpenAPI UI interpreter
If you need to display OpenAPI v3 spec in browser you can have a look also on **oas-ui**
plugin https://github.com/vahanito/oas-ui (which was developed together with spring-openapi) . 
It supports searching of components and resources. Correctly displays inheritance also 
with discriminator info. You can use it in react as plugin or also as standalone bundle js. 

## Annotations
### @OpenApiExample
You can define it on class, method, parameter or field. There are 2 main use cases:

- Providing the example value **in place**
```java
@OpenApiExample(name = "StandardExample", value = "standardExampleValue")
```
- providing **key** to example. You have to manage that key is handled by examples resolver
```java
@OpenApiExample(name = "KeyExample", key = "KEY")
```
Example resolver is a simple interface:
```java
public interface OpenApiExampleResolver {
	
    String resolveExample(String exampleKey);

}
```
The **exampleKey** parameter is a key for which example value should be provided by resolver. It can be 
for example a file name with example request - then goal of the resolver is to read file content and provide
it. 

It's possible to define more examples for one element by wrapping @OpenApiExample annotation to **@OpenApiExamples**
one. Name of the example becomes important in this case, because it will be stored to spec as example name.

### @OpenApiIgnore
Element annotated with this annotation will not be generated to spec.

### @Response
Sometimes you need to specify method (operation) response in your own way. It can 
be necessary when you have several return statuses or response types. Also when 
you want to specify response headers. For this purpose you can use this annotation.

It's also possible to wrap multiple @Response annotations to **@Responses** annotations. E.g.:
```java
@Responses({
	@Response(responseCode = 201, description = "Created", responseBody = ValidationDummy.class,
			headers = @Header(name = "SomeHeader", description = "TestHeader")),
	@Response(responseCode = 200, description = "Replaced", responseBody = ValidationDummy.class)
}) 
```
@Header annotation is also custom spring-openapi annotation.

## Generate spec from code
### Runtime usage
Include dependency

```java
<dependency>
    <groupId>com.github.jrcodeza</groupId>
    <artifactId>spring-openapi-schema-generator</artifactId>
    <version>1.4.0</version>
</dependency>
```

Instantiate **OpenAPIGenerator**

```java
OpenAPIGenerator openAPIGenerator = new OpenAPIGenerator(
                singletonList("org.spring.openapi.schema.generator.plugin.model.*"),
                singletonList("org.spring.openapi.schema.generator.plugin.controller.*"),
                createTestInfo()
        );
```
The first parameter is model package and the second is controller package. It
is possible to define multiple packages for each. The third parameter
is OpenAPI basic info like project title, description, version etc. It is
required in order to create valid OpenAPI 3 output.
Additionally you can define interceptors
* **OperationInterceptor** - executed after controller method is mapped
* **OperationParameterInterceptor** - executed after each method parameter is mapped
* **RequestBodyInterceptor** - executed after RequestBody annotated parameter of a method is mapped
* **SchemaFieldInterceptor** - executed after class field is mapped
* **SchemaInterceptor** - executed after each class is mapped

You can add these interceptors either to constructor after info parameter or
by calling add + interceptorName method on OpenAPIGenerator class. E.g.:

```java
openAPIGenerator.addOperationInterceptor(operationInterceptor);
openAPIGenerator.addOperationParameterInterceptor(operationParameterInterceptor);
openAPIGenerator.addRequestBodyInterceptor(requestBodyInterceptor);
openAPIGenerator.addSchemaFieldInterceptor(schemaFieldInterceptor);
openAPIGenerator.addSchemaInterceptor(schemaInterceptor);
```

You can define your own interceptor as follows:
```java
package com.github.jrcodeza.schema.generator.plugin.interceptors;

import java.lang.reflect.Method;

import com.github.jrcodeza.schema.generator.interceptors.OperationInterceptor;

import io.swagger.v3.oas.models.Operation;

public class TestOperationInterceptor implements OperationInterceptor {

	@Override
	public void intercept(Method method, Operation transformedOperation) {
		transformedOperation.setSummary("Interceptor summary");
	}

}
```

It's possible to define also global headers which are applicable for all resources.
```java
openAPIGenerator.addGlobalHeader("Test-Global-Header", "Some desc", false);
```

OpenAPIGenerator method **generate** can also take OpenApiGeneratorConfig as parameter.
In this config you can define if generation of examples is enabled. You can also define
example resolver, which can be useful if for example you have bigger examples of POST body requests
stored in files. You can define it as follows:

```java
OpenApiGeneratorConfigBuilder.defaultConfig()
                        .withGenerateExamples(true)
                        .withOpenApiExampleResolver(createExampleResolver())
                        .build()
```

Finally when you want to **generate OpenAPI 3 spec** you have to execute
generate method on OpenAPIGenerator instance.
```java
OpenAPI openAPI = openAPIGenerator.generate();
```
and you will receive OpenAPI object (from swagger API) which can be serialized
to string like this
```java
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
try {
    String generated = objectMapper.writeValueAsString(openAPI);
    JSONAssert.assertEquals(getResourceFileAsString(), generated, false);
} catch (JsonProcessingException | JSONException e) {
    e.printStackTrace();
}
```

### Maven plugin usage
Maven plugin wraps before mentioned functionality into maven plugin.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.github.jrcodeza</groupId>
            <artifactId>spring-openapi-schema-generator-plugin</artifactId>
            <configuration>
                <title>Test title</title>
                <description>Test description</description>
                <version>1.0.0-TEST</version>
                <modelPackages>
                    <modelPackage>org.spring.openapi.schema.generator.test.model.*</modelPackage>
                </modelPackages>
                <controllerBasePackages>
                    <controllerBasePackage>org.spring.openapi.schema.generator.test.controller.*</controllerBasePackage>
                </controllerBasePackages>
                <outputDirectory>target/openapi</outputDirectory>
                <schemaFieldInterceptors>
                    <schemaFieldInterceptor>com.github.jrcodeza.schema.generator.plugin.interceptor.TestSchemaFieldInterceptor</schemaFieldInterceptor>
                </schemaFieldInterceptors>
                <operationInterceptors>
                    <operationInterceptor>com.github.jrcodeza.schema.generator.plugin.interceptor.TestOperationInterceptor</operationInterceptor>
                </operationInterceptors>
                <generateExamples>true</generateExamples>
                <openApiExamplesResolver>com.github.jrcodeza.schema.generator.plugin.example.TestExampleResolver</openApiExamplesResolver>
            </configuration>
        </plugin>
    </plugins>
</build>
```

You can see that title, description and version are the OpenAPI info parameters.
Then you have to define also modelPackages and controllerPackages. Additionally
you should define also outputDirectory, in our case target/openapi will be the target folder
where the swagger.json will be saved.

From 1.2.0 version it is also possible to define all interceptors also in plugin. Additionally there
is also option to turn on/off generateExamples and of course specify openApiExamplesResolver.

## Generate client from spec

### Runtime usage
Include dependency

```java
<dependency>
    <groupId>com.github.jrcodeza</groupId>
    <artifactId>spring-openapi-client-generator</artifactId>
    <version>1.4.0</version>
</dependency>
```

Use generator in code

```java
new OpenApiClientGenerator().generateClient(
				"target.package",
				"pathToOpenApi3Spec",
				"targetFolder", // e.g. /target/generatedSources/oas
				true); // should generate interface
```

### Maven plugin usage

1. Include dependency
```java
<dependency>
    <groupId>com.github.jrcodeza</groupId>
    <artifactId>spring-openapi-client-generator-plugin</artifactId>
    <version>1.4.0</version>
</dependency>
```

2. Add plugin to maven plugins section
```xml
<plugin>
    <groupId>com.github.jrcodeza</groupId>
    <artifactId>spring-openapi-client-generator-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>generateClientFromOpenApi</goal>
            </goals>
            <configuration>
                <outputPackage>test.package</outputPackage>
                <outputPath>target/generated-sources</outputPath>
                <schemaPath>src/main/resources/oas3.json</schemaPath>
                <generateResourceInterface>true</generateResourceInterface>
                <generateDiscriminatorProperty>true</generateDiscriminatorProperty>
            </configuration>
        </execution>
    </executions>
</plugin>
```
**generateResourceInterface** option allows you to turn on/off generation of interface
with methods that represent operations of the API.

**generateDiscriminatorProperty** allows you to turn on/off functionality where
plugin generates the discriminator property explicitly to class (setting it visible=true in jackson).
This is handy when you need to see the discriminator property inside the class
and being able to access it using setter or getter. 

## Contributions
Pull requests are welcome. If you would like to collaborate more feel free to contact
me on remenec.jakub@gmail.com .

## License
License for this tool is GNU GPL V3.

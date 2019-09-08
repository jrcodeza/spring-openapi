# spring-openapi
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

## Core usage
1. Include dependency
```java
<dependency>
    <groupId>org.spring.openapi</groupId>
    <artifactId>spring-openapi-schema-generator</artifactId>
    <version>1.0.0</version>
</dependency>
```
2. Instantiate **OpenAPIGenerator**
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
package org.spring.openapi.schema.generator.plugin.interceptors;

import java.lang.reflect.Method;

import org.spring.openapi.schema.generator.interceptors.OperationInterceptor;

import io.swagger.v3.oas.models.Operation;

public class TestOperationInterceptor implements OperationInterceptor {

	@Override
	public void intercept(Method method, Operation transformedOperation) {
		transformedOperation.setSummary("Interceptor summary");
	}

}
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

## Maven plugin usage
Maven plugin wraps before mentioned functionality into maven plugin.

```java
<build>
		<plugins>
			<plugin>
				<groupId>org.spring.openapi</groupId>
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
				</configuration>
			</plugin>
		</plugins>
	</build>
```

You can see that title, description and version are the OpenAPI info parameters.
Then you have to define also modelPackages and controllerPackages. Additionally
you should define also outputDirectory, in our case target/openapi will be the target folder
where the swagger.json will be saved.

## Contributions
Pull requests are welcome. If you would like to collaborate more feel free to contact
me on remenec.jakub@gmail.com .

## License
License for this tool is GNU GPL V3.

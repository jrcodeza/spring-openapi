package com.github.jrcodeza.schema.v2.generator;

import io.swagger.models.Info;
import io.swagger.models.Swagger;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jrcodeza.schema.v2.generator.config.builder.OpenApiGeneratorConfigBuilder;
import com.github.jrcodeza.schema.v2.generator.interceptors.TestOperationInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.TestOperationParameterInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.TestRequestBodyInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.TestSchemaFieldInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.TestSchemaInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.examples.OpenApiExampleResolver;

import static java.util.Collections.singletonList;

public class OpenAPIGeneratorTest {

	private static final TestOperationInterceptor operationInterceptor = new TestOperationInterceptor();
	private static final TestOperationParameterInterceptor operationParameterInterceptor = new TestOperationParameterInterceptor();
	private static final TestRequestBodyInterceptor requestBodyInterceptor = new TestRequestBodyInterceptor();
	private static final TestSchemaFieldInterceptor schemaFieldInterceptor = new TestSchemaFieldInterceptor();
	private static final TestSchemaInterceptor schemaInterceptor = new TestSchemaInterceptor();

	@Test
	public void generateStandardScenario() {
		Swagger openAPI = createTestGenerator().generate();
		assertOpenApiResult(openAPI, "expected_standard_openapi.json");
	}

	@Test
	public void generateExampleScenario() {
		Swagger openAPI = createTestGenerator().generate(
				OpenApiGeneratorConfigBuilder.defaultConfig()
											 .withGenerateExamples(true)
											 .withOpenApiExampleResolver(createExampleResolver())
											 .build()
		);
		assertOpenApiResult(openAPI, "expected_example_openapi.json");
	}

	private void assertOpenApiResult(Swagger openAPI, String pathToExpectedFile) {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		try {
			String generated = objectMapper.writeValueAsString(openAPI);
			JSONAssert.assertEquals(getResourceFileAsString(pathToExpectedFile), generated, true);
		} catch (JsonProcessingException | JSONException e) {
			e.printStackTrace();
		}
	}

	private OpenApiExampleResolver createExampleResolver() {
		return exampleKey -> "TestExampleResolvedWithKey=" + exampleKey;
	}

	private OpenAPIGenerator createTestGenerator() {
		OpenAPIGenerator openAPIGenerator = new OpenAPIGenerator(
				singletonList("com.github.jrcodeza.schema.v2.generator.model.*"),
				singletonList("com.github.jrcodeza.schema.v2.generator.controller.*"),
				createTestInfo()
		);
		openAPIGenerator.addOperationInterceptor(operationInterceptor);
		openAPIGenerator.addOperationParameterInterceptor(operationParameterInterceptor);
		openAPIGenerator.addRequestBodyInterceptor(requestBodyInterceptor);
		openAPIGenerator.addSchemaFieldInterceptor(schemaFieldInterceptor);
		openAPIGenerator.addSchemaInterceptor(schemaInterceptor);
		openAPIGenerator.addGlobalHeader("Test-Global-Header", "Some desc", false);
		return openAPIGenerator;
	}

	private Info createTestInfo() {
		Info info = new Info();
		info.setTitle("Test API");
		info.setDescription("Test description");
		info.setVersion("1.0.0");
		return info;
	}

	private String getResourceFileAsString(String pathToExpectedFile) {
		ClassLoader classLoader = getClass().getClassLoader();
		try (InputStream inputStream = classLoader.getResourceAsStream(pathToExpectedFile)) {
			return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

}

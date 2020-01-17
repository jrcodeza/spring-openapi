package com.github.jrcodeza.schema.v2.generator;

import io.swagger.models.Info;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.jrcodeza.schema.v2.generator.interceptors.TestOperationInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.TestOperationParameterInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.TestRequestBodyInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.TestSchemaFieldInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.TestSchemaInterceptor;

import static java.util.Collections.singletonList;

public class OpenAPIV2GeneratorTest {

	private static final TestOperationInterceptor operationInterceptor = new TestOperationInterceptor();
	private static final TestOperationParameterInterceptor operationParameterInterceptor = new TestOperationParameterInterceptor();
	private static final TestRequestBodyInterceptor requestBodyInterceptor = new TestRequestBodyInterceptor();
	private static final TestSchemaFieldInterceptor schemaFieldInterceptor = new TestSchemaFieldInterceptor();
	private static final TestSchemaInterceptor schemaInterceptor = new TestSchemaInterceptor();

	@Test
	public void generateStandardScenario() throws JsonProcessingException {
		String openAPIJson = createTestGenerator().generateJson();
		assertOpenApiResult(openAPIJson, "expected_v2_openapi.json");
	}

	private void assertOpenApiResult(String openAPI, String pathToExpectedFile) {
		try {
			JSONAssert.assertEquals(getResourceFileAsString(pathToExpectedFile), openAPI, true);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private OpenAPIV2Generator createTestGenerator() {
		OpenAPIV2Generator openAPIGenerator = new OpenAPIV2Generator(
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

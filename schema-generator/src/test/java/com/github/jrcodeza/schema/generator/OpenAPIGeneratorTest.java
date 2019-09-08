package com.github.jrcodeza.schema.generator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import com.github.jrcodeza.schema.generator.interceptors.TestOperationInterceptor;
import com.github.jrcodeza.schema.generator.interceptors.TestOperationParameterInterceptor;
import com.github.jrcodeza.schema.generator.interceptors.TestRequestBodyInterceptor;
import com.github.jrcodeza.schema.generator.interceptors.TestSchemaFieldInterceptor;
import com.github.jrcodeza.schema.generator.interceptors.TestSchemaInterceptor;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import static java.util.Collections.singletonList;

public class OpenAPIGeneratorTest {

    private static final TestOperationInterceptor operationInterceptor = new TestOperationInterceptor();
    private static final TestOperationParameterInterceptor operationParameterInterceptor = new TestOperationParameterInterceptor();
    private static final TestRequestBodyInterceptor requestBodyInterceptor = new TestRequestBodyInterceptor();
    private static final TestSchemaFieldInterceptor schemaFieldInterceptor = new TestSchemaFieldInterceptor();
    private static final TestSchemaInterceptor schemaInterceptor = new TestSchemaInterceptor();

    @Test
    public void generateStandardScenario() {
        OpenAPIGenerator openAPIGenerator = new OpenAPIGenerator(
                singletonList("com.github.jrcodeza.schema.generator.model.*"),
                singletonList("com.github.jrcodeza.schema.generator.controller.*"),
                createTestInfo()
        );
        openAPIGenerator.addOperationInterceptor(operationInterceptor);
        openAPIGenerator.addOperationParameterInterceptor(operationParameterInterceptor);
        openAPIGenerator.addRequestBodyInterceptor(requestBodyInterceptor);
        openAPIGenerator.addSchemaFieldInterceptor(schemaFieldInterceptor);
        openAPIGenerator.addSchemaInterceptor(schemaInterceptor);

        OpenAPI openAPI = openAPIGenerator.generate();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            String generated = objectMapper.writeValueAsString(openAPI);
            JSONAssert.assertEquals(getResourceFileAsString(), generated, false);
        } catch (JsonProcessingException | JSONException e) {
            e.printStackTrace();
        }
    }

    private Info createTestInfo() {
        Info info = new Info();
        info.setTitle("Test API");
        info.setDescription("Test description");
        info.setVersion("1.0.0");
        return info;
    }

    private String getResourceFileAsString() {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream("expected_standard_openapi.json")) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}

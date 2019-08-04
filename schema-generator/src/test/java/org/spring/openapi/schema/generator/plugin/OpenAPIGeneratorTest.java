package org.spring.openapi.schema.generator.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.spring.openapi.schema.generator.OpenAPIGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static java.util.Collections.singletonList;

public class OpenAPIGeneratorTest {

    private static final OpenAPIGenerator openAPIGenerator = new OpenAPIGenerator(
            singletonList("org.spring.openapi.schema.generator.plugin.model"),
            singletonList("org.spring.openapi.schema.generator.plugin.controller"),
            "target/openapi"
    );

    @Test
    public void generateStandardScenario() {
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

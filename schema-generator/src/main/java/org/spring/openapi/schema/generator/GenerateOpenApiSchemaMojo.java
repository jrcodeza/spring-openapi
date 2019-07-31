package org.spring.openapi.schema.generator;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Mojo(name = "generateOpenApi")
public class GenerateOpenApiSchemaMojo extends AbstractMojo {

    @Parameter
    private String[] modelPackages;

    @Parameter
    private String[] controllerBasePackages;

    @Parameter
    private String outputDirectory;

    private SimpleSchemaTransformer simpleSchemaTransformer;

    public void execute() {
        simpleSchemaTransformer = new SimpleSchemaTransformer(getLog());

        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(createComponentsWrapper());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        try {
            if (!new File(outputDirectory).mkdirs()) {
                getLog().error(String.format("Error creating directories for path [%s]", outputDirectory));
                return;
            }
            objectMapper.writeValue(new File(outputDirectory + "/swagger.json"), openAPI);
        } catch (IOException e) {
            getLog().error("Cannot serialize generated OpenAPI spec", e);
        }
    }

    private Components createComponentsWrapper() {
        Components componentsWrapper = new Components();
        componentsWrapper.setSchemas(createSchemas());
        return componentsWrapper;
    }

    private Map<String, Schema> createSchemas() {
        Map<String, Schema> schemaMap = new HashMap<>();
        for (String modelPackage : modelPackages) {
            try (ScanResult scanResult = getClassGraph(modelPackage).scan()) {
                for (ClassInfo classInfo : scanResult.getAllClasses()) {
                    if (schemaMap.containsKey(classInfo.getSimpleName())) {
                        continue;
                    }
                    schemaMap.put(classInfo.getSimpleName(), simpleSchemaTransformer.transformSimpleSchema(classInfo));
                }
                // TODO required lookup
            }
        }
        return schemaMap;
    }

    private ClassGraph getClassGraph(String modelPackage) {
        return new ClassGraph()
                .verbose()
                .enableAllInfo()
                .whitelistPackages(modelPackage);
    }

}

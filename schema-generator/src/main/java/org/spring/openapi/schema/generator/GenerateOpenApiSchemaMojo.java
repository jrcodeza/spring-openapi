package org.spring.openapi.schema.generator;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
import org.spring.openapi.schema.generator.model.InheritanceInfo;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

@Mojo(name = "generateOpenApi")
public class GenerateOpenApiSchemaMojo extends AbstractMojo {

    private static final String OPEN_API_IGNORE_ANNOTATION = "org.spring.openapi.schema.generator.annotations.OpenApiIgnore";

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
                Map<String, InheritanceInfo> inheritanceMap = new HashMap<>();
                for (ClassInfo classInfo : scanResult.getAllClasses()) {
                    if (inheritanceMap.containsKey(classInfo.getSimpleName()) ||
                            classInfo.hasAnnotation(OPEN_API_IGNORE_ANNOTATION)) {
                        continue;
                    }
                    getInheritanceInfo(classInfo)
                            .ifPresent(info -> inheritanceMap.put(classInfo.getSimpleName(), info));
                }
                for (ClassInfo classInfo : scanResult.getAllClasses()) {
                    if (schemaMap.containsKey(classInfo.getSimpleName()) ||
                            classInfo.hasAnnotation(OPEN_API_IGNORE_ANNOTATION)) {
                        continue;
                    }
                    schemaMap.put(
                            classInfo.getSimpleName(),
                            simpleSchemaTransformer.transformSimpleSchema(classInfo, inheritanceMap)
                    );
                }
                // TODO required lookup
            }
        }
        return schemaMap;
    }

    private Optional<InheritanceInfo> getInheritanceInfo(ClassInfo classInfo) {
        if (classInfo.hasAnnotation("com.fasterxml.jackson.annotation.JsonSubTypes")) {
            Class<?> clazz = classInfo.loadClass();
            List<Annotation> annotations = unmodifiableList(asList(clazz.getAnnotations()));
            JsonTypeInfo jsonTypeInfo = annotations.stream()
                    .filter(annotation -> annotation instanceof JsonTypeInfo)
                    .map(annotation -> (JsonTypeInfo) annotation)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("JsonSubTypes has to be found"));
            InheritanceInfo inheritanceInfo = new InheritanceInfo();
            inheritanceInfo.setDiscriminatorFieldName(jsonTypeInfo.property());
            inheritanceInfo.setDiscriminatorClassMap(scanJacksonInheritance(annotations));
            return Optional.of(inheritanceInfo);
        }
        return Optional.empty();
    }

    private Map<String, String> scanJacksonInheritance(List<Annotation> annotations) {
        return annotations.stream()
                .filter(annotation -> annotation instanceof JsonSubTypes)
                .map(annotation -> (JsonSubTypes) annotation)
                .flatMap(jsonSubTypesMapped -> Arrays.stream(jsonSubTypesMapped.value()))
                .collect(Collectors.toMap(o -> o.value().getSimpleName(), JsonSubTypes.Type::name));
    }

    private ClassGraph getClassGraph(String modelPackage) {
        return new ClassGraph()
                .verbose()
                .enableAllInfo()
                .whitelistPackages(modelPackage);
    }

}

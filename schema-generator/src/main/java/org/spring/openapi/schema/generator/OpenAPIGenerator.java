package org.spring.openapi.schema.generator;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spring.openapi.schema.generator.model.InheritanceInfo;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

public class OpenAPIGenerator {

    public static final String DEFAULT_DISCRIMINATOR_NAME = "type";
    private static Logger logger = LoggerFactory.getLogger(OpenAPIGenerator.class);

    private static final String OPEN_API_IGNORE_ANNOTATION =
            "org.spring.openapi.schema.generator.annotations.OpenApiIgnore";

    private List<String> modelPackages;
    private List<String> controllerBasePackages;

    private ComponentSchemaTransformer componentSchemaTransformer;

    public OpenAPIGenerator(List<String> modelPackages, List<String> controllerBasePackages) {
        this.modelPackages = modelPackages;
        this.controllerBasePackages = controllerBasePackages;
        componentSchemaTransformer = new ComponentSchemaTransformer();
    }

    public OpenAPI generate() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(createComponentsWrapper());
        return openAPI;
    }

    private Components createComponentsWrapper() {
        Components componentsWrapper = new Components();
        componentsWrapper.setSchemas(createSchemas());
        return componentsWrapper;
    }

    private Map<String, Schema> createSchemas() {
        Map<String, Schema> schemaMap = new HashMap<>();
        for (String modelPackage : modelPackages) {
            logger.info("Processing [{}] package", modelPackage);
            try (ScanResult scanResult = getClassGraph(modelPackage).scan()) {
                Map<String, InheritanceInfo> inheritanceMap = new HashMap<>();
                // populating inheritance info
                for (ClassInfo classInfo : scanResult.getAllClasses()) {
                    if (inheritanceMap.containsKey(classInfo.getName()) ||
                            classInfo.hasAnnotation(OPEN_API_IGNORE_ANNOTATION)) {
                        continue;
                    }
                    getInheritanceInfo(classInfo)
                            .ifPresent(info -> inheritanceMap.put(classInfo.getName(), info));
                }
                // mapping components
                for (ClassInfo classInfo : scanResult.getAllClasses()) {
                    if (schemaMap.containsKey(classInfo.getSimpleName()) ||
                            classInfo.hasAnnotation(OPEN_API_IGNORE_ANNOTATION)) {
                        continue;
                    }
                    schemaMap.put(
                            classInfo.getSimpleName(),
                            componentSchemaTransformer.transformSimpleSchema(classInfo, inheritanceMap, modelPackages)
                    );
                }
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
                    .orElse(null);

            InheritanceInfo inheritanceInfo = new InheritanceInfo();
            inheritanceInfo.setDiscriminatorFieldName(getDiscriminatorName(jsonTypeInfo));
            inheritanceInfo.setDiscriminatorClassMap(scanJacksonInheritance(annotations));
            return Optional.of(inheritanceInfo);
        }
        return Optional.empty();
    }

    private String getDiscriminatorName(JsonTypeInfo jsonTypeInfo) {
        if (jsonTypeInfo == null) {
            return DEFAULT_DISCRIMINATOR_NAME;
        }
        return jsonTypeInfo.property();
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

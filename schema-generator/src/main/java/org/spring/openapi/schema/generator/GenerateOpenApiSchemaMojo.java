package org.spring.openapi.schema.generator;

import io.github.classgraph.*;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mojo(name = "generateOpenApi")
public class GenerateOpenApiSchemaMojo extends AbstractMojo {

    @Parameter
    private String[] modelPackages;

    @Parameter
    private String[] controllerBasePackages;

    @Parameter
    private String outputDirectory;

    public void execute() throws MojoExecutionException, MojoFailureException {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(createComponentsWrapper());
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
                    schemaMap.put(classInfo.getSimpleName(), createSchema(classInfo));
                }
            }
        }
        return schemaMap;
    }

    private Schema createSchema(ClassInfo classInfo) {
        Schema<?> schema = new Schema<>();
        schema.setType("object");
        schema.setProperties(getClassProperties(classInfo.getDeclaredFieldInfo()));
        return schema;
    }

    private Map<String, Schema> getClassProperties(FieldInfoList declaredFieldInfo) {
        Map<String, Schema> classPropertyMap = new HashMap<>();
        for (FieldInfo fieldInfo : declaredFieldInfo) {
            getFieldSchema(fieldInfo).ifPresent(schema -> classPropertyMap.put(fieldInfo.getName(), schema));
        }
        return classPropertyMap;
    }

    private Optional<Schema> getFieldSchema(FieldInfo fieldInfo) {
        TypeSignature typeSignature = fieldInfo.getTypeSignatureOrTypeDescriptor();
        if (typeSignature instanceof BaseTypeSignature) {
            return Optional.ofNullable(parseBaseTypeSignature((BaseTypeSignature) typeSignature));
        } else if (typeSignature instanceof ArrayTypeSignature) {

        } else if (typeSignature instanceof ReferenceTypeSignature) {

        }
        return Optional.empty();
    }

    private Schema parseBaseTypeSignature(BaseTypeSignature typeSignature) {
        switch (typeSignature.getTypeStr()) {
            case "byte":
            case "short":
            case "int":
                return createNumberSchema("integer", "int32");
            case "long":
                return createNumberSchema("integer", "int64");
            case "float":
                return createNumberSchema("number", "float");
            case "double":
                return createNumberSchema("number", "double");
            case "char":
                return createCharSchema();
            case "boolean":
                return createBooleanSchema();
        }
        getLog().info(String.format("Ignoring unsupported type=[%s]", typeSignature.getTypeStr()));
        return null;
    }

    private Schema createBooleanSchema() {
        Schema<?> schema = new Schema<>();
        schema.setType("boolean");
        return schema;
    }

    private Schema createCharSchema() {
        Schema<?> schema = new Schema<>();
        schema.setType("string");
        return schema;
    }

    private Schema createNumberSchema(String type, String format) {
        Schema<?> schema = new Schema<>();
        schema.setType("integer");
        schema.setFormat(format);
        // TODO min inclusive etc
        return schema;
    }

    private ClassGraph getClassGraph(String modelPackage) {
        return new ClassGraph()
                .verbose()
                .enableAllInfo()
                .whitelistPackages(modelPackage);
    }

}

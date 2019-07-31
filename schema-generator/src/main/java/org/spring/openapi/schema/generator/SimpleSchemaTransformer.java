package org.spring.openapi.schema.generator;

import io.github.classgraph.*;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.maven.plugin.logging.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SimpleSchemaTransformer extends Transformer {

    public SimpleSchemaTransformer(Log log) {
        super(log);
    }

    public Schema transformSimpleSchema(ClassInfo classInfo) {
        io.swagger.v3.oas.models.media.Schema<?> schema = new io.swagger.v3.oas.models.media.Schema<>();
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

        } else if (typeSignature instanceof ClassRefTypeSignature) {
            return Optional.ofNullable(parseClassRefTypeSignature((ClassRefTypeSignature) typeSignature));
        }
        return Optional.empty();
    }

    private Schema parseClassRefTypeSignature(ClassRefTypeSignature typeSignature) {
        switch (typeSignature.getFullyQualifiedClassName()) {
            case "java.lang.Byte":
            case "java.lang.Short":
            case "java.lang.Integer":
                return createNumberSchema("integer", "int32");
            case "java.lang.Long":
            case "java.math.BigInteger":
                return createNumberSchema("integer", "int64");
            case "java.lang.Float":
                return createNumberSchema("number", "float");
            case "java.lang.Double":
            case "java.math.BigDecimal":
                return createNumberSchema("number", "double");
            case "java.lang.Character":
            case "java.lang.String":
                return createCharSchema();
            case "java.lang.Boolean":
                return createBooleanSchema();
            case "java.util.List":
                // TODO referencies necessary
                return null;
            default:
                // TODO implement referencies
                return null;
        }
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
        io.swagger.v3.oas.models.media.Schema<?> schema = new io.swagger.v3.oas.models.media.Schema<>();
        schema.setType("boolean");
        return schema;
    }

    private Schema createCharSchema() {
        io.swagger.v3.oas.models.media.Schema<?> schema = new io.swagger.v3.oas.models.media.Schema<>();
        schema.setType("string");
        return schema;
    }

    private Schema createNumberSchema(String type, String format) {
        io.swagger.v3.oas.models.media.Schema<?> schema = new io.swagger.v3.oas.models.media.Schema<>();
        schema.setType(type);
        schema.setFormat(format);
        // TODO min inclusive etc
        return schema;
    }

}

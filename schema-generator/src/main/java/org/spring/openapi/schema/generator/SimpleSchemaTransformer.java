package org.spring.openapi.schema.generator;

import io.github.classgraph.*;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.logging.Log;
import org.spring.openapi.schema.generator.model.InheritanceInfo;

import javax.validation.constraints.*;
import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class SimpleSchemaTransformer extends Transformer {

    private static final String COMPONENT_REF_PREFIX = "#/components/schemas/";

    public SimpleSchemaTransformer(Log log) {
        super(log);
    }

    public Schema transformSimpleSchema(ClassInfo classInfo, Map<String, InheritanceInfo> inheritanceMap) {
        Schema<?> schema = new Schema<>();
        schema.setType("object");
        schema.setProperties(getClassProperties(classInfo.getDeclaredFieldInfo(), inheritanceMap));
        if (inheritanceMap.containsKey(classInfo.getSimpleName())) {
            schema.setDiscriminator(createDiscriminator(inheritanceMap.get(classInfo.getSimpleName())));
        }
        return schema;
    }

    private Discriminator createDiscriminator(InheritanceInfo inheritanceInfo) {
        Map<String, String> discriminatorTypeMapping = inheritanceInfo.getDiscriminatorClassMap().entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        Discriminator discriminator = new Discriminator();
        discriminator.setPropertyName(inheritanceInfo.getDiscriminatorFieldName());
        discriminator.setMapping(discriminatorTypeMapping);
        return discriminator;
    }

    private Map<String, Schema> getClassProperties(FieldInfoList declaredFieldInfo,
                                                   Map<String, InheritanceInfo> inheritanceMap) {
        Map<String, Schema> classPropertyMap = new HashMap<>();
        for (FieldInfo fieldInfo : declaredFieldInfo) {
            getFieldSchema(fieldInfo, inheritanceMap)
                    .ifPresent(schema -> classPropertyMap.put(fieldInfo.getName(), schema));
        }
        return classPropertyMap;
    }

    private Optional<Schema> getFieldSchema(FieldInfo fieldInfo, Map<String, InheritanceInfo> inheritanceMap) {
        TypeSignature typeSignature = fieldInfo.getTypeSignatureOrTypeDescriptor();
        Annotation[] annotations = fieldInfo.loadClassAndGetField().getAnnotations();
        if (typeSignature instanceof BaseTypeSignature) {
            return Optional.ofNullable(parseBaseTypeSignature((BaseTypeSignature) typeSignature, annotations));
        } else if (typeSignature instanceof ArrayTypeSignature) {
            // TODO
        } else if (typeSignature instanceof ClassRefTypeSignature) {
            return Optional.ofNullable(
                    parseClassRefTypeSignature((ClassRefTypeSignature) typeSignature, annotations, inheritanceMap)
            );
        }
        return Optional.empty();
    }

    private Schema parseClassRefTypeSignature(ClassRefTypeSignature typeSignature, Annotation[] annotations,
                                              Map<String, InheritanceInfo> inheritanceMap) {
        switch (typeSignature.getFullyQualifiedClassName()) {
            case "java.lang.Byte":
            case "java.lang.Short":
            case "java.lang.Integer":
                return createNumberSchema("integer", "int32", annotations);
            case "java.lang.Long":
            case "java.math.BigInteger":
                return createNumberSchema("integer", "int64", annotations);
            case "java.lang.Float":
                return createNumberSchema("number", "float", annotations);
            case "java.lang.Double":
            case "java.math.BigDecimal":
                return createNumberSchema("number", "double", annotations);
            case "java.lang.Character":
            case "java.lang.String":
                return createStringSchema(null, annotations);
            case "java.lang.Boolean":
                return createBooleanSchema();
            case "java.util.List":
                // TODO referencies necessary
                return null;
            case "java.time.LocalDate":
            case "java.lang.Date":
                return createStringSchema("date", annotations);
            case "java.time.LocalDateTime":
            case "java.time.LocalTime":
                return createStringSchema("date-time", annotations);
            default:
                return createRefSchema(typeSignature.getClassInfo().getSimpleName(), inheritanceMap);
        }
    }

    private ComposedSchema createRefSchema(String baseClassName, Map<String, InheritanceInfo> inheritanceMap) {
        ComposedSchema schema = new ComposedSchema();
        if (inheritanceMap.containsKey(baseClassName)) {
            InheritanceInfo inheritanceInfo = inheritanceMap.get(baseClassName);
            schema.setOneOf(createOneOf(inheritanceInfo));
            schema.setDiscriminator(createDiscriminator(inheritanceInfo));
            return schema;
        }
        schema.set$ref(COMPONENT_REF_PREFIX + baseClassName);
        return schema;
    }

    private List<Schema> createOneOf(InheritanceInfo inheritanceInfo) {
        return inheritanceInfo.getDiscriminatorClassMap().keySet().stream()
                .map(key -> {
                    Schema<?> schema = new Schema<>();
                    schema.set$ref(COMPONENT_REF_PREFIX + key);
                    return schema;
                })
                .collect(Collectors.toList());
    }

    private Schema parseBaseTypeSignature(BaseTypeSignature typeSignature, Annotation[] annotations) {
        switch (typeSignature.getTypeStr()) {
            case "byte":
            case "short":
            case "int":
                return createNumberSchema("integer", "int32", annotations);
            case "long":
                return createNumberSchema("integer", "int64", annotations);
            case "float":
                return createNumberSchema("number", "float", annotations);
            case "double":
                return createNumberSchema("number", "double", annotations);
            case "char":
                return createStringSchema(null, annotations);
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

    private Schema createStringSchema(String format, Annotation[] annotations) {
        Schema<?> schema = new Schema<>();
        schema.setType("string");
        if (StringUtils.isNotBlank(format)) {
            schema.setFormat(format);
        }
        asList(annotations).forEach(annotation -> applyStringAnnotations(schema, annotation));
        return schema;
    }

    private Schema createNumberSchema(String type, String format, Annotation[] annotations) {
        Schema<?> schema = new Schema<>();
        schema.setType(type);
        schema.setFormat(format);
        asList(annotations).forEach(annotation -> applyNumberAnnotation(schema, annotation));
        return schema;
    }

    private void applyStringAnnotations(Schema<?> schema, Annotation annotation) {
        if (annotation instanceof Email) {
            schema.format("email");
        } else if (annotation instanceof Pattern) {
            schema.pattern(((Pattern) annotation).regexp());
        } else if (annotation instanceof Size) {
            schema.minLength(((Size) annotation).min());
            schema.maxLength(((Size) annotation).max());
        }
    }

    private void applyNumberAnnotation(Schema<?> schema, Annotation annotation) {
        if (annotation instanceof DecimalMin) {
            schema.setMinimum(new BigDecimal(((DecimalMin) annotation).value()));
        } else if (annotation instanceof DecimalMax) {
            schema.setMaximum(new BigDecimal(((DecimalMax) annotation).value()));
        } else if (annotation instanceof Min) {
            schema.setMinimum(BigDecimal.valueOf(((Min) annotation).value()));
        } else if (annotation instanceof Max) {
            schema.setMaximum(BigDecimal.valueOf(((Max) annotation).value()));
        } else if (annotation instanceof Positive) {
            schema.setMinimum(BigDecimal.ZERO);
            schema.setExclusiveMinimum(true);
        } else if (annotation instanceof PositiveOrZero) {
            schema.setMinimum(BigDecimal.ZERO);
        } else if (annotation instanceof Negative) {
            schema.setMaximum(BigDecimal.ZERO);
            schema.setExclusiveMaximum(true);
        } else if (annotation instanceof NegativeOrZero) {
            schema.setMaximum(BigDecimal.ZERO);
        }
    }


}

package org.spring.openapi.schema.generator;

import io.github.classgraph.*;
import io.swagger.v3.oas.models.media.*;
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
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Arrays.asList;

public class SimpleSchemaTransformer extends Transformer {

    private static final String COMPONENT_REF_PREFIX = "#/components/schemas/";

    public SimpleSchemaTransformer(Log log) {
        super(log);
    }

    public Schema transformSimpleSchema(ClassInfo classInfo, Map<String, InheritanceInfo> inheritanceMap) {
        if (classInfo.isEnum()) {
            return createEnumSchema(classInfo.loadClass().getEnumConstants());
        }
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
            return Optional.ofNullable(parseArraySignature(
                    ((ArrayTypeSignature) typeSignature).getElementTypeSignature(), inheritanceMap,
                    annotations));
        } else if (typeSignature instanceof ClassRefTypeSignature) {
            return Optional.ofNullable(
                    parseClassRefTypeSignature((ClassRefTypeSignature) typeSignature, annotations, inheritanceMap)
            );
        }
        return Optional.empty();
    }

    private Schema parseArraySignature(TypeSignature elementTypeSignature,
                                       Map<String, InheritanceInfo> inheritanceMap, Annotation[] annotations) {
        ArraySchema arraySchema = new ArraySchema();
        Stream.of(annotations).forEach(annotation -> applyArrayAnnotations(arraySchema, annotation));
        if (elementTypeSignature instanceof ClassRefTypeSignature) {
            ClassRefTypeSignature classRefTypeSignature = (ClassRefTypeSignature) elementTypeSignature;
            String basicLangItemsType = mapBasicLangItemsType(classRefTypeSignature);
            if (basicLangItemsType != null) {
                Schema<?> itemSchema = new Schema<>();
                itemSchema.setType(basicLangItemsType);
                arraySchema.setItems(itemSchema);
                return arraySchema;
            }
            String simpleElementClassName = classRefTypeSignature.getClassInfo().getSimpleName();
            if (inheritanceMap.containsKey(simpleElementClassName)) {
                InheritanceInfo inheritanceInfo = inheritanceMap.get(simpleElementClassName);
                ComposedSchema itemSchema = new ComposedSchema();
                itemSchema.setOneOf(createOneOf(inheritanceInfo));
                itemSchema.setDiscriminator(createDiscriminator(inheritanceInfo));
                arraySchema.setItems(itemSchema);
                return arraySchema;
            }
            Schema<?> itemSchema = new Schema<>();
            itemSchema.set$ref(COMPONENT_REF_PREFIX + simpleElementClassName);
            arraySchema.setItems(itemSchema);
            return arraySchema;
        } else if (elementTypeSignature instanceof BaseTypeSignature) {
            Schema<?> itemSchema = new Schema<>();
            itemSchema.setType(mapBaseType((BaseTypeSignature) elementTypeSignature));
            arraySchema.setItems(itemSchema);
            return arraySchema;
        }
        return arraySchema;
    }

    private Schema parseClassRefTypeSignature(ClassRefTypeSignature typeSignature, Annotation[] annotations,
                                              Map<String, InheritanceInfo> inheritanceMap) {
        if (typeSignature.getClassInfo() != null && typeSignature.getClassInfo().isEnum()) {
            return createEnumSchema(typeSignature.getClassInfo().loadClass().getEnumConstants());
        }
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
                return createListSchema(typeSignature, inheritanceMap, annotations);
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

    private Schema createListSchema(ClassRefTypeSignature typeSignature, Map<String, InheritanceInfo> inheritanceMap,
                                    Annotation[] annotations) {
        List<TypeArgument> typeArguments = typeSignature.getTypeArguments();
        if (typeArguments.size() != 1) {
            throw new IllegalArgumentException("List is expected to have 1 generic type argument");
        }
        return parseArraySignature(typeArguments.get(0).getTypeSignature(), inheritanceMap, annotations);
    }

    private ComposedSchema createRefSchema(String simpleClassName, Map<String, InheritanceInfo> inheritanceMap) {
        ComposedSchema schema = new ComposedSchema();
        if (inheritanceMap.containsKey(simpleClassName)) {
            InheritanceInfo inheritanceInfo = inheritanceMap.get(simpleClassName);
            schema.setOneOf(createOneOf(inheritanceInfo));
            schema.setDiscriminator(createDiscriminator(inheritanceInfo));
            return schema;
        }
        schema.set$ref(COMPONENT_REF_PREFIX + simpleClassName);
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
        getLog().info(format("Ignoring unsupported type=[%s]", typeSignature.getTypeStr()));
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

    private <T> StringSchema createEnumSchema(T[] enumConstants) {
        StringSchema schema = new StringSchema();
        schema.setType("string");
        schema.setEnum(Stream.of(enumConstants).map(Object::toString).collect(Collectors.toList()));
        return schema;
    }

    private Schema createNumberSchema(String type, String format, Annotation[] annotations) {
        Schema<?> schema = new Schema<>();
        schema.setType(type);
        schema.setFormat(format);
        asList(annotations).forEach(annotation -> applyNumberAnnotation(schema, annotation));
        return schema;
    }

    private void applyArrayAnnotations(ArraySchema schema, Annotation annotation) {
        if (annotation instanceof Size) {
            schema.minItems(((Size) annotation).min());
            schema.maxItems(((Size) annotation).max());
        }
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

    private String mapBasicLangItemsType(ClassRefTypeSignature classRefTypeSignature) {
        switch (classRefTypeSignature.getFullyQualifiedClassName()) {
            case "java.lang.Byte":
            case "java.lang.Short":
            case "java.lang.Integer":
            case "java.lang.Long":
            case "java.math.BigInteger":
                return "integer";
            case "java.lang.Float":
            case "java.lang.Double":
            case "java.math.BigDecimal":
                return "number";
            case "java.lang.Character":
            case "java.lang.String":
            case "java.time.LocalDate":
            case "java.lang.Date":
            case "java.time.LocalDateTime":
            case "java.time.LocalTime":
                return "string";
            case "java.lang.Boolean":
                return "boolean";
            case "java.util.List":
                throw new IllegalArgumentException("Nested List types are not supported"
                        + classRefTypeSignature.getBaseClassName()
                );
            default:
                return null;
        }
    }

    private String mapBaseType(BaseTypeSignature elementTypeSignature) {
        switch (elementTypeSignature.getTypeStr()) {
            case "byte":
            case "short":
            case "int":
            case "long":
                return "integer";
            case "float":
            case "double":
                return "number";
            case "char":
                return "string";
            case "boolean":
                return "boolean";
        }
        throw new IllegalArgumentException(format("Unsupported base type=[%s]", elementTypeSignature.getTypeStr()));
    }

}

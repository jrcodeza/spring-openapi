package org.spring.openapi.schema.generator;

import io.github.classgraph.*;
import io.swagger.v3.oas.models.media.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spring.openapi.schema.generator.model.InheritanceInfo;

import javax.validation.constraints.*;
import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Arrays.asList;

public class ComponentSchemaTransformer {

    private static Logger logger = LoggerFactory.getLogger(ComponentSchemaTransformer.class);

    private static final String COMPONENT_REF_PREFIX = "#/components/schemas/";

    public Schema transformSimpleSchema(ClassInfo classInfo, Map<String, InheritanceInfo> inheritanceMap) {
        if (classInfo.isEnum()) {
            return createEnumSchema(classInfo.loadClass().getEnumConstants());
        }
        List<String> requiredFields = new ArrayList<>();

        ComposedSchema schema = new ComposedSchema();
        schema.setType("object");
        schema.setProperties(getClassProperties(classInfo.getDeclaredFieldInfo(), inheritanceMap, requiredFields));

        if (!requiredFields.isEmpty()) {
            schema.setRequired(requiredFields);
        }
        if (inheritanceMap.containsKey(classInfo.getSimpleName())) {
            schema.setDiscriminator(createDiscriminator(inheritanceMap.get(classInfo.getSimpleName())));
        }
        if (classInfo.getSuperclass() != null) {
            Schema<?> parentClass = new Schema<>();
            parentClass.set$ref(COMPONENT_REF_PREFIX + classInfo.getSuperclass().getSimpleName());
            schema.setAllOf(Collections.singletonList(parentClass));
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
                                                   Map<String, InheritanceInfo> inheritanceMap,
                                                   List<String> requiredFields) {
        Map<String, Schema> classPropertyMap = new HashMap<>();
        for (FieldInfo fieldInfo : declaredFieldInfo) {
            getFieldSchema(fieldInfo, inheritanceMap, requiredFields)
                    .ifPresent(schema -> classPropertyMap.put(fieldInfo.getName(), schema));
        }
        return classPropertyMap;
    }

    private Optional<Schema> getFieldSchema(FieldInfo fieldInfo, Map<String, InheritanceInfo> inheritanceMap,
                                            List<String> requiredFields) {
        TypeSignature typeSignature = fieldInfo.getTypeSignatureOrTypeDescriptor();
        Annotation[] annotations = fieldInfo.loadClassAndGetField().getAnnotations();
        if (isRequired(annotations)) {
            requiredFields.add(fieldInfo.getName());
        }

        if (typeSignature instanceof BaseTypeSignature) {
            return createBaseTypeSchema(fieldInfo, requiredFields, (BaseTypeSignature) typeSignature, annotations);
        } else if (typeSignature instanceof ArrayTypeSignature) {
            return createArrayTypeSchema(inheritanceMap, (ArrayTypeSignature) typeSignature, annotations);
        } else if (typeSignature instanceof ClassRefTypeSignature) {
            return createClassRefSchema(inheritanceMap, (ClassRefTypeSignature) typeSignature, annotations);
        }
        return Optional.empty();
    }

    private Optional<Schema> createClassRefSchema(Map<String, InheritanceInfo> inheritanceMap,
                                                  ClassRefTypeSignature typeSignature, Annotation[] annotations) {
        Schema<?> schema = parseClassRefTypeSignature(
                typeSignature, annotations,
                inheritanceMap
        );
        enrichWithTypeAnnotations(schema, annotations);
        return Optional.ofNullable(schema);
    }

    private Optional<Schema> createArrayTypeSchema(Map<String, InheritanceInfo> inheritanceMap,
                                                   ArrayTypeSignature typeSignature, Annotation[] annotations) {
        Schema<?> schema = parseArraySignature(
                typeSignature.getElementTypeSignature(), inheritanceMap,
                annotations
        );
        enrichWithTypeAnnotations(schema, annotations);
        return Optional.ofNullable(schema);
    }

    private Optional<Schema> createBaseTypeSchema(FieldInfo fieldInfo, List<String> requiredFields,
                                                  BaseTypeSignature typeSignature, Annotation[] annotations) {
        if (!requiredFields.contains(fieldInfo.getName())) {
            requiredFields.add(fieldInfo.getName());
        }
        Schema<?> schema = parseBaseTypeSignature(typeSignature, annotations);
        enrichWithTypeAnnotations(schema, annotations);
        return Optional.ofNullable(schema);
    }

    private void enrichWithTypeAnnotations(Schema<?> schema, Annotation[] annotations) {
        enrichWithAnnotation(io.swagger.v3.oas.annotations.media.Schema.class, annotations,
                schemaAnnotation -> {
                    schema.setDeprecated(schemaAnnotation.deprecated());
                    schema.setDescription(schemaAnnotation.description());
                });
        enrichWithAnnotation(Deprecated.class, annotations, deprecatedAnnotation -> schema.setDeprecated(true));
    }

    private <T> void enrichWithAnnotation(Class<T> annotationClazz, Annotation[] annotations, Consumer<T> consumer) {
        Stream.of(annotations)
                .filter(annotation -> annotationClazz.isAssignableFrom(annotation.getClass()))
                .map(annotationClazz::cast)
                .findFirst()
                .ifPresent(consumer);
    }

    private boolean isRequired(Annotation[] annotations) {
        return Stream.of(annotations)
                .anyMatch(annotation -> annotation instanceof NotNull || annotation instanceof NotEmpty
                        || annotation instanceof NotBlank);
    }

    private Schema parseArraySignature(TypeSignature elementTypeSignature,
                                       Map<String, InheritanceInfo> inheritanceMap, Annotation[] annotations) {
        ArraySchema arraySchema = new ArraySchema();
        Stream.of(annotations).forEach(annotation -> applyArrayAnnotations(arraySchema, annotation));
        if (elementTypeSignature instanceof ClassRefTypeSignature) {
            ClassRefTypeSignature classRefTypeSignature = (ClassRefTypeSignature) elementTypeSignature;
            String basicLangItemsType = mapBasicLangItemsType(classRefTypeSignature);
            // basic types like Integer or String
            if (basicLangItemsType != null) {
                Schema<?> itemSchema = new Schema<>();
                itemSchema.setType(basicLangItemsType);
                arraySchema.setItems(itemSchema);
                return arraySchema;
            }
            String simpleElementClassName = classRefTypeSignature.getClassInfo().getSimpleName();
            // is inheritance needed
            if (inheritanceMap.containsKey(simpleElementClassName)) {
                InheritanceInfo inheritanceInfo = inheritanceMap.get(simpleElementClassName);
                ComposedSchema itemSchema = new ComposedSchema();
                itemSchema.setOneOf(createOneOf(inheritanceInfo));
                itemSchema.setDiscriminator(createDiscriminator(inheritanceInfo));
                arraySchema.setItems(itemSchema);
                return arraySchema;
            }
            // else do ref
            Schema<?> itemSchema = new Schema<>();
            itemSchema.set$ref(COMPONENT_REF_PREFIX + simpleElementClassName);
            arraySchema.setItems(itemSchema);
            return arraySchema;
        } else if (elementTypeSignature instanceof BaseTypeSignature) {
            // primitive type like int
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
        logger.info(format("Ignoring unsupported type=[%s]", typeSignature.getTypeStr()));
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

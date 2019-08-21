package org.spring.openapi.schema.generator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonSubTypes;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spring.openapi.schema.generator.model.GenerationContext;
import org.spring.openapi.schema.generator.model.InheritanceInfo;
import org.springframework.util.ReflectionUtils;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

import static java.lang.String.format;
import static java.util.Arrays.asList;

public class ComponentSchemaTransformer {

    private static Logger logger = LoggerFactory.getLogger(ComponentSchemaTransformer.class);

    private static final String COMPONENT_REF_PREFIX = "#/components/schemas/";

    public Schema transformSimpleSchema(Class<?> clazz, GenerationContext generationContext) {
        if (clazz.isEnum()) {
            return createEnumSchema(clazz.getEnumConstants());
        }
        List<String> requiredFields = new ArrayList<>();

        Schema<?> schema = new Schema<>();
        schema.setType("object");
        schema.setProperties(getClassProperties(clazz, generationContext, requiredFields));

        if (!requiredFields.isEmpty()) {
            schema.setRequired(requiredFields);
        }
        if (generationContext.getInheritanceMap().containsKey(clazz.getName())) {
            schema.setDiscriminator(createDiscriminator(generationContext.getInheritanceMap().get(clazz.getName())));
        }
        if (clazz.getSuperclass() != null) {
            if (isInPackagesToBeScanned(clazz.getSuperclass(), generationContext)) {
                Schema<?> parentClassSchema = new Schema<>();
                parentClassSchema.set$ref(COMPONENT_REF_PREFIX + clazz.getSuperclass().getSimpleName());

                ComposedSchema composedSchema = new ComposedSchema();
                composedSchema.setAllOf(Arrays.asList(parentClassSchema, schema));
                return composedSchema;
            } else {
                traverseAndAddProperties(schema, generationContext, clazz.getSuperclass());
            }
        }
        return schema;
    }

    private void traverseAndAddProperties(Schema<?> schema, GenerationContext generationContext, Class<?> superclass) {
        List<String> requiredFields = new ArrayList<>();

        schema.getProperties().putAll(getClassProperties(superclass, generationContext, requiredFields));
        if (!requiredFields.isEmpty()) {
            schema.setRequired(requiredFields);
        }
        if (superclass.getSuperclass() != null && !"java.lang".equals(superclass.getSuperclass().getPackage().getName())) {
            traverseAndAddProperties(schema, generationContext, superclass.getSuperclass());
        }
    }

    private boolean isInPackagesToBeScanned(Class<?> clazz, GenerationContext generationContext) {
        return generationContext.getModelPackages().stream().anyMatch(pkg -> clazz.getPackage().getName().startsWith(pkg));
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

    private Map<String, Schema> getClassProperties(Class<?> clazz, GenerationContext generationContext, List<String> requiredFields) {
        Map<String, Schema> classPropertyMap = new HashMap<>();
        ReflectionUtils.doWithLocalFields(clazz,
                field -> getFieldSchema(field, generationContext, requiredFields).ifPresent(schema -> classPropertyMap.put(field.getName(), schema))
        );
        return classPropertyMap;
    }

    private Optional<Schema> getFieldSchema(Field field, GenerationContext generationContext, List<String> requiredFields) {
        Class<?> typeSignature = field.getType();
        Annotation[] annotations = field.getAnnotations();
        if (isRequired(annotations)) {
            requiredFields.add(field.getName());
        }

        if (typeSignature.isPrimitive()) {
            return createBaseTypeSchema(field, requiredFields, annotations);
        } else if (typeSignature.isArray()) {
            return createArrayTypeSchema(generationContext, typeSignature, annotations);
        } else if (typeSignature.isAssignableFrom(List.class)) {
            if (field.getGenericType() instanceof ParameterizedType) {
                Class<?> listGenericParameter = (Class<?>)((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                return Optional.of(parseArraySignature(listGenericParameter, generationContext, annotations));
            }
            return Optional.empty();
        } else {
            return createClassRefSchema(generationContext, typeSignature, annotations);
        }
    }

    private Optional<Schema> createClassRefSchema(GenerationContext generationContext, Class<?> typeClass, Annotation[] annotations) {
        Schema<?> schema = parseClassRefTypeSignature(typeClass, annotations, generationContext);
        enrichWithTypeAnnotations(schema, annotations);
        return Optional.ofNullable(schema);
    }

    private Optional<Schema> createArrayTypeSchema(GenerationContext generationContext, Class<?> typeSignature, Annotation[] annotations) {
        Class<?> arrayComponentType = typeSignature.getComponentType();
        Schema<?> schema = parseArraySignature(arrayComponentType, generationContext, annotations);
        enrichWithTypeAnnotations(schema, annotations);
        return Optional.ofNullable(schema);
    }

    private Optional<Schema> createBaseTypeSchema(Field field, List<String> requiredFields, Annotation[] annotations) {
        if (!requiredFields.contains(field.getName())) {
            requiredFields.add(field.getName());
        }
        Schema<?> schema = parseBaseTypeSignature(field.getType(), annotations);
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
        return Stream.of(annotations).anyMatch(annotation -> annotation instanceof NotNull);
    }

    private Schema parseArraySignature(Class<?> elementTypeSignature, GenerationContext generationContext, Annotation[] annotations) {
        ArraySchema arraySchema = new ArraySchema();
        Stream.of(annotations).forEach(annotation -> applyArrayAnnotations(arraySchema, annotation));
        if (elementTypeSignature.isPrimitive()) {
            // primitive type like int
            Schema<?> itemSchema = new Schema<>();
            itemSchema.setType(mapBaseType(elementTypeSignature));
            arraySchema.setItems(itemSchema);
            return arraySchema;
        } else if (isInPackagesToBeScanned(elementTypeSignature, generationContext) || elementTypeSignature.getPackage().getName().startsWith("java.lang")) {
            String basicLangItemsType = mapBasicLangItemsType(elementTypeSignature);
            // basic types like Integer or String
            if (basicLangItemsType != null) {
                Schema<?> itemSchema = new Schema<>();
                itemSchema.setType(basicLangItemsType);
                arraySchema.setItems(itemSchema);
                return arraySchema;
            }
            String className = elementTypeSignature.getName();
            // is inheritance needed
            if (generationContext.getInheritanceMap().containsKey(className)) {
                InheritanceInfo inheritanceInfo = generationContext.getInheritanceMap().get(className);
                ComposedSchema itemSchema = new ComposedSchema();
                itemSchema.setOneOf(createOneOf(inheritanceInfo));
                itemSchema.setDiscriminator(createDiscriminator(inheritanceInfo));
                arraySchema.setItems(itemSchema);
                return arraySchema;
            }
            // else do ref
            Schema<?> itemSchema = new Schema<>();
            itemSchema.set$ref(COMPONENT_REF_PREFIX + elementTypeSignature.getSimpleName());
            arraySchema.setItems(itemSchema);
            return arraySchema;
        }

        Schema<?> itemSchema = new Schema<>();
        itemSchema.setType("object");
        arraySchema.setItems(itemSchema);
        return arraySchema;
    }

    private Schema parseClassRefTypeSignature(Class<?> typeClass, Annotation[] annotations, GenerationContext generationContext) {
        if (typeClass.isEnum()) {
            return createEnumSchema(typeClass.getEnumConstants());
        }
        if (Byte.class.equals(typeClass) || Short.class.equals(typeClass) || Integer.class.equals(typeClass)) {
            return createNumberSchema("integer", "int32", annotations);
        } else if (Long.class.equals(typeClass) || BigInteger.class.equals(typeClass)) {
            return createNumberSchema("integer", "int64", annotations);
        } else if (Float.class.equals(typeClass)) {
            return createNumberSchema("number", "float", annotations);
        } else if (Double.class.equals(typeClass) || BigDecimal.class.equals(typeClass)) {
            return createNumberSchema("number", "double", annotations);
        } else if (Character.class.equals(typeClass) || String.class.equals(typeClass)) {
            return createStringSchema(null, annotations);
        } else if (Boolean.class.equals(typeClass)) {
            return createBooleanSchema();
        } else if (List.class.equals(typeClass)) {
            return createListSchema(typeClass, generationContext, annotations);
        } else if (LocalDate.class.equals(typeClass) || Date.class.equals(typeClass)) {
            return createStringSchema("date", annotations);
        } else if (LocalDateTime.class.equals(typeClass) || LocalTime.class.equals(typeClass)) {
            return createStringSchema("date-time", annotations);
        }
        return createRefSchema(typeClass, generationContext);
    }

    private Schema createListSchema(Class<?> typeSignature, GenerationContext generationContext, Annotation[] annotations) {
        return parseArraySignature(typeSignature, generationContext, annotations);
    }

    private ComposedSchema createRefSchema(Class<?> typeSignature, GenerationContext generationContext) {
        ComposedSchema schema = new ComposedSchema();
        if (isInPackagesToBeScanned(typeSignature, generationContext)) {
            if (generationContext.getInheritanceMap().containsKey(typeSignature.getName())) {
                InheritanceInfo inheritanceInfo = generationContext.getInheritanceMap().get(typeSignature.getName());
                schema.setOneOf(createOneOf(inheritanceInfo));
                schema.setDiscriminator(createDiscriminator(inheritanceInfo));
                return schema;
            }
            schema.set$ref(COMPONENT_REF_PREFIX + typeSignature.getSimpleName());
            return schema;
        }

        // fallback
        schema.setType("object");
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

    private Schema parseBaseTypeSignature(Class<?> type, Annotation[] annotations) {
        if (byte.class.equals(type) || short.class.equals(type) || int.class.equals(type)) {
            return createNumberSchema("integer", "int32", annotations);
        } else if (long.class.equals(type)) {
            return createNumberSchema("integer", "int64", annotations);
        } else if (float.class.equals(type)) {
            return createNumberSchema("number", "float", annotations);
        } else if (double.class.equals(type)) {
            return createNumberSchema("number", "double", annotations);
        } else if (char.class.equals(type)) {
            return createStringSchema(null, annotations);
        } else if (boolean.class.equals(type)) {
            return createBooleanSchema();
        }
        logger.info(format("Ignoring unsupported type=[%s]", type.getSimpleName()));
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
        if (annotation instanceof Pattern) {
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
        }
    }

    private String mapBasicLangItemsType(Class<?> classRefTypeSignature) {
        if (Byte.class.equals(classRefTypeSignature) || Short.class.equals(classRefTypeSignature) || Integer.class.equals(classRefTypeSignature)
                || Long.class.equals(classRefTypeSignature) || BigInteger.class.equals(classRefTypeSignature)) {
            return "integer";
        } else if (Float.class.equals(classRefTypeSignature) || Double.class.equals(classRefTypeSignature) || BigDecimal.class.equals(classRefTypeSignature)) {
            return "number";
        } else if (Character.class.equals(classRefTypeSignature) || String.class.equals(classRefTypeSignature) || LocalDate.class.equals(classRefTypeSignature)
                || Date.class.equals(classRefTypeSignature) || LocalDateTime.class.equals(classRefTypeSignature)
                || LocalTime.class.equals(classRefTypeSignature)) {
            return "string";
        } else if (Boolean.class.equals(classRefTypeSignature)) {
            return "boolean";
        } else if (List.class.equals(classRefTypeSignature)) {
            throw new IllegalArgumentException("Nested List types are not supported"
                    + classRefTypeSignature.getName()
            );
        }
        return null;
    }

    private String mapBaseType(Class<?> elementTypeSignature) {
        if (byte.class.equals(elementTypeSignature) || short.class.equals(elementTypeSignature) || int.class.equals(elementTypeSignature)
                || long.class.equals(elementTypeSignature)) {
            return "integer";
        } else if (float.class.equals(elementTypeSignature) || double.class.equals(elementTypeSignature)) {
            return "number";
        } else if (char.class.equals(elementTypeSignature)) {
            return "string";
        } else if (boolean.class.equals(elementTypeSignature)) {
            return "boolean";
        }
        throw new IllegalArgumentException(format("Unsupported base type=[%s]", elementTypeSignature.getSimpleName()));
    }

}

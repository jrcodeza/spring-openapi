package com.github.jrcodeza.schema.generator.util;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.jrcodeza.schema.generator.util.CommonConstants.COMPONENT_REF_PREFIX;
import static java.lang.String.format;
import static java.util.Arrays.asList;

public class SchemaGeneratorHelper {
    private static Logger logger = LoggerFactory.getLogger(SchemaGeneratorHelper.class);

    private final List<String> modelPackages;

    public SchemaGeneratorHelper(List<String> modelPackages) {
        this.modelPackages = modelPackages;
    }

    public MediaType createMediaType(Class<?> requestBodyParameter,
                                     String parameterName,
                                     List<Class<?>> genericParams) {
        Schema<?> rootMediaSchema = new Schema<>();
        if (isFile(requestBodyParameter)) {
            Schema<?> fileSchema = new Schema<>();
            fileSchema.setType("string");
            fileSchema.setFormat("binary");

            if (parameterName == null) {
                rootMediaSchema = fileSchema;
            } else {
                Map<String, Schema> properties = new HashMap<>();
                properties.put(parameterName, fileSchema);
                rootMediaSchema.setType("object");
                rootMediaSchema.setProperties(properties);
            }
        } else if (isList(requestBodyParameter, genericParams)) {
            rootMediaSchema = parseArraySignature(getFirstOrNull(genericParams), null, new Annotation[]{});
        } else if (!StringUtils.equalsIgnoreCase(requestBodyParameter.getSimpleName(), "void")) {
            if (isInPackagesToBeScanned(requestBodyParameter, modelPackages)) {
                rootMediaSchema.set$ref(COMPONENT_REF_PREFIX + requestBodyParameter.getSimpleName());
            } else if (requestBodyParameter.isAssignableFrom(ResponseEntity.class) && !CollectionUtils.isEmpty(genericParams)
                    && !genericParams.get(0).isAssignableFrom(Void.class)) {
                rootMediaSchema.set$ref(COMPONENT_REF_PREFIX + genericParams.get(0).getSimpleName());
            } else {
                return null;
            }
        } else {
            return null;
        }

        MediaType mediaType = new MediaType();
        mediaType.setSchema(rootMediaSchema);
        return mediaType;
    }


    private Class<?> getFirstOrNull(List<Class<?>> genericParams) {
        if (CollectionUtils.isEmpty(genericParams) || genericParams.get(0).isAssignableFrom(List.class)) {
            return null;
        }
        return genericParams.get(0);
    }

    private boolean isList(Class<?> requestBodyParameter, List<Class<?>> genericTypes) {
        Class<?> classToCheck = requestBodyParameter;
        if (requestBodyParameter.isAssignableFrom(ResponseEntity.class) && !genericTypes.isEmpty()) {
            classToCheck = genericTypes.get(genericTypes.size() - 1);
        }
        return classToCheck.isAssignableFrom(List.class);
    }


    public boolean isFile(Class<?> parameter) {
        return parameter.isAssignableFrom(MultipartFile.class);
    }


    @SuppressWarnings("squid:S1192") // better in-place defined for better readability
    public Schema parseBaseTypeSignature(Class<?> type, Annotation[] annotations) {
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
        logger.info("Ignoring unsupported type=[{}]", type.getSimpleName());
        return null;
    }

    @SuppressWarnings("squid:S3776") // no other solution
    public Schema parseClassRefTypeSignature(Class<?> typeClass,
                                             Annotation[] annotations) {
        return this.parseClassRefTypeSignature(typeClass, annotations, this.modelPackages);
    }

    @SuppressWarnings("squid:S3776") // no other solution
    public Schema parseClassRefTypeSignature(Class<?> typeClass,
                                             Annotation[] annotations,
                                             List<String> modelPackages) {
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
            return createListSchema(typeClass, modelPackages, annotations);
        } else if (LocalDate.class.equals(typeClass) || Date.class.equals(typeClass)) {
            return createStringSchema("date", annotations);
        } else if (LocalDateTime.class.equals(typeClass) || LocalTime.class.equals(typeClass)) {
            return createStringSchema("date-time", annotations);
        }
        return createRefSchema(typeClass, modelPackages);
    }

    public Schema parseArraySignature(Class<?> elementTypeSignature,
                                      Annotation[] annotations) {
        return this.parseArraySignature(elementTypeSignature, this.modelPackages, annotations);
    }

    public Schema parseArraySignature(Class<?> elementTypeSignature,
                                      List<String> modelPackages,
                                      Annotation[] annotations) {
        ArraySchema arraySchema = new ArraySchema();
        if (elementTypeSignature == null) {
            arraySchema.setItems(createObjectSchema());
            return arraySchema;
        }
        enrichWithTypeAnnotations(arraySchema, annotations);
        Stream.of(annotations).forEach(annotation -> applyArrayAnnotations(arraySchema, annotation));
        if (elementTypeSignature.isPrimitive()) {
            // primitive type like int
            Schema<?> itemSchema = new Schema<>();
            itemSchema.setType(mapBaseType(elementTypeSignature));
            arraySchema.setItems(itemSchema);
            return arraySchema;
        } else if (isInPackagesToBeScanned(elementTypeSignature, modelPackages) || elementTypeSignature.getPackage().getName().startsWith("java.lang")) {
            String basicLangItemsType = mapBasicLangItemsType(elementTypeSignature);
            // basic types like Integer or String
            if (basicLangItemsType != null) {
                Schema<?> itemSchema = new Schema<>();
                itemSchema.setType(basicLangItemsType);
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

    private Schema<?> createObjectSchema() {
        Schema<?> schema = new Schema<>();
        schema.setType("object");
        return schema;
    }

    @SuppressWarnings("squid:S1192") // better in-place defined for better readability
    protected Schema createBooleanSchema() {
        Schema<?> schema = new Schema<>();
        schema.setType("boolean");
        return schema;
    }

    @SuppressWarnings("squid:S1192") // better in-place defined for better readability
    protected Schema createStringSchema(String format, Annotation[] annotations) {
        Schema<?> schema = new Schema<>();
        schema.setType("string");
        if (StringUtils.isNotBlank(format)) {
            schema.setFormat(format);
        }
        asList(annotations).forEach(annotation -> applyStringAnnotations(schema, annotation));
        return schema;
    }

    public <T> StringSchema createEnumSchema(T[] enumConstants) {
        StringSchema schema = new StringSchema();
        schema.setType("string");
        schema.setEnum(Stream.of(enumConstants).map(Object::toString).collect(Collectors.toList()));
        return schema;
    }

    protected Schema createNumberSchema(String type, String format, Annotation[] annotations) {
        Schema<?> schema = new Schema<>();
        schema.setType(type);
        schema.setFormat(format);
        asList(annotations).forEach(annotation -> applyNumberAnnotation(schema, annotation));
        return schema;
    }

    protected ComposedSchema createRefSchema(Class<?> typeSignature, List<String> modelPackages) {
        ComposedSchema composedSchema = new ComposedSchema();

        if (modelPackages == null || isInPackagesToBeScanned(typeSignature, modelPackages)) {
            composedSchema.set$ref(COMPONENT_REF_PREFIX + typeSignature.getSimpleName());
            return composedSchema;

        }

        // fallback
        composedSchema.setType("object");
        return composedSchema;
    }

    protected Schema createListSchema(Class<?> typeSignature, List<String> modelPackages, Annotation[] annotations) {
        return parseArraySignature(typeSignature, modelPackages, annotations);
    }

    protected void applyStringAnnotations(Schema<?> schema, Annotation annotation) {
        if (annotation instanceof Pattern) {
            schema.pattern(((Pattern) annotation).regexp());
        } else if (annotation instanceof Size) {
            schema.minLength(((Size) annotation).min());
            schema.maxLength(((Size) annotation).max());
        }
    }

    protected void applyNumberAnnotation(Schema<?> schema, Annotation annotation) {
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

    protected void applyArrayAnnotations(ArraySchema schema, Annotation annotation) {
        if (annotation instanceof Size) {
            schema.minItems(((Size) annotation).min());
            schema.maxItems(((Size) annotation).max());
        }
    }

    protected String mapBasicLangItemsType(Class<?> classRefTypeSignature) {
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

    protected String mapBaseType(Class<?> elementTypeSignature) {
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

    public boolean isInPackagesToBeScanned(Class<?> clazz, List<String> modelPackages) {
        return modelPackages == null
                || modelPackages.stream().anyMatch(pkg -> clazz.getPackage().getName().startsWith(pkg));
    }

    public boolean isInPackagesToBeScanned(Class<?> clazz) {
        return isInPackagesToBeScanned(clazz, this.modelPackages);
    }

    public void enrichWithTypeAnnotations(Schema<?> schema, Annotation[] annotations) {
        enrichWithAnnotation(io.swagger.v3.oas.annotations.media.Schema.class, annotations,
                schemaAnnotation -> {
                    schema.setDeprecated(schemaAnnotation.deprecated());
                    schema.setDescription(schemaAnnotation.description());
                    enrichWithAccessMode(schema, schemaAnnotation);
                });
        enrichWithAnnotation(Deprecated.class, annotations, deprecatedAnnotation -> schema.setDeprecated(true));
    }

    public void enrichWithTypeAnnotations(Parameter parameter, Annotation[] annotations) {
        enrichWithAnnotation(io.swagger.v3.oas.annotations.media.Schema.class, annotations,
                schemaAnnotation -> {
                    parameter.setDeprecated(schemaAnnotation.deprecated());
                    parameter.setDescription(schemaAnnotation.description());
                });
        enrichWithAnnotation(Deprecated.class, annotations, deprecatedAnnotation -> parameter.setDeprecated(true));
    }

    private void enrichWithAccessMode(Schema<?> schema, io.swagger.v3.oas.annotations.media.Schema schemaAnnotation) {
        if (schemaAnnotation.accessMode() == io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY) {
            schema.setReadOnly(true);
        } else if (schemaAnnotation.accessMode() == io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY) {
            schema.setWriteOnly(true);
        }
    }

    protected <T> void enrichWithAnnotation(Class<T> annotationClazz, Annotation[] annotations, Consumer<T> consumer) {
        Stream.of(annotations)
                .filter(annotation -> annotationClazz.isAssignableFrom(annotation.getClass()))
                .map(annotationClazz::cast)
                .findFirst()
                .ifPresent(consumer);
    }
}

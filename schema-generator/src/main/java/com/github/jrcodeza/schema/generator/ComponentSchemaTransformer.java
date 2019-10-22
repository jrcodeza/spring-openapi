package com.github.jrcodeza.schema.generator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.github.jrcodeza.schema.generator.interceptors.SchemaFieldInterceptor;
import com.github.jrcodeza.schema.generator.model.GenerationContext;
import com.github.jrcodeza.schema.generator.model.InheritanceInfo;

import org.springframework.util.ReflectionUtils;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

import static com.github.jrcodeza.schema.generator.util.CommonConstants.COMPONENT_REF_PREFIX;
import static com.github.jrcodeza.schema.generator.util.GeneratorUtils.shouldBeIgnored;


public class ComponentSchemaTransformer extends OpenApiTransformer {

    private final List<SchemaFieldInterceptor> schemaFieldInterceptors;

    public ComponentSchemaTransformer(List<SchemaFieldInterceptor> schemaFieldInterceptors) {
        this.schemaFieldInterceptors = schemaFieldInterceptors;
    }

    public Schema transformSimpleSchema(Class<?> clazz, GenerationContext generationContext) {
        if (clazz.isEnum()) {
            return createEnumSchema(clazz.getEnumConstants());
        }
        List<String> requiredFields = new ArrayList<>();

        Schema<?> schema = new Schema<>();
        schema.setType("object");
        schema.setProperties(getClassProperties(clazz, generationContext, requiredFields));

        updateRequiredFields(schema, requiredFields);

        if (generationContext.getInheritanceMap().containsKey(clazz.getName())) {
            Discriminator discriminator = createDiscriminator(generationContext.getInheritanceMap().get(clazz.getName()));
            schema.setDiscriminator(discriminator);
            enrichWithDiscriminatorProperty(schema, discriminator);
        }
        if (clazz.getSuperclass() != null && isInPackagesToBeScanned(clazz.getSuperclass(), generationContext)) {
            return traverseAndAddProperties(schema, generationContext, clazz.getSuperclass());
        }
        return schema;
    }

    private void updateRequiredFields(Schema schema, List<String> requiredFields) {
        if (requiredFields == null || requiredFields.isEmpty()) {
            return;
        }
        if (schema.getRequired() == null) {
            schema.setRequired(requiredFields);
            return;
        }
        schema.getRequired().addAll(requiredFields);
    }

    private void enrichWithDiscriminatorProperty(Schema schema, Discriminator discriminator) {
        if (schema != null && !schema.getProperties().containsKey(discriminator.getPropertyName())) {
            schema.getRequired().add(discriminator.getPropertyName());
            schema.getProperties().put(discriminator.getPropertyName(), new StringSchema());
        }
    }

    private Schema<?> traverseAndAddProperties(Schema<?> schema, GenerationContext generationContext, Class<?> superclass) {
        if (superclass.getAnnotation(JsonSubTypes.class) == null) {
            List<String> requiredFields = new ArrayList<>();
            schema.getProperties().putAll(getClassProperties(superclass, generationContext, requiredFields));
            updateRequiredFields(schema, requiredFields);
            if (superclass.getSuperclass() != null && !"java.lang".equals(superclass.getSuperclass().getPackage().getName())) {
                return traverseAndAddProperties(schema, generationContext, superclass.getSuperclass());
            }
            return schema;
        } else {
            Schema<?> parentClassSchema = new Schema<>();
            parentClassSchema.set$ref(COMPONENT_REF_PREFIX + superclass.getSimpleName());

            ComposedSchema composedSchema = new ComposedSchema();
            composedSchema.setAllOf(Arrays.asList(parentClassSchema, schema));
            return composedSchema;
        }
    }

    private Map<String, Schema> getClassProperties(Class<?> clazz, GenerationContext generationContext, List<String> requiredFields) {
        Map<String, Schema> classPropertyMap = new HashMap<>();
        ReflectionUtils.doWithLocalFields(clazz,
                field -> getFieldSchema(field, generationContext, requiredFields).ifPresent(schema -> {
                    schemaFieldInterceptors.forEach(modelClassFieldInterceptor -> modelClassFieldInterceptor.intercept(clazz, field, schema));
                    classPropertyMap.put(field.getName(), schema);
                })
        );
        return classPropertyMap;
    }

    private Optional<Schema> getFieldSchema(Field field, GenerationContext generationContext, List<String> requiredFields) {
        if (shouldBeIgnored(field)) {
            return Optional.empty();
        }

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

    private boolean isRequired(Annotation[] annotations) {
        return Stream.of(annotations).anyMatch(annotation -> annotation instanceof NotNull);
    }

    @Override
    protected Schema createListSchema(Class<?> typeSignature, GenerationContext generationContext, Annotation[] annotations) {
        return parseArraySignature(typeSignature, generationContext, annotations);
    }

    @Override
    protected ComposedSchema createRefSchema(Class<?> typeSignature, GenerationContext generationContext) {
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

}

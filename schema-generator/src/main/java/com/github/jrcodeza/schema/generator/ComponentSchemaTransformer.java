package com.github.jrcodeza.schema.generator;

import com.github.jrcodeza.schema.generator.filters.SchemaFieldFilter;
import com.github.jrcodeza.schema.generator.interceptors.SchemaFieldInterceptor;
import com.github.jrcodeza.schema.generator.model.CustomComposedSchema;
import com.github.jrcodeza.schema.generator.model.InheritanceInfo;
import com.github.jrcodeza.schema.generator.util.SchemaGeneratorHelper;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ReflectionUtils;

import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.jrcodeza.schema.generator.util.CommonConstants.COMPONENT_REF_PREFIX;
import static com.github.jrcodeza.schema.generator.util.GeneratorUtils.shouldBeIgnored;


public class ComponentSchemaTransformer {

    private final List<SchemaFieldInterceptor> schemaFieldInterceptors;
    private AtomicReference<SchemaFieldFilter> schemaFieldFilter;
    private final SchemaGeneratorHelper schemaGeneratorHelper;

    public ComponentSchemaTransformer(List<SchemaFieldInterceptor> schemaFieldInterceptors,
                                      AtomicReference<SchemaFieldFilter> schemaFieldFilter,
                                      SchemaGeneratorHelper schemaGeneratorHelper) {
        this.schemaFieldInterceptors = schemaFieldInterceptors;
        this.schemaFieldFilter = schemaFieldFilter;
        this.schemaGeneratorHelper = schemaGeneratorHelper;
    }

    public Schema transformSimpleSchema(Class<?> clazz, Map<String, InheritanceInfo> inheritanceMap) {
        if (clazz.isEnum()) {
            return schemaGeneratorHelper.createEnumSchema(clazz.getEnumConstants());
        }
        List<String> requiredFields = new ArrayList<>();

        Schema<?> schema = new Schema<>();
        schema.setType("object");
        schema.setProperties(getClassProperties(clazz, requiredFields));
		schemaGeneratorHelper.enrichWithTypeAnnotations(schema, clazz.getDeclaredAnnotations());

        updateRequiredFields(schema, requiredFields);

        if (inheritanceMap.containsKey(clazz.getName())) {
            Discriminator discriminator = createDiscriminator(inheritanceMap.get(clazz.getName()));
            schema.setDiscriminator(discriminator);
            enrichWithDiscriminatorProperty(schema, discriminator);
        }
        if (clazz.getSuperclass() != null) {
            return traverseAndAddProperties(schema, inheritanceMap, clazz.getSuperclass(), clazz);
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

    private void updateSchemaProperties(Schema schema, String propertyName, Schema propertyValue) {
        if (StringUtils.isBlank(propertyName) || propertyValue == null) {
            return;
        }
        if (schema.getProperties() == null) {
            schema.setProperties(new HashMap<>());
        }
        schema.getProperties().put(propertyName, propertyValue);
    }

    private void enrichWithDiscriminatorProperty(Schema schema, Discriminator discriminator) {
        if (schema != null && !schema.getProperties().containsKey(discriminator.getPropertyName())) {
            List<String> discriminatorTypeRequiredProperty = new ArrayList<>();
            discriminatorTypeRequiredProperty.add(discriminator.getPropertyName());

            updateSchemaProperties(schema, discriminator.getPropertyName(), new StringSchema());
            updateRequiredFields(schema, discriminatorTypeRequiredProperty);
        }
    }

    private Schema<?> traverseAndAddProperties(Schema<?> schema, Map<String, InheritanceInfo> inheritanceMap, Class<?> superclass, Class<?> actualClass) {
        if (!schemaGeneratorHelper.isInPackagesToBeScanned(superclass)) {
            // adding properties from parent classes is present due to swagger ui bug, after using different ui
            // this becomes relevant only for third party packages
            List<String> requiredFields = new ArrayList<>();
            schema.getProperties().putAll(getClassProperties(superclass, requiredFields));
            updateRequiredFields(schema, requiredFields);
            if (superclass.getSuperclass() != null && !"java.lang".equals(superclass.getSuperclass().getPackage().getName())) {
                return traverseAndAddProperties(schema, inheritanceMap, superclass.getSuperclass(), superclass);
            }
            return schema;
        } else {
            Schema<?> parentClassSchema = new Schema<>();
            parentClassSchema.set$ref(COMPONENT_REF_PREFIX + superclass.getSimpleName());

            CustomComposedSchema composedSchema = new CustomComposedSchema();
            enrichWithAdditionalProperties(composedSchema, inheritanceMap, superclass.getName(), actualClass.getSimpleName());
            composedSchema.setAllOf(Arrays.asList(parentClassSchema, schema));
            composedSchema.setDescription(schema.getDescription());
            return composedSchema;
        }
    }

    private void enrichWithAdditionalProperties(CustomComposedSchema customComposedSchema, Map<String, InheritanceInfo> inheritanceInfoMap, String superClassName,
                                                String actualClassName) {
        if (inheritanceInfoMap.containsKey(superClassName)) {
            Map<String, String> discriminatorClassMap = inheritanceInfoMap.get(superClassName).getDiscriminatorClassMap();
            if (discriminatorClassMap.containsKey(actualClassName)) {
                customComposedSchema.setDiscriminatorValue(discriminatorClassMap.get(actualClassName));
            }
        }
    }

    private Map<String, Schema> getClassProperties(Class<?> clazz, List<String> requiredFields) {
        Map<String, Schema> classPropertyMap = new HashMap<>();
        ReflectionUtils.doWithLocalFields(clazz,
                field -> getFieldSchema(clazz, field, requiredFields).ifPresent(schema -> {
                    schemaFieldInterceptors.forEach(modelClassFieldInterceptor -> modelClassFieldInterceptor.intercept(clazz, field, schema));
                    classPropertyMap.put(field.getName(), schema);
                })
        );
        return classPropertyMap;
    }

    private Optional<Schema> getFieldSchema(Class<?> clazz, Field field, List<String> requiredFields) {
        if (shouldIgnoreField(clazz, field)) {
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
            return createArrayTypeSchema(typeSignature, annotations);
        } else if (StringUtils.equalsIgnoreCase(typeSignature.getName(), "java.lang.Object")) {
            ObjectSchema objectSchema = new ObjectSchema();
            objectSchema.setName(field.getName());
            return Optional.of(objectSchema);
        } else if (typeSignature.isAssignableFrom(List.class)) {
            if (field.getGenericType() instanceof ParameterizedType) {
                Class<?> listGenericParameter = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                return Optional.of(schemaGeneratorHelper.parseArraySignature(listGenericParameter, annotations));
            }
            return Optional.empty();
        } else {
            return createClassRefSchema(typeSignature, annotations);
        }
    }

    private boolean shouldIgnoreField(Class<?> clazz, Field field) {
        if (shouldBeIgnored(field)) {
            return true;
        }

        return schemaFieldFilter.get() != null && schemaFieldFilter.get().shouldIgnore(clazz, field);
    }

    private Optional<Schema> createClassRefSchema(Class<?> typeClass, Annotation[] annotations) {
        Schema<?> schema = schemaGeneratorHelper.parseClassRefTypeSignature(typeClass, annotations);
        schemaGeneratorHelper.enrichWithTypeAnnotations(schema, annotations);
        return Optional.ofNullable(schema);
    }

    private Optional<Schema> createArrayTypeSchema(Class<?> typeSignature, Annotation[] annotations) {
        Class<?> arrayComponentType = typeSignature.getComponentType();
        Schema<?> schema = schemaGeneratorHelper.parseArraySignature(arrayComponentType, annotations);
        schemaGeneratorHelper.enrichWithTypeAnnotations(schema, annotations);
        return Optional.ofNullable(schema);
    }

    private Optional<Schema> createBaseTypeSchema(Field field, List<String> requiredFields, Annotation[] annotations) {
        if (!requiredFields.contains(field.getName())) {
            requiredFields.add(field.getName());
        }
        Schema<?> schema = schemaGeneratorHelper.parseBaseTypeSignature(field.getType(), annotations);
        schemaGeneratorHelper.enrichWithTypeAnnotations(schema, annotations);
        return Optional.ofNullable(schema);
    }

    private boolean isRequired(Annotation[] annotations) {
        return Stream.of(annotations).anyMatch(annotation -> annotation instanceof NotNull);
    }

}

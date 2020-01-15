package com.github.jrcodeza.schema.v2.generator;

import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.RefModel;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.springframework.util.ReflectionUtils;

import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.github.jrcodeza.schema.v2.generator.interceptors.SchemaFieldInterceptor;
import com.github.jrcodeza.schema.v2.generator.model.GenerationContext;
import com.github.jrcodeza.schema.v2.generator.model.InheritanceInfo;
import com.github.jrcodeza.schema.v2.generator.util.CommonConstants;
import com.github.jrcodeza.schema.v2.generator.util.GeneratorUtils;

public class ComponentSchemaTransformer extends OpenApiTransformer {

	private final List<SchemaFieldInterceptor> schemaFieldInterceptors;

	public ComponentSchemaTransformer(List<SchemaFieldInterceptor> schemaFieldInterceptors) {
		this.schemaFieldInterceptors = schemaFieldInterceptors;
	}

	public Model transformSimpleSchema(Class<?> clazz, GenerationContext generationContext) {
		if (clazz.isEnum()) {
			return createEnumSchema(clazz.getEnumConstants());
		}

		ModelImpl schema = new ModelImpl();
		schema.setType("object");
		schema.setProperties(getClassProperties(clazz, generationContext));

		if (generationContext.getInheritanceMap().containsKey(clazz.getName())) {
			InheritanceInfo inheritanceInfo = generationContext.getInheritanceMap().get(clazz.getName());
			schema.setDiscriminator(inheritanceInfo.getDiscriminatorFieldName());
		}
		if (clazz.getSuperclass() != null) {
			return traverseAndAddProperties(schema, generationContext, clazz.getSuperclass());
		}
		return schema;
	}

	private Model traverseAndAddProperties(Model schema, GenerationContext generationContext, Class<?> superclass) {
		if (!isInPackagesToBeScanned(superclass, generationContext)) {
			// adding properties from parent classes is present due to swagger ui bug, after using different ui
			// this becomes relevant only for third party packages
			schema.getProperties().putAll(getClassProperties(superclass, generationContext));
			if (superclass.getSuperclass() != null && !"java.lang".equals(superclass.getSuperclass().getPackage().getName())) {
				return traverseAndAddProperties(schema, generationContext, superclass.getSuperclass());
			}
			return schema;
		} else {
			RefModel parentClassSchema = new RefModel();
			parentClassSchema.set$ref(CommonConstants.COMPONENT_REF_PREFIX + superclass.getSimpleName());

			ComposedModel composedSchema = new ComposedModel();
			composedSchema.setAllOf(Arrays.asList(parentClassSchema, schema));
			return composedSchema;
		}
	}

	private Map<String, Property> getClassProperties(Class<?> clazz, GenerationContext generationContext) {
		Map<String, Property> classPropertyMap = new HashMap<>();
		ReflectionUtils.doWithLocalFields(clazz,
										  field -> getFieldSchema(field, generationContext).ifPresent(schema -> {
											  schemaFieldInterceptors
													  .forEach(modelClassFieldInterceptor -> modelClassFieldInterceptor.intercept(clazz, field, schema));
											  classPropertyMap.put(field.getName(), schema);
										  })
		);
		return classPropertyMap;
	}

	private Optional<Property> getFieldSchema(Field field, GenerationContext generationContext) {
		if (GeneratorUtils.shouldBeIgnored(field)) {
			return Optional.empty();
		}

		Class<?> typeSignature = field.getType();
		Annotation[] annotations = field.getAnnotations();

		Optional<Property> optionalProperty;
		if (typeSignature.isPrimitive()) {
			optionalProperty = createBaseTypeSchema(field, annotations);
		} else if (typeSignature.isArray()) {
			optionalProperty = createArrayTypeSchema(generationContext, typeSignature, annotations);
		} else if (typeSignature.isAssignableFrom(List.class)) {
			if (field.getGenericType() instanceof ParameterizedType) {
				Class<?> listGenericParameter = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
				optionalProperty = Optional.of(parseArraySignature(listGenericParameter, generationContext, annotations));
			} else {
				optionalProperty = Optional.empty();
			}
		} else {
			optionalProperty = createClassRefSchema(generationContext, typeSignature, annotations);
		}
		optionalProperty.ifPresent(property -> property.setRequired(isRequired(annotations)));
		return optionalProperty;
	}

	private Optional<Property> createClassRefSchema(GenerationContext generationContext, Class<?> typeClass, Annotation[] annotations) {
		Property schema = parseClassRefTypeSignature(typeClass, annotations, generationContext);
		return Optional.ofNullable(schema);
	}

	private Optional<Property> createArrayTypeSchema(GenerationContext generationContext, Class<?> typeSignature, Annotation[] annotations) {
		Class<?> arrayComponentType = typeSignature.getComponentType();
		Property schema = parseArraySignature(arrayComponentType, generationContext, annotations);
		return Optional.ofNullable(schema);
	}

	private Optional<Property> createBaseTypeSchema(Field field, Annotation[] annotations) {
		Property schema = parseBaseTypeSignature(field.getType(), annotations);
		schema.setRequired(true);
		return Optional.ofNullable(schema);
	}

	private boolean isRequired(Annotation[] annotations) {
		return Stream.of(annotations).anyMatch(annotation -> annotation instanceof NotNull);
	}

	@Override
	protected Property createListSchema(Class<?> typeSignature, GenerationContext generationContext, Annotation[] annotations) {
		return parseArraySignature(typeSignature, generationContext, annotations);
	}

	@Override
	protected Property createRefSchema(Class<?> typeSignature, GenerationContext generationContext) {
		RefProperty schema = new RefProperty();
		if (isInPackagesToBeScanned(typeSignature, generationContext)) {
			if (generationContext.getInheritanceMap().containsKey(typeSignature.getName())) {
				InheritanceInfo inheritanceInfo = generationContext.getInheritanceMap().get(typeSignature.getName());
				try {
					String className = inheritanceInfo.getDiscriminatorClassMap().keySet().stream().findFirst().orElseThrow(RuntimeException::new);
					Class<?> clazz = Class.forName(className);
					schema.set$ref(CommonConstants.COMPONENT_REF_PREFIX + clazz.getSuperclass().getSimpleName());
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
				return schema;
			}
			schema.set$ref(CommonConstants.COMPONENT_REF_PREFIX + typeSignature.getSimpleName());
			return schema;
		}
		// fallback
		schema.setType("object");
		return schema;
	}

}

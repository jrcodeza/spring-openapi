package com.github.jrcodeza.schema.v2.generator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import com.github.jrcodeza.schema.v2.generator.interceptors.SchemaFieldInterceptor;
import com.github.jrcodeza.schema.v2.generator.model.GenerationContext;
import com.github.jrcodeza.schema.v2.generator.model.InheritanceInfo;
import com.github.jrcodeza.schema.v2.generator.util.CommonConstants;
import com.github.jrcodeza.schema.v2.generator.util.GeneratorUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.RefModel;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.UntypedProperty;

public class ComponentSchemaTransformer extends OpenApiTransformer {

	private static Logger logger = LoggerFactory.getLogger(ComponentSchemaTransformer.class);

	private final List<SchemaFieldInterceptor> schemaFieldInterceptors;

	public ComponentSchemaTransformer(List<SchemaFieldInterceptor> schemaFieldInterceptors) {
		this.schemaFieldInterceptors = schemaFieldInterceptors;
	}

	public Model transformSimpleSchema(Class<?> clazz, GenerationContext generationContext) {
		if (clazz.isEnum()) {
			return createEnumModel(clazz.getEnumConstants());
		}

		ModelImpl schema = new ModelImpl();
		schema.setType("object");
		getClassProperties(clazz, generationContext).forEach(schema::addProperty);

		if (generationContext.getInheritanceMap().containsKey(clazz.getName())) {
			InheritanceInfo inheritanceInfo = generationContext.getInheritanceMap().get(clazz.getName());
			schema.setDiscriminator(inheritanceInfo.getDiscriminatorFieldName());
		}
		if (clazz.getSuperclass() != null) {
			return traverseAndAddProperties(schema, generationContext, clazz.getSuperclass(), clazz);
		}
		return schema;
	}

	private Model traverseAndAddProperties(ModelImpl schema, GenerationContext generationContext, Class<?> superclass, Class<?> clazz) {
		if (!isInPackagesToBeScanned(superclass, generationContext)) {
			// adding properties from parent classes is present due to swagger ui bug, after using different ui
			// this becomes relevant only for third party packages
			getClassProperties(superclass, generationContext).forEach(schema::addProperty);
			if (superclass.getSuperclass() != null && !"java.lang".equals(superclass.getSuperclass().getPackage().getName())) {
				return traverseAndAddProperties(schema, generationContext, superclass.getSuperclass(), superclass);
			}
			return schema;
		} else {
			RefModel parentClassSchema = new RefModel();
			parentClassSchema.set$ref(CommonConstants.COMPONENT_REF_PREFIX + superclass.getSimpleName());

			ComposedModel composedSchema = new ComposedModel();
			composedSchema.setAllOf(Arrays.asList(parentClassSchema, schema));
			InheritanceInfo inheritanceInfo = generationContext.getInheritanceMap().get(superclass.getName());
			if (inheritanceInfo != null) {
				String discriminatorName = inheritanceInfo.getDiscriminatorClassMap().get(clazz.getName());
				composedSchema.setVendorExtension("x-discriminator-value", discriminatorName);
				composedSchema.setVendorExtension("x-ms-discriminator-value", discriminatorName);
			}
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
			optionalProperty = createBaseTypeProperty(field, annotations);
		} else if (typeSignature.isArray()) {
			optionalProperty = createArrayTypeProperty(generationContext, typeSignature, annotations);
		} else if (typeSignature.isAssignableFrom(List.class)) {
			if (field.getGenericType() instanceof ParameterizedType) {
				Class<?> listGenericParameter = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
				optionalProperty = Optional.of(parseArraySignature(listGenericParameter, generationContext, annotations));
			} else {
				optionalProperty = Optional.empty();
			}
		} else {
			optionalProperty = createClassRefProperty(generationContext, typeSignature, annotations);
		}
		optionalProperty.ifPresent(property -> property.setRequired(isRequired(annotations)));
		return optionalProperty;
	}

	private Optional<Property> createClassRefProperty(GenerationContext generationContext, Class<?> typeClass, Annotation[] annotations) {
		Property schema = createRefTypeProperty(typeClass, annotations, generationContext);
		return Optional.ofNullable(schema);
	}

	private Optional<Property> createArrayTypeProperty(GenerationContext generationContext, Class<?> typeSignature, Annotation[] annotations) {
		Class<?> arrayComponentType = typeSignature.getComponentType();
		Property schema = parseArraySignature(arrayComponentType, generationContext, annotations);
		return Optional.ofNullable(schema);
	}

	private Optional<Property> createBaseTypeProperty(Field field, Annotation[] annotations) {
		Property schema = createBaseTypeProperty(field.getType(), annotations);
		schema.setRequired(true);
		return Optional.ofNullable(schema);
	}

	private boolean isRequired(Annotation[] annotations) {
		return Stream.of(annotations).anyMatch(annotation -> annotation instanceof NotNull);
	}

	@Override
	protected Property createArrayProperty(Class<?> typeSignature, GenerationContext generationContext, Annotation[] annotations) {
		return parseArraySignature(typeSignature, generationContext, annotations);
	}

	@Override
	protected Property createRefProperty(Class<?> typeSignature, GenerationContext generationContext) {
		RefProperty schema = new RefProperty();
		if (isInPackagesToBeScanned(typeSignature, generationContext)) {
			if (generationContext.getInheritanceMap().containsKey(typeSignature.getName())) {
				InheritanceInfo inheritanceInfo = generationContext.getInheritanceMap().get(typeSignature.getName());
				try {
					String className = inheritanceInfo.getDiscriminatorClassMap().keySet().stream().findFirst().orElseThrow(RuntimeException::new);
					Class<?> clazz = Class.forName(className);
					schema.set$ref(CommonConstants.COMPONENT_REF_PREFIX + clazz.getSuperclass().getSimpleName());
				} catch (ClassNotFoundException e) {
					logger.info("Exception occurred", e);
				}
				return schema;
			}
			schema.set$ref(CommonConstants.COMPONENT_REF_PREFIX + typeSignature.getSimpleName());
			return schema;
		}
		UntypedProperty fallBack = new UntypedProperty();
		fallBack.setType("object");
		return fallBack;
	}

}

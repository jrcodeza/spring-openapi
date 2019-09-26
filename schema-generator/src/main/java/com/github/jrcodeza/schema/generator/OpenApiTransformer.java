package com.github.jrcodeza.schema.generator;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.jrcodeza.schema.generator.model.GenerationContext;
import com.github.jrcodeza.schema.generator.model.InheritanceInfo;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static com.github.jrcodeza.schema.generator.util.CommonConstants.COMPONENT_REF_PREFIX;

public abstract class OpenApiTransformer {

	private static Logger logger = LoggerFactory.getLogger(OpenApiTransformer.class);

	protected abstract ComposedSchema createRefSchema(Class<?> typeSignature, GenerationContext generationContext);
	protected abstract Schema createListSchema(Class<?> typeSignature, GenerationContext generationContext, Annotation[] annotations);

	@SuppressWarnings("squid:S1192") // better in-place defined for better readability
	protected Schema parseBaseTypeSignature(Class<?> type, Annotation[] annotations) {
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
	protected Schema parseClassRefTypeSignature(Class<?> typeClass, Annotation[] annotations, GenerationContext generationContext) {
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

	protected Schema parseArraySignature(Class<?> elementTypeSignature, GenerationContext generationContext, Annotation[] annotations) {
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
			if (generationContext != null && generationContext.getInheritanceMap() != null && generationContext.getInheritanceMap().containsKey(className)) {
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

	protected Discriminator createDiscriminator(InheritanceInfo inheritanceInfo) {
		Map<String, String> discriminatorTypeMapping = inheritanceInfo.getDiscriminatorClassMap().entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

		Discriminator discriminator = new Discriminator();
		discriminator.setPropertyName(inheritanceInfo.getDiscriminatorFieldName());
		discriminator.setMapping(discriminatorTypeMapping);
		return discriminator;
	}

	protected List<Schema> createOneOf(InheritanceInfo inheritanceInfo) {
		return inheritanceInfo.getDiscriminatorClassMap().keySet().stream()
				.map(key -> {
					Schema<?> schema = new Schema<>();
					schema.set$ref(COMPONENT_REF_PREFIX + key);
					return schema;
				})
				.collect(Collectors.toList());
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

	protected <T> StringSchema createEnumSchema(T[] enumConstants) {
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

	protected boolean isInPackagesToBeScanned(Class<?> clazz, GenerationContext generationContext) {
		return generationContext == null || generationContext.getModelPackages() == null
				|| generationContext.getModelPackages().stream().anyMatch(pkg -> clazz.getPackage().getName().startsWith(pkg));
	}

	protected void enrichWithTypeAnnotations(Schema<?> schema, Annotation[] annotations) {
		enrichWithAnnotation(io.swagger.v3.oas.annotations.media.Schema.class, annotations,
				schemaAnnotation -> {
					schema.setDeprecated(schemaAnnotation.deprecated());
					schema.setDescription(schemaAnnotation.description());
				});
		enrichWithAnnotation(Deprecated.class, annotations, deprecatedAnnotation -> schema.setDeprecated(true));
	}

	protected  <T> void enrichWithAnnotation(Class<T> annotationClazz, Annotation[] annotations, Consumer<T> consumer) {
		Stream.of(annotations)
				.filter(annotation -> annotationClazz.isAssignableFrom(annotation.getClass()))
				.map(annotationClazz::cast)
				.findFirst()
				.ifPresent(consumer);
	}
}

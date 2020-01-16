package com.github.jrcodeza.schema.v2.generator;

import io.swagger.models.ModelImpl;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.properties.AbstractNumericProperty;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.BooleanProperty;
import io.swagger.models.properties.DecimalProperty;
import io.swagger.models.properties.DoubleProperty;
import io.swagger.models.properties.FloatProperty;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.LongProperty;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import com.github.jrcodeza.schema.v2.generator.model.GenerationContext;
import com.github.jrcodeza.schema.v2.generator.util.CommonConstants;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

public abstract class OpenApiTransformer {

	private static Logger logger = LoggerFactory.getLogger(OpenApiTransformer.class);

	protected abstract Property createRefSchema(Class<?> typeSignature, GenerationContext generationContext);

	protected abstract Property createListSchema(Class<?> typeSignature, GenerationContext generationContext, Annotation[] annotations);

	@SuppressWarnings("squid:S1192") // better in-place defined for better readability
	protected Property parseBaseTypeSignature(Class<?> type, Annotation[] annotations) {
		if (byte.class.equals(type) || short.class.equals(type) || int.class.equals(type)) {
			return createNumberSchema(new IntegerProperty(), "integer", "int32", annotations);
		} else if (long.class.equals(type)) {
			return createNumberSchema(new LongProperty(), "integer", "int64", annotations);
		} else if (float.class.equals(type)) {
			return createNumberSchema(new FloatProperty(), "number", "float", annotations);
		} else if (double.class.equals(type)) {
			return createNumberSchema(new DoubleProperty(), "number", "double", annotations);
		} else if (char.class.equals(type)) {
			return createStringSchema(null, annotations);
		} else if (boolean.class.equals(type)) {
			return createBooleanSchema();
		}
		logger.info("Ignoring unsupported type=[{}]", type.getSimpleName());
		return null;
	}

	protected void parseBaseTypeParameter(QueryParameter oasParameter, Class<?> type, Annotation[] annotations) {
		if (byte.class.equals(type) || short.class.equals(type) || int.class.equals(type) || Byte.class.equals(type) ||
			Short.class.equals(type) || Integer.class.equals(type)) {
			oasParameter.setType("integer");
			oasParameter.setFormat("int32");
		} else if (long.class.equals(type) || Long.class.equals(type) || BigInteger.class.equals(type)) {
			oasParameter.setType("integer");
			oasParameter.setFormat("int64");
		} else if (float.class.equals(type) || Float.class.equals(type)) {
			oasParameter.setType("number");
			oasParameter.setFormat("float");
		} else if (double.class.equals(type) || Double.class.equals(type) || BigDecimal.class.equals(type)) {
			oasParameter.setType("number");
			oasParameter.setFormat("double");
		} else if (char.class.equals(type) || Character.class.equals(type) || String.class.equals(type)) {
			oasParameter.setType("string");
		} else if (boolean.class.equals(type) || Boolean.class.equals(type)) {
			oasParameter.setType("boolean");
		} else if (List.class.equals(type)) {
			oasParameter.setProperty(createListSchema(type, null, annotations));
		} else if (LocalDate.class.equals(type) || Date.class.equals(type)) {
			oasParameter.setType("string");
			oasParameter.setFormat("date");
		} else if (LocalDateTime.class.equals(type) || LocalTime.class.equals(type)) {
			oasParameter.setType("string");
			oasParameter.setFormat("date-time");
		}  else if (type.isEnum()) {
			oasParameter.setType("string");
			oasParameter.setEnumValue(asList(type.getEnumConstants()));
		} else {
			oasParameter.setProperty(createRefSchema(type, null));
		}
		asList(annotations).forEach(annotation -> applyAnnotationOnParameter(oasParameter, annotation));
	}

	@SuppressWarnings("squid:S3776") // no other solution
	protected Property parseClassRefTypeSignature(Class<?> typeClass, Annotation[] annotations, GenerationContext generationContext) {
		if (Byte.class.equals(typeClass) || Short.class.equals(typeClass) || Integer.class.equals(typeClass)) {
			return createNumberSchema(new IntegerProperty(), "integer", "int32", annotations);
		} else if (Long.class.equals(typeClass) || BigInteger.class.equals(typeClass)) {
			return createNumberSchema(new LongProperty(), "integer", "int64", annotations);
		} else if (Float.class.equals(typeClass)) {
			return createNumberSchema(new FloatProperty(), "number", "float", annotations);
		} else if (Double.class.equals(typeClass) || BigDecimal.class.equals(typeClass)) {
			return createNumberSchema(new DoubleProperty(), "number", "double", annotations);
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

	protected Property parseArraySignature(Class<?> elementTypeSignature, GenerationContext generationContext, Annotation[] annotations) {
		ArrayProperty arraySchema = new ArrayProperty();
		if (elementTypeSignature == null) {
			arraySchema.setItems(createObjectSchema());
			return arraySchema;
		}
		Stream.of(annotations).forEach(annotation -> applyArrayAnnotations(arraySchema, annotation));
		if (elementTypeSignature.isPrimitive()) {
			// primitive type like int
			Property property = mapBaseType(elementTypeSignature);
			if (property == null) {
				throw new IllegalArgumentException(format("Unsupported base type=[%s]", elementTypeSignature.getSimpleName()));
			}
			arraySchema.setItems(property);
			return arraySchema;
		} else if (isInPackagesToBeScanned(elementTypeSignature, generationContext) || elementTypeSignature.getPackage().getName().startsWith("java.lang")) {
			Property property = mapBaseType(elementTypeSignature);
			// basic types like Integer or String
			if (property != null) {
				arraySchema.setItems(property);
				return arraySchema;
			}
			String className = elementTypeSignature.getName();
			// is inheritance needed
			if (generationContext != null && generationContext.getInheritanceMap() != null && generationContext.getInheritanceMap().containsKey(className)) {
				RefProperty itemSchema = new RefProperty();
				itemSchema.set$ref(CommonConstants.COMPONENT_REF_PREFIX + elementTypeSignature.getSimpleName());
				arraySchema.setItems(itemSchema);
				return arraySchema;
			}
			// else do ref
			RefProperty itemSchema = new RefProperty();
			itemSchema.set$ref(CommonConstants.COMPONENT_REF_PREFIX + elementTypeSignature.getSimpleName());
			arraySchema.setItems(itemSchema);
			return arraySchema;
		}

		ObjectProperty itemSchema = new ObjectProperty();
		itemSchema.setType("object");
		arraySchema.setItems(itemSchema);
		return arraySchema;
	}

	private Property createObjectSchema() {
		ObjectProperty schema = new ObjectProperty();
		schema.setType("object");
		return schema;
	}

	@SuppressWarnings("squid:S1192") // better in-place defined for better readability
	protected Property createBooleanSchema() {
		BooleanProperty schema = new BooleanProperty();
		schema.setType("boolean");
		return schema;
	}

	@SuppressWarnings("squid:S1192") // better in-place defined for better readability
	protected Property createStringSchema(String format, Annotation[] annotations) {
		StringProperty schema = new StringProperty();
		schema.setType("string");
		if (StringUtils.isNotBlank(format)) {
			schema.setFormat(format);
		}
		asList(annotations).forEach(annotation -> applyStringAnnotations(schema, annotation));
		return schema;
	}

	protected <T> ModelImpl createEnumSchema(T[] enumConstants) {
		ModelImpl schema = new ModelImpl();
		schema.setType("string");
		schema.setEnum(Stream.of(enumConstants).map(Object::toString).collect(toList()));
		return schema;
	}

	protected Property createNumberSchema(AbstractNumericProperty property, String type, String format, Annotation[] annotations) {
		property.setType(type);
		property.setFormat(format);
		asList(annotations).forEach(annotation -> applyNumberAnnotation(property, annotation));
		return property;
	}

	protected void applyStringAnnotations(StringProperty schema, Annotation annotation) {
		if (annotation instanceof Pattern) {
			schema.pattern(((Pattern) annotation).regexp());
		} else if (annotation instanceof Size) {
			schema.minLength(((Size) annotation).min());
			schema.maxLength(((Size) annotation).max());
		} else if (annotation instanceof Deprecated) {
			schema.setVendorExtension("x-deprecated", true);
		}
	}

	protected void applyNumberAnnotation(AbstractNumericProperty schema, Annotation annotation) {
		if (annotation instanceof DecimalMin) {
			schema.setMinimum(new BigDecimal(((DecimalMin) annotation).value()));
		} else if (annotation instanceof DecimalMax) {
			schema.setMaximum(new BigDecimal(((DecimalMax) annotation).value()));
		} else if (annotation instanceof Min) {
			schema.setMinimum(BigDecimal.valueOf(((Min) annotation).value()));
		} else if (annotation instanceof Max) {
			schema.setMaximum(BigDecimal.valueOf(((Max) annotation).value()));
		} else if (annotation instanceof Deprecated) {
			schema.setVendorExtension("x-deprecated", true);
		}
	}

	protected void applyAnnotationOnParameter(AbstractSerializableParameter schema, Annotation annotation) {
		if (annotation instanceof DecimalMin) {
			schema.setMinimum(new BigDecimal(((DecimalMin) annotation).value()));
		} else if (annotation instanceof DecimalMax) {
			schema.setMaximum(new BigDecimal(((DecimalMax) annotation).value()));
		} else if (annotation instanceof Min) {
			schema.setMinimum(BigDecimal.valueOf(((Min) annotation).value()));
		} else if (annotation instanceof Max) {
			schema.setMaximum(BigDecimal.valueOf(((Max) annotation).value()));
		}
		if (annotation instanceof Pattern) {
			schema.setPattern(((Pattern) annotation).regexp());
		} else if (annotation instanceof Size) {
			schema.setMinLength(((Size) annotation).min());
			schema.setMaxLength(((Size) annotation).max());
		} else if (annotation instanceof Deprecated) {
			schema.setVendorExtension("x-deprecated", true);
		}
	}

	protected void applyArrayAnnotations(ArrayProperty schema, Annotation annotation) {
		if (annotation instanceof Size) {
			schema.setMinItems(((Size) annotation).min());
			schema.setMaxItems(((Size) annotation).max());
		} else if (annotation instanceof Deprecated) {
			schema.setVendorExtension("x-deprecated", true);
		}
	}

	protected Property mapBaseType(Class<?> elementTypeSignature) {
		if (byte.class.equals(elementTypeSignature) || short.class.equals(elementTypeSignature) || int.class.equals(elementTypeSignature)
			|| long.class.equals(elementTypeSignature) || Byte.class.equals(elementTypeSignature) || Short.class.equals(elementTypeSignature)
			|| Integer.class.equals(elementTypeSignature) || Long.class.equals(elementTypeSignature) || BigInteger.class.equals(elementTypeSignature)) {
			IntegerProperty property = new IntegerProperty();
			property.setType("integer");
			return property;
		} else if (float.class.equals(elementTypeSignature) || Float.class.equals(elementTypeSignature)) {
			FloatProperty property = new FloatProperty();
			property.setType("float");
			return property;
		} else if (double.class.equals(elementTypeSignature) || Double.class.equals(elementTypeSignature)) {
			DoubleProperty property = new DoubleProperty();
			property.setType("double");
			return property;
		} else if (BigDecimal.class.equals(elementTypeSignature)) {
			DecimalProperty property = new DecimalProperty();
			property.setType("decimal");
			return property;

		} else if (List.class.equals(elementTypeSignature)) {
			throw new IllegalArgumentException("Nested List types are not supported"
											   + elementTypeSignature.getName()
			);
		} else {
			if (char.class.equals(elementTypeSignature) || Character.class.equals(elementTypeSignature) || String.class.equals(elementTypeSignature)
				|| LocalDate.class.equals(elementTypeSignature) || Date.class.equals(elementTypeSignature)
				|| LocalDateTime.class.equals(elementTypeSignature) || LocalTime.class.equals(elementTypeSignature)) {
				StringProperty property = new StringProperty();
				property.setType("string");
				return property;
			} else if (elementTypeSignature.isEnum()) {
				StringProperty property = new StringProperty();
				property.setType("string");
				property.setEnum(Arrays.stream(elementTypeSignature.getEnumConstants()).map(Object::toString).collect(toList()));
				return property;
			} else if (boolean.class.equals(elementTypeSignature) || Boolean.class.equals(elementTypeSignature)) {
				BooleanProperty property = new BooleanProperty();
				property.setType("boolean");
				return property;
			}
		}
		return null;
	}

	protected boolean isInPackagesToBeScanned(Class<?> clazz, GenerationContext generationContext) {
		return generationContext == null || generationContext.getModelPackages() == null
			   || generationContext.getModelPackages().stream().anyMatch(pkg -> clazz.getPackage().getName().startsWith(pkg));
	}
}

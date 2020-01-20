package com.github.jrcodeza.schema.v2.generator;

import io.swagger.models.ModelImpl;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.properties.AbstractNumericProperty;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.BooleanProperty;
import io.swagger.models.properties.DateProperty;
import io.swagger.models.properties.DateTimeProperty;
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

	protected abstract Property createRefProperty(Class<?> typeSignature, GenerationContext generationContext);

	protected abstract Property createArrayProperty(Class<?> typeSignature, GenerationContext generationContext, Annotation[] annotations);

	@SuppressWarnings("squid:S1192") // better in-place defined for better readability
	protected Property createBaseTypeProperty(Class<?> type, Annotation[] annotations) {
		if (byte.class.equals(type) || short.class.equals(type) || int.class.equals(type)) {
			return createNumberSchema(new IntegerProperty(), annotations);
		} else if (long.class.equals(type)) {
			return createNumberSchema(new LongProperty(), annotations);
		} else if (float.class.equals(type)) {
			return createNumberSchema(new FloatProperty().vendorExtension("x-type", "System.BigDecimal"), annotations);
		} else if (double.class.equals(type)) {
			return createNumberSchema(new DoubleProperty().vendorExtension("x-type", "System.BigDecimal"), annotations);
		} else if (char.class.equals(type)) {
			return createStringProperty(null, annotations);
		} else if (boolean.class.equals(type)) {
			return new BooleanProperty();
		}
		logger.info("Ignoring unsupported type=[{}]", type.getSimpleName());
		return null;
	}

	protected void setParameterDetails(AbstractSerializableParameter<?> oasParameter, Class<?> type, Annotation[] annotations) {
		if (byte.class.equals(type) || short.class.equals(type) || int.class.equals(type) || Byte.class.equals(type) ||
			Short.class.equals(type) || Integer.class.equals(type)) {
			oasParameter.setProperty(new IntegerProperty());
		} else if (long.class.equals(type) || Long.class.equals(type) || BigInteger.class.equals(type)) {
			oasParameter.setProperty(new LongProperty());
		} else if (float.class.equals(type) || Float.class.equals(type)) {
			oasParameter.setProperty(new FloatProperty().vendorExtension("x-type", "System.BigDecimal"));
		} else if (double.class.equals(type) || Double.class.equals(type) || BigDecimal.class.equals(type)) {
			oasParameter.setProperty(new DoubleProperty().vendorExtension("x-type", "System.BigDecimal"));
		} else if (char.class.equals(type) || Character.class.equals(type) || String.class.equals(type)) {
			oasParameter.setProperty(new StringProperty());
		} else if (boolean.class.equals(type) || Boolean.class.equals(type)) {
			oasParameter.setProperty(new BooleanProperty());
		} else if (List.class.equals(type)) {
			oasParameter.setProperty(createArrayProperty(type, null, annotations));
		} else if (LocalDate.class.equals(type) || Date.class.equals(type)) {
			oasParameter.setProperty(new DateProperty());
		} else if (LocalDateTime.class.equals(type) || LocalTime.class.equals(type)) {
			oasParameter.setProperty(new DateTimeProperty());
		}  else if (type.isEnum()) {
			oasParameter.setType("string");
			oasParameter.setEnumValue(asList(type.getEnumConstants()));
		} else {
			oasParameter.setProperty(createRefProperty(type, null));
		}
		asList(annotations).forEach(annotation -> applyAnnotationDetailsOnParameter(oasParameter, annotation));
	}

	@SuppressWarnings("squid:S3776") // no other solution
	protected Property createRefTypeProperty(Class<?> typeClass, Annotation[] annotations, GenerationContext generationContext) {
		if (Byte.class.equals(typeClass) || Short.class.equals(typeClass) || Integer.class.equals(typeClass)) {
			return createNumberSchema(new IntegerProperty(), annotations);
		} else if (Long.class.equals(typeClass) || BigInteger.class.equals(typeClass)) {
			return createNumberSchema(new LongProperty(), annotations);
		} else if (Float.class.equals(typeClass)) {
			return createNumberSchema(new FloatProperty().vendorExtension("x-type", "System.BigDecimal"), annotations);
		} else if (Double.class.equals(typeClass) || BigDecimal.class.equals(typeClass)) {
			return createNumberSchema(new DoubleProperty().vendorExtension("x-type", "System.BigDecimal"), annotations);
		} else if (Character.class.equals(typeClass) || String.class.equals(typeClass)) {
			return createStringProperty(null, annotations);
		} else if (Boolean.class.equals(typeClass)) {
			return new BooleanProperty();
		} else if (List.class.equals(typeClass)) {
			return createArrayProperty(typeClass, generationContext, annotations);
		} else if (LocalDate.class.equals(typeClass) || Date.class.equals(typeClass)) {
			return createStringProperty("date", annotations);
		} else if (LocalDateTime.class.equals(typeClass) || LocalTime.class.equals(typeClass)) {
			return createStringProperty("date-time", annotations);
		}
		return createRefProperty(typeClass, generationContext);
	}

	protected Property parseArraySignature(Class<?> elementTypeSignature, GenerationContext generationContext, Annotation[] annotations) {
		ArrayProperty arraySchema = new ArrayProperty();
		if (elementTypeSignature == null) {
			arraySchema.setItems(new ObjectProperty());
			return arraySchema;
		}
		Stream.of(annotations).forEach(annotation -> applyArrayAnnotationDetails(arraySchema, annotation));
		if (elementTypeSignature.isPrimitive()) {
			// primitive type like int
			Property property = createProperty(elementTypeSignature);
			if (property == null) {
				throw new IllegalArgumentException(format("Unsupported base type=[%s]", elementTypeSignature.getSimpleName()));
			}
			arraySchema.setItems(property);
			return arraySchema;
		} else if (isInPackagesToBeScanned(elementTypeSignature, generationContext) || elementTypeSignature.getPackage().getName().startsWith("java.lang")) {
			Property property = createProperty(elementTypeSignature);
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
		arraySchema.setItems(itemSchema);
		return arraySchema;
	}

	@SuppressWarnings("squid:S1192") // better in-place defined for better readability
	protected Property createStringProperty(String format, Annotation[] annotations) {
		StringProperty schema = new StringProperty();
		if (StringUtils.isNotBlank(format)) {
			schema.setFormat(format);
		}
		asList(annotations).forEach(annotation -> applyStringAnnotationDetails(schema, annotation));
		return schema;
	}

	protected <T> ModelImpl createEnumModel(T[] enumConstants) {
		ModelImpl schema = new ModelImpl();
		schema.setType("string");
		schema.setEnum(Stream.of(enumConstants).map(Object::toString).collect(toList()));
		return schema;
	}

	protected Property createNumberSchema(AbstractNumericProperty property, Annotation[] annotations) {
		asList(annotations).forEach(annotation -> applyNumberAnnotationDetails(property, annotation));
		return property;
	}

	protected void applyStringAnnotationDetails(StringProperty schema, Annotation annotation) {
		if (annotation instanceof Pattern) {
			schema.pattern(((Pattern) annotation).regexp());
		} else if (annotation instanceof Size) {
			schema.minLength(((Size) annotation).min());
			schema.maxLength(((Size) annotation).max());
		} else if (annotation instanceof Deprecated) {
			schema.setVendorExtension("x-deprecated", true);
		}
	}

	protected void applyNumberAnnotationDetails(AbstractNumericProperty schema, Annotation annotation) {
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

	protected void applyAnnotationDetailsOnParameter(AbstractSerializableParameter schema, Annotation annotation) {
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

	protected void applyArrayAnnotationDetails(ArrayProperty schema, Annotation annotation) {
		if (annotation instanceof Size) {
			schema.setMinItems(((Size) annotation).min());
			schema.setMaxItems(((Size) annotation).max());
		} else if (annotation instanceof Deprecated) {
			schema.setVendorExtension("x-deprecated", true);
		}
	}

	protected Property createProperty(Class<?> elementTypeSignature) {
		if (byte.class.equals(elementTypeSignature) || short.class.equals(elementTypeSignature) || int.class.equals(elementTypeSignature)
			|| long.class.equals(elementTypeSignature) || Byte.class.equals(elementTypeSignature) || Short.class.equals(elementTypeSignature)
			|| Integer.class.equals(elementTypeSignature) || Long.class.equals(elementTypeSignature) || BigInteger.class.equals(elementTypeSignature)) {
			return new IntegerProperty();
		} else if (float.class.equals(elementTypeSignature) || Float.class.equals(elementTypeSignature)) {
			return new FloatProperty().vendorExtension("x-type", "System.BigDecimal");
		} else if (double.class.equals(elementTypeSignature) || Double.class.equals(elementTypeSignature) || BigDecimal.class.equals(elementTypeSignature)) {
			return new DoubleProperty().vendorExtension("x-type", "System.BigDecimal");
		} else if (List.class.equals(elementTypeSignature)) {
			throw new IllegalArgumentException("Nested List types are not supported"
											   + elementTypeSignature.getName()
			);
		} else {
			if (char.class.equals(elementTypeSignature) || Character.class.equals(elementTypeSignature) || String.class.equals(elementTypeSignature)
				|| LocalDate.class.equals(elementTypeSignature) || Date.class.equals(elementTypeSignature)
				|| LocalDateTime.class.equals(elementTypeSignature) || LocalTime.class.equals(elementTypeSignature)) {
				return new StringProperty();
			} else if (elementTypeSignature.isEnum()) {
				StringProperty property = new StringProperty();
				property.setEnum(Arrays.stream(elementTypeSignature.getEnumConstants()).map(Object::toString).collect(toList()));
				return property;
			} else if (boolean.class.equals(elementTypeSignature) || Boolean.class.equals(elementTypeSignature)) {
				return new BooleanProperty();
			}
		}
		return null;
	}

	protected boolean isInPackagesToBeScanned(Class<?> clazz, GenerationContext generationContext) {
		return generationContext == null || generationContext.getModelPackages() == null
			   || generationContext.getModelPackages().stream().anyMatch(pkg -> clazz.getPackage().getName().startsWith(pkg));
	}
}

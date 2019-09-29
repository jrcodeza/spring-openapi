package org.spring.openapi.client.generator;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

public final class ClientGeneratorUtils {

	public static final String JAVA_LANG_PKG = "java.lang";
	public static final String JAVA_TIME_PKG = "java.time";

	public static void buildTypeSpec(String targetPackage, TypeSpec.Builder typeSpecBuilder, String outputPath) {
		try {
			JavaFile.builder(targetPackage, typeSpecBuilder.build())
					.build()
					.writeTo(new File(outputPath));
		} catch (IOException e) {
			e.printStackTrace(); // TODO
		}
	}

	private ClientGeneratorUtils() {
		throw new AssertionError();
	}

	public static String getNameFromRef(String ref) {
		return ref.replace("#/components/schemas/", "");
	}

	public static ClassName getStringGenericClassName(Schema<?> genericSchema) {
		if (genericSchema.getFormat() == null) {
			return ClassName.get(JAVA_LANG_PKG, "String");
		} else if (equalsIgnoreCase(genericSchema.getFormat(), "date")) {
			return ClassName.get(JAVA_TIME_PKG, "LocalDate");
		} else if (equalsIgnoreCase(genericSchema.getFormat(), "date-time")) {
			return ClassName.get(JAVA_TIME_PKG, "LocalDateTime");
		}
		throw new IllegalArgumentException("Error parsing string based property");
	}

	public static ClassName getNumberGenericClassName(Schema<?> genericSchema) {
		if (genericSchema.getFormat() == null || StringUtils.equalsIgnoreCase(genericSchema.getFormat(), "int32")) {
			return ClassName.get(JAVA_LANG_PKG, "Integer");
		} else if (StringUtils.equalsIgnoreCase(genericSchema.getFormat(), "int64")) {
			return ClassName.get(JAVA_LANG_PKG, "Long");
		} else if (StringUtils.equalsIgnoreCase(genericSchema.getFormat(), "float")) {
			return ClassName.get(JAVA_LANG_PKG, "Float");
		} else if (StringUtils.equalsIgnoreCase(genericSchema.getFormat(), "double")) {
			return ClassName.get(JAVA_LANG_PKG, "Double");
		} else {
			return ClassName.get(JAVA_LANG_PKG, "Integer");
		}
	}

	public static String determineParentClassNameUsingDiscriminator(Schema innerSchema, String fieldName, Map<String, Schema> allComponents) {
		Set<Map.Entry<String,String>> discriminatorEntries = innerSchema.getDiscriminator().getMapping().entrySet();
		if (CollectionUtils.isEmpty(discriminatorEntries)) {
			throw new IllegalArgumentException("Discriminator needs to have at least one value defined. Field: " + fieldName);
		}
		return determineParentClassName(discriminatorEntries.iterator().next().getValue(), allComponents);
	}

	public static String determineParentClassName(String childClassToFind, Map<String, Schema> allComponents) {
		Schema childClass = allComponents.get(childClassToFind);
		if (childClass instanceof ComposedSchema) {
			ComposedSchema childClassComposed = (ComposedSchema) childClass;
			if (CollectionUtils.isNotEmpty(childClassComposed.getAllOf())) {
				String parentClassRef = childClassComposed.getAllOf().get(0).get$ref();
				if (parentClassRef == null) {
					throw new IllegalArgumentException("Unsupported inheritance model. AllOf $ref for parent class has to be defined");
				}
				return getNameFromRef(parentClassRef);
			}
		}
		throw new IllegalArgumentException("Unsupported inheritance model for " + (childClass == null ? "null" : childClass.getName()));
	}

	public static String determineParentClassNameUsingOneOf(Schema innerSchema, String fieldName, Map<String, Schema> allComponents) {
		if (!(innerSchema instanceof ComposedSchema)) {
			throw new IllegalArgumentException("To determine class name using allOf schema has to be Composed");
		}
		List<Schema> allOf = ((ComposedSchema) innerSchema).getAllOf();
		String refToOneOf = allOf.get(0).get$ref();
		if (refToOneOf == null) {
			throw new IllegalArgumentException("OneOf entry needs to have defined $ref. Field: " + fieldName);
		}
		return determineParentClassName(getNameFromRef(refToOneOf), allComponents);
	}


}

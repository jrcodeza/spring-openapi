package org.spring.openapi.client.generator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import io.swagger.v3.oas.integration.IntegrationObjectMapperFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

public class OpenApiClientGenerator {

	private static final String JAVA_LANG_PKG = "java.lang";
	private static final String JAVA_TIME_PKG = "java.time";

	private Map<String, Schema> allComponents;

	public void generateClient(String targetPackage, File openApiJson) {
		try {
			OpenAPI openAPI = IntegrationObjectMapperFactory.createJson().readValue(openApiJson, OpenAPI.class);
			allComponents = openAPI.getComponents().getSchemas();
			allComponents.entrySet().forEach(schemaEntry -> processSchemaEntry(targetPackage, schemaEntry));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void processSchemaEntry(String targetPackage, Map.Entry<String, Schema> schemaEntry) {
		Schema schema = schemaEntry.getValue();
		if (schema.getEnum() != null) {
			TypeSpec.Builder typeSpecBuilder = createEnumClass(schemaEntry.getKey(), schema);
			buildTypeSpec(targetPackage, typeSpecBuilder);
			return;
		}
		TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(schemaEntry.getKey()).addModifiers(Modifier.PUBLIC);
		if (schema instanceof ComposedSchema) {
			ComposedSchema composedSchema = (ComposedSchema) schema;
			if (CollectionUtils.isNotEmpty(composedSchema.getAllOf())) {
				typeSpecBuilder
						.superclass(ClassName.get(targetPackage, getNameFromRef(composedSchema.getAllOf().get(0).get$ref())))
						.build();
			}
			schema = composedSchema.getAllOf().get(1);
		}
		if (schema.getDiscriminator() != null) {
			parseDiscriminator(typeSpecBuilder, schema.getDiscriminator(), targetPackage);
		}
		if (schema.getProperties() != null) {
			parseProperties(typeSpecBuilder, schema.getProperties(), targetPackage, schema.getRequired());
		}

		buildTypeSpec(targetPackage, typeSpecBuilder);
	}

	private void buildTypeSpec(String targetPackage, TypeSpec.Builder typeSpecBuilder) {
		try {
			JavaFile.builder(targetPackage, typeSpecBuilder.build())
					.build()
					.writeTo(Paths.get("C:\\Users\\jremenec.DAVINCI\\Projects\\spring-openapi\\client-generator\\target\\openapi"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parseProperties(TypeSpec.Builder typeSpecBuilder, Map<String, Schema> properties, String targetPackage, List<String> requiredFields) {
		for (Map.Entry<String, Schema> propertyEntry :  properties.entrySet()) {
			// type or ref or oneOf + (discriminator)
			Schema innerSchema = propertyEntry.getValue();
			FieldSpec.Builder fieldSpecBuilder;
			if (innerSchema.getType() != null) {
				fieldSpecBuilder = parseTypeBasedSchema(propertyEntry.getKey(), innerSchema, targetPackage, typeSpecBuilder);
			} else if (innerSchema.get$ref() != null) {
				// simple no inheritance
				fieldSpecBuilder = createSimpleFieldSpec(targetPackage, getNameFromRef(innerSchema.get$ref()), propertyEntry.getKey(), typeSpecBuilder);
			} else if (innerSchema instanceof ComposedSchema && CollectionUtils.isNotEmpty(((ComposedSchema) innerSchema).getAllOf())) {
				fieldSpecBuilder = createSimpleFieldSpec(targetPackage, determineParentClassNameUsingOneOf(innerSchema, propertyEntry.getKey()), propertyEntry.getKey(), typeSpecBuilder);
			} else if (innerSchema.getDiscriminator() != null) {
				// complicated inheritance - identify target class
				fieldSpecBuilder = createSimpleFieldSpec(targetPackage, determineParentClassNameUsingDiscriminator(innerSchema, propertyEntry.getKey()), propertyEntry.getKey(), typeSpecBuilder);
			} else {
				throw new IllegalArgumentException("Incorrect schema. One of [type, $ref, discriminator+oneOf] has to be defined in property schema");
			}
			if (requiredFields != null && requiredFields.contains(propertyEntry.getKey())) {
				fieldSpecBuilder.addAnnotation(NotNull.class);
			}
			if (fieldSpecBuilder != null) {
				typeSpecBuilder.addField(fieldSpecBuilder.build());
			}
		}
	}

	private String determineParentClassNameUsingOneOf(Schema innerSchema, String fieldName) {
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

	private String determineParentClassNameUsingDiscriminator(Schema innerSchema, String fieldName) {
		Set<Map.Entry<String,String>> discriminatorEntries = innerSchema.getDiscriminator().getMapping().entrySet();
		if (CollectionUtils.isEmpty(discriminatorEntries)) {
			throw new IllegalArgumentException("Discriminator needs to have at least one value defined. Field: " + fieldName);
		}
		return determineParentClassName(discriminatorEntries.iterator().next().getValue(), allComponents);
	}

	private String determineParentClassName(String childClassToFind, Map<String, Schema> allComponents) {
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

	private FieldSpec.Builder parseTypeBasedSchema(String fieldName, Schema innerSchema, String targetPackage,
										   TypeSpec.Builder typeSpecBuilder) {
		if (innerSchema.getEnum() != null) {
			typeSpecBuilder.addType(createEnumClass(StringUtils.capitalize(fieldName), innerSchema).build());
			return createSimpleFieldSpec(targetPackage, StringUtils.capitalize(fieldName), fieldName, typeSpecBuilder);
		}
		if (equalsIgnoreCase(innerSchema.getType(), "string")) {
			return getStringBasedSchemaField(fieldName, innerSchema, typeSpecBuilder);
		} else if (equalsIgnoreCase(innerSchema.getType(), "integer") || equalsIgnoreCase(innerSchema.getType(), "number")) {
			return getNumberBasedSchemaField(fieldName, innerSchema, typeSpecBuilder);
		} else if (equalsIgnoreCase(innerSchema.getType(), "boolean")) {
			return createSimpleFieldSpec(JAVA_LANG_PKG, "Boolean", fieldName, typeSpecBuilder);
		} else if (equalsIgnoreCase(innerSchema.getType(), "array") && innerSchema instanceof ArraySchema) {
			ArraySchema arraySchema = (ArraySchema) innerSchema;
			ParameterizedTypeName listParameterizedTypeName = ParameterizedTypeName.get(
					ClassName.get("java.util", "List"),
					determineTypeName(arraySchema.getItems(), targetPackage)
			);
			enrichWithGetSetList(typeSpecBuilder, listParameterizedTypeName, fieldName);
			return FieldSpec.builder(listParameterizedTypeName, fieldName, Modifier.PRIVATE);
		}
		return createSimpleFieldSpec(JAVA_LANG_PKG, "Object", fieldName, typeSpecBuilder);
	}

	private ClassName determineTypeName(Schema<?> arrayItemsSchema, String targetPackage) {
		if (equalsIgnoreCase(arrayItemsSchema.getType(), "string")) {
			return getStringGenericClassName(arrayItemsSchema);
		} else if (equalsIgnoreCase(arrayItemsSchema.getType(), "integer") || equalsIgnoreCase(arrayItemsSchema.getType(), "number")) {
			return getNumberGenericClassName(arrayItemsSchema);
		} else if (equalsIgnoreCase(arrayItemsSchema.getType(), "boolean")) {
			return ClassName.get(JAVA_LANG_PKG, "Boolean");
		} else if (arrayItemsSchema.get$ref() != null) {
			return ClassName.get(targetPackage, getNameFromRef(arrayItemsSchema.get$ref()));
		} else if (arrayItemsSchema instanceof ComposedSchema && CollectionUtils.isNotEmpty(((ComposedSchema) arrayItemsSchema).getAllOf())) {
			return ClassName.get(targetPackage, determineParentClassNameUsingOneOf(arrayItemsSchema, "innerArray"));
		} else if (arrayItemsSchema.getDiscriminator() != null) {
			return ClassName.get(targetPackage, determineParentClassNameUsingDiscriminator(arrayItemsSchema, "innerArray"));
		}
		return ClassName.get(JAVA_LANG_PKG, "Object");
	}

	private ClassName getStringGenericClassName(Schema<?> genericSchema) {
		if (genericSchema.getFormat() == null) {
			return ClassName.get(JAVA_LANG_PKG, "String");
		} else if (equalsIgnoreCase(genericSchema.getFormat(), "date")) {
			return ClassName.get(JAVA_TIME_PKG, "LocalDate");
		} else if (equalsIgnoreCase(genericSchema.getFormat(), "date-time")) {
			return ClassName.get(JAVA_TIME_PKG, "LocalDateTime");
		}
		throw new IllegalArgumentException("Error parsing string based property");
	}

	private ClassName getNumberGenericClassName(Schema<?> genericSchema) {
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

	private TypeSpec.Builder createEnumClass(String name, Schema schema) {
		TypeSpec.Builder typeSpecBuilder = TypeSpec.enumBuilder(name).addModifiers(Modifier.PUBLIC);
		schema.getEnum().forEach(enumConstant -> typeSpecBuilder.addEnumConstant(String.valueOf(enumConstant)));
		return typeSpecBuilder;
	}

	private FieldSpec.Builder getNumberBasedSchemaField(String fieldName, Schema innerSchema, TypeSpec.Builder typeSpecBuilder) {
		FieldSpec.Builder fieldBuilder = createNumberBasedFieldWithFormat(fieldName, innerSchema, typeSpecBuilder);
		if (innerSchema.getMinimum() != null) {
			fieldBuilder.addAnnotation(AnnotationSpec.builder(DecimalMin.class)
					.addMember("value", "$S", innerSchema.getMinimum().toString())
					.build()
			);
		}
		if (innerSchema.getMaximum() != null) {
			fieldBuilder.addAnnotation(AnnotationSpec.builder(DecimalMax.class)
					.addMember("value", "$S", innerSchema.getMaximum().toString())
					.build()
			);
		}
		return fieldBuilder;
	}

	private FieldSpec.Builder createNumberBasedFieldWithFormat(String fieldName, Schema innerSchema, TypeSpec.Builder typeSpecBuilder) {
		if (innerSchema.getFormat() == null || StringUtils.equalsIgnoreCase(innerSchema.getFormat(), "int32")) {
			return createSimpleFieldSpec(JAVA_LANG_PKG, "Integer", fieldName, typeSpecBuilder);
		} else if (StringUtils.equalsIgnoreCase(innerSchema.getFormat(), "int64")) {
			return createSimpleFieldSpec(JAVA_LANG_PKG, "Long", fieldName, typeSpecBuilder);
		} else if (StringUtils.equalsIgnoreCase(innerSchema.getFormat(), "float")) {
			return createSimpleFieldSpec(JAVA_LANG_PKG, "Float", fieldName, typeSpecBuilder);
		} else if (StringUtils.equalsIgnoreCase(innerSchema.getFormat(), "double")) {
			return createSimpleFieldSpec(JAVA_LANG_PKG, "Double", fieldName, typeSpecBuilder);
		} else {
			return createSimpleFieldSpec(JAVA_LANG_PKG, "Integer", fieldName, typeSpecBuilder);
		}
	}

	private FieldSpec.Builder createSimpleFieldSpec(String packageName, String className, String fieldName, TypeSpec.Builder typeSpecBuilder) {
		ClassName simpleFieldClassName = ClassName.get(packageName, className);
		if (typeSpecBuilder != null) {
			enrichWithGetSet(typeSpecBuilder, simpleFieldClassName, fieldName);
		}
		return FieldSpec.builder(simpleFieldClassName, fieldName, Modifier.PRIVATE);
	}

	private FieldSpec.Builder getStringBasedSchemaField(String fieldName, Schema innerSchema, TypeSpec.Builder typeSpecBuilder) {
		if (innerSchema.getFormat() == null) {
			ClassName stringClassName = ClassName.get(JAVA_LANG_PKG, "String");
			FieldSpec.Builder fieldBuilder = FieldSpec.builder(stringClassName, fieldName, Modifier.PRIVATE);
			if (innerSchema.getPattern() != null) {
				fieldBuilder.addAnnotation(AnnotationSpec.builder(Pattern.class)
						.addMember("regexp", "$S", innerSchema.getPattern())
						.build()
				);
			}
			if (innerSchema.getMinLength() != null || innerSchema.getMaxLength() != null) {
				AnnotationSpec.Builder sizeAnnotationBuilder = AnnotationSpec.builder(Size.class);
				if (innerSchema.getMinLength() != null) {
					sizeAnnotationBuilder.addMember("min", "$L", innerSchema.getMinLength());
				}
				if (innerSchema.getMaxLength() != null) {
					sizeAnnotationBuilder.addMember("max", "$L", innerSchema.getMaxLength());
				}
				fieldBuilder.addAnnotation(sizeAnnotationBuilder.build());
			}
			enrichWithGetSet(typeSpecBuilder, stringClassName, fieldName);
			return fieldBuilder;
		} else if (equalsIgnoreCase(innerSchema.getFormat(), "date")) {
			ClassName localDateClassName = ClassName.get(JAVA_TIME_PKG, "LocalDate");
			enrichWithGetSet(typeSpecBuilder, localDateClassName, fieldName);
			return FieldSpec.builder(localDateClassName, fieldName, Modifier.PRIVATE);
		} else if (equalsIgnoreCase(innerSchema.getFormat(), "date-time")) {
			ClassName localDateTimeClassName = ClassName.get(JAVA_TIME_PKG, "LocalDateTime");
			enrichWithGetSet(typeSpecBuilder, localDateTimeClassName, fieldName);
			return FieldSpec.builder(localDateTimeClassName, fieldName, Modifier.PRIVATE);
		}
		throw new IllegalArgumentException(String.format("Error parsing string based property [%s]", fieldName));
	}

	private void enrichWithGetSet(TypeSpec.Builder typeSpecBuilder, ClassName className, String fieldName) {
		typeSpecBuilder.addMethod(MethodSpec.methodBuilder("set" + StringUtils.capitalize(fieldName))
				.addParameter(ParameterSpec.builder(className, fieldName).build())
				.addModifiers(Modifier.PUBLIC)
				.addStatement(String.format("this.%s = %s", fieldName, fieldName))
				.returns(TypeName.VOID)
				.build());
		typeSpecBuilder.addMethod(MethodSpec.methodBuilder("get" + StringUtils.capitalize(fieldName))
				.addModifiers(Modifier.PUBLIC)
				.addStatement(String.format("return this.%s", fieldName))
				.returns(className)
				.build());
	}

	private void enrichWithGetSetList(TypeSpec.Builder typeSpecBuilder, ParameterizedTypeName parameterizedTypeName, String fieldName) {
		typeSpecBuilder.addMethod(MethodSpec.methodBuilder("set" + StringUtils.capitalize(fieldName))
				.addParameter(ParameterSpec.builder(parameterizedTypeName, fieldName).build())
				.addModifiers(Modifier.PUBLIC)
				.addStatement(String.format("this.%s = %s", fieldName, fieldName))
				.returns(TypeName.VOID)
				.build());
		typeSpecBuilder.addMethod(MethodSpec.methodBuilder("get" + StringUtils.capitalize(fieldName))
				.addModifiers(Modifier.PUBLIC)
				.addStatement(String.format("return this.%s", fieldName))
				.returns(parameterizedTypeName)
				.build());
	}

	private void parseDiscriminator(TypeSpec.Builder typeSpecBuilder, Discriminator discriminator, String targetPackage) {
		if (discriminator.getPropertyName() != null) {
			AnnotationSpec jsonTypeInfoAnnotation = AnnotationSpec.builder(JsonTypeInfo.class)
					.addMember("use", "$L", JsonTypeInfo.Id.NAME)
					.addMember("include", "$L", JsonTypeInfo.As.PROPERTY)
					.addMember("property", "$S", discriminator.getPropertyName())
					.build();
			typeSpecBuilder.addAnnotation(jsonTypeInfoAnnotation);
		}
		if (discriminator.getMapping() != null) {
			List<AnnotationSpec> annotationSpecs = discriminator.getMapping().entrySet().stream()
					.map(discriminatorMappingEntry ->
							AnnotationSpec.builder(Type.class)
									.addMember("value", "$T.class", ClassName.get(targetPackage, discriminatorMappingEntry.getValue()))
									.addMember("name", "$S", discriminatorMappingEntry.getKey())
									.build())
					.collect(Collectors.toList());

			AnnotationSpec jsonSubTypesAnnotation = AnnotationSpec.builder(JsonSubTypes.class)
					.addMember("value", "$L", annotationSpecs)
					.build();

			typeSpecBuilder.addAnnotation(jsonSubTypesAnnotation);
		}
	}

	private String getNameFromRef(String ref) {
		return ref.replace("#/components/schemas/", "");
	}

}

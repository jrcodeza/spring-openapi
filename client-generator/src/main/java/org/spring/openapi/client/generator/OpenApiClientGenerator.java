package org.spring.openapi.client.generator;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
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
import static org.spring.openapi.client.generator.ClientGeneratorUtils.JAVA_LANG_PKG;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.JAVA_TIME_PKG;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.buildTypeSpec;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.determineParentClassNameUsingDiscriminator;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.determineParentClassNameUsingOneOf;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.getNameFromRef;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.getNumberGenericClassName;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.getStringGenericClassName;

public class OpenApiClientGenerator {

	private Map<String, Schema> allComponents;

	public void generateClient(String targetPackage, String openApiSchemaPath, String outputPath) {
		generateClient(targetPackage, openApiSchemaPath, outputPath, true, false);
	}

	public void generateClient(String targetPackage, String openApiSchemaPath, String outputPath, boolean generateResourceInterface,
							   boolean generateDiscriminatorProperty) {
		try {
			OpenAPI openAPI = IntegrationObjectMapperFactory.createJson().readValue(new File(openApiSchemaPath), OpenAPI.class);
			allComponents = openAPI.getComponents().getSchemas();
			allComponents.entrySet().forEach(schemaEntry -> processSchemaEntry(targetPackage, schemaEntry, outputPath, generateDiscriminatorProperty));

			if (generateResourceInterface) {
				new ResourceInterfaceGenerator(allComponents).generateResourceInterface(openAPI.getPaths(), targetPackage, outputPath);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void processSchemaEntry(String targetPackage, Map.Entry<String, Schema> schemaEntry, String outputPath, boolean generateDiscriminatorProperty) {
		Schema schema = schemaEntry.getValue();
		if (schema.getEnum() != null) {
			TypeSpec.Builder typeSpecBuilder = createEnumClass(schemaEntry.getKey(), schema);
			buildTypeSpec(targetPackage, typeSpecBuilder, outputPath);
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
			parseDiscriminator(typeSpecBuilder, schema.getDiscriminator(), targetPackage, generateDiscriminatorProperty);
		}
		if (schema.getProperties() != null) {
			parseProperties(typeSpecBuilder, schema.getProperties(), targetPackage, schema.getRequired(), schema.getDiscriminator(),
					generateDiscriminatorProperty);
		}

		buildTypeSpec(targetPackage, typeSpecBuilder, outputPath);
	}

	private void parseProperties(TypeSpec.Builder typeSpecBuilder, Map<String, Schema> properties, String targetPackage, List<String> requiredFields,
								 Discriminator discriminator, boolean generateDiscriminatorProperty) {
		String discriminatorPropertyName = discriminator == null ? null : discriminator.getPropertyName();
		for (Map.Entry<String, Schema> propertyEntry : properties.entrySet()) {
			if (!generateDiscriminatorProperty && equalsIgnoreCase(propertyEntry.getKey(), discriminatorPropertyName)) {
				continue;
			}
			// type or ref or oneOf + (discriminator)
			Schema innerSchema = propertyEntry.getValue();
			FieldSpec.Builder fieldSpecBuilder;
			if (innerSchema.getType() != null) {
				fieldSpecBuilder = parseTypeBasedSchema(propertyEntry.getKey(), innerSchema, targetPackage, typeSpecBuilder);
			} else if (innerSchema.get$ref() != null) {
				// simple no inheritance
				fieldSpecBuilder = createSimpleFieldSpec(targetPackage, getNameFromRef(innerSchema.get$ref()), propertyEntry.getKey(), typeSpecBuilder);
			} else if (innerSchema instanceof ComposedSchema && CollectionUtils.isNotEmpty(((ComposedSchema) innerSchema).getAllOf())) {
				fieldSpecBuilder = createSimpleFieldSpec(targetPackage, determineParentClassNameUsingOneOf(innerSchema, propertyEntry.getKey(), allComponents), propertyEntry.getKey(), typeSpecBuilder);
			} else if (innerSchema.getDiscriminator() != null) {
				// complicated inheritance - identify target class
				fieldSpecBuilder = createSimpleFieldSpec(targetPackage, determineParentClassNameUsingDiscriminator(innerSchema, propertyEntry.getKey(), allComponents), propertyEntry.getKey(), typeSpecBuilder);
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

	private FieldSpec.Builder parseTypeBasedSchema(String fieldName, Schema innerSchema, String targetPackage,
										   TypeSpec.Builder typeSpecBuilder) {
		if (innerSchema.getEnum() != null) {
			typeSpecBuilder.addType(createEnumClass(StringUtils.capitalize(fieldName), innerSchema).build());
			return createSimpleFieldSpec(null, StringUtils.capitalize(fieldName), fieldName, typeSpecBuilder);
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
			return ClassName.get(targetPackage, determineParentClassNameUsingOneOf(arrayItemsSchema, "innerArray", allComponents));
		} else if (arrayItemsSchema.getDiscriminator() != null) {
			return ClassName.get(targetPackage, determineParentClassNameUsingDiscriminator(arrayItemsSchema, "innerArray", allComponents));
		}
		return ClassName.get(JAVA_LANG_PKG, "Object");
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
		if (innerSchema.getFormat() == null || equalsIgnoreCase(innerSchema.getFormat(), "int32")) {
			return createSimpleFieldSpec(JAVA_LANG_PKG, "Integer", fieldName, typeSpecBuilder);
		} else if (equalsIgnoreCase(innerSchema.getFormat(), "int64")) {
			return createSimpleFieldSpec(JAVA_LANG_PKG, "Long", fieldName, typeSpecBuilder);
		} else if (equalsIgnoreCase(innerSchema.getFormat(), "float")) {
			return createSimpleFieldSpec(JAVA_LANG_PKG, "Float", fieldName, typeSpecBuilder);
		} else if (equalsIgnoreCase(innerSchema.getFormat(), "double")) {
			return createSimpleFieldSpec(JAVA_LANG_PKG, "Double", fieldName, typeSpecBuilder);
		} else {
			return createSimpleFieldSpec(JAVA_LANG_PKG, "Integer", fieldName, typeSpecBuilder);
		}
	}

	private FieldSpec.Builder createSimpleFieldSpec(String packageName, String className, String fieldName, TypeSpec.Builder typeSpecBuilder) {
		ClassName simpleFieldClassName = packageName == null ? ClassName.bestGuess(className) : ClassName.get(packageName, className);
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

	private void parseDiscriminator(TypeSpec.Builder typeSpecBuilder, Discriminator discriminator, String targetPackage,
									boolean generateDiscriminatorProperty) {
		if (discriminator.getPropertyName() != null) {
			AnnotationSpec.Builder annotationSpecBuilder = AnnotationSpec.builder(JsonTypeInfo.class)
					.addMember("use", "JsonTypeInfo.Id.NAME")
					.addMember("include", "JsonTypeInfo.As.PROPERTY")
					.addMember("property", "$S", discriminator.getPropertyName());
			if (generateDiscriminatorProperty) {
				annotationSpecBuilder.addMember("visible", "true");
			}
			typeSpecBuilder.addAnnotation(annotationSpecBuilder.build());
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
					.addMember("value", wrapAnnotationsIntoArray(annotationSpecs))
					.build();

			typeSpecBuilder.addAnnotation(jsonSubTypesAnnotation);
		}
	}

	private CodeBlock wrapAnnotationsIntoArray(List<AnnotationSpec> annotationSpecs) {
		CodeBlock.Builder codeBuilder  = CodeBlock.builder();
		boolean arrayStart = true;
		codeBuilder.add("{");
		for (AnnotationSpec annotationSpec : annotationSpecs) {
			if (!arrayStart)
				codeBuilder.add(", ");
			arrayStart = false;
			codeBuilder.add("$L", annotationSpec);
		}
		codeBuilder.add("}");
		return codeBuilder.build();
	}

}

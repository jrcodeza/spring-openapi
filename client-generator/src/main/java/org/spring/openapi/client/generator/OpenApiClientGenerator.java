package org.spring.openapi.client.generator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import io.swagger.v3.oas.integration.IntegrationObjectMapperFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

public class OpenApiClientGenerator {

	private List<SchemaEntry> classCreateOrder;

	public void generateClient(String targetPackage, File openApiJson) {
		try {
			OpenAPI openAPI = IntegrationObjectMapperFactory.createJson().readValue(openApiJson, OpenAPI.class);
			Map<String, Schema> schemas = openAPI.getComponents().getSchemas();
			schemas.entrySet().forEach(schemaEntry -> processSchemaEntry(targetPackage, schemaEntry));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void processSchemaEntry(String targetPackage, Map.Entry<String, Schema> schemaEntry) {
		Schema schema = schemaEntry.getValue();
		TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(schemaEntry.getKey()).addModifiers(Modifier.PUBLIC);
		if (schema instanceof ComposedSchema) {
			ComposedSchema composedSchema = (ComposedSchema) schema;
			typeSpecBuilder
					.superclass(ClassName.get(targetPackage, getNameFromRef(composedSchema.getAllOf().get(0).get$ref()))) // TODO null check
					.build();
			schema = composedSchema.getAllOf().get(1);
		}
		if (schema.getDiscriminator() != null) {
			parseDiscriminator(typeSpecBuilder, schema.getDiscriminator(), targetPackage);
		}
		if (schema.getProperties() != null) {
			parseProperties(typeSpecBuilder, schema.getProperties(), targetPackage);
		}
		try {
			JavaFile.builder(targetPackage, typeSpecBuilder.build())
					.build()
					.writeTo(Paths.get("C:\\Users\\jremenec.DAVINCI\\Projects\\spring-openapi\\schema-generator\\target\\openapi"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parseProperties(TypeSpec.Builder typeSpecBuilder, Map<String, Schema> properties, String targetPackage) {
		for (Map.Entry<String, Schema> propertyEntry :  properties.entrySet()) {
			// type or ref or oneOf + (discriminator)
			Schema innerSchema = propertyEntry.getValue();
			if (innerSchema.getType() != null) {
				typeSpecBuilder.addField(parseTypeBasedSchema(propertyEntry.getKey(), innerSchema, targetPackage));
			} else if (innerSchema.get$ref() != null) {

			} else if (innerSchema.getDiscriminator() != null) {

			} else {
				throw new IllegalArgumentException("Incorrect schema. One of [type, $ref, discriminator+oneOf] has to be defined in property schema");
			}
			FieldSpec.builder(ClassName.get(targetPackage, ))
		}
	}

	private FieldSpec parseTypeBasedSchema(String fieldName, Schema innerSchema, String targetPackage) {
		if (innerSchema.getEnum() != null) {

		}
		if (equalsIgnoreCase(innerSchema.getType(), "string")) {
			return getStringBasedSchemaField(fieldName, innerSchema);
		}
	}

	private FieldSpec getStringBasedSchemaField(String fieldName, Schema innerSchema) {
		if (innerSchema.getFormat() == null) {
			FieldSpec.Builder fieldBuilder = FieldSpec.builder(ClassName.get("java.lang", "String"), fieldName, Modifier.PRIVATE);
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
			return fieldBuilder.build();
		} else if (equalsIgnoreCase(innerSchema.getFormat(), "date")) {
			return FieldSpec.builder(ClassName.get("java.time", "LocalDate"), fieldName, Modifier.PRIVATE).build();
		} else if (equalsIgnoreCase(innerSchema.getFormat(), "date-time")) {
			return FieldSpec.builder(ClassName.get("java.time", "LocalDateTime"), fieldName, Modifier.PRIVATE).build();
		}
		throw new IllegalArgumentException(String.format("Error parsing string based property [%s]", fieldName));
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

	class SchemaEntry {
		private String name;
		private Schema schema;

		public SchemaEntry(String name, Schema schema) {
			this.name = name;
			this.schema = schema;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Schema getSchema() {
			return schema;
		}

		public void setSchema(Schema schema) {
			this.schema = schema;
		}
	}

}

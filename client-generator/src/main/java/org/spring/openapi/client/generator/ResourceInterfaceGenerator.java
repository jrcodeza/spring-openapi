package org.spring.openapi.client.generator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.JAVA_LANG_PKG;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.JAVA_TIME_PKG;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.buildTypeSpec;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.determineParentClassName;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.determineParentClassNameUsingOneOf;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.getNameFromRef;
import static org.spring.openapi.client.generator.ClientGeneratorUtils.getNumberGenericClassName;

public class ResourceInterfaceGenerator {

	private String targetPackage;

	private final Map<String, Schema> allComponents;

	public ResourceInterfaceGenerator(Map<String, Schema> allComponents) {
		this.allComponents = allComponents;
	}

	public void generateResourceInterface(Paths paths, String targetPackage, String outputPath) {
		this.targetPackage = targetPackage;
		Map<String, List<OperationData>> resourceMap = new HashMap<>();
		paths.entrySet().forEach(pathItemEntry -> addToResourceMap(pathItemEntry, resourceMap));
		resourceMap.entrySet().forEach(resource -> createInterface(resource, targetPackage, outputPath));
	}

	private void createInterface(Map.Entry<String, List<OperationData>> resource, String targetPackage, String outputPath) {
		TypeSpec.Builder typeSpecBuilder = TypeSpec.interfaceBuilder(resource.getKey())
				.addModifiers(Modifier.PUBLIC);
		resource.getValue().forEach(operationData -> typeSpecBuilder.addMethod(createMethod(operationData)));
		buildTypeSpec(targetPackage + ".operations", typeSpecBuilder, outputPath);
	}

	private MethodSpec createMethod(OperationData operationData) {
		Operation operation = operationData.getOperation();
		MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder(getMethodName(operation))
				.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
		if (operation.getDescription() != null) {
			methodSpecBuilder.addJavadoc(operation.getDescription());
		}
		if (operation.getParameters() != null) {
			operation.getParameters()
					.forEach(parameter -> methodSpecBuilder.addParameter(
							parseProperties(formatParameterName(parameter.getName()), parameter.getSchema()).build()
					));
		}
		if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
			LinkedHashMap<String, MediaType> mediaTypes = operation.getRequestBody().getContent();
			methodSpecBuilder.addParameter(parseProperties("requestBody", mediaTypes.entrySet().iterator().next().getValue().getSchema()).build());
		}
		if (operation.getResponses() == null || CollectionUtils.isEmpty(operation.getResponses().entrySet())) {
			methodSpecBuilder.returns(TypeName.VOID);
		} else {
			ApiResponse apiResponse = operation.getResponses().entrySet().stream()
					.filter(responseEntry -> StringUtils.startsWith(responseEntry.getKey(), "2")) // HTTP 20x
					.findFirst()
					.map(Map.Entry::getValue)
					.orElse(null);
			if (apiResponse != null && apiResponse.getContent() != null && !apiResponse.getContent().isEmpty()) {
				MediaType mediaType = apiResponse.getContent().entrySet().iterator().next().getValue();
				if (mediaType.getSchema() != null) {
					methodSpecBuilder.returns(determineTypeName(mediaType.getSchema()));
					return methodSpecBuilder.build();
				}
			}
			methodSpecBuilder.returns(TypeName.VOID);
		}

		return methodSpecBuilder.build();
	}

	private String getMethodName(Operation operation) {
		String operationIdPrefix = "";
		if (CollectionUtils.isNotEmpty(operation.getTags())) {
			operationIdPrefix = StringUtils.uncapitalize(toResourceInterfaceName(operation.getTags().get(0)));
		}
		return StringUtils.uncapitalize(operation.getOperationId()).replaceFirst(operationIdPrefix, "");
	}

	private String formatParameterName(String name) {
		String result = Stream.of(name.split("-"))
				.map(StringUtils::capitalize)
				.collect(Collectors.joining());
		return StringUtils.uncapitalize(result);
	}

	private void addToResourceMap(Map.Entry<String, PathItem> pathItemEntry, Map<String, List<OperationData>> resourceMap) {
		String url = pathItemEntry.getKey();
		String resourceName = getResourceName(pathItemEntry.getValue());
		PathItem pathItem = pathItemEntry.getValue();
		Stream.of(pathItem.getPost(), pathItem.getPatch(), pathItem.getPut(), pathItem.getHead(), pathItem.getOptions(), pathItem.getGet(), pathItem.getDelete())
				.filter(Objects::nonNull)
				.forEach(operation -> {
					if (resourceMap.containsKey(resourceName)) {
						resourceMap.get(resourceName).add(new OperationData(operation, url));
					} else {
						List<OperationData> operations = new ArrayList<>();
						operations.add(new OperationData(operation, url));
						resourceMap.put(resourceName, operations);
					}
				});
	}

	private String getResourceName(PathItem value) {
		return Stream.of(value.getPost(), value.getPatch(), value.getPut(), value.getHead(), value.getOptions(), value.getGet(), value.getDelete())
				.filter(Objects::nonNull)
				.filter(operation -> CollectionUtils.isNotEmpty(operation.getTags()))
				.findFirst()
				.map(operation -> toResourceInterfaceName(operation.getTags().get(0)))
				.orElse(null);
	}

	private String toResourceInterfaceName(String operationTag) {
		return Stream.of(operationTag.split("-"))
				.map(StringUtils::capitalize)
				.collect(Collectors.joining());
	}

	private ParameterSpec.Builder parseProperties(String parameterName, Schema parameterSchema) {
		if (parameterSchema.getType() != null) {
			return parseTypeBasedSchema(parameterName, parameterSchema);
		} else if (parameterSchema.get$ref() != null) {
			// simple no inheritance
			return createSimpleParameterSpec(null, getNameFromRef(parameterSchema.get$ref()), parameterName);
		} else if (parameterSchema instanceof ComposedSchema && CollectionUtils.isNotEmpty(((ComposedSchema) parameterSchema).getAllOf())) {
			return createSimpleParameterSpec(null, determineParentClassNameUsingOneOf(parameterSchema, parameterName, allComponents), parameterName);
		} else {
			throw new IllegalArgumentException("Incorrect schema. One of [type, $ref, discriminator+oneOf] has to be defined in property schema");
		}
	}

	private ParameterSpec.Builder parseTypeBasedSchema(String parameterName, Schema innerSchema) {
		if (equalsIgnoreCase(innerSchema.getType(), "string")) {
			return ParameterSpec.builder(getStringGenericClassName(innerSchema), parameterName);
		} else if (equalsIgnoreCase(innerSchema.getType(), "integer") || equalsIgnoreCase(innerSchema.getType(), "number")) {
			return getNumberBasedSchemaParameter(parameterName, innerSchema);
		} else if (equalsIgnoreCase(innerSchema.getType(), "boolean")) {
			return createSimpleParameterSpec(JAVA_LANG_PKG, "Boolean", parameterName);
		} else if (equalsIgnoreCase(innerSchema.getType(), "array") && innerSchema instanceof ArraySchema) {
			ArraySchema arraySchema = (ArraySchema) innerSchema;
			ParameterizedTypeName listParameterizedTypeName = ParameterizedTypeName.get(
					ClassName.get("java.util", "List"),
					determineTypeName(arraySchema.getItems())
			);
			return ParameterSpec.builder(listParameterizedTypeName, parameterName);
		} else if (equalsIgnoreCase(innerSchema.getType(), "object") && isFile(innerSchema.getProperties())) {
			return ParameterSpec.builder(ClassName.get(File.class), parameterName);
		}
		return createSimpleParameterSpec(JAVA_LANG_PKG, "Object", parameterName);
	}

	private ClassName determineTypeName(Schema<?> schema) {
		if (equalsIgnoreCase(schema.getType(), "string") && !StringUtils.equalsIgnoreCase(schema.getFormat(), "binary")) {
			return getStringGenericClassName(schema);
		} else if (equalsIgnoreCase(schema.getType(), "integer") || equalsIgnoreCase(schema.getType(), "number")) {
			return getNumberGenericClassName(schema);
		} else if (equalsIgnoreCase(schema.getType(), "boolean")) {
			return ClassName.get(JAVA_LANG_PKG, "Boolean");
		} else if (schema.get$ref() != null) {
			return ClassName.bestGuess(targetPackage + "." + getNameFromRef(schema.get$ref()));
		} else if (schema instanceof ComposedSchema && CollectionUtils.isNotEmpty(((ComposedSchema) schema).getAllOf())) {
			return ClassName.bestGuess(targetPackage + "." + determineParentClassNameUsingOneOf(schema, "innerArray", allComponents));
		} else if (schema.getDiscriminator() != null) {
			return ClassName.bestGuess(targetPackage + "." + determineParentClassNameUsingDiscriminator(schema, "innerArray"));
		} else if ((equalsIgnoreCase(schema.getType(), "object") || equalsIgnoreCase(schema.getType(), "string")) && isFile(schema.getProperties())) {
			return ClassName.get(File.class);
		}
		return ClassName.get(JAVA_LANG_PKG, "Object");
	}

	private boolean isFile(Map<String, Schema> properties) {
		if (properties == null || properties.isEmpty()) {
			return false;
		}
		return properties.entrySet().stream()
				.map(Map.Entry::getValue)
				.anyMatch(schema -> equalsIgnoreCase(schema.getFormat(), "binary"));
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

	private String determineParentClassNameUsingDiscriminator(Schema innerSchema, String fieldName) {
		Set<Map.Entry<String,String>> discriminatorEntries = innerSchema.getDiscriminator().getMapping().entrySet();
		if (CollectionUtils.isEmpty(discriminatorEntries)) {
			throw new IllegalArgumentException("Discriminator needs to have at least one value defined. Field: " + fieldName);
		}
		return determineParentClassName(discriminatorEntries.iterator().next().getValue(), allComponents);
	}

	private ParameterSpec.Builder getNumberBasedSchemaParameter(String fieldName, Schema innerSchema) {
		ParameterSpec.Builder fieldBuilder = createNumberBasedParameterWithFormat(fieldName, innerSchema);
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

	private ParameterSpec.Builder createNumberBasedParameterWithFormat(String fieldName, Schema innerSchema) {
		if (innerSchema.getFormat() == null || StringUtils.equalsIgnoreCase(innerSchema.getFormat(), "int32")) {
			return createSimpleParameterSpec(JAVA_LANG_PKG, "Integer", fieldName);
		} else if (StringUtils.equalsIgnoreCase(innerSchema.getFormat(), "int64")) {
			return createSimpleParameterSpec(JAVA_LANG_PKG, "Long", fieldName);
		} else if (StringUtils.equalsIgnoreCase(innerSchema.getFormat(), "float")) {
			return createSimpleParameterSpec(JAVA_LANG_PKG, "Float", fieldName);
		} else if (StringUtils.equalsIgnoreCase(innerSchema.getFormat(), "double")) {
			return createSimpleParameterSpec(JAVA_LANG_PKG, "Double", fieldName);
		} else {
			return createSimpleParameterSpec(JAVA_LANG_PKG, "Integer", fieldName);
		}
	}

	private ParameterSpec.Builder createSimpleParameterSpec(String packageName, String className, String parameterName) {
		ClassName simpleFieldClassName = packageName == null ? ClassName.bestGuess(targetPackage + "." + className) : ClassName.get(packageName, className);
		return ParameterSpec.builder(simpleFieldClassName, parameterName);
	}

}

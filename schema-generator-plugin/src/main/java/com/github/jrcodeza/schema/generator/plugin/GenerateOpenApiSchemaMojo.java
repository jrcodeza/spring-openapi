package com.github.jrcodeza.schema.generator.plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jrcodeza.schema.generator.OpenAPIGenerator;
import com.github.jrcodeza.schema.generator.config.OpenApiGeneratorConfig;
import com.github.jrcodeza.schema.generator.config.builder.OpenApiGeneratorConfigBuilder;
import com.github.jrcodeza.schema.generator.interceptors.OperationInterceptor;
import com.github.jrcodeza.schema.generator.interceptors.OperationParameterInterceptor;
import com.github.jrcodeza.schema.generator.interceptors.RequestBodyInterceptor;
import com.github.jrcodeza.schema.generator.interceptors.SchemaFieldInterceptor;
import com.github.jrcodeza.schema.generator.interceptors.SchemaInterceptor;
import com.github.jrcodeza.schema.generator.interceptors.examples.OpenApiExampleResolver;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import static java.util.Arrays.asList;

@Mojo(name = "generateOpenApi", defaultPhase = LifecyclePhase.INSTALL)
public class GenerateOpenApiSchemaMojo extends AbstractMojo {

	@Parameter(required = true)
	private String title;

	@Parameter
	private String description;

	@Parameter(required = true)
	private String version;

	@Parameter(required = true)
	private String[] modelPackages;

	@Parameter(required = true)
	private String[] controllerBasePackages;

	@Parameter(required = true)
	private String outputDirectory;

	@Parameter
	private List<String> schemaInterceptors;

	@Parameter
	private List<String> schemaFieldInterceptors;

	@Parameter
	private List<String> operationParameterInterceptors;

	@Parameter
	private List<String> operationInterceptors;

	@Parameter
	private List<String> requestBodyInterceptors;

	@Parameter
	private Boolean generateExamples;

	@Parameter
	private String openApiExamplesResolver;

	public void execute() {
		OpenAPIGenerator openApiGenerator = new OpenAPIGenerator(
				asList(modelPackages), asList(controllerBasePackages), createInfoFromParameters(),
				parseInputInterceptors(schemaInterceptors, SchemaInterceptor.class),
				parseInputInterceptors(schemaFieldInterceptors, SchemaFieldInterceptor.class),
				parseInputInterceptors(operationParameterInterceptors, OperationParameterInterceptor.class),
				parseInputInterceptors(operationInterceptors, OperationInterceptor.class),
				parseInputInterceptors(requestBodyInterceptors, RequestBodyInterceptor.class)
		);

		OpenApiGeneratorConfig openApiGeneratorConfig = OpenApiGeneratorConfigBuilder.defaultConfig().build();
		if (BooleanUtils.isTrue(generateExamples)) {
			openApiGeneratorConfig.setGenerateExamples(true);
			if (StringUtils.isNotBlank(openApiExamplesResolver)) {
				openApiGeneratorConfig.setOpenApiExampleResolver(instantiateClass(openApiExamplesResolver, OpenApiExampleResolver.class));
			}
		}
		OpenAPI openAPI = openApiGenerator.generate(openApiGeneratorConfig);

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

		try {
			if (!new File(outputDirectory).mkdirs()) {
				getLog().error(String.format("Error creating directories for path [%s]", outputDirectory));
				return;
			}
			objectMapper.writeValue(new File(outputDirectory + "/swagger.json"), openAPI);
		} catch (IOException e) {
			getLog().error("Cannot serialize generated OpenAPI spec", e);
		}
	}

	private <T> List<T> parseInputInterceptors(List<String> classNames, Class<T> clazz) {
		if (classNames == null || classNames.isEmpty()) {
			return new ArrayList<>();
		}
		List<T> result = new ArrayList<>();
		for (String className : classNames) {
			result.add(instantiateClass(className, clazz));
		}

		return result;
	}

	private <T> T instantiateClass(String className, Class<T> superClass) {
		try {
			Class<?> inputClass = Class.forName(className);
			if (superClass.isAssignableFrom(inputClass)) {
				return (T) inputClass.newInstance();
			} else {
				getLog().error(String.format("Incorrect class type = [%s]. Expected is = [%s]", className, superClass.getName()));
			}
		} catch (IllegalAccessException | InstantiationException | ClassNotFoundException e) {
			getLog().error(e.getMessage());
			System.exit(1);
		}
		return null;
	}

	private Info createInfoFromParameters() {
		Info info = new Info();
		info.setTitle(title);
		info.setDescription(description);
		info.setVersion(version);
		return info;
	}

}

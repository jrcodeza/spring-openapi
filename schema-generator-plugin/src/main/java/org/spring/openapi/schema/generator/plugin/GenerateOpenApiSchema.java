package org.spring.openapi.schema.generator.plugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spring.openapi.schema.generator.OpenAPIGenerator;

import io.swagger.v3.oas.models.OpenAPI;

public class GenerateOpenApiSchema {

	private static Logger logger = LoggerFactory.getLogger(OpenAPIGenerator.class);

	public static void main(String[] args) {
		validateArgs(args);
		String outputDirectory = getOutputDirectory(args);
		OpenAPIGenerator openApiGenerator = new OpenAPIGenerator(
				extractCommandLineArgument(args[0]),
				extractCommandLineArgument(args[1])
		);

		OpenAPI openAPI = openApiGenerator.generate();

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

		try {
			if (!new File(outputDirectory).mkdirs()) {
				logger.error("Error creating directories for path [{}]", outputDirectory);
				return;
			}
			objectMapper.writeValue(new File(outputDirectory + "/swagger.json"), openAPI);
		} catch (IOException e) {
			logger.error("Cannot serialize generated OpenAPI spec", e);
		}
	}

	private static String getOutputDirectory(String[] args) {
		List<String> outputDirectories = extractCommandLineArgument(args[2]);
		if (outputDirectories.size() != 1) {
			throw new IllegalArgumentException(String.format("You have to specify one output directory"));
		}
		return outputDirectories.get(0);
	}

	private static List<String> extractCommandLineArgument(String arg) {
		return Stream.of(arg.replaceAll(" ", "").split(",")).collect(Collectors.toList());
	}

	private static void validateArgs(String[] args) {
		if (args == null || args.length != 3) {
			throw new IllegalArgumentException("Please specify correct args");
		}
	}

}

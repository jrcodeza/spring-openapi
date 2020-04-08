package com.github.jrcodeza.schema.v2.generator;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jrcodeza.OpenApiIgnore;
import com.github.jrcodeza.schema.v2.generator.config.OpenApiV2GeneratorConfig;
import com.github.jrcodeza.schema.v2.generator.config.builder.OpenApiV2GeneratorConfigBuilder;
import com.github.jrcodeza.schema.v2.generator.interceptors.OperationInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.OperationParameterInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.RequestBodyInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.SchemaFieldInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.SchemaInterceptor;
import com.github.jrcodeza.schema.v2.generator.model.GenerationContext;
import com.github.jrcodeza.schema.v2.generator.model.Header;
import com.github.jrcodeza.schema.v2.generator.model.InheritanceInfo;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.models.Info;
import io.swagger.models.Model;
import io.swagger.models.Path;
import io.swagger.models.Swagger;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

public class OpenAPIV2Generator {

	private static final String DEFAULT_DISCRIMINATOR_NAME = "type";
	private static Logger logger = LoggerFactory.getLogger(OpenAPIV2Generator.class);
	private final ComponentSchemaTransformer componentSchemaTransformer;
	private final OperationsTransformer operationsTransformer;
	private final Info info;
	private final List<SchemaInterceptor> schemaInterceptors;
	private final List<SchemaFieldInterceptor> schemaFieldInterceptors;
	private final List<OperationParameterInterceptor> operationParameterInterceptors;
	private final List<OperationInterceptor> operationInterceptors;
	private final List<RequestBodyInterceptor> requestBodyInterceptors;
	private final List<Header> globalHeaders;
	private List<String> modelPackages;
	private List<String> controllerBasePackages;
	private Environment environment;

	public OpenAPIV2Generator(List<String> modelPackages, List<String> controllerBasePackages, Info info) {
		this(modelPackages, controllerBasePackages, info, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
	}

	public OpenAPIV2Generator(List<String> modelPackages, List<String> controllerBasePackages, Info info,
							  List<SchemaInterceptor> schemaInterceptors,
							  List<SchemaFieldInterceptor> schemaFieldInterceptors,
							  List<OperationParameterInterceptor> operationParameterInterceptors,
							  List<OperationInterceptor> operationInterceptors,
							  List<RequestBodyInterceptor> requestBodyInterceptors) {
		this.modelPackages = modelPackages;
		this.controllerBasePackages = controllerBasePackages;
		componentSchemaTransformer = new ComponentSchemaTransformer(schemaFieldInterceptors);
		globalHeaders = new ArrayList<>();

		GenerationContext operationsGenerationContext = new GenerationContext(null, removeRegexFormatFromPackages(modelPackages));
		operationsTransformer = new OperationsTransformer(
				operationsGenerationContext, operationParameterInterceptors, operationInterceptors, requestBodyInterceptors, globalHeaders
		);

		this.info = info;
		this.schemaInterceptors = schemaInterceptors;
		this.schemaFieldInterceptors = schemaFieldInterceptors;
		this.operationParameterInterceptors = operationParameterInterceptors;
		this.operationInterceptors = operationInterceptors;
		this.requestBodyInterceptors = requestBodyInterceptors;
	}

	public String generateJson() throws JsonProcessingException {
		return generateJson(OpenApiV2GeneratorConfigBuilder.empty().build());
	}

	public String generateJson(OpenApiV2GeneratorConfig config) throws JsonProcessingException {
		Swagger openAPI = generate(config);
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		DocumentContext doc = JsonPath.parse(objectMapper.writeValueAsString(openAPI));
		doc.delete("$..responseSchema");
		doc.delete("$..originalRef");
		return doc.jsonString();
	}

	public Swagger generate(OpenApiV2GeneratorConfig config) {
		logger.info("Starting OpenAPI v2 generation");
		environment = config.getEnvironment();
		Swagger openAPI = new Swagger();
		openAPI.setDefinitions(createDefinitions());
		openAPI.setPaths(createPaths(config));
		openAPI.setInfo(info);
		openAPI.setBasePath(config.getBasePath());
		openAPI.setHost(config.getHost());
		logger.info("OpenAPI v2 generation done!");
		return openAPI;
	}

	public Swagger generate() {
		return generate(OpenApiV2GeneratorConfigBuilder.empty().build());
	}

	public void addSchemaInterceptor(SchemaInterceptor schemaInterceptor) {
		schemaInterceptors.add(schemaInterceptor);
	}

	public void addSchemaFieldInterceptor(SchemaFieldInterceptor schemaFieldInterceptor) {
		schemaFieldInterceptors.add(schemaFieldInterceptor);
	}

	public void addOperationParameterInterceptor(OperationParameterInterceptor operationParameterInterceptor) {
		operationParameterInterceptors.add(operationParameterInterceptor);
	}

	public void addOperationInterceptor(OperationInterceptor operationInterceptor) {
		operationInterceptors.add(operationInterceptor);
	}

	public void addRequestBodyInterceptor(RequestBodyInterceptor requestBodyInterceptor) {
		requestBodyInterceptors.add(requestBodyInterceptor);
	}

	public void addGlobalHeader(String name, String description, boolean required) {
		globalHeaders.add(new Header(name, description, required));
	}

	private Map<String, Path> createPaths(OpenApiV2GeneratorConfig config) {
		ClassPathScanningCandidateComponentProvider scanner = createClassPathScanningCandidateComponentProvider();
		scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

		List<Class<?>> controllerClasses = new ArrayList<>();
		List<String> packagesWithoutRegex = removeRegexFormatFromPackages(controllerBasePackages);
		for (String controllerPackage : packagesWithoutRegex) {
			logger.debug("Scanning controller package=[{}]", controllerPackage);
			for (BeanDefinition beanDefinition : scanner.findCandidateComponents(controllerPackage)) {
				logger.debug("Scanning controller class=[{}]", beanDefinition.getBeanClassName());
				controllerClasses.add(getClass(beanDefinition));
			}
		}
		return operationsTransformer.transformOperations(controllerClasses, config);
	}

	private ClassPathScanningCandidateComponentProvider createClassPathScanningCandidateComponentProvider() {
		if (environment == null) {
			return new ClassPathScanningCandidateComponentProvider(false);
		}
		return new ClassPathScanningCandidateComponentProvider(false, environment);
	}

	private Map<String, Model> createDefinitions() {
		Map<String, Model> schemaMap = new HashMap<>();
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		modelPackages.forEach(modelPackage -> scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(modelPackage))));

		List<String> packagesWithoutRegex = removeRegexFormatFromPackages(modelPackages);
		Map<String, InheritanceInfo> inheritanceMap = new HashMap<>();
		for (String modelPackage : packagesWithoutRegex) {
			logger.debug("Scanning model package=[{}]", modelPackage);
			for (BeanDefinition beanDefinition : scanner.findCandidateComponents(modelPackage)) {
				logger.debug("Scanning model class=[{}]", beanDefinition.getBeanClassName());
				// populating inheritance info
				Class<?> clazz = getClass(beanDefinition);
				if (inheritanceMap.containsKey(clazz.getName()) || clazz.getAnnotation(OpenApiIgnore.class) != null) {
					continue;
				}
				getInheritanceInfo(clazz).ifPresent(inheritanceInfo -> {
					logger.debug("Adding entry [{}] to inheritance map", clazz.getName());
					inheritanceMap.put(clazz.getName(), inheritanceInfo);
				});
			}
			for (BeanDefinition beanDefinition : scanner.findCandidateComponents(modelPackage)) {
				Class<?> clazz = getClass(beanDefinition);
				if (schemaMap.containsKey(clazz.getSimpleName()) || clazz.getAnnotation(OpenApiIgnore.class) != null) {
					continue;
				}
				GenerationContext generationContext = new GenerationContext(inheritanceMap, packagesWithoutRegex);
				Model transformedComponentSchema = componentSchemaTransformer.transformSimpleSchema(clazz, generationContext);
				schemaInterceptors.forEach(schemaInterceptor -> schemaInterceptor.intercept(clazz, transformedComponentSchema));
				schemaMap.put(clazz.getSimpleName(), transformedComponentSchema);
			}
		}
		return schemaMap;
	}

	private Class<?> getClass(BeanDefinition beanDefinition) {
		try {
			return Class.forName(beanDefinition.getBeanClassName());
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private Optional<InheritanceInfo> getInheritanceInfo(Class<?> clazz) {
		if (clazz.getAnnotation(JsonSubTypes.class) != null) {
			List<Annotation> annotations = unmodifiableList(asList(clazz.getAnnotations()));
			JsonTypeInfo jsonTypeInfo = annotations.stream()
												   .filter(annotation -> annotation instanceof JsonTypeInfo)
												   .map(annotation -> (JsonTypeInfo) annotation)
												   .findFirst()
												   .orElse(null);

			InheritanceInfo inheritanceInfo = new InheritanceInfo();
			inheritanceInfo.setDiscriminatorFieldName(getDiscriminatorName(jsonTypeInfo));
			inheritanceInfo.setDiscriminatorClassMap(scanJacksonInheritance(annotations));
			return Optional.of(inheritanceInfo);
		}
		return Optional.empty();
	}

	private String getDiscriminatorName(JsonTypeInfo jsonTypeInfo) {
		if (jsonTypeInfo == null) {
			return DEFAULT_DISCRIMINATOR_NAME;
		}
		return jsonTypeInfo.property();
	}

	private List<String> removeRegexFormatFromPackages(List<String> modelPackages) {
		return modelPackages.stream()
							.map(modelPackage -> modelPackage.replace(".*", ""))
							.collect(Collectors.toList());
	}

	private Map<String, String> scanJacksonInheritance(List<Annotation> annotations) {
		return annotations.stream()
						  .filter(annotation -> annotation instanceof JsonSubTypes)
						  .map(annotation -> (JsonSubTypes) annotation)
						  .flatMap(jsonSubTypesMapped -> Arrays.stream(jsonSubTypesMapped.value()))
						  .collect(Collectors.toMap(o -> o.value().getCanonicalName(), JsonSubTypes.Type::name));
	}

}

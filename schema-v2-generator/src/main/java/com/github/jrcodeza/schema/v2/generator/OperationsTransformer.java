package com.github.jrcodeza.schema.v2.generator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.jrcodeza.Response;
import com.github.jrcodeza.Responses;
import com.github.jrcodeza.schema.v2.generator.config.OpenApiV2GeneratorConfig;
import com.github.jrcodeza.schema.v2.generator.interceptors.OperationInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.OperationParameterInterceptor;
import com.github.jrcodeza.schema.v2.generator.interceptors.RequestBodyInterceptor;
import com.github.jrcodeza.schema.v2.generator.model.CustomQueryParameter;
import com.github.jrcodeza.schema.v2.generator.model.GenerationContext;
import com.github.jrcodeza.schema.v2.generator.util.CommonConstants;
import com.github.jrcodeza.schema.v2.generator.util.GeneratorUtils;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.models.ModelImpl;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.HeaderParameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.FileProperty;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;

public class OperationsTransformer extends OpenApiTransformer {

	private static final String DEFAULT_RESPONSE_STATUS = "200";
	private static Logger logger = LoggerFactory.getLogger(OperationsTransformer.class);

	private static final String DEFAULT_CONTENT_TYPE = "application/json";
	private static final String MULTIPART_FORM_DATA_CONTENT_TYPE = "multipart/form-data";
	private static final LocalVariableTableParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();

	private static final List<Class<?>> OPERATION_ANNOTATIONS = asList(RequestMapping.class, PostMapping.class, GetMapping.class, PutMapping.class,
																	   PatchMapping.class, DeleteMapping.class);

	private final Set<String> operationIds = new HashSet<>();

	private final GenerationContext generationContext;
	private final List<OperationParameterInterceptor> operationParameterInterceptors;
	private final List<OperationInterceptor> operationInterceptors;
	private final List<RequestBodyInterceptor> requestBodyInterceptors;
	private final List<com.github.jrcodeza.schema.v2.generator.model.Header> globalHeaders;

	public OperationsTransformer(GenerationContext generationContext,
								 List<OperationParameterInterceptor> operationParameterInterceptors,
								 List<OperationInterceptor> operationInterceptors,
								 List<RequestBodyInterceptor> requestBodyInterceptors,
								 List<com.github.jrcodeza.schema.v2.generator.model.Header> globalHeaders) {
		this.generationContext = generationContext;
		this.operationParameterInterceptors = operationParameterInterceptors;
		this.operationInterceptors = operationInterceptors;
		this.requestBodyInterceptors = requestBodyInterceptors;
		this.globalHeaders = globalHeaders;
	}

	public Map<String, Path> transformOperations(List<Class<?>> restControllerClasses, OpenApiV2GeneratorConfig config) {
		super.openApiV2GeneratorConfig.set(config);
		operationIds.clear();
		final Map<String, Path> operationsMap = new HashMap<>();

		for (Class<?> clazz : restControllerClasses) {
			if (GeneratorUtils.shouldBeIgnored(clazz)) {
				logger.info("Ignoring class {}", clazz.getName());
				continue;
			}

			logger.debug("Transforming {} controller class", clazz.getName());
			String baseControllerPath = getBaseControllerPath(clazz);
			ReflectionUtils.doWithMethods(clazz, method -> createOperation(method, baseControllerPath, operationsMap, clazz.getSimpleName()),
										  this::isOperationMethod);
		}
		fixDuplicateOperationIds(operationsMap);
		return operationsMap;
	}

	private void fixDuplicateOperationIds(Map<String, Path> operationsMap) {
		Map<String, Integer> operationIdCount = new HashMap<>();
		operationsMap.values().forEach(pathItem -> {
			handleOperation(pathItem.getHead(), operationIdCount);
			handleOperation(pathItem.getOptions(), operationIdCount);
			handleOperation(pathItem.getPost(), operationIdCount);
			handleOperation(pathItem.getPatch(), operationIdCount);
			handleOperation(pathItem.getPut(), operationIdCount);
			handleOperation(pathItem.getGet(), operationIdCount);
			handleOperation(pathItem.getDelete(), operationIdCount);
		});
	}

	private void handleOperation(Operation operation, Map<String, Integer> operationIdCount) {
		if (operation == null) {
			return;
		}
		String operationId = operation.getOperationId();
		if (operationIdCount.containsKey(operationId)) {
			Integer newValue = operationIdCount.get(operationId) + 1;
			operation.setOperationId(operationId + "_" + newValue);
			operationIdCount.put(operationId, newValue);
			return;
		}
		operationIdCount.put(operationId, 0);
	}

	private void createOperation(Method method, String baseControllerPath, Map<String, Path> operationsMap, String controllerClassName) {
		logger.debug("Transforming {} controller method", method.getName());
		getAnnotation(method, PostMapping.class).ifPresent(postMapping -> mapPost(postMapping, method, operationsMap, controllerClassName, baseControllerPath));
		getAnnotation(method, PutMapping.class).ifPresent(putMapping -> mapPut(putMapping, method, operationsMap, controllerClassName, baseControllerPath));
		getAnnotation(method, PatchMapping.class).ifPresent(patchMapping -> mapPatch(patchMapping, method, operationsMap, controllerClassName,
																					 baseControllerPath));
		getAnnotation(method, GetMapping.class).ifPresent(getMapping -> mapGet(getMapping, method, operationsMap, controllerClassName, baseControllerPath));
		getAnnotation(method, DeleteMapping.class).ifPresent(deleteMapping -> mapDelete(deleteMapping, method, operationsMap, controllerClassName,
																						baseControllerPath));
		getAnnotation(method, RequestMapping.class).ifPresent(requestMapping -> mapRequestMapping(requestMapping, method, operationsMap, controllerClassName,
																								  baseControllerPath));
	}

	private void mapRequestMapping(RequestMapping requestMapping, Method method, Map<String, Path> operationsMap, String controllerClassName,
								   String baseControllerPath) {
		List<HttpMethod> httpMethods = Arrays.stream(requestMapping.method())
											 .map(this::getSpringMethod)
											 .collect(toList());
		httpMethods.forEach(httpMethod -> {
			Operation operation = mapOperation(requestMapping.name(), httpMethod, method, requestMapping.produces(),
											   requestMapping.consumes(), controllerClassName);

			String path = ObjectUtils.defaultIfNull(getFirstFromArray(requestMapping.value()), getFirstFromArray(requestMapping.path()));
			updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap,
								pathItem -> setContentBasedOnHttpMethod(pathItem, httpMethod, operation)
			);
		});

	}

	private String prepareUrl(String... url) {
		String preparedUrl = Stream.of(url).filter(Objects::nonNull).collect(Collectors.joining());
		if (preparedUrl.charAt(preparedUrl.length() - 1) == '/') {
			preparedUrl = preparedUrl.substring(0, preparedUrl.length() - 1);
		}
		preparedUrl = preparedUrl.replaceAll("//", "/");
		if (!preparedUrl.startsWith("/")) {
			preparedUrl = "/" + preparedUrl;
		}
		return preparedUrl.replaceAll("[^A-Za-z0-9-/{}]", "");
	}

	private void setContentBasedOnHttpMethod(Path pathItem, HttpMethod method, Operation operation) {
		if (method == null) {
			throw new IllegalArgumentException("RequestMethod in RequestMapping must have at least one value");
		}
		switch (method) {
			case GET:
				pathItem.setGet(operation);
				return;
			case PUT:
				pathItem.setPut(operation);
				return;
			case POST:
				pathItem.setPost(operation);
				return;
			case PATCH:
				pathItem.setPatch(operation);
				return;
			case HEAD:
				pathItem.setHead(operation);
				return;
			case OPTIONS:
				pathItem.setOptions(operation);
				return;
			case DELETE:
				pathItem.setDelete(operation);
		}
	}

	private boolean isHttpMethodWithRequestBody(HttpMethod method) {
		return asList(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH).contains(method);
	}

	private String classNameToTag(String controllerClassName) {
		return Stream.of(StringUtils.splitByCharacterTypeCamelCase(controllerClassName))
					 .map(StringUtils::lowerCase)
					 .collect(Collectors.joining("-"));
	}

	private HttpMethod getSpringMethod(RequestMethod method) {
		if (method == null) {
			throw new IllegalArgumentException("HttpMethod must be specified on RequestMapping annotated method");
		}
		return HttpMethod.valueOf(method.name());
	}

	private void mapDelete(DeleteMapping deleteMapping, Method method, Map<String, Path> operationsMap, String controllerClassName,
						   String baseControllerPath) {
		Operation operation = mapOperation(deleteMapping.name(), HttpMethod.DELETE, method, deleteMapping.produces(), deleteMapping.consumes(), controllerClassName);

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(deleteMapping.value()), getFirstFromArray(deleteMapping.path()));
		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setDelete(operation));
	}

	private Map<String, io.swagger.models.Response> createApiResponses(Method method) {
		Responses apiResponsesAnnotation = method.getAnnotation(Responses.class);

		if (apiResponsesAnnotation == null) {
			Class<?> methodReturnType = method.getReturnType();
			Property mediaType = createMediaType(methodReturnType, null, getGenericParams(method));
			String responseStatusCode = resolveResponseStatus(method);
			io.swagger.models.Response apiResponse = new io.swagger.models.Response();
			apiResponse.setDescription(HttpStatus.valueOf(Integer.parseInt(responseStatusCode)).getReasonPhrase());

			if (mediaType != null) {
				apiResponse.setSchema(mediaType);
			}

			Map<String, io.swagger.models.Response> apiResponses = new HashMap<>();
			apiResponses.put(responseStatusCode, apiResponse);
			return apiResponses;
		}

		Map<String, io.swagger.models.Response> apiResponses = new HashMap<>();
		for (Response responseAnnotation : apiResponsesAnnotation.value()) {
			io.swagger.models.Response apiResponse = new io.swagger.models.Response();
			apiResponse.setDescription(responseAnnotation.description());
			apiResponse.setHeaders(createHeaderResponse(responseAnnotation.headers()));

			if (!StringUtils.containsIgnoreCase(responseAnnotation.responseBody().getSimpleName(), "void")) {
				Property mediaType;
				if (isFileResponse(responseAnnotation.responseBody())) {
					mediaType = new FileProperty();
					((FileProperty) mediaType).setType("string");
					((FileProperty) mediaType).setFormat("binary");

				} else {
					mediaType = new RefProperty();
					((RefProperty) mediaType).set$ref(CommonConstants.COMPONENT_REF_PREFIX + responseAnnotation.responseBody().getSimpleName());
				}

				apiResponse.setSchema(mediaType);

			}
			apiResponses.put(String.valueOf(responseAnnotation.responseCode()), apiResponse);
		}
		return apiResponses;
	}

	private boolean isFileResponse(Class<?> responseBodyClass) {
		return responseBodyClass.isAssignableFrom(MultipartFile.class);
	}

	private List<Class<?>> getGenericParams(Method method) {
		if (method.getGenericReturnType() instanceof ParameterizedType) {
			Type[] typeArguments = ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments();
			if (typeArguments != null && typeArguments.length > 0) {
				Type typeArgument = typeArguments[0];
				if (typeArgument instanceof Class<?>) {
					return singletonList((Class<?>) typeArgument);
				} else if (typeArguments[0] instanceof ParameterizedType) {
					ParameterizedType parameterizedType = (ParameterizedType) typeArgument;
					Type[] innerGenericTypes = parameterizedType.getActualTypeArguments();
					return innerGenericTypes.length > 0 ?
						   asList(classForName(innerGenericTypes[0]), classForName(parameterizedType.getRawType())) :
						   null;
				}
			}
		}
		return null;
	}

	private Class<?> classForName(Type innerGenericType) {
		try {
			return Class.forName(innerGenericType.getTypeName());
		} catch (ClassNotFoundException e) {
			logger.error("Exception occurred", e);
			return null;
		}
	}

	private Map<String, Property> createHeaderResponse(com.github.jrcodeza.Header[] headers) {
		Map<String, Property> responseHeaders = new HashMap<>();
		for (com.github.jrcodeza.Header headerAnnotation : headers) {
			StringProperty header = new StringProperty();
			header.setDescription(headerAnnotation.description());
			responseHeaders.put(headerAnnotation.name(), header);
		}
		return responseHeaders;
	}

	private String resolveResponseStatus(Method method) {
		ResponseStatus responseStatusAnnotation = method.getAnnotation(ResponseStatus.class);
		if (responseStatusAnnotation == null) {
			return DEFAULT_RESPONSE_STATUS;
		}
		return String.valueOf(defaultIfUnexpectedServerError(responseStatusAnnotation.code(), responseStatusAnnotation.value()).value());
	}

	private HttpStatus defaultIfUnexpectedServerError(HttpStatus code, HttpStatus value) {
		return code == HttpStatus.INTERNAL_SERVER_ERROR ? value : code;
	}

	private void mapGet(GetMapping getMapping, Method method, Map<String, Path> operationsMap, String controllerClassName, String baseControllerPath) {
		Operation operation = mapOperation(getMapping.name(), HttpMethod.GET, method, getMapping.produces(), getMapping.consumes(), controllerClassName);

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(getMapping.value()), getFirstFromArray(getMapping.path()));
		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setGet(operation));
	}

	private void mapPatch(PatchMapping patchMapping, Method method, Map<String, Path> operationsMap, String controllerClassName,
						  String baseControllerPath) {
		Operation operation = mapOperation(patchMapping.name(), HttpMethod.PATCH, method, patchMapping.produces(), patchMapping.consumes(), controllerClassName);

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(patchMapping.value()), getFirstFromArray(patchMapping.path()));
		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setPatch(operation));
	}

	private void mapPut(PutMapping putMapping, Method method, Map<String, Path> operationsMap, String controllerClassName, String baseControllerPath) {
		Operation operation = mapOperation(putMapping.name(), HttpMethod.PUT, method, putMapping.produces(), putMapping.consumes(), controllerClassName);

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(putMapping.value()), getFirstFromArray(putMapping.path()));
		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setPut(operation));
	}

	private Operation mapOperation(String operationName, HttpMethod httpMethod, Method method, String[] produces, String[] consumes, String controllerClassName) {
		Operation operation = new Operation();
		operation.setOperationId(getOperationId(operationName, method, httpMethod));
		operation.setSummary(StringUtils.isBlank(operationName) ? operationName : method.getName());
		operation.setTags(singletonList(classNameToTag(controllerClassName)));

		operation.setResponses(createApiResponses(method));
		operation.setParameters(transformParameters(method));
		if (isNotEmpty(consumes)) {
			operation.setConsumes(asList(consumes));
		}
		if (isNotEmpty(produces)) {
			operation.setProduces(asList(produces));
		}

		if (isHttpMethodWithRequestBody(httpMethod)) {
			BodyParameter requestBody = createRequestBody(method, getFirstFromArray(consumes));
			if (requestBody != null) {
				operation.getParameters().add(requestBody);
			}
		}

		applyAnnotationsForOperation(operation, method.getAnnotations());
		operationInterceptors.forEach(interceptor -> interceptor.intercept(method, operation));
		return operation;
	}

	private void mapPost(PostMapping postMapping, Method method, Map<String, Path> operationsMap, String controllerClassName, String baseControllerPath) {
		Operation operation = mapOperation(postMapping.name(), HttpMethod.POST, method, postMapping.produces(), postMapping.consumes(), controllerClassName);

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(postMapping.value()), getFirstFromArray(postMapping.path()));
		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setPost(operation));
	}

	private void applyAnnotationsForOperation(Operation operation, Annotation[] annotations) {
		 asList(annotations).forEach(annotation -> {
		 	if (annotation instanceof Deprecated) {
		 		operation.setVendorExtension("x-deprecated", true);
			}
		 });
	}

	private void updateOperationsMap(String url, Map<String, Path> existingMap, Consumer<Path> pathItemUpdater) {
		if (existingMap.containsKey(url)) {
			pathItemUpdater.accept(existingMap.get(url));
		} else {
			Path pathItem = new Path();
			pathItemUpdater.accept(pathItem);
			existingMap.put(url, pathItem);
		}
	}

	private List<io.swagger.models.parameters.Parameter> transformParameters(Method method) {
		String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
		Parameter[] parameters = method.getParameters();
		List<io.swagger.models.parameters.Parameter> result = new ArrayList<>();
		addGlobalHeaders(result);
		for (int i = 0; i < parameters.length; i++) {
			Parameter actualParameter = parameters[i];
			if (GeneratorUtils.shouldBeIgnored(actualParameter)) {
				logger.info("Ignoring parameter {}", parameterNames[i]);
				continue;
			}
			if (shouldBeIncludedInDocumentation(actualParameter)) {
				String parameterName = parameterNames[i];
				io.swagger.models.parameters.Parameter oasParameter;
					oasParameter = mapQueryParameter(actualParameter, parameterName);
				if (oasParameter != null) {
					operationParameterInterceptors.forEach(interceptor -> interceptor.intercept(method, actualParameter, parameterName, oasParameter));
					result.add(oasParameter);
				}
			}
		}
		return result;
	}

	private void addGlobalHeaders(List<io.swagger.models.parameters.Parameter> result) {
		if (!globalHeaders.isEmpty()) {
			List<io.swagger.models.parameters.Parameter> globalOasHeaders = globalHeaders.stream()
																						 .map(this::createOasHeader)
																						 .collect(toList());
			result.addAll(globalOasHeaders);
		}
	}

	private io.swagger.models.parameters.Parameter createOasHeader(com.github.jrcodeza.schema.v2.generator.model.Header header) {
		io.swagger.models.parameters.HeaderParameter parameter = new io.swagger.models.parameters.HeaderParameter();
		parameter.setType("string");
		parameter.setIn("header");
		parameter.setName(header.getName());
		parameter.setDescription(header.getDescription());
		parameter.setRequired(header.isRequired());
		return parameter;
	}

	private boolean shouldBeIncludedInDocumentation(Parameter parameter) {
		return parameter.getAnnotation(PathVariable.class) != null || parameter.getAnnotation(RequestParam.class) != null
			   || parameter.getAnnotation(RequestHeader.class) != null;
	}

	private io.swagger.models.parameters.Parameter mapQueryParameter(Parameter parameter, String parameterName) {
		AbstractSerializableParameter<?> oasParameter;
		if (parameter.getAnnotation(PathVariable.class) != null) {
			PathVariable pathVariableAnnotation = parameter.getAnnotation(PathVariable.class);
			oasParameter = new PathParameter();
			oasParameter.setName(resolveNameFromAnnotation(pathVariableAnnotation.name(), pathVariableAnnotation.value(), parameterName));
			oasParameter.setRequired(true);
		} else if (parameter.getAnnotation(RequestParam.class) != null && !parameter.getType().isAssignableFrom(MultipartFile.class)) {
			RequestParam requestParamAnnotation = parameter.getAnnotation(RequestParam.class);
			oasParameter = new CustomQueryParameter();
			oasParameter.setName(resolveNameFromAnnotation(requestParamAnnotation.name(), requestParamAnnotation.value(), parameterName));
			oasParameter.setRequired(requestParamAnnotation.required());
			oasParameter.setType(null);
		} else if (parameter.getAnnotation(RequestHeader.class) != null) {
			RequestHeader requestHeaderAnnotation = parameter.getAnnotation(RequestHeader.class);
			oasParameter = new HeaderParameter();
			oasParameter.setName(resolveNameFromAnnotation(requestHeaderAnnotation.name(), requestHeaderAnnotation.value(), parameterName));
			oasParameter.setRequired(requestHeaderAnnotation.required());
		} else {
			return null;
		}
		fillParameterInfo(oasParameter, parameter, parameterName);
		return oasParameter;
	}

	private String resolveNameFromAnnotation(String nameFromAnnotation, String valueFromAnnotation, String reflectionParameterName) {
		return Stream.of(nameFromAnnotation, valueFromAnnotation, reflectionParameterName)
					 .filter(StringUtils::isNotBlank)
					 .map(s -> s.replaceAll("/[^A-Za-z0-9]/", ""))
					 .findFirst()
					 .orElse(null);
	}

	private BodyParameter createRequestBody(Method method, String userDefinedContentType) {
		ParameterNamePair requestBodyParameter = getRequestBody(method);

		if (requestBodyParameter == null) {
			return null;
		}
		if (GeneratorUtils.shouldBeIgnored(requestBodyParameter.getParameter())) {
			logger.info("Ignoring parameter {}", requestBodyParameter.getName());
			return null;
		}

		ModelImpl content = new ModelImpl();
		content.addProperty(resolveContentType(userDefinedContentType, requestBodyParameter.getParameter()),
							createMediaType(
									requestBodyParameter.getParameter().getType(),
									requestBodyParameter.getName(),
									singletonList(getGenericParam(requestBodyParameter.getParameter()))
							));

		BodyParameter requestBody = new BodyParameter();
		requestBody.setRequired(true);
		requestBody.setSchema(content);
		requestBody.setDescription("requestBody");
		requestBody.setName(requestBodyParameter.getName());

		requestBodyInterceptors.forEach(interceptor ->
												interceptor.intercept(method, requestBodyParameter.getParameter(), requestBodyParameter.getName(), requestBody)
		);

		return requestBody;
	}

	private Class<?> getGenericParam(Parameter parameter) {
		if (parameter.getParameterizedType() instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) parameter.getParameterizedType();
			if (isNotEmpty(parameterizedType.getActualTypeArguments())) {
				return (Class<?>) parameterizedType.getActualTypeArguments()[0];
			}
		}
		return null;
	}

	private Property createMediaType(Class<?> requestBodyParameter, String parameterName, List<Class<?>> genericParams) {
		Property rootMediaSchema = new ObjectProperty();
		if (isFile(requestBodyParameter)) {
			FileProperty fileSchema = new FileProperty();
			fileSchema.setType("string");
			fileSchema.setFormat("binary");

			if (parameterName == null) {
				rootMediaSchema = fileSchema;
			} else {
				Map<String, Property> properties = new HashMap<>();
				properties.put(parameterName, fileSchema);
				((ObjectProperty) rootMediaSchema).setProperties(properties);
			}
		} else if (isList(requestBodyParameter, genericParams)) {
			rootMediaSchema = parseArraySignature(getFirstOrNull(genericParams), null, new Annotation[]{});
		} else if (!StringUtils.equalsIgnoreCase(requestBodyParameter.getSimpleName(), "void")) {
			if (isInPackagesToBeScanned(requestBodyParameter, generationContext)) {
				rootMediaSchema = new RefProperty();
				((RefProperty) rootMediaSchema).set$ref(CommonConstants.COMPONENT_REF_PREFIX + requestBodyParameter.getSimpleName());
			} else if (requestBodyParameter.isAssignableFrom(ResponseEntity.class) && !CollectionUtils.isEmpty(genericParams)
					   && !genericParams.get(0).isAssignableFrom(Void.class)) {
				rootMediaSchema = new RefProperty();
				((RefProperty) rootMediaSchema).set$ref(CommonConstants.COMPONENT_REF_PREFIX + genericParams.get(0).getSimpleName());
			} else {
				return null;
			}
		} else {
			return null;
		}
		return rootMediaSchema;
	}

	private Class<?> getFirstOrNull(List<Class<?>> genericParams) {
		if (CollectionUtils.isEmpty(genericParams) || genericParams.get(0).isAssignableFrom(List.class)) {
			return null;
		}
		return genericParams.get(0);
	}

	private boolean isList(Class<?> requestBodyParameter, List<Class<?>> genericTypes) {
		Class<?> classToCheck = requestBodyParameter;
		if (requestBodyParameter.isAssignableFrom(ResponseEntity.class) && !genericTypes.isEmpty()) {
			classToCheck = genericTypes.get(genericTypes.size() - 1);
		}
		return classToCheck.isAssignableFrom(List.class);
	}

	private void fillParameterInfo(AbstractSerializableParameter<?> oasParameter, Parameter parameter, String parameterName) {
		Class<?> clazz = parameter.getType();
		Annotation[] annotations = parameter.getAnnotations();

		if (clazz.isPrimitive()) {
			setParameterDetails(oasParameter, clazz, annotations);
		} else if (clazz.isArray()) {
			oasParameter.setProperty(parseArraySignatureForParameter(clazz.getComponentType(), annotations));
		} else if (clazz.isAssignableFrom(List.class)) {
			if (!(parameter.getParameterizedType() instanceof ParameterizedType)) {
				throw new IllegalArgumentException(String.format("List [%s] not being parametrized type.", parameterName));
			}
			Class<?> listGenericParameter = (Class<?>) ((ParameterizedType) parameter.getParameterizedType()).getActualTypeArguments()[0];
			oasParameter.setProperty(parseArraySignature(listGenericParameter, null, annotations));
		} else {
			setParameterDetails(oasParameter, clazz, annotations);
		}
	}

	private Property parseArraySignatureForParameter(Class<?> elementTypeSignature, Annotation[] annotations) {
		ArrayProperty arraySchema = new ArrayProperty();
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
		}

		arraySchema.setItems(new StringProperty());
		return arraySchema;
	}

	private String resolveContentType(String userDefinedContentType, Parameter requestBody) {
		if (StringUtils.isBlank(userDefinedContentType)) {
			return isFile(requestBody.getType()) ? MULTIPART_FORM_DATA_CONTENT_TYPE : DEFAULT_CONTENT_TYPE;
		}
		return userDefinedContentType;
	}

	private ParameterNamePair getRequestBody(Method method) {
		String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
		Parameter[] parameters = method.getParameters();
		for (int i = 0; i < parameters.length; i++) {
			Parameter actualParameter = parameters[i];
			if (actualParameter.getAnnotation(org.springframework.web.bind.annotation.RequestBody.class) != null) {
				return new ParameterNamePair(parameterNames[i], actualParameter);
			}
		}
		for (int i = 0; i < parameters.length; i++) {
			Parameter actualParameter = parameters[i];
			if (isFile(actualParameter.getType())) {
				return new ParameterNamePair(parameterNames[i], actualParameter);
			}
		}
		return null;
	}

	private boolean isFile(Class<?> parameter) {
		return parameter.isAssignableFrom(MultipartFile.class);
	}

	private String getOperationId(String nameFromAnnotation, Method method, HttpMethod httpMethod) {
		return StringUtils.isBlank(nameFromAnnotation) ? method.getName() + "Using" + httpMethod.name() : nameFromAnnotation;
	}

	private <T extends Annotation> Optional<T> getAnnotation(Method method, Class<T> annotationClass) {
		return Optional.ofNullable(method.getAnnotation(annotationClass));
	}

	private boolean isOperationMethod(Method method) {
		if (GeneratorUtils.shouldBeIgnored(method)) {
			logger.info("Ignoring operation {}", method.getName());
			return false;
		}
		return Stream.of(method.getAnnotations())
					 .anyMatch(annotation -> OPERATION_ANNOTATIONS.stream()
																  .anyMatch(operationAnnotation -> operationAnnotation.isAssignableFrom(annotation.getClass()))
					 );
	}

	private String getBaseControllerPath(Class<?> clazz) {
		RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
		if (requestMapping == null) {
			return "/";
		}
		return requestMapping.value().length > 0 ? getFirstFromArray(requestMapping.value()) : getFirstFromArray(requestMapping.path());
	}

	@Override
	protected RefProperty createRefProperty(Class<?> typeSignature, GenerationContext generationContext) {
		RefProperty composedSchema = new RefProperty();
		composedSchema.set$ref(CommonConstants.COMPONENT_REF_PREFIX + typeSignature.getSimpleName());
		return composedSchema;
	}

	@Override
	protected Property createArrayProperty(Class<?> typeSignature, GenerationContext generationContext, Annotation[] annotations) {
		return parseArraySignature(typeSignature, null, annotations);
	}

	public String getFirstFromArray(String[] strings) {
		return strings == null || strings.length == 0 ? null : strings[0];
	}

	static class ParameterNamePair {

		private String name;
		private Parameter parameter;

		public ParameterNamePair(String name, Parameter parameter) {
			this.name = name;
			this.parameter = parameter;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Parameter getParameter() {
			return parameter;
		}

		public void setParameter(Parameter parameter) {
			this.parameter = parameter;
		}
	}
}

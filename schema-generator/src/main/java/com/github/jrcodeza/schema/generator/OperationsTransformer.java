package com.github.jrcodeza.schema.generator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.jrcodeza.Response;
import com.github.jrcodeza.Responses;
import com.github.jrcodeza.schema.generator.filters.OperationFilter;
import com.github.jrcodeza.schema.generator.filters.OperationParameterFilter;
import com.github.jrcodeza.schema.generator.interceptors.OperationInterceptor;
import com.github.jrcodeza.schema.generator.interceptors.OperationParameterInterceptor;
import com.github.jrcodeza.schema.generator.interceptors.RequestBodyInterceptor;
import com.github.jrcodeza.schema.generator.model.GenerationContext;

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

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import static com.github.jrcodeza.schema.generator.util.CommonConstants.COMPONENT_REF_PREFIX;
import static com.github.jrcodeza.schema.generator.util.GeneratorUtils.shouldBeIgnored;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;

public class OperationsTransformer extends OpenApiTransformer {

	private static final String DEFAULT_RESPONSE_STATUS = "200";
	private static Logger logger = LoggerFactory.getLogger(OperationsTransformer.class);

	private static final String DEFAULT_CONTENT_TYPE = "application/json";
	private static final String DEFAULT_FILE_RETURN_CONTENT_TYPE = "application/octet-stream";
	private static final String MULTIPART_FORM_DATA_CONTENT_TYPE = "multipart/form-data";
	private static final LocalVariableTableParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();

	private static final List<Class<?>> OPERATION_ANNOTATIONS = asList(RequestMapping.class, PostMapping.class, GetMapping.class, PutMapping.class,
			PatchMapping.class, DeleteMapping.class);

	private final GenerationContext generationContext;
	private final List<OperationParameterInterceptor> operationParameterInterceptors;
	private final List<OperationInterceptor> operationInterceptors;
	private final List<RequestBodyInterceptor> requestBodyInterceptors;
	private final List<com.github.jrcodeza.schema.generator.model.Header> globalHeaders;

	private final AtomicReference<OperationFilter> operationFilter;
	private final AtomicReference<OperationParameterFilter> operationParameterFilter;

	public OperationsTransformer(GenerationContext generationContext,
								 List<OperationParameterInterceptor> operationParameterInterceptors,
								 List<OperationInterceptor> operationInterceptors,
								 List<RequestBodyInterceptor> requestBodyInterceptors,
								 List<com.github.jrcodeza.schema.generator.model.Header> globalHeaders,
								 AtomicReference<OperationFilter> operationFilter,
								 AtomicReference<OperationParameterFilter> operationParameterFilter) {
		this.generationContext = generationContext;
		this.operationParameterInterceptors = operationParameterInterceptors;
		this.operationInterceptors = operationInterceptors;
		this.requestBodyInterceptors = requestBodyInterceptors;
		this.globalHeaders = globalHeaders;
		this.operationFilter = operationFilter;
		this.operationParameterFilter = operationParameterFilter;
	}

	public Map<String, PathItem> transformOperations(List<Class<?>> restControllerClasses) {
		final Map<String, PathItem> operationsMap = new HashMap<>();

		for (Class<?> clazz : restControllerClasses) {
			if (shouldBeIgnored(clazz)) {
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

	private void createOperation(Method method, String baseControllerPath, Map<String, PathItem> operationsMap, String controllerClassName) {
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

	private void mapRequestMapping(RequestMapping requestMapping, Method method, Map<String, PathItem> operationsMap, String controllerClassName,
								   String baseControllerPath) {
		Operation operation = new Operation();
		operation.setOperationId(getOperationId(requestMapping.name(), method, getSpringMethod(requestMapping.method())));
		operation.setSummary(StringUtils.isBlank(requestMapping.name()) ? requestMapping.name() : method.getName());
		operation.setTags(singletonList(classNameToTag(controllerClassName)));

		if (isHttpMethodWithRequestBody(requestMapping.method())) {
			operation.setRequestBody(createRequestBody(method, getFirstFromArray(requestMapping.consumes())));
		}
		operation.setParameters(transformParameters(method));
		operation.setResponses(createApiResponses(method, getFirstFromArray(requestMapping.produces())));

		operationInterceptors.forEach(interceptor -> interceptor.intercept(method, operation));

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(requestMapping.value()), getFirstFromArray(requestMapping.path()));
		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap,
				pathItem -> setContentBasedOnHttpMethod(pathItem, requestMapping.method(), operation)
		);
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

	private void setContentBasedOnHttpMethod(PathItem pathItem, RequestMethod[] method, Operation operation) {
		if (method == null || method.length == 0) {
			throw new IllegalArgumentException("RequestMethod in RequestMapping must have at least one value");
		}
		RequestMethod requestMethod = method[0];
		switch (requestMethod) {
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
				return;
			case TRACE:
				pathItem.setTrace(operation);
		}
	}

	private boolean isHttpMethodWithRequestBody(RequestMethod[] methods) {
		return Stream.of(methods).anyMatch(requestMethod -> asList(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH).contains(requestMethod));
	}

	private String classNameToTag(String controllerClassName) {
		return Stream.of(StringUtils.splitByCharacterTypeCamelCase(controllerClassName))
				.map(StringUtils::lowerCase)
				.collect(Collectors.joining("-"));
	}

	private HttpMethod getSpringMethod(RequestMethod[] method) {
		if (method == null || method.length == 0) {
			throw new IllegalArgumentException("HttpMethod must be specified on RequestMapping annotated method");
		}
		return HttpMethod.valueOf(method[0].name());
	}

	private void mapDelete(DeleteMapping deleteMapping, Method method, Map<String, PathItem> operationsMap, String controllerClassName,
						   String baseControllerPath) {
		Operation operation = new Operation();
		operation.setOperationId(getOperationId(deleteMapping.name(), method, HttpMethod.DELETE));
		operation.setSummary(StringUtils.isBlank(deleteMapping.name()) ? deleteMapping.name() : method.getName());
		operation.setTags(singletonList(classNameToTag(controllerClassName)));

		operation.setParameters(transformParameters(method));
		operation.setResponses(createApiResponses(method, getFirstFromArray(deleteMapping.produces())));
		String path = ObjectUtils.defaultIfNull(getFirstFromArray(deleteMapping.value()), getFirstFromArray(deleteMapping.path()));

		operationInterceptors.forEach(interceptor -> interceptor.intercept(method, operation));
		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setDelete(operation));
	}

	private ApiResponses createApiResponses(Method method, String produces) {
		Responses apiResponsesAnnotation = method.getAnnotation(Responses.class);

		if (apiResponsesAnnotation == null) {
			Class<?> methodReturnType = method.getReturnType();
			MediaType mediaType = createMediaType(methodReturnType, null, getGenericParams(method));

			String responseStatusCode = resolveResponseStatus(method);
			ApiResponse apiResponse = new ApiResponse();
			apiResponse.setDescription(HttpStatus.valueOf(Integer.parseInt(responseStatusCode)).getReasonPhrase());

			if (mediaType != null) {
				Content content = new Content();
				content.addMediaType(StringUtils.isBlank(produces) ? resolveDefaultContentType(methodReturnType) : produces, mediaType);
				apiResponse.setContent(content);
			}

			ApiResponses apiResponses = new ApiResponses();
			apiResponses.put(responseStatusCode, apiResponse);
			return apiResponses;
		}

		ApiResponses apiResponses = new ApiResponses();
		for (Response responseAnnotation : apiResponsesAnnotation.value()) {
			ApiResponse apiResponse = new ApiResponse();
			apiResponse.setDescription(responseAnnotation.description());
			apiResponse.setHeaders(createHeaderResponse(responseAnnotation.headers()));

			if (!StringUtils.containsIgnoreCase(responseAnnotation.responseBody().getSimpleName(), "void")) {
				Schema<?> schema = new Schema<>();

				if (isFileResponse(responseAnnotation.responseBody())) {
					schema.setType("string");
					schema.setFormat("binary");
				} else {
					schema.set$ref(COMPONENT_REF_PREFIX + responseAnnotation.responseBody().getSimpleName());
				}

				MediaType mediaType = new MediaType();
				mediaType.setSchema(schema);

				Content content = new Content();
				content.addMediaType(StringUtils.isBlank(produces) ? resolveDefaultContentType(responseAnnotation.responseBody()) : produces, mediaType);
				apiResponse.setContent(content);
			}
			apiResponses.put(String.valueOf(responseAnnotation.responseCode()), apiResponse);
		}
		return apiResponses;
	}

	private String resolveDefaultContentType(Class<?> responseBody) {
		if (isFileResponse(responseBody)) {
			return DEFAULT_FILE_RETURN_CONTENT_TYPE;
		}
		return DEFAULT_CONTENT_TYPE;
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

	private Map<String, Header> createHeaderResponse(com.github.jrcodeza.Header[] headers) {
		Map<String, Header> responseHeaders = new HashMap<>();
		for (com.github.jrcodeza.Header headerAnnotation : headers) {
			Schema<?> schema = new Schema<>();
			schema.setType("string");

			Header oasHeader = new Header();
			oasHeader.setDescription(headerAnnotation.description());
			oasHeader.setSchema(schema);
			responseHeaders.put(headerAnnotation.name(), oasHeader);
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

	private void mapGet(GetMapping getMapping, Method method, Map<String, PathItem> operationsMap, String controllerClassName, String baseControllerPath) {
		Operation operation = new Operation();
		operation.setOperationId(getOperationId(getMapping.name(), method, HttpMethod.GET));
		operation.setSummary(StringUtils.isBlank(getMapping.name()) ? getMapping.name() : method.getName());
		operation.setTags(singletonList(classNameToTag(controllerClassName)));

		operation.setParameters(transformParameters(method));
		operation.setResponses(createApiResponses(method, getFirstFromArray(getMapping.produces())));

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(getMapping.value()), getFirstFromArray(getMapping.path()));

		operationInterceptors.forEach(interceptor -> interceptor.intercept(method, operation));
		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setGet(operation));
	}

	private void mapPatch(PatchMapping patchMapping, Method method, Map<String, PathItem> operationsMap, String controllerClassName, String baseControllerPath) {
		Operation operation = new Operation();
		operation.setOperationId(getOperationId(patchMapping.name(), method, HttpMethod.PATCH));
		operation.setSummary(StringUtils.isBlank(patchMapping.name()) ? patchMapping.name() : method.getName());
		operation.setTags(singletonList(classNameToTag(controllerClassName)));

		operation.setRequestBody(createRequestBody(method, getFirstFromArray(patchMapping.consumes())));
		operation.setResponses(createApiResponses(method, getFirstFromArray(patchMapping.produces())));
		operation.setParameters(transformParameters(method));

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(patchMapping.value()), getFirstFromArray(patchMapping.path()));

		operationInterceptors.forEach(interceptor -> interceptor.intercept(method, operation));
		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setPatch(operation));
	}

	private void mapPut(PutMapping putMapping, Method method, Map<String, PathItem> operationsMap, String controllerClassName, String baseControllerPath) {
		Operation operation = new Operation();
		operation.setOperationId(getOperationId(putMapping.name(), method, HttpMethod.PUT));
		operation.setSummary(StringUtils.isBlank(putMapping.name()) ? putMapping.name() : method.getName());
		operation.setTags(singletonList(classNameToTag(controllerClassName)));

		operation.setRequestBody(createRequestBody(method, getFirstFromArray(putMapping.consumes())));
		operation.setResponses(createApiResponses(method, getFirstFromArray(putMapping.produces())));
		operation.setParameters(transformParameters(method));

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(putMapping.value()), getFirstFromArray(putMapping.path()));

		operationInterceptors.forEach(interceptor -> interceptor.intercept(method, operation));
		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setPut(operation));
	}

	private void mapPost(PostMapping postMapping, Method method, Map<String, PathItem> operationsMap, String controllerClassName, String baseControllerPath) {
		Operation operation = new Operation();
		operation.setOperationId(getOperationId(postMapping.name(), method, HttpMethod.POST));
		operation.setSummary(StringUtils.isBlank(postMapping.name()) ? postMapping.name() : method.getName());
		operation.setTags(singletonList(classNameToTag(controllerClassName)));

		operation.setRequestBody(createRequestBody(method, getFirstFromArray(postMapping.consumes())));
		operation.setResponses(createApiResponses(method, getFirstFromArray(postMapping.produces())));
		operation.setParameters(transformParameters(method));

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(postMapping.value()), getFirstFromArray(postMapping.path()));

		operationInterceptors.forEach(interceptor -> interceptor.intercept(method, operation));
		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setPost(operation));
	}

	private void updateOperationsMap(String url, Map<String, PathItem> existingMap, Consumer<PathItem> pathItemUpdater) {
		if (existingMap.containsKey(url)) {
			pathItemUpdater.accept(existingMap.get(url));
		} else {
			PathItem pathItem = new PathItem();
			pathItemUpdater.accept(pathItem);
			existingMap.put(url, pathItem);
		}
	}

	private List<io.swagger.v3.oas.models.parameters.Parameter> transformParameters(Method method) {
		String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
		Parameter[] parameters = method.getParameters();
		List<io.swagger.v3.oas.models.parameters.Parameter> result = new ArrayList<>();
		addGlobalHeaders(result);
		for (int i = 0; i < parameters.length; i++) {
			Parameter actualParameter = parameters[i];
			String parameterName = parameterNames[i];
			if (shouldIgnoreParameter(method, actualParameter, parameterName)) {
				logger.info("Ignoring parameter {}", parameterName);
				continue;
			}
			if (shouldBeIncludedInDocumentation(actualParameter)) {
				io.swagger.v3.oas.models.parameters.Parameter oasParameter = mapQueryParameter(actualParameter, parameterName);
				if (oasParameter != null) {
					operationParameterInterceptors.forEach(interceptor -> interceptor.intercept(method, actualParameter, parameterName, oasParameter));
					result.add(oasParameter);
				}
			}
		}
		return result;
	}

	private boolean shouldIgnoreParameter(Method method, Parameter parameter, String parameterName) {
		if (shouldBeIgnored(parameter)) {
			return true;
		}

		return this.operationParameterFilter.get() != null
				&& this.operationParameterFilter.get().shouldIgnore(method, parameter, parameterName);
	}

	private void addGlobalHeaders(List<io.swagger.v3.oas.models.parameters.Parameter> result) {
		if (!globalHeaders.isEmpty()) {
			List<io.swagger.v3.oas.models.parameters.Parameter> globalOasHeaders = globalHeaders.stream()
					.map(this::createOasHeader)
					.collect(Collectors.toList());
			result.addAll(globalOasHeaders);
		}
	}

	private io.swagger.v3.oas.models.parameters.Parameter createOasHeader(com.github.jrcodeza.schema.generator.model.Header header) {
		Schema<?> schema = new Schema<>();
		schema.setType("string");

		io.swagger.v3.oas.models.parameters.Parameter parameter = new io.swagger.v3.oas.models.parameters.Parameter();
		parameter.setIn("header");
		parameter.setName(header.getName());
		parameter.setDescription(header.getDescription());
		parameter.setRequired(header.isRequired());

		parameter.setSchema(schema);
		return parameter;
	}

	private boolean shouldBeIncludedInDocumentation(Parameter parameter) {
		return parameter.getAnnotation(PathVariable.class) != null || parameter.getAnnotation(RequestParam.class) != null
				|| parameter.getAnnotation(RequestHeader.class) != null;
	}

	private io.swagger.v3.oas.models.parameters.Parameter mapQueryParameter(Parameter parameter, String parameterName) {
		io.swagger.v3.oas.models.parameters.Parameter oasParameter = new io.swagger.v3.oas.models.parameters.Parameter();
		if (parameter.getAnnotation(PathVariable.class) != null) {
			PathVariable pathVariableAnnotation = parameter.getAnnotation(PathVariable.class);
			oasParameter.setName(resolveNameFromAnnotation(pathVariableAnnotation.name(), pathVariableAnnotation.value(), parameterName));
			oasParameter.setIn("path");
			oasParameter.setRequired(true);
		} else if (parameter.getAnnotation(RequestParam.class) != null && !parameter.getType().isAssignableFrom(MultipartFile.class)) {
			RequestParam requestParamAnnotation = parameter.getAnnotation(RequestParam.class);
			oasParameter.setName(resolveNameFromAnnotation(requestParamAnnotation.name(), requestParamAnnotation.value(), parameterName));
			oasParameter.setIn("query");
			oasParameter.setRequired(requestParamAnnotation.required());
		} else if (parameter.getAnnotation(RequestHeader.class) != null) {
			RequestHeader requestHeaderAnnotation = parameter.getAnnotation(RequestHeader.class);
			oasParameter.setName(resolveNameFromAnnotation(requestHeaderAnnotation.name(), requestHeaderAnnotation.value(), parameterName));
			oasParameter.setIn("header");
			oasParameter.setRequired(requestHeaderAnnotation.required());
		} else {
			return null;
		}
		oasParameter.setSchema(createSchemaFromParameter(parameter, parameterName));
		enrichWithTypeAnnotations(oasParameter, parameter.getAnnotations());
		return oasParameter;
	}

	private String resolveNameFromAnnotation(String nameFromAnnotation, String valueFromAnnotation, String reflectionParameterName) {
		return Stream.of(nameFromAnnotation, valueFromAnnotation, reflectionParameterName)
				.filter(StringUtils::isNotBlank)
				.map(s -> s.replaceAll("/[^A-Za-z0-9]/", ""))
				.findFirst()
				.orElse(null);
	}

	private RequestBody createRequestBody(Method method, String userDefinedContentType) {
		ParameterNamePair requestBodyParameter = getRequestBody(method);

		if (requestBodyParameter == null) {
			return null;
		}
		if (shouldBeIgnored(requestBodyParameter.getParameter())) {
			logger.info("Ignoring parameter {}", requestBodyParameter.getName());
			return null;
		}

		Content content = new Content();
		content.addMediaType(resolveContentType(userDefinedContentType, requestBodyParameter.getParameter()),
				createMediaType(
						requestBodyParameter.getParameter().getType(),
						requestBodyParameter.getName(),
						singletonList(getGenericParam(requestBodyParameter.getParameter()))
				)
		);

		RequestBody requestBody = new RequestBody();
		requestBody.setRequired(true);
		requestBody.setContent(content);
		requestBody.setDescription("requestBody");

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

	private MediaType createMediaType(Class<?> requestBodyParameter, String parameterName, List<Class<?>> genericParams) {
		Schema<?> rootMediaSchema = new Schema<>();
		if (isFile(requestBodyParameter)) {
			Schema<?> fileSchema = new Schema<>();
			fileSchema.setType("string");
			fileSchema.setFormat("binary");

			if (parameterName == null) {
				rootMediaSchema = fileSchema;
			} else {
				Map<String, Schema> properties = new HashMap<>();
				properties.put(parameterName, fileSchema);
				rootMediaSchema.setType("object");
				rootMediaSchema.setProperties(properties);
			}
		} else if (isList(requestBodyParameter, genericParams)) {
			rootMediaSchema = parseArraySignature(getFirstOrNull(genericParams), null, new Annotation[]{});
		} else if (!StringUtils.equalsIgnoreCase(requestBodyParameter.getSimpleName(), "void")) {
			if (isInPackagesToBeScanned(requestBodyParameter, generationContext)) {
				rootMediaSchema.set$ref(COMPONENT_REF_PREFIX + requestBodyParameter.getSimpleName());
			} else if (requestBodyParameter.isAssignableFrom(ResponseEntity.class) && !CollectionUtils.isEmpty(genericParams)
					&& !genericParams.get(0).isAssignableFrom(Void.class)) {
				rootMediaSchema.set$ref(COMPONENT_REF_PREFIX + genericParams.get(0).getSimpleName());
			} else {
				return null;
			}
		} else {
			return null;
		}

		MediaType mediaType = new MediaType();
		mediaType.setSchema(rootMediaSchema);
		return mediaType;
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

	private Schema createSchemaFromParameter(Parameter parameter, String parameterName) {
		Schema schema;
		Class<?> clazz = parameter.getType();
		Annotation[] annotations = parameter.getAnnotations();

		if (clazz.isPrimitive()) {
			schema = parseBaseTypeSignature(clazz, annotations);
		} else if (clazz.isArray()) {
			schema = parseArraySignature(clazz.getComponentType(), null, annotations);
		} else if (clazz.isAssignableFrom(List.class)) {
			if (parameter.getParameterizedType() instanceof ParameterizedType) {
				Class<?> listGenericParameter = (Class<?>)((ParameterizedType) parameter.getParameterizedType()).getActualTypeArguments()[0];
				return parseArraySignature(listGenericParameter, null, annotations);
			}

			throw new IllegalArgumentException(String.format("List [%s] not being parametrized type.", parameterName));
		} else {
			schema = parseClassRefTypeSignature(clazz, annotations, null);
		}
		return schema;
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

	private void fixDuplicateOperationIds(Map<String, PathItem> operationsMap) {
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

	private <T extends Annotation> Optional<T> getAnnotation(Method method, Class<T> annotationClass) {
		return Optional.ofNullable(method.getAnnotation(annotationClass));
	}

	private boolean shouldIgnoreMethod(Method method) {
		if (shouldBeIgnored(method)) {
			return true;
		}
		return this.operationFilter.get() != null && this.operationFilter.get().shouldIgnore(method);
	}

	private boolean isOperationMethod(Method method) {
		if (shouldIgnoreMethod(method)) {
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
	protected ComposedSchema createRefSchema(Class<?> typeSignature, GenerationContext generationContext) {
		ComposedSchema composedSchema = new ComposedSchema();
		composedSchema.set$ref(COMPONENT_REF_PREFIX + typeSignature.getSimpleName());
		return composedSchema;
	}

	@Override
	protected Schema createListSchema(Class<?> typeSignature, GenerationContext generationContext, Annotation[] annotations) {
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

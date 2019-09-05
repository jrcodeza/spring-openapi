package org.spring.openapi.schema.generator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spring.openapi.annotations.Response;
import org.spring.openapi.annotations.Responses;
import org.spring.openapi.schema.generator.model.GenerationContext;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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

import static org.spring.openapi.schema.generator.util.CommonConstants.COMPONENT_REF_PREFIX;

public class OperationsTransformer extends OpenApiTransformer {

	private static final String DEFAULT_RESPONSE_STATUS = "200";
	private static Logger logger = LoggerFactory.getLogger(OperationsTransformer.class);

	private static final String DEFAULT_CONTENT_TYPE = "application/json";
	private static final String MULTIPART_FORM_DATA_CONTENT_TYPE = "multipart/form-data";
	private static final LocalVariableTableParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();

	private static final List<Class<?>> OPERATION_ANNOTATIONS = Arrays.asList(RequestMapping.class, PostMapping.class, GetMapping.class, PutMapping.class,
			PatchMapping.class, DeleteMapping.class);

	public Map<String, PathItem> transformOperations(List<Class<?>> restControllerClasses) {
		final Map<String, PathItem> operationsMap = new HashMap<>();

		for (Class<?> clazz : restControllerClasses) {
			String baseControllerPath = getBaseControllerPath(clazz);
			ReflectionUtils.doWithMethods(clazz, method -> createOperation(method, baseControllerPath, operationsMap, clazz.getSimpleName()),
					this::isOperationMethod);
		}

		return operationsMap;
	}

	private void createOperation(Method method, String baseControllerPath, Map<String, PathItem> operationsMap, String controllerClassName) {
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
		operation.setTags(Collections.singletonList(classNameToTag(controllerClassName)));

		if (isHttpMethodWithRequestBody(requestMapping.method())) {
			operation.setRequestBody(createRequestBody(method, getFirstFromArray(requestMapping.consumes())));
		}
		operation.setParameters(transformParameters(operation, method));
		operation.setResponses(createApiResponses(method, getFirstFromArray(requestMapping.produces())));

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
		return preparedUrl.replaceAll("//", "/");
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
		return Stream.of(methods).anyMatch(requestMethod -> Arrays.asList(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH).contains(requestMethod));
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
		operation.setTags(Collections.singletonList(classNameToTag(controllerClassName)));

		operation.setParameters(transformParameters(operation, method));
		operation.setResponses(createApiResponses(method, getFirstFromArray(deleteMapping.produces())));
		String path = ObjectUtils.defaultIfNull(getFirstFromArray(deleteMapping.value()), getFirstFromArray(deleteMapping.path()));

		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setGet(operation));
	}

	private ApiResponses createApiResponses(Method method, String produces) {
		Responses apiResponsesAnnotation = method.getAnnotation(Responses.class);

		if (apiResponsesAnnotation == null) {
			MediaType mediaType = createMediaType(method.getReturnType(), getGenericParams(method));

			String responseStatusCode = resolveResponseStatus(method);
			ApiResponse apiResponse = new ApiResponse();
			apiResponse.setDescription(HttpStatus.resolve(Integer.parseInt(responseStatusCode)).getReasonPhrase());

			if (mediaType != null) {
				Content content = new Content();
				content.addMediaType(StringUtils.isBlank(produces) ? DEFAULT_CONTENT_TYPE : produces, mediaType);
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
				schema.set$ref(COMPONENT_REF_PREFIX + responseAnnotation.responseBody().getSimpleName());

				MediaType mediaType = new MediaType();
				mediaType.setSchema(schema);

				Content content = new Content();
				content.addMediaType(StringUtils.isBlank(produces) ? DEFAULT_CONTENT_TYPE : produces, mediaType);
				apiResponse.setContent(content);
			}
			apiResponses.put(String.valueOf(responseAnnotation.responseCode()), apiResponse);
		}
		return apiResponses;
	}

	private Class<?> getGenericParams(Method method) {
		if (method.getGenericReturnType() != null && method.getGenericReturnType() instanceof ParameterizedType) {
			Type[] typeArguments = ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments();
			if (typeArguments != null && typeArguments.length > 0) {
				return (Class<?>)typeArguments[0];
			}
		}
		return null;
	}

	private Map<String, Header> createHeaderResponse(org.spring.openapi.annotations.Header[] headers) {
		Map<String, Header> responseHeaders = new HashMap<>();
		for (org.spring.openapi.annotations.Header headerAnnotation : headers) {
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
		operation.setTags(Collections.singletonList(classNameToTag(controllerClassName)));

		operation.setParameters(transformParameters(operation, method));
		operation.setResponses(createApiResponses(method, getFirstFromArray(getMapping.produces())));

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(getMapping.value()), getFirstFromArray(getMapping.path()));

		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setGet(operation));
	}

	private void mapPatch(PatchMapping patchMapping, Method method, Map<String, PathItem> operationsMap, String controllerClassName, String baseControllerPath) {
		Operation operation = new Operation();
		operation.setOperationId(getOperationId(patchMapping.name(), method, HttpMethod.PATCH));
		operation.setSummary(StringUtils.isBlank(patchMapping.name()) ? patchMapping.name() : method.getName());
		operation.setTags(Collections.singletonList(classNameToTag(controllerClassName)));

		operation.setRequestBody(createRequestBody(method, getFirstFromArray(patchMapping.consumes())));
		operation.setResponses(createApiResponses(method, getFirstFromArray(patchMapping.produces())));
		operation.setParameters(transformParameters(operation, method));

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(patchMapping.value()), getFirstFromArray(patchMapping.path()));

		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setPatch(operation));
	}

	private void mapPut(PutMapping putMapping, Method method, Map<String, PathItem> operationsMap, String controllerClassName, String baseControllerPath) {
		Operation operation = new Operation();
		operation.setOperationId(getOperationId(putMapping.name(), method, HttpMethod.PUT));
		operation.setSummary(StringUtils.isBlank(putMapping.name()) ? putMapping.name() : method.getName());
		operation.setTags(Collections.singletonList(classNameToTag(controllerClassName)));

		operation.setRequestBody(createRequestBody(method, getFirstFromArray(putMapping.consumes())));
		operation.setResponses(createApiResponses(method, getFirstFromArray(putMapping.produces())));
		operation.setParameters(transformParameters(operation, method));

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(putMapping.value()), getFirstFromArray(putMapping.path()));

		updateOperationsMap(prepareUrl(baseControllerPath, "/", path), operationsMap, pathItem -> pathItem.setPut(operation));
	}

	private void mapPost(PostMapping postMapping, Method method, Map<String, PathItem> operationsMap, String controllerClassName, String baseControllerPath) {
		Operation operation = new Operation();
		operation.setOperationId(getOperationId(postMapping.name(), method, HttpMethod.POST));
		operation.setSummary(StringUtils.isBlank(postMapping.name()) ? postMapping.name() : method.getName());
		operation.setTags(Collections.singletonList(classNameToTag(controllerClassName)));

		operation.setRequestBody(createRequestBody(method, getFirstFromArray(postMapping.consumes())));
		operation.setResponses(createApiResponses(method, getFirstFromArray(postMapping.produces())));
		operation.setParameters(transformParameters(operation, method));

		String path = ObjectUtils.defaultIfNull(getFirstFromArray(postMapping.value()), getFirstFromArray(postMapping.path()));

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

	private List<io.swagger.v3.oas.models.parameters.Parameter> transformParameters(Operation operation, Method method) {
		String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
		Parameter[] parameters = method.getParameters();
		List<io.swagger.v3.oas.models.parameters.Parameter> result = new ArrayList<>();
		for (int i = 0; i < parameters.length; i++) {
			if (shuoldBeIncludedInDocumentation(parameters[i])) {
				io.swagger.v3.oas.models.parameters.Parameter oasParameter = mapQueryParameter(parameters[i], parameterNames[i]);
				if (oasParameter != null) {
					result.add(oasParameter);
				}
			}
		}
		return result;
	}

	private boolean shuoldBeIncludedInDocumentation(Parameter parameter) {
		return parameter.getAnnotation(PathVariable.class) != null || parameter.getAnnotation(RequestParam.class) != null
				|| parameter.getAnnotation(RequestHeader.class) != null;
	}

	private io.swagger.v3.oas.models.parameters.Parameter mapQueryParameter(Parameter parameter, String parameterName) {
		io.swagger.v3.oas.models.parameters.Parameter oasParameter = new io.swagger.v3.oas.models.parameters.Parameter();
		if (parameter.getAnnotation(PathVariable.class) != null) {
			PathVariable pathVariableAnnotation = parameter.getAnnotation(PathVariable.class);
			oasParameter.setName(resolveNameFromAnnotation(pathVariableAnnotation.name(), pathVariableAnnotation.value(), parameterName));
			oasParameter.setIn("path");
			oasParameter.setRequired(pathVariableAnnotation.required());
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
		return oasParameter;
	}

	private String resolveNameFromAnnotation(String nameFromAnnotation, String valueFromAnnotation, String reflectionParameterName) {
		return Stream.of(nameFromAnnotation, valueFromAnnotation, reflectionParameterName)
				.filter(StringUtils::isNotBlank)
				.findFirst()
				.orElse(null);
	}

	private RequestBody createRequestBody(Method method, String userDefinedContentType) {
		Parameter requestBodyParameter = getRequestBody(method);

		if (requestBodyParameter == null) {
			return null;
		}

		Content content = new Content();
		content.addMediaType(resolveContentType(userDefinedContentType, requestBodyParameter), createMediaType(requestBodyParameter.getType()));

		RequestBody requestBody = new RequestBody();
		requestBody.setRequired(true);
		requestBody.setContent(content);
		requestBody.setDescription("requestBody");
		return requestBody;
	}

	private MediaType createMediaType(Class<?> requestBodyParameter, Class<?>... genericParams) {
		Schema<?> rootMediaSchema = new Schema<>();
		if (isFile(requestBodyParameter)) {
			rootMediaSchema.setType("string");
			rootMediaSchema.setFormat("binary");
		} else if (requestBodyParameter.isAssignableFrom(List.class) && genericParams != null && genericParams.length > 0) {
			rootMediaSchema = parseArraySignature(genericParams[0], null, new Annotation[]{});
		} else if (!StringUtils.equalsIgnoreCase(requestBodyParameter.getSimpleName(), "void")) {
			rootMediaSchema.set$ref(COMPONENT_REF_PREFIX + requestBodyParameter.getSimpleName());
		} else {
			return null;
		}

		MediaType mediaType = new MediaType();
		mediaType.setSchema(rootMediaSchema);
		return mediaType;
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
		enrichWithTypeAnnotations(schema, annotations);
		return schema;
	}

	private String resolveContentType(String userDefinedContentType, Parameter requestBody) {
		if (StringUtils.isBlank(userDefinedContentType)) {
			return isFile(requestBody.getType()) ? MULTIPART_FORM_DATA_CONTENT_TYPE : DEFAULT_CONTENT_TYPE;
		}
		return userDefinedContentType;
	}

	private Parameter getRequestBody(Method method) {
		Parameter result = Stream.of(method.getParameters())
				.filter(parameter -> parameter.getAnnotation(org.springframework.web.bind.annotation.RequestBody.class) != null)
				.findFirst()
				.orElse(null);
		if (result == null) {
			return Stream.of(method.getParameters())
					.filter(parameter -> isFile(parameter.getType()))
					.findFirst()
					.orElse(null);
		}
		return result;
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
}

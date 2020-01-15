//package com.github.jrcodeza.schema.v2.generator.interceptors.examples;
//
//import io.swagger.models.Model;
//import io.swagger.models.parameters.BodyParameter;
//import io.swagger.v3.oas.models.examples.Example;
//import io.swagger.v3.oas.models.media.MediaType;
//import io.swagger.v3.oas.models.media.Schema;
//import io.swagger.v3.oas.models.parameters.RequestBody;
//import org.apache.commons.lang3.StringUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.lang.annotation.Annotation;
//import java.lang.reflect.AnnotatedElement;
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import java.lang.reflect.Parameter;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//import java.util.function.Consumer;
//import java.util.stream.Stream;
//
//import com.github.jrcodeza.OpenApiExample;
//import com.github.jrcodeza.OpenApiExamples;
//import com.github.jrcodeza.schema.v2.generator.interceptors.OperationParameterInterceptor;
//import com.github.jrcodeza.schema.v2.generator.interceptors.RequestBodyInterceptor;
//import com.github.jrcodeza.schema.v2.generator.interceptors.SchemaFieldInterceptor;
//import com.github.jrcodeza.schema.v2.generator.interceptors.SchemaInterceptor;
//
//import static org.apache.commons.lang3.StringUtils.defaultString;
//
//public class OperationParameterExampleInterceptor implements OperationParameterInterceptor, RequestBodyInterceptor, SchemaFieldInterceptor, SchemaInterceptor {
//
//	private static Logger logger = LoggerFactory.getLogger(OperationParameterExampleInterceptor.class);
//
//	private final OpenApiExampleResolver openApiExampleResolver;
//
//	public OperationParameterExampleInterceptor(OpenApiExampleResolver openApiExampleResolver) {
//		this.openApiExampleResolver = openApiExampleResolver;
//	}
//
//	@Override
//	public void intercept(Method method, Parameter parameter, String parameterName, io.swagger.models.parameters.Parameter transformedParameter) {
//		updateExamples(parameter, transformedParameter::setExample, transformedParameter::setExamples);
//	}
//
//	@Override
//	public void intercept(Method method, Parameter parameter, String parameterName, BodyParameter transformedRequestBody) {
//		MediaType requestBodyMediaType = getRequestBodyMediaType(transformedRequestBody);
//		if (requestBodyMediaType == null) {
//			return;
//		}
//		updateExamples(parameter, requestBodyMediaType::setExample, requestBodyMediaType::setExamples);
//	}
//
//	@Override
//	public void intercept(Class<?> clazz, Field field, Model transformedFieldSchema) {
//		updateExamples(field, transformedFieldSchema::setExample,
//					   stringExampleMap -> transformedFieldSchema.setExample(getFirstExampleFromMap(stringExampleMap)));
//	}
//
//	@Override
//	public void intercept(Class<?> clazz, Model transformedSchema) {
//		updateExamples(clazz, transformedSchema::setExample, stringExampleMap -> transformedSchema.setExample(getFirstExampleFromMap(stringExampleMap)));
//	}
//
//	private MediaType getRequestBodyMediaType(RequestBody transformedRequestBody) {
//		if (transformedRequestBody == null || transformedRequestBody.getContent() == null || transformedRequestBody.getContent().isEmpty()) {
//			return null;
//		}
//		Object object = ((LinkedHashMap) transformedRequestBody.getContent()).values().iterator().next();
//		if (object instanceof MediaType) {
//			return (MediaType) object;
//		}
//		return null;
//	}
//
//	private Example getFirstExampleFromMap(Map<String, Example> exampleMap) {
//		if (exampleMap == null || exampleMap.isEmpty()) {
//			return null;
//		}
//		return exampleMap.entrySet().iterator().next().getValue();
//	}
//
//	private void updateExamples(AnnotatedElement annotatedElement, Consumer<Example> exampleUpdater, Consumer<Map<String, Example>> examplesUpdater) {
//		List<OpenApiExample> openApiExamples = extractExampleAnnotations(annotatedElement.getAnnotations());
//		if (openApiExamples.isEmpty()) {
//			return;
//		}
//
//		if (openApiExamples.size() == 1) {
//			Example example = createExample(openApiExamples.get(0));
//			if (example != null) {
//				exampleUpdater.accept(example);
//			}
//		} else {
//			Map<String, Example> exampleMap = new HashMap<>();
//			openApiExamples.forEach(openApiExample -> {
//				Example example = createExample(openApiExample);
//				if (example != null) {
//					exampleMap.put(defaultString(openApiExample.name(), UUID.randomUUID().toString()), createExample(openApiExample));
//				}
//			});
//			examplesUpdater.accept(exampleMap);
//		}
//	}
//
//	private Example createExample(OpenApiExample openApiExample) {
//		if (StringUtils.isBlank(openApiExample.value())) {
//			if (StringUtils.isBlank(openApiExample.key())) {
//				logger.warn("None of [OpenApiExample.key,OpenApiExample.value] was defined.");
//				return null;
//			} else if (openApiExampleResolver == null) {
//				logger.warn("OpenApiExample.key was defined but no OpenApiExampleResolver was found.");
//				return null;
//			} else {
//				return createExample(openApiExampleResolver.resolveExample(openApiExample.key()), openApiExample.description());
//			}
//		} else {
//			return createExample(openApiExample.value(), openApiExample.description());
//		}
//	}
//
//	private Example createExample(String resolveExample, String description) {
//		Example example = new Example();
//		example.setValue(resolveExample);
//		example.setDescription(StringUtils.defaultIfBlank(description, null));
//		return example;
//	}
//
//	private List<OpenApiExample> extractExampleAnnotations(Annotation[] annotations) {
//		OpenApiExamples openApiExamplesAnnotation = getAnnotation(annotations, OpenApiExamples.class);
//		if (openApiExamplesAnnotation == null) {
//			OpenApiExample openApiExample = getAnnotation(annotations, OpenApiExample.class);
//			if (openApiExample == null) {
//				return Collections.emptyList();
//			}
//			return Collections.singletonList(openApiExample);
//		} else {
//			return Arrays.asList(openApiExamplesAnnotation.value());
//		}
//	}
//
//	private <T> T getAnnotation(Annotation[] annotations, Class<T> clazz) {
//		return (T) Stream.of(annotations)
//						 .filter(annotation -> annotation.annotationType().equals(clazz))
//						 .findFirst()
//						 .orElse(null);
//	}
//
//}

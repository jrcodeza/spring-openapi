package org.spring.openapi.schema.generator.plugin.interceptors;

import java.lang.reflect.Field;

import org.spring.openapi.schema.generator.interceptors.SchemaFieldInterceptor;

import io.swagger.v3.oas.models.media.Schema;

import static org.spring.openapi.schema.generator.plugin.util.TestUtils.emptyIfNull;

public class TestSchemaFieldInterceptor implements SchemaFieldInterceptor {

	@Override
	public void intercept(Class<?> clazz, Field field, Schema<?> transformedFieldSchema) {
		transformedFieldSchema.setDescription(emptyIfNull(transformedFieldSchema.getDescription()) + ". Test schemaField interceptor");
	}

}

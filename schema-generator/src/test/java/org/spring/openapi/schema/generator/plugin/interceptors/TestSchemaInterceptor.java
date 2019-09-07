package org.spring.openapi.schema.generator.plugin.interceptors;

import org.spring.openapi.schema.generator.interceptors.SchemaInterceptor;

import io.swagger.v3.oas.models.media.Schema;

import static org.spring.openapi.schema.generator.plugin.util.TestUtils.emptyIfNull;

public class TestSchemaInterceptor implements SchemaInterceptor {

	@Override
	public void intercept(Class<?> clazz, Schema<?> transformedSchema) {
		transformedSchema.setDescription(emptyIfNull(transformedSchema.getDescription()) + ". Test schema interceptors");
	}

}

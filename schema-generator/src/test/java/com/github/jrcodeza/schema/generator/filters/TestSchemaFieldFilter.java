package com.github.jrcodeza.schema.generator.filters;

import java.lang.reflect.Field;

public class TestSchemaFieldFilter implements SchemaFieldFilter {
    @Override
    public boolean shouldIgnore(Class<?> clazz, Field field) {
        return field.getName().equals("maxSpeed");
    }
}

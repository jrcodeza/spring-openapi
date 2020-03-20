package com.github.jrcodeza.schema.generator.filters;

import java.lang.reflect.Field;

public interface SchemaFieldFilter {
    boolean shouldIgnore(Class<?> clazz, Field field);
}

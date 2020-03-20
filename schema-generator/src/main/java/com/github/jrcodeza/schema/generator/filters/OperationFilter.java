package com.github.jrcodeza.schema.generator.filters;

import java.lang.reflect.Method;

public interface OperationFilter {
    boolean shouldIgnore(Method method);
}

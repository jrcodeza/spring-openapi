package org.spring.openapi.schema.generator;

import org.apache.maven.plugin.logging.Log;

public abstract class Transformer {

    private final Log log;

    public Transformer(Log log) {
        this.log = log;
    }

    public Log getLog() {
        return log;
    }
}

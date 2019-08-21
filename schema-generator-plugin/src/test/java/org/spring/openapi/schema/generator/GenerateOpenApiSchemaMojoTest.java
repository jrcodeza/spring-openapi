package org.spring.openapi.schema.generator;

import java.io.File;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.Test;
import org.spring.openapi.schema.generator.plugin.GenerateOpenApiSchemaMojo;

public class GenerateOpenApiSchemaMojoTest extends AbstractMojoTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testStandardScenario() throws Exception {
        File testPom = new File(getBasedir(), "src/test/resources/unit/generate-open-api-standard/pom.xml");
        GenerateOpenApiSchemaMojo mojo = (GenerateOpenApiSchemaMojo) lookupMojo("generateOpenApi", testPom);
        mojo.execute();

    }

}

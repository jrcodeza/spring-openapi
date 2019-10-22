package org.spring.openapi.client.generator;

import org.junit.Test;

public class OpenApiClientGeneratorTest {

	@Test
	public void testClientGenerated() {
		new OpenApiClientGenerator().generateClient(
				"test.openapi",
				"src/test/resources/input_example.json",
				"target/openapi",
				true,
				true);
	}

}

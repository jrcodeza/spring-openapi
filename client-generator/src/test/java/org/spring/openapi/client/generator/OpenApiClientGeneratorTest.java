package org.spring.openapi.client.generator;

import java.io.File;

import org.junit.Test;

public class OpenApiClientGeneratorTest {

	@Test
	public void testClientGenerated() {
		new OpenApiClientGenerator().generateClient("test.openapi",
				new File("C:\\Users\\jremenec.DAVINCI\\Projects\\spring-openapi\\client-generator\\src\\test\\resources\\input_openapi.json"));
	}

}

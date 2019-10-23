package com.github.jrcodeza.client.generator.plugin;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.spring.openapi.client.generator.OpenApiClientGenerator;

@Mojo(name = "generateClientFromOpenApi", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateClientFromSchemaMojo extends AbstractMojo {

	@Parameter
	private String outputPackage;

	@Parameter
	private String outputPath;

	@Parameter
	private String schemaPath;

	@Parameter
	private Boolean generateResourceInterface;

	@Parameter
	private Boolean generateDiscriminatorProperty;

	@Parameter(defaultValue = "${project}")
	private MavenProject project;

	@Override
	public void execute() {
		new OpenApiClientGenerator().generateClient(outputPackage, schemaPath, outputPath,
				BooleanUtils.isTrue(generateResourceInterface),
				BooleanUtils.isTrue(generateDiscriminatorProperty)
		);
		project.addCompileSourceRoot(outputPath);
	}
}

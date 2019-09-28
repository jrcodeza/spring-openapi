package org.spring.openapi.client.generator;

import java.io.File;
import java.nio.file.Path;

public class OpenApiClientGenerationSettings {

	private String targetPackage;

	private File schemaFile;
	private String schemaPathString;
	private Path schemaPath;

	private File targetFolder;
	private String targetFolderPathString;
	private Path targetFolderPath;

	public OpenApiClientGenerationSettings(String targetPackage, File schemaFile, File targetFolder) {
		this.targetPackage = targetPackage;
		this.schemaFile = schemaFile;
		this.targetFolder = targetFolder;
	}

	public OpenApiClientGenerationSettings(String targetPackage, File schemaFile, String targetFolderPathString) {
		this.targetPackage = targetPackage;
		this.schemaFile = schemaFile;
		this.targetFolderPathString = targetFolderPathString;
	}

	public OpenApiClientGenerationSettings(String targetPackage, File schemaFile, Path targetFolderPath) {
		this.targetPackage = targetPackage;
		this.schemaFile = schemaFile;
		this.targetFolderPath = targetFolderPath;
	}

	public OpenApiClientGenerationSettings(String targetPackage, String schemaPathString, File targetFolder) {
		this.targetPackage = targetPackage;
		this.schemaPathString = schemaPathString;
		this.targetFolder = targetFolder;
	}

	public OpenApiClientGenerationSettings(String targetPackage, String schemaPathString, String targetFolderPathString) {
		this.targetPackage = targetPackage;
		this.schemaPathString = schemaPathString;
		this.targetFolderPathString = targetFolderPathString;
	}


}

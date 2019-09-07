package org.spring.openapi.schema.generator.plugin.controller;

import java.util.List;

import org.spring.openapi.annotations.Header;
import org.spring.openapi.annotations.OpenApiIgnore;
import org.spring.openapi.annotations.Response;
import org.spring.openapi.annotations.Responses;
import org.spring.openapi.schema.generator.plugin.model.OptionsClass;
import org.spring.openapi.schema.generator.plugin.model.dummy.ValidationDummy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dummy")
public class DummyController {

	@RequestMapping(path = "/{id}", method = RequestMethod.HEAD)
	public ResponseEntity<Void> isPresent(@PathVariable("id") Integer id) {
		return null;
	}

	@RequestMapping(path = "/{id}", method = RequestMethod.OPTIONS)
	public ResponseEntity<OptionsClass> getOptions(@PathVariable("id") Integer id) {
		return null;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ValidationDummy create(@RequestBody @Validated ValidationDummy validationDummy) {
		return null;
	}

	@PutMapping("/{id}")
	@Responses({
			@Response(responseCode = 201, description = "Created", responseBody = ValidationDummy.class,
					headers = @Header(name = "SomeHeader", description = "TestHeader")),
			@Response(responseCode = 200, description = "Replaced", responseBody = ValidationDummy.class)
	})
	public ValidationDummy createOrReplace(@PathVariable(required = false) Integer id, @RequestBody @Validated ValidationDummy validationDummy) {
		return null;
	}

	@PatchMapping(path = "/{id}")
	public ValidationDummy patch(@PathVariable Integer id, @RequestBody @Validated ValidationDummy validationDummy) {
		return null;
	}

	@GetMapping("/{id}/subpath/{anotherId}")
	public ValidationDummy getTwoPathVariables(@PathVariable Integer id, @PathVariable("anotherId") Integer another) {
		return null;
	}

	@GetMapping("/{id}/subpath/")
	public ValidationDummy subpath(
			@RequestHeader String headerA,
			@RequestHeader("headerB") String headerB,
			@PathVariable Integer id,
			@RequestParam("requestParamA") String requestParamA,
			@RequestParam String requestParamB,
			@RequestParam(name = "requestParamC", required = false) String requestParamC
	) {
		return null;
	}

	@GetMapping("/onlyRequestParams")
	public ValidationDummy onlyRequestParams(
			@RequestParam("requestParamA") String requestParamA,
			@RequestParam String requestParamB,
			@RequestParam(name = "requestParamC", required = false) String requestParamC
	) {
		return null;
	}

	@PostMapping("/{id}/subpath/")
	public ValidationDummy complexPost(
			@RequestHeader String headerA,
			@RequestHeader("headerB") String headerB,
			@PathVariable Integer id,
			@RequestParam("requestParamA") String requestParamA,
			@RequestParam String requestParamB,
			@RequestParam(name = "requestParamC", required = false) String requestParamC,
			@RequestBody ValidationDummy testBody
	) {
		return null;
	}

	@DeleteMapping(path = "/{id}")
	public ValidationDummy delete(@PathVariable Integer id) {
		return null;
	}

	@GetMapping(path = "/requestParamList")
	public ValidationDummy requestParamList(@RequestParam List<ValidationDummy> validationDummies) {
		return null;
	}

	@PostMapping(path = "/requestBodyList")
	public ValidationDummy requestBodyList(@RequestBody List<ValidationDummy> validationDummies,
										   @RequestParam @OpenApiIgnore String toBeIgnored) {
		return null;
	}

	@PostMapping(path = "/toBeIgnored")
	@OpenApiIgnore
	public ValidationDummy methodToBeIgnored(@RequestBody List<ValidationDummy> validationDummies) {
		return null;
	}

	@PatchMapping(path = "/bodyToBeIgnored/{variable}")
	@OpenApiIgnore
	public ValidationDummy methodToBeIgnored(
			@PathVariable String variable,
			@RequestBody @OpenApiIgnore List<ValidationDummy> validationDummies) {
		return null;
	}
}

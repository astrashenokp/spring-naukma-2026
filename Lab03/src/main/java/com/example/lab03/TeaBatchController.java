package com.example.lab03;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/tea-batches")
public class TeaBatchController {

	private final TeaBatchService teaBatchService;

	public TeaBatchController(TeaBatchService teaBatchService) {
		this.teaBatchService = teaBatchService;
	}

	@PostMapping
	public ResponseEntity<TeaBatchResponse> register(@RequestBody RegisterTeaBatchRequest request) {
		TeaBatchResponse response = teaBatchService.registerBatch(request);

		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(response.id())
				.toUri();

		return ResponseEntity.created(location).body(response);
	}

	@GetMapping("/{id}")
	public TeaBatchResponse getById(@PathVariable String id) {
		return teaBatchService.getBatch(id);
	}

	@PatchMapping("/{id}/status")
	public TeaBatchResponse updateStatus(@PathVariable String id, @RequestBody UpdateTeaBatchStatusRequest request) {
		return teaBatchService.updateStatus(id, request);
	}

}

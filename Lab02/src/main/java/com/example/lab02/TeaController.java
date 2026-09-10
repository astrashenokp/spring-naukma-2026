package com.example.lab02;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/teas")
public class TeaController {

	private final Map<UUID, TeaResponse> storage = new ConcurrentHashMap<>();

	@GetMapping
	public List<TeaResponse> getAll() {
		return new ArrayList<>(storage.values());
	}

	@GetMapping("/{id}")
	public ResponseEntity<TeaResponse> getById(@PathVariable UUID id) {
		TeaResponse tea = storage.get(id);

		if (tea == null) {
			throw new TeaNotFoundException("Чай " + id + " не знайдено");
		}

		return ResponseEntity.ok(tea);
	}

	@PostMapping
	public ResponseEntity<Void> create(@RequestBody @Valid TeaCreateRequest request) {
		UUID id = UUID.randomUUID();

		storage.put(id, new TeaResponse(
				id,
				request.name(),
				request.type(),
				request.originRegion(),
				request.brewTemperatureC(),
				request.brewTimeSeconds(),
				request.supplierEmail(),
				request.harvestedOn()
		));

		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(id)
				.toUri();

		return ResponseEntity.created(location).build();
	}

	@PutMapping("/{id}")
	public ResponseEntity<TeaResponse> update(@PathVariable UUID id, @RequestBody @Valid TeaCreateRequest request) {
		if (!storage.containsKey(id)) {
			throw new TeaNotFoundException("Чай " + id + " не знайдено");
		}

		TeaResponse tea = new TeaResponse(
				id,
				request.name(),
				request.type(),
				request.originRegion(),
				request.brewTemperatureC(),
				request.brewTimeSeconds(),
				request.supplierEmail(),
				request.harvestedOn()
		);

		storage.put(id, tea);

		return ResponseEntity.ok(tea);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		if (storage.remove(id) == null) {
			throw new TeaNotFoundException("Чай " + id + " не знайдено");
		}

		return ResponseEntity.noContent().build();
	}

}

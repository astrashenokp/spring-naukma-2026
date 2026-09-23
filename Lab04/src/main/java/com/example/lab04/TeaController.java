package com.example.lab04;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/teas")
public class TeaController {

	private final TeaService teaService;

	public TeaController(TeaService teaService) {
		this.teaService = teaService;
	}

	@GetMapping
	public List<Tea> findAll() {
		return teaService.findAll();
	}

	@GetMapping("/search")
	public List<Tea> search(@RequestParam String name) {
		return teaService.search(name);
	}

	@GetMapping("/{id}")
	public Tea findById(@PathVariable long id) {
		return teaService.findById(id);
	}

	@PostMapping
	public ResponseEntity<Tea> create(@RequestBody TeaRequest request) {
		Tea created = teaService.create(request);

		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();

		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	public Tea update(@PathVariable long id, @RequestBody TeaRequest request) {
		return teaService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable long id) {
		teaService.delete(id);
		return ResponseEntity.noContent().build();
	}

}

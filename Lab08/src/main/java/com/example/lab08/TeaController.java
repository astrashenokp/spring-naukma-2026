package com.example.lab08;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/teas")
public class TeaController {

	private final TeaRepository repository;

	public TeaController(TeaRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	public List<Tea> findAll() {
		return repository.findAll();
	}

	@GetMapping("/{id}")
	public Tea findById(@PathVariable Long id) {
		return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Tea create(@Valid @RequestBody TeaRequest request) {
		return repository.save(new Tea(request.name(), request.teaType()));
	}

	@PutMapping("/{id}")
	public Tea update(@PathVariable Long id, @Valid @RequestBody TeaRequest request) {
		Tea tea = findById(id);
		tea.update(request.name(), request.teaType());
		return repository.save(tea);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		repository.delete(findById(id));
	}

}

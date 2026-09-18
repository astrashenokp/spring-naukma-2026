package com.example.lab03;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTeaBatchRepository implements TeaBatchRepository {

	private final Map<String, TeaBatch> storage = new ConcurrentHashMap<>();

	@Override
	public TeaBatch save(TeaBatch teaBatch) {
		storage.put(teaBatch.getId(), teaBatch);
		return teaBatch;
	}

	@Override
	public Optional<TeaBatch> findById(String id) {
		return Optional.ofNullable(storage.get(id));
	}

	@Override
	public boolean existsById(String id) {
		return storage.containsKey(id);
	}

}

package com.example.lab03;

import java.util.Optional;

public interface TeaBatchRepository {

	TeaBatch save(TeaBatch teaBatch);

	Optional<TeaBatch> findById(String id);

	boolean existsById(String id);

}

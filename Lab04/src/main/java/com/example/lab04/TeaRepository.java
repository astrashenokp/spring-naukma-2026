package com.example.lab04;

import java.util.List;
import java.util.Optional;

public interface TeaRepository {

	List<Tea> findAll();

	Optional<Tea> findById(long id);

	List<Tea> findByName(String name);

	Tea insert(TeaRequest request);

	boolean update(long id, TeaRequest request);

	boolean deleteById(long id);

}

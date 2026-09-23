package com.example.lab04;

import java.util.List;

public interface TeaService {

	List<Tea> findAll();

	Tea findById(long id);

	List<Tea> search(String name);

	Tea create(TeaRequest request);

	Tea update(long id, TeaRequest request);

	void delete(long id);

}

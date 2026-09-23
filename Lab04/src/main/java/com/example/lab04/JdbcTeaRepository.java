package com.example.lab04;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTeaRepository implements TeaRepository {

	private final JdbcClient jdbcClient;

	public JdbcTeaRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public List<Tea> findAll() {
		return jdbcClient.sql("SELECT id, name, tea_type, origin_region FROM teas ORDER BY id")
				.query(Tea.class)
				.list();
	}

	@Override
	public Optional<Tea> findById(long id) {
		return jdbcClient.sql("SELECT id, name, tea_type, origin_region FROM teas WHERE id = :id")
				.param("id", id)
				.query(Tea.class)
				.optional();
	}

	@Override
	public List<Tea> findByName(String name) {
		return jdbcClient.sql("SELECT id, name, tea_type, origin_region FROM teas WHERE LOWER(name) LIKE LOWER(:name) ORDER BY id")
				.param("name", "%" + name + "%")
				.query(Tea.class)
				.list();
	}

	@Override
	public Tea insert(TeaRequest request) {
		KeyHolder keyHolder = new GeneratedKeyHolder();

		jdbcClient.sql("INSERT INTO teas (name, tea_type, origin_region) VALUES (:name, :teaType, :originRegion)")
				.param("name", request.name())
				.param("teaType", request.teaType())
				.param("originRegion", request.originRegion())
				.update(keyHolder, "id");

		Long id = keyHolder.getKey().longValue();
		return new Tea(id, request.name(), request.teaType(), request.originRegion());
	}

	@Override
	public boolean update(long id, TeaRequest request) {
		int rows = jdbcClient.sql("UPDATE teas SET name = :name, tea_type = :teaType, origin_region = :originRegion WHERE id = :id")
				.param("name", request.name())
				.param("teaType", request.teaType())
				.param("originRegion", request.originRegion())
				.param("id", id)
				.update();

		return rows > 0;
	}

	@Override
	public boolean deleteById(long id) {
		int rows = jdbcClient.sql("DELETE FROM teas WHERE id = :id")
				.param("id", id)
				.update();

		return rows > 0;
	}

}

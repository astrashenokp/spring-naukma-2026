package com.example.lab07;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class TeaService {

	private final Map<Long, Tea> teas = new ConcurrentSkipListMap<>();
	private final AtomicLong nextId = new AtomicLong(1);

	public List<Tea> findAll() {
		return List.copyOf(teas.values());
	}

	public Tea addTea(String name, String teaType) {
		long id = nextId.getAndIncrement();
		Tea tea = new Tea(id, name, teaType);
		teas.put(id, tea);
		return tea;
	}

}

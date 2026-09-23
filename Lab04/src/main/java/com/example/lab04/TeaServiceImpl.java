package com.example.lab04;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class TeaServiceImpl implements TeaService {

	private final TeaRepository teaRepository;

	public TeaServiceImpl(TeaRepository teaRepository) {
		this.teaRepository = teaRepository;
	}

	@Override
	public List<Tea> findAll() {
		return teaRepository.findAll();
	}

	@Override
	public Tea findById(long id) {
		return teaRepository.findById(id)
				.orElseThrow(() -> new TeaNotFoundException(id));
	}

	@Override
	public List<Tea> search(String name) {
		return teaRepository.findByName(name);
	}

	@Override
	public Tea create(TeaRequest request) {
		return teaRepository.insert(request);
	}

	@Override
	public Tea update(long id, TeaRequest request) {
		if (!teaRepository.update(id, request)) {
			throw new TeaNotFoundException(id);
		}

		return findById(id);
	}

	@Override
	public void delete(long id) {
		if (!teaRepository.deleteById(id)) {
			throw new TeaNotFoundException(id);
		}
	}

}

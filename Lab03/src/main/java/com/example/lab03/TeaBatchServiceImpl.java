package com.example.lab03;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class TeaBatchServiceImpl implements TeaBatchService {

	private final TeaBatchRepository teaBatchRepository;
	private final Map<TeaType, ShelfLifeStrategy> strategies;

	public TeaBatchServiceImpl(TeaBatchRepository teaBatchRepository, List<ShelfLifeStrategy> strategyList) {
		this.teaBatchRepository = teaBatchRepository;
		this.strategies = strategyList.stream()
				.collect(Collectors.toMap(ShelfLifeStrategy::getTeaType, Function.identity()));
	}

	@Override
	public TeaBatchResponse registerBatch(RegisterTeaBatchRequest request) {
		if (teaBatchRepository.existsById(request.id())) {
			throw new DuplicateTeaBatchException("Tea batch with ID '" + request.id() + "' already exists");
		}

		ShelfLifeStrategy strategy = strategies.get(request.teaType());
		int shelfLifeDays = strategy.calculateShelfLifeDays();

		TeaBatch teaBatch = new TeaBatch(
				request.id(),
				request.teaType(),
				request.supplierName(),
				shelfLifeDays,
				TeaBatchStatus.IN_STOCK
		);

		TeaBatch saved = teaBatchRepository.save(teaBatch);
		return TeaBatchResponse.from(saved);
	}

	@Override
	public TeaBatchResponse getBatch(String id) {
		TeaBatch teaBatch = teaBatchRepository.findById(id)
				.orElseThrow(() -> new TeaBatchNotFoundException("Tea batch with ID '" + id + "' not found"));

		return TeaBatchResponse.from(teaBatch);
	}

	@Override
	public TeaBatchResponse updateStatus(String id, UpdateTeaBatchStatusRequest request) {
		TeaBatch teaBatch = teaBatchRepository.findById(id)
				.orElseThrow(() -> new TeaBatchNotFoundException("Tea batch with ID '" + id + "' not found"));

		TeaBatchStatus currentStatus = teaBatch.getStatus();
		TeaBatchStatus targetStatus = request.targetStatus();

		if (!currentStatus.canTransitionTo(targetStatus)) {
			throw new InvalidTeaBatchStateException(
					"Illegal state transition for tea batch '" + id + "' from " + currentStatus + " to " + targetStatus);
		}

		teaBatch.setStatus(targetStatus);
		TeaBatch updated = teaBatchRepository.save(teaBatch);
		return TeaBatchResponse.from(updated);
	}

}

package com.example.lab03;

public record TeaBatchResponse(
		String id,
		TeaType teaType,
		String supplierName,
		TeaBatchStatus status,
		int shelfLifeDays
) {

	public static TeaBatchResponse from(TeaBatch teaBatch) {
		return new TeaBatchResponse(
				teaBatch.getId(),
				teaBatch.getTeaType(),
				teaBatch.getSupplierName(),
				teaBatch.getStatus(),
				teaBatch.getShelfLifeDays()
		);
	}

}

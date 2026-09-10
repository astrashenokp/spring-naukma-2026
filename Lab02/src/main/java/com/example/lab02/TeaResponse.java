package com.example.lab02;

import java.time.LocalDate;
import java.util.UUID;

public record TeaResponse(
		UUID id,
		String name,
		TeaType type,
		String originRegion,
		Integer brewTemperatureC,
		Integer brewTimeSeconds,
		String supplierEmail,
		LocalDate harvestedOn
) {
}

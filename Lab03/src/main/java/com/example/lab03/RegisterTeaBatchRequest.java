package com.example.lab03;

public record RegisterTeaBatchRequest(
		String id,
		TeaType teaType,
		String supplierName
) {
}

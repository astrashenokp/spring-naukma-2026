package com.example.lab08;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeaRequest(

		@NotBlank
		@Size(min = 2, max = 50)
		String name,

		@NotBlank
		@Size(min = 2, max = 20)
		String teaType

) {
}

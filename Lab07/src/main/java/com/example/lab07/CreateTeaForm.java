package com.example.lab07;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeaForm(

		@NotBlank(message = "Назва є обов’язковою")
		@Size(min = 2, max = 50, message = "Назва має містити від 2 до 50 символів")
		String name,

		@NotBlank(message = "Тип чаю є обов’язковим")
		@Size(min = 2, max = 20, message = "Тип чаю має містити від 2 до 20 символів")
		String teaType

) {
}

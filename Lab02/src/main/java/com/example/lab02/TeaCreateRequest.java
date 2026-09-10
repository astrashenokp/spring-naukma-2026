package com.example.lab02;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record TeaCreateRequest(

		@NotBlank(message = "Вкажи назву")
		@Size(min = 2, max = 80, message = "Назва від 2 до 80 символів")
		String name,

		@NotNull(message = "Вкажи тип чаю")
		TeaType type,

		@NotBlank(message = "Вкажи регіон")
		@Size(max = 100, message = "Регіон до 100 символів")
		String originRegion,

		@NotNull(message = "Вкажи температуру")
		@Min(value = 50, message = "Температура мінімум 50 градусів")
		@Max(value = 100, message = "Температура максимум 100 градусів")
		Integer brewTemperatureC,

		@NotNull(message = "Вкажи час заварювання")
		@Min(value = 10, message = "Заварювати мінімум 10 секунд")
		@Max(value = 900, message = "Заварювати максимум 900 секунд")
		Integer brewTimeSeconds,

		@NotBlank(message = "Вкажи пошту постачальника")
		@Email(message = "Неправильна пошта")
		String supplierEmail,

		@NotNull(message = "Вкажи дату збору")
		@Past(message = "Дата збору не може бути в майбутньому")
		LocalDate harvestedOn
) {
}

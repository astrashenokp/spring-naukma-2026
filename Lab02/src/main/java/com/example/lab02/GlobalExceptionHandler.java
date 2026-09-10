package com.example.lab02;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Дані неправильні");
		pd.setTitle("Помилка валідації");
		pd.setType(URI.create("https://api.example.com/errors/validation"));

		Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(
						FieldError::getField,
						FieldError::getDefaultMessage,
						(first, second) -> first));

		pd.setProperty("errors", errors);

		return pd;
	}

	@ExceptionHandler(TeaNotFoundException.class)
	public ProblemDetail handleNotFound(TeaNotFoundException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(
				HttpStatus.NOT_FOUND, ex.getMessage());
		pd.setTitle("Не знайдено");
		pd.setType(URI.create("https://api.example.com/errors/not-found"));

		return pd;
	}

}

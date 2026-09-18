package com.example.lab03;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(DuplicateTeaBatchException.class)
	public ProblemDetail handleDuplicate(DuplicateTeaBatchException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problem.setTitle("Партія вже існує");
		problem.setType(URI.create("https://api.example.com/errors/duplicate"));
		return problem;
	}

	@ExceptionHandler(TeaBatchNotFoundException.class)
	public ProblemDetail handleNotFound(TeaBatchNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Не знайдено");
		problem.setType(URI.create("https://api.example.com/errors/not-found"));
		return problem;
	}

	@ExceptionHandler(InvalidTeaBatchStateException.class)
	public ProblemDetail handleInvalidState(InvalidTeaBatchStateException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
		problem.setTitle("Неприпустимий перехід стану");
		problem.setType(URI.create("https://api.example.com/errors/invalid-state"));
		return problem;
	}

}

package com.example.lab04;

public class TeaNotFoundException extends RuntimeException {

	public TeaNotFoundException(long id) {
		super("Чай " + id + " не знайдено");
	}

}

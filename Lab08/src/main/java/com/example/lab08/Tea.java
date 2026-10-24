package com.example.lab08;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Tea {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private String teaType;

	protected Tea() {
	}

	public Tea(String name, String teaType) {
		this.name = name;
		this.teaType = teaType;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getTeaType() {
		return teaType;
	}

	public void update(String name, String teaType) {
		this.name = name;
		this.teaType = teaType;
	}

}

package com.example.lab05;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teas")
public class TeaController {

	private static final Logger log = LoggerFactory.getLogger(TeaController.class);

	private static final List<Tea> TEAS = List.of(
			new Tea(1L, "Да Хун Пао", "OOLONG"),
			new Tea(2L, "Сенча", "GREEN"));

	@GetMapping
	public List<Tea> findAll() {
		log.info("Повернення списку чаїв, кількість: {}", TEAS.size());
		return TEAS;
	}

}

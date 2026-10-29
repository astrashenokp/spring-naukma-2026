package com.example.lab09;

import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SteepFunctionConfig {

	@Bean
	public Function<SteepRequest, SteepAdvice> steepAdvice() {
		return request -> switch (request.teaType()) {
			case "GREEN" -> new SteepAdvice(request.name(), 80, 120);
			case "BLACK" -> new SteepAdvice(request.name(), 95, 240);
			case "OOLONG" -> new SteepAdvice(request.name(), 90, 180);
			default -> new SteepAdvice(request.name(), 95, 180);
		};
	}

}

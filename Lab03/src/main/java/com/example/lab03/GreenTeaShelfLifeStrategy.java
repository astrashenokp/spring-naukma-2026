package com.example.lab03;

import org.springframework.stereotype.Component;

@Component
public class GreenTeaShelfLifeStrategy implements ShelfLifeStrategy {

	@Override
	public TeaType getTeaType() {
		return TeaType.GREEN;
	}

	@Override
	public int calculateShelfLifeDays() {
		return 365;
	}

}

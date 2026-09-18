package com.example.lab03;

import org.springframework.stereotype.Component;

@Component
public class BlackTeaShelfLifeStrategy implements ShelfLifeStrategy {

	@Override
	public TeaType getTeaType() {
		return TeaType.BLACK;
	}

	@Override
	public int calculateShelfLifeDays() {
		return 730;
	}

}

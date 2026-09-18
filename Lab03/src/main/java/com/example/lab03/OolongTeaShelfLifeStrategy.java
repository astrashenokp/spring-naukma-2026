package com.example.lab03;

import org.springframework.stereotype.Component;

@Component
public class OolongTeaShelfLifeStrategy implements ShelfLifeStrategy {

	@Override
	public TeaType getTeaType() {
		return TeaType.OOLONG;
	}

	@Override
	public int calculateShelfLifeDays() {
		return 545;
	}

}

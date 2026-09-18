package com.example.lab03;

import org.springframework.stereotype.Component;

@Component
public class WhiteTeaShelfLifeStrategy implements ShelfLifeStrategy {

	@Override
	public TeaType getTeaType() {
		return TeaType.WHITE;
	}

	@Override
	public int calculateShelfLifeDays() {
		return 365;
	}

}

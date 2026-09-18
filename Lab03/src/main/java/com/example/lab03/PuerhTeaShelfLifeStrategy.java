package com.example.lab03;

import org.springframework.stereotype.Component;

@Component
public class PuerhTeaShelfLifeStrategy implements ShelfLifeStrategy {

	@Override
	public TeaType getTeaType() {
		return TeaType.PUERH;
	}

	@Override
	public int calculateShelfLifeDays() {
		return 3650;
	}

}

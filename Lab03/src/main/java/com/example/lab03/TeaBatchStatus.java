package com.example.lab03;

public enum TeaBatchStatus {
	IN_STOCK,
	QUARANTINE,
	DISCONTINUED;

	public boolean canTransitionTo(TeaBatchStatus next) {
		return switch (this) {
			case IN_STOCK -> next == QUARANTINE || next == DISCONTINUED;
			case QUARANTINE -> next == IN_STOCK || next == DISCONTINUED;
			case DISCONTINUED -> false;
		};
	}
}

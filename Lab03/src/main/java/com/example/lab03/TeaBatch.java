package com.example.lab03;

public class TeaBatch {

	private final String id;
	private final TeaType teaType;
	private final String supplierName;
	private final int shelfLifeDays;
	private TeaBatchStatus status;

	public TeaBatch(String id, TeaType teaType, String supplierName, int shelfLifeDays, TeaBatchStatus status) {
		this.id = id;
		this.teaType = teaType;
		this.supplierName = supplierName;
		this.shelfLifeDays = shelfLifeDays;
		this.status = status;
	}

	public String getId() {
		return id;
	}

	public TeaType getTeaType() {
		return teaType;
	}

	public String getSupplierName() {
		return supplierName;
	}

	public int getShelfLifeDays() {
		return shelfLifeDays;
	}

	public TeaBatchStatus getStatus() {
		return status;
	}

	public void setStatus(TeaBatchStatus status) {
		this.status = status;
	}

}

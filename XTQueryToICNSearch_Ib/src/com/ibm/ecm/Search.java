package com.ibm.ecm;

public class Search {
	private String name;
	private String status;

	public Search(String name) {
		this.name = name;
		this.status = "pending";
	}

	public Search(String name, String status) {
		this.name = name;
		this.status = status;
	}

	public String getName() {
		return this.name;
	}

	public String getStatus() {
		return this.status;
	}
}

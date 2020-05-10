package com.ibm.ecm;

public class ObjectStoreData {
	private String name;
	private String symbolicName;
	private String id;
	
	public ObjectStoreData(String name, String symbolicName, String id) {
		this.setName(name);
		this.setSymbolicName(symbolicName);
		this.setId(id);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSymbolicName() {
		return symbolicName;
	}

	public void setSymbolicName(String symbolicName) {
		this.symbolicName = symbolicName;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

}

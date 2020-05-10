package com.ibm.ecm;

public class PropertyInfo {
	
	private String displayName;
	private String symbolicName;
	private String dataType;
	
	public PropertyInfo(String displayName, String symbolicName, String dataType) {
		this.setDisplayName(displayName);
		this.setSymbolicName(symbolicName);
		this.setDataType(dataType);
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getSymbolicName() {
		return symbolicName;
	}

	public void setSymbolicName(String symbolicName) {
		this.symbolicName = symbolicName;
	}

	public String getDataType() {
		return dataType;
	}

	public void setDataType(String dataType) {
		this.dataType = dataType;
	}

}

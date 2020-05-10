package com.ibm.ecm;

import java.util.ArrayList;

public class UserSearches {
	private ArrayList<Search> searches;
	private String sid;

	public UserSearches(String sid, ArrayList<Search> searches) {
		this.searches = searches;
		this.sid = sid;
	}

	public ArrayList<Search> getSearches() {
		return this.searches;
	}

	public String getSid() {
		return this.sid;
	}
}

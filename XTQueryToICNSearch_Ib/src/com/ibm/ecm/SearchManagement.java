package com.ibm.ecm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.filenet.api.collection.ContentElementList;
import com.filenet.api.collection.RepositoryRowSet;
import com.filenet.api.core.Connection;
import com.filenet.api.core.ContentTransfer;
import com.filenet.api.core.Document;
import com.filenet.api.core.Factory;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.query.RepositoryRow;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;
import com.filenet.api.util.Id;

public class SearchManagement {

	private static String SEARCH_MANAGEMENT_FILE = "SearchManagement.json";
	private static String XT_USERS_PATH;

	private static FileWriter searchMngFileWriter;

	private static Logger logger;

	public SearchManagement(String XT_USERS_PATH, Logger logger) {
		this.logger = logger;
		this.XT_USERS_PATH = XT_USERS_PATH;
	}

	public Map<String, Map<String, String>> getUserSearchesMap(Connection con, ObjectStore objStore) {
		Map<String, Map<String, String>> userSearchesMap = new HashMap<>();

		try {
			File file = new File(SEARCH_MANAGEMENT_FILE);
			if (!file.exists() || file.length() == 0) {
				loadUserSearchesFromFN(con, objStore, userSearchesMap);
				writeSM(userSearchesMap);
			} else {
				loadUserSearchesFromFile(userSearchesMap);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

		return userSearchesMap;
	}

	public void writeSM(Map<String, Map<String, String>> userSearchesMap) throws IOException {

		JSONArray userSearchesJSONArray = new JSONArray();
		JSONObject userInfoJSONObject = new JSONObject();

		Iterator it = userSearchesMap.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
			String sid = (String) pair.getKey();
			Map<String, String> searches = (Map<String, String>) pair.getValue();

			userInfoJSONObject = new JSONObject();
			JSONObject userSearchesJSONObject = new JSONObject();

			searches.forEach((k, v) -> userSearchesJSONObject.put(k, v));

			userInfoJSONObject.put("SID", sid);
			userInfoJSONObject.put("searches", userSearchesJSONObject);

			userSearchesJSONArray.add(userInfoJSONObject);

		}

		try {
			searchMngFileWriter = new FileWriter(SEARCH_MANAGEMENT_FILE);
			searchMngFileWriter.write(userSearchesJSONArray.toJSONString());
		} catch (IOException e1) {
			logger.error(e1.getMessage(), e1);
		} finally {
			try {
				searchMngFileWriter.flush();
				searchMngFileWriter.close();
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}

	}

	private void loadUserSearchesFromFN(Connection con, ObjectStore objStore,
			Map<String, Map<String, String>> userSearchesMap) {
		String sid = "";

		String mySQLString = "SELECT Id,DocumentTitle FROM PreferencesDocument where isCurrentVersion=True and DocumentTitle like 'Us%'";
		SearchSQL sqlObject = new SearchSQL();
		sqlObject.setQueryString(mySQLString);
		SearchScope searchScope = new SearchScope(objStore);
		RepositoryRowSet rowSet = searchScope.fetchRows(sqlObject, null, null, new Boolean(true));

		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(XT_USERS_PATH));

			Iterator iter = rowSet.iterator();
			while (iter.hasNext()) {
				RepositoryRow row = (RepositoryRow) iter.next();
				Id userprefId = row.getProperties().get("Id").getIdValue();
				String docTitle = row.getProperties().get("DocumentTitle").getStringValue();
				Document userprefdoc = Factory.Document.fetchInstance(objStore, userprefId, null);

				Map<String, String> searchesMap = new HashMap<>();
				sid = docTitle.substring(docTitle.indexOf("for ") + 4, docTitle.indexOf(" on "));

				ContentElementList docContentList = userprefdoc.get_ContentElements();
				Iterator iterc = docContentList.iterator();
				while (iterc.hasNext()) {
					ContentTransfer ct = (ContentTransfer) iterc.next();
					InputStream stream = ct.accessContentStream();
					int docLen = ct.get_ContentSize().intValue();
					byte[] buf = new byte[docLen];
					String readStr = "";
					try {
						stream.read(buf);
						readStr = new String(buf);
						stream.close();
					} catch (IOException ioe) {
						logger.error(ioe.getMessage(), ioe);
					}

					Reader inputString = new StringReader(readStr);
					BufferedReader bufferedReader = new BufferedReader(inputString);
					String sline;
					String searchName = "";
					while ((sline = bufferedReader.readLine()) != null) {
						if (sline.startsWith("<object key=\"xtQuery\">")) {
							searchName = "";
						} else if (sline.startsWith("<setting key=\"searchName\">")) {
							searchName = sline.substring("<setting key=\"searchName\">".length(),
									sline.indexOf("</setting>"));

							searchesMap.put(searchName, "pending");
						} else if (sline.startsWith("<setting key=\"includeSubclasses\">") && searchName.isEmpty()) {
							searchesMap.put("Default Advanced Search", "pending");
						}
					}
				}

				userSearchesMap.put(sid, searchesMap);
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} finally {
			try {
				writer.flush();
				writer.close();
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	private void loadUserSearchesFromFile(Map<String, Map<String, String>> userSearchesMap) {
		try {

			FileReader reader = new FileReader(SEARCH_MANAGEMENT_FILE);
			Object obj = new JSONParser().parse(reader);
			JSONArray userSearchesJSONArray = (JSONArray) obj;
			userSearchesJSONArray
					.forEach(aUserSearches -> parseUserSearchesObject((JSONObject) aUserSearches, userSearchesMap));

		} catch (FileNotFoundException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} catch (ParseException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private void parseUserSearchesObject(JSONObject aUserSearches, Map<String, Map<String, String>> userSearchesMap) {

		String sid = (String) aUserSearches.get("SID");
		JSONObject searchesObject = (JSONObject) aUserSearches.get("searches");

		Map<String, String> searchesMap = new HashMap<>();

		for (Iterator iterator = searchesObject.keySet().iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();
			searchesMap.put(key, (String) searchesObject.get(key));
		}

		userSearchesMap.put(sid, searchesMap);
	}
}

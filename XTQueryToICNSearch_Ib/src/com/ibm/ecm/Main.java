package com.ibm.ecm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

import com.filenet.api.collection.ContentElementList;
import com.filenet.api.collection.RepositoryRowSet;
import com.filenet.api.core.Connection;
import com.filenet.api.core.ContentTransfer;
import com.filenet.api.core.Document;
import com.filenet.api.core.Domain;
import com.filenet.api.core.Factory;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.query.RepositoryRow;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;
import com.filenet.api.security.User;
import com.filenet.api.util.Id;
import com.filenet.api.util.UserContext;

public class Main {

	private static Connection sourceCon;
	private static Domain sourceDom;
	private static ObjectStore sourceObjStore;

	private static ArrayList<Connection> connections;

	private static Logger logger;

	private static Properties properties = null;

	private static String AdminUser = null;
	private static String AdminGroup = null;
	private static String XT_USERS_PATH;
	private static String JSON_PATH;
	private static String XML_PATH;
	private static String XT_QUERY_PATH;

	private static String SERVER_VALUE;
	private static String USER_VALUE;
	private static String PWD_VALUE;
	private static String OBJECT_STORE_VALUE;
	private static String LOG_FILE_VALUE;
	private static String LOG_LEVEL_VALUE;
	private static String DEST_SERVERS_VALUE;

	private static String USER_PREF_QUERIES_FILE = "ExtractUserPrefQueries.properties";

	private static Scanner input;
	private static int ixtquery = 0;

	private static SearchManagement searchManagement;
	private static Map<String, Map<String, String>> userSearchesMap;

	private static Map<String, String> destinationServers;

	private static boolean developerMode = true;

	public static void main(String[] args) {
//		BasicConfigurator.configure();
//		org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);

		loadProps();

		setLogging();

		try {
			stablishOriginConnection();

			loadSearchManagement();

			readDestinationServersFile();

			runMenu();

		} catch (EngineRuntimeException e) {
			logger.error(e);
			logger.error("FN connection could not be stablished. Please, check your connection");
		}

	}

	public static void loadSearchManagement() {
		searchManagement = new SearchManagement(XT_USERS_PATH, logger);
		userSearchesMap = searchManagement.getUserSearchesMap(sourceCon, sourceObjStore);
		logger.debug("Loaded Search Management info");
	}

	public static void setLogging() {
		// creates pattern layout
		PatternLayout fileLayout = new PatternLayout();
		String conversionPattern = "%-7p %d [%t] [%l] %c %x - %m%n";
		fileLayout.setConversionPattern(conversionPattern);

		PatternLayout consoleLayout = new PatternLayout();
		String consoleConversionPattern = "%m%n";
		consoleLayout.setConversionPattern(consoleConversionPattern);

		// creates console appender
		ConsoleAppender consoleAppender = new ConsoleAppender();
		consoleAppender.setLayout(consoleLayout);
		consoleAppender.activateOptions();

		// creates file appender
		RollingFileAppender fileAppender = new RollingFileAppender();
		fileAppender.setFile(LOG_FILE_VALUE);
		fileAppender.setLayout(fileLayout);
		fileAppender.activateOptions();
		fileAppender.setThreshold(Level.DEBUG);

		// configures the root logger
		Logger rootLogger = Logger.getRootLogger();
		rootLogger.setLevel(Level.DEBUG);
//		if (developerMode)
//			rootLogger.addAppender(consoleAppender);
		rootLogger.addAppender(fileAppender);

		logger = Logger.getLogger(Main.class);
		if (LOG_LEVEL_VALUE.equalsIgnoreCase("INFO"))
			logger.setLevel(Level.INFO);
		if (LOG_LEVEL_VALUE.equalsIgnoreCase("DEBUG"))
			logger.setLevel(Level.DEBUG);
		if (LOG_LEVEL_VALUE.equalsIgnoreCase("ERROR"))
			logger.setLevel(Level.ERROR);
//		logger.addAppender(fileAppender);
		logger.addAppender(consoleAppender);
	}

	public static void loadProps() {
		properties = new Properties();
		try {
			properties.load(new FileInputStream(USER_PREF_QUERIES_FILE));

			AdminUser = properties.getProperty("FNP8.StoredSearchUser");
			AdminGroup = properties.getProperty("FNP8.StoredSearchGroup");

			XT_USERS_PATH = properties.getProperty("FNP8.XTUsersPath");
			XT_QUERY_PATH = properties.getProperty("FNP8.XTQueryPath");
			XML_PATH = properties.getProperty("FNP8.XMLPath");
			JSON_PATH = properties.getProperty("FNP8.JSONPath");

			SERVER_VALUE = properties.getProperty("FNP8.Server");
			USER_VALUE = properties.getProperty("FNP8.User");
			PWD_VALUE = properties.getProperty("FNP8.Password");
			OBJECT_STORE_VALUE = properties.getProperty("FNP8.ObjectStore");

			LOG_FILE_VALUE = properties.getProperty("FNP8.LogPath");
			LOG_LEVEL_VALUE = properties.getProperty("FNP8.LogLevel");

			DEST_SERVERS_VALUE = properties.getProperty("DestinationServers");

			if (AdminUser == null || AdminGroup == null || XT_USERS_PATH == null || XT_QUERY_PATH == null
					|| XML_PATH == null || JSON_PATH == null || SERVER_VALUE == null || USER_VALUE == null
					|| PWD_VALUE == null || OBJECT_STORE_VALUE == null || LOG_FILE_VALUE == null
					|| LOG_LEVEL_VALUE == null || DEST_SERVERS_VALUE == null) {

				logger.error("Missing properties in " + USER_PREF_QUERIES_FILE);
				System.exit(8);
			}

		} catch (FileNotFoundException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public static void readDestinationServersFile() {
		destinationServers = new HashMap<>();

		String[] osList;
		String serverURL = "";

		try {
			Scanner scanner = new Scanner(new File(DEST_SERVERS_VALUE));
			while (scanner.hasNextLine()) {
				osList = scanner.nextLine().split("-");
				serverURL = scanner.nextLine();
				for (String os : osList)
					destinationServers.put(os, serverURL);
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		logger.debug("Loaded Destination Servers info");

//		destinationServers.entrySet().forEach(entry -> {
//			System.out.println(entry.getKey() + " " + entry.getValue());
//		});
	}

	public static void closeSearchManagement() {

	}

	public static void performOption(int n) {
		String shortname;

		switch (n) {
		case 1: {
			printSeparator();
			getListXTUsers(sourceCon, sourceObjStore);
			break;
		}
		case 2: {
			System.out.println("Please, introduce the user name:");
			shortname = input.next();
			printSeparator();
			convertXT(shortname);
			break;
		}
		case 3: {
			printSeparator();
			convertXT(null);
			break;
		}
		case 4:
			System.exit(1);
			break;
		default:
			System.out.println("Please select an option [1-4]");
			break;
		}

	}

	public static void runMenu() {
		input = new Scanner(System.in);
		int number;
		try {

			while (true) {
				number = 0;

				printMenu();
				try {
					number = Integer.parseInt(input.next());
				} catch (NumberFormatException e) {
					System.out.println("\nInvalid option!\n");
				}
				performOption(number);

			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	public static void printSeparator() {
		System.out.println("-----------------------------------------------------------");
	}

	public static void printMenu() {
		printSeparator();
		System.out.println(" Searches Converter - Workplace XT to ICN ");
		printSeparator();
		System.out.println("   1 - List user's shortname and SID with XT queries");
		System.out.println("   2 - Convert XT queries to ICN for a specific shortname");
		System.out.println("   3 - Convert all XT queries for all users to ICN");
		System.out.println("   4 - Exit");
		printSeparator();
		System.out.println("Please, select an option [1-4]:");
	}

	public static void stablishOriginConnection() throws EngineRuntimeException {
		sourceCon = Factory.Connection.getConnection(SERVER_VALUE);
		UserContext uc = UserContext.get();
		uc.pushSubject(UserContext.createSubject(sourceCon, USER_VALUE, PWD_VALUE, "FileNetP8WSI"));
		sourceDom = Factory.Domain.fetchInstance(sourceCon, null, null);
		sourceObjStore = Factory.ObjectStore.getInstance(sourceDom, OBJECT_STORE_VALUE);
		logger.info("Connected to " + SERVER_VALUE + " object store " + OBJECT_STORE_VALUE);
	}

	public static void stablishDestConnections() throws EngineRuntimeException {
		sourceCon = Factory.Connection.getConnection(SERVER_VALUE);
		UserContext uc = UserContext.get();
		uc.pushSubject(UserContext.createSubject(sourceCon, USER_VALUE, PWD_VALUE, "FileNetP8WSI"));
		sourceDom = Factory.Domain.fetchInstance(sourceCon, null, null);
		sourceObjStore = Factory.ObjectStore.getInstance(sourceDom, OBJECT_STORE_VALUE);
		logger.info("Connected to " + SERVER_VALUE + " object store " + OBJECT_STORE_VALUE);
	}

	public static void closeOriginConnection() {
		UserContext.get().popSubject();
	}

	public static String getUserNamebySID(String sid) throws EngineRuntimeException {
		try {
			User xtUser = Factory.User.fetchInstance(sourceCon, sid, null);
			return xtUser.get_ShortName();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return "";
	}

	public static String getSIDbyUserName(String shortname) throws EngineRuntimeException {
		User xtUser = Factory.User.fetchInstance(sourceCon, shortname, null);
		return xtUser.get_Id();
	}

	public static void getListXTUsers(Connection con, ObjectStore objStore) {

		String sid = "";
		List<String> searchNames = new ArrayList<>();
		try {

			logger.debug("getListXTUsers ini");

			String mySQLString = "SELECT Id,DocumentTitle FROM PreferencesDocument where isCurrentVersion=True and DocumentTitle like 'Us%'";

			logger.debug(mySQLString);

			SearchSQL sqlObject = new SearchSQL();
			sqlObject.setQueryString(mySQLString);
			SearchScope searchScope = new SearchScope(objStore);
			RepositoryRowSet rowSet = searchScope.fetchRows(sqlObject, null, null, new Boolean(true));

			BufferedWriter writer = new BufferedWriter(new FileWriter(XT_USERS_PATH));

			Iterator iter = rowSet.iterator();
			while (iter.hasNext()) {
				RepositoryRow row = (RepositoryRow) iter.next();
				Id userprefId = row.getProperties().get("Id").getIdValue();
				String docTitle = row.getProperties().get("DocumentTitle").getStringValue();
				Document userprefdoc = Factory.Document.fetchInstance(objStore, userprefId, null);

				ixtquery = 0;
				searchNames = new ArrayList<>();
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
					} catch (IOException e) {
						logger.error(e.getMessage(), e);
					}

					Reader inputString = new StringReader(readStr);
					BufferedReader bufferedReader = new BufferedReader(inputString);
					String sline;
					String searchName = "";

					while ((sline = bufferedReader.readLine()) != null) {
						if (sline.startsWith("<object key=\"xtQuery\">")) {
							searchName = "";
							ixtquery++;
						} else if (sline.startsWith("<setting key=\"searchName\">")) {
							searchName = sline.substring("<setting key=\"searchName\">".length(),
									sline.indexOf("</setting>"));
							searchNames.add(searchName);
						} else if (sline.startsWith("<setting key=\"includeSubclasses\">") && searchName.isEmpty()) {
							searchNames.add("Default Advanced Search");
						}
					}

				}
				if (ixtquery > 0) {
					try {
						writer.write("User: " + getUserNamebySID(sid) + " - Number of XTQueries: " + ixtquery
								+ " - SID: " + sid);
						writer.newLine();
						logger.info("User: " + getUserNamebySID(sid) + " - Number of XTQueries: " + ixtquery
								+ " - SID: " + sid);
						logger.info("\t\t\tSearches " + searchNames.toString());
						System.out.println("");
					} catch (EngineRuntimeException e) {
						writer.write("User: " + sid + " was not found.");
						writer.newLine();
						logger.error("User: " + sid + " was not found.");
						logger.error(e.getMessage(), e);

					}
				}
			}
			writer.flush();
			writer.close();

			logger.debug("getListXTUsers end");

		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

	}

	public static void convertXT(String userShortName) {
		String mySQLString, value;

		try {

			if (userShortName != null) {
				value = "%" + getSIDbyUserName(userShortName);
				logger.debug("SID for XTUser: " + value);

			} else {
				value = "Us";
			}

			mySQLString = "SELECT Id,DocumentTitle FROM PreferencesDocument where isCurrentVersion=True and DocumentTitle like '"
					+ value + "%'";
			logger.debug(mySQLString);

			logger.debug("convertXTforUser ini");
			performXTConversion(mySQLString, userShortName);
			logger.debug("convertXTforUser end");
		} catch (EngineRuntimeException e) {
			logger.error("User could not be found");
			logger.error(e.getMessage());
		}

	}

	public static void performXTConversion(String mySQLString, String shortname) {

		String objstore = "";

		String avdSearchName = "";
		BufferedWriter writer = null;

		try {

			SearchSQL sqlObject = new SearchSQL();
			sqlObject.setQueryString(mySQLString);
			SearchScope searchScope = new SearchScope(sourceObjStore);
			RepositoryRowSet rowSet = searchScope.fetchRows(sqlObject, null, null, new Boolean(true));

			Iterator iter = rowSet.iterator();
			while (iter.hasNext()) {

				RepositoryRow row = (RepositoryRow) iter.next();
				Id userprefId = row.getProperties().get("Id").getIdValue();
				String docTitle = row.getProperties().get("DocumentTitle").getStringValue();

				if (shortname == null) {
					String sid = docTitle.substring(docTitle.indexOf("for ") + 4, docTitle.indexOf(" on "));
					shortname = getUserNamebySID(sid);
				}

				logger.info("Processing XT Advanced Queries for user: " + shortname + " Document Id: "
						+ userprefId.toString() + " Document Title: " + docTitle);

				Document userprefdoc = Factory.Document.fetchInstance(sourceObjStore, userprefId, null);

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
					} catch (IOException e) {
						logger.error(e.getMessage(), e);
					}

					Reader inputString = new StringReader(readStr);
					BufferedReader bufferedReader = new BufferedReader(inputString);
					String sline;

					boolean bwrite = false;
					boolean noSearches = true;
					boolean alreadyDoneSearches = true;

					try {
						while ((sline = bufferedReader.readLine()) != null) {
							if (sline.startsWith("<object key=\"xtQuery\">")) {
								noSearches = false;
								avdSearchName = "";
								bwrite = true;
								writer = new BufferedWriter(new FileWriter(XT_QUERY_PATH));
								ixtquery++;
							}
							if (bwrite) {
								writer.write(sline);
								writer.newLine();
								if (sline.startsWith("<setting key=\"searchName\">")) {
									avdSearchName = sline.substring("<setting key=\"searchName\">".length(),
											sline.indexOf("</setting>"));
								}
								if (sline.startsWith("<setting key=\"objectStoreName\">")) {
									objstore = sline.substring("<setting key=\"objectStoreName\">".length(),
											sline.indexOf("</setting>"));
								}
							}
							if (sline.contains("</object>")) {
								if (bwrite) {
									writer.flush();
									writer.close();
									bwrite = false;

									if (avdSearchName.isEmpty()) {
										avdSearchName = "Default Advanced Search";
									}

									if (userSearchesMap.get(getSIDbyUserName(shortname)).get(avdSearchName)
											.equals("done")) {
										logger.debug("\t\t- Stored Search Document for user: " + shortname
												+ " Search Name: " + avdSearchName + " has already been migrated");
									} else {
										alreadyDoneSearches = false;

										File fxml = new File(XML_PATH);
										if (fxml.exists())
											fxml.delete();
										File fjson = new File(JSON_PATH);
										if (fjson.exists())
											fjson.delete();

										try {
											XTManagement xtManagement = new XTManagement(sourceCon, sourceDom,
													sourceObjStore, true, "10000", logger);

											logger.debug("\t\tCreating Stored Search Document for user: " + shortname
													+ " Search Name:" + avdSearchName);
											xtManagement.readXTQuery(XT_QUERY_PATH);
											xtManagement.fillPropertiesMap();
											xtManagement.generateXML(XML_PATH);
											xtManagement.generateJSON(JSON_PATH);

											ICNManagement icnManagement = new ICNManagement(sourceCon, sourceObjStore,
													objstore, avdSearchName, shortname, XML_PATH, JSON_PATH, AdminUser,
													AdminGroup, logger);
											icnManagement.createStoredSearch();
											userSearchesMap.get(getSIDbyUserName(shortname)).put(avdSearchName, "done");
											logger.info("\t\t✓ Created Stored Search Document for user: " + shortname
													+ " Search Name: " + avdSearchName + " ID: "
													+ icnManagement.getId());
										} catch (EngineRuntimeException e) {
											userSearchesMap.get(getSIDbyUserName(shortname)).put(avdSearchName,
													"failed");
											logger.debug("\t\t✗ Stored Search Document for user: " + shortname
													+ " Search Name: " + avdSearchName + " could not be created");
											logger.error(e.getMessage(), e);
										}
									}
								}
							}
						}
					} catch (IOException e) {
						logger.error(e.getMessage(), e);
					} catch (EngineRuntimeException e) {
						logger.error(e.getMessage(), e);
					}

					if (noSearches) {
						logger.info("\t\tNo searches for user " + shortname);

					} else if (alreadyDoneSearches) {
						logger.info("\t\tAll searches were already migrated for " + shortname);
					}
				}
				shortname = null;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		} finally {
			try {
				searchManagement.writeSM(userSearchesMap);
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

}

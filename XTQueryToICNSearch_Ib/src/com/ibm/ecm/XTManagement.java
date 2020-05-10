package com.ibm.ecm;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.unbescape.html.HtmlEscape;
import org.unbescape.html.HtmlEscapeLevel;
import org.unbescape.html.HtmlEscapeType;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.filenet.api.collection.PropertyDescriptionList;
import com.filenet.api.constants.FilteredPropertyType;
import com.filenet.api.core.Connection;
import com.filenet.api.core.Domain;
import com.filenet.api.core.Factory;
import com.filenet.api.core.Folder;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.meta.ClassDescription;
import com.filenet.api.meta.PropertyDescription;
import com.filenet.api.property.PropertyFilter;
import com.filenet.apiimpl.core.ReplicableClassDefinitionImpl;
import com.opencsv.CSVWriter;

public class XTManagement {
	private Connection con;
	private Domain dom;
	private ObjectStore os;

	private CSVWriter csvWriter;

	public static final int pageSize = 50;

	// properties attributes

//	private String userid = null;
//	private String password = null;
//	private String stanza = null;
//	private String uri = null;
//	private String objectStore = null;
//	private String P8Domain = null;
	private String timeout = null;
	private Logger logger = null;
	private Boolean includesubclasses = null;

	// XML
	private org.w3c.dom.Document w3Doc;
	private Element rootElement, searchSpecElement;
	private int itemId = 1;

	private HashMap<String, String> objectStoresMap;
	HashMap<String, PropertyDescription> propertiesMap;

	// XT QUERY VARIABLES
	String objectStoreName;
	String objectType;
	String folderId;
	String includeSubfolders;
	String className;
	String includeSubclasses;
	String version;
	String propertyLogicalOperator;
	String operator;
	String keyword;
	String exactWords;
	String caseSensitive;
	String showContentSummary;

	List<String> selectPropertiesArray;
	List<String> propertyNamesArray;
	List<String> propertyOperatorsArray;
	List<String> propertyValuesArray;
	List<String> propertyDisplayValuesArray;
	List<String> docTypesValuesArray;

	List<String> defaultSelectProperties;
	List<String> defaultSelectFolderProperties;

	public XTManagement(Connection con, Domain dom, ObjectStore os, Boolean includesubclasses, String timeout,
			Logger log) {
		this.con = con;
		this.dom = dom;
		this.os = os;

		this.includesubclasses = includesubclasses;
		this.timeout = timeout;
		this.logger = log;

		defaultSelectProperties = new ArrayList<>();
		defaultSelectProperties.add("DocumentTitle");
		defaultSelectProperties.add("ContentSize");
		defaultSelectProperties.add("LastModifier");
		defaultSelectProperties.add("DateLastModified");
		defaultSelectProperties.add("MajorVersionNumber");

		defaultSelectFolderProperties = new ArrayList<>();
		defaultSelectFolderProperties.add("FolderName");
		defaultSelectFolderProperties.add("LastModifier");
		defaultSelectFolderProperties.add("DateLastModified");

		folderId = "";
		keyword = "";

	}

	public void startXML() {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try {
			docBuilder = docFactory.newDocumentBuilder();

			w3Doc = docBuilder.newDocument();
			w3Doc.setXmlStandalone(true);
			rootElement = w3Doc.createElement("storedsearch");
			Attr attr = w3Doc.createAttribute("xmlns");
			attr.setValue("http://filenet.com/namespaces/wcm/apps/1.0");
			rootElement.setAttributeNode(attr);
			w3Doc.appendChild(rootElement);

		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		}
	}

	public void basicXMLInfo() {
		/**
		 * Version
		 */
		Element docElement = ((org.w3c.dom.Document) w3Doc).createElement("version");
		Attr attr = w3Doc.createAttribute("dtd");
		attr.setValue("3.0");
		docElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("searchobject");
		attr.setValue("3");
		docElement.setAttributeNode(attr);

		rootElement.appendChild(docElement);

		/**
		 * Product
		 */
		docElement = ((org.w3c.dom.Document) w3Doc).createElement("product");
		attr = w3Doc.createAttribute("name");
		attr.setValue("Navigator");
		docElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("version");
		attr.setValue("3.0.3");
		docElement.setAttributeNode(attr);

		rootElement.appendChild(docElement);

		/**
		 * Search specifications
		 */
		searchSpecElement = ((org.w3c.dom.Document) w3Doc).createElement("searchspec");

		String versionLabel = "";
		switch (Integer.valueOf(version)) {
		case 0:
			versionLabel = "releasedversion";
			break;
		case 1:
			versionLabel = "currentversion";
			break;
		case 2:
			versionLabel = "allversions";
			break;
		}

		if (this.objectType.equals("folder"))
			versionLabel = "none";

		attr = w3Doc.createAttribute("versionselection");
		attr.setValue(versionLabel);
		searchSpecElement.setAttributeNode(attr);

		rootElement.appendChild(searchSpecElement);

		logger.debug("Basic XML info created");

	}

	public void addSearchType() {
		Element templateElement = ((org.w3c.dom.Document) w3Doc).createElement("template");
		Attr attr = w3Doc.createAttribute("showandorconditions");
		attr.setValue("true");
		templateElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("showmaxrecords");
		attr.setValue("false");
		templateElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("showoperators");
		attr.setValue("true");
		templateElement.setAttributeNode(attr);

		Element searchTypeElement = ((org.w3c.dom.Document) w3Doc).createElement("searchtype");
		searchTypeElement.appendChild(templateElement);
		searchSpecElement.appendChild(searchTypeElement);

		logger.debug("XML Search Type created");

	}

	public void addObjectStores() {
		Element objectStoreElement = ((org.w3c.dom.Document) w3Doc).createElement("objectstore");

		com.filenet.api.core.ObjectStore os = Factory.ObjectStore.fetchInstance(dom, this.objectStoreName, null);
		String osId = os.get_Id().toString();
		String osSymbolicName = Factory.ObjectStore.fetchInstance(dom, this.objectStoreName, null).get_Name();
		String osName = Factory.ObjectStore.fetchInstance(dom, this.objectStoreName, null).get_DisplayName();

		Attr attr = w3Doc.createAttribute("id");
		attr.setValue(osId);
		objectStoreElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("name");
		attr.setValue(osName);
		objectStoreElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("repositoryId");
		attr.setValue(osSymbolicName);
		objectStoreElement.setAttributeNode(attr);

		Element objectStoresElement = ((org.w3c.dom.Document) w3Doc).createElement("objectstores");
		attr = w3Doc.createAttribute("mergeoption");
		attr.setValue("union");
		objectStoresElement.setAttributeNode(attr);
		objectStoresElement.appendChild(objectStoreElement);
		searchSpecElement.appendChild(objectStoresElement);

		logger.debug("XML object stores created");

	}

	public void addSearchCriteria() throws EngineRuntimeException {
		Element searchCriteriaElement = ((org.w3c.dom.Document) w3Doc).createElement("searchcriteria");

		addFolders(searchCriteriaElement);
		addSearchClauses(searchCriteriaElement);

		searchSpecElement.appendChild(searchCriteriaElement);
		logger.debug("XML Search criteria created");

	}

	public void addFolders(Element searchCriteriaElement) throws EngineRuntimeException {
		Element foldersElement = ((org.w3c.dom.Document) w3Doc).createElement("folders");

		if (!folderId.isEmpty())
			addFolder(foldersElement);

		searchCriteriaElement.appendChild(foldersElement);

		logger.debug("XML folders created");

	}

	public void addFolder(Element foldersElement) throws EngineRuntimeException {
		Element folderElement = ((org.w3c.dom.Document) w3Doc).createElement("folder");
		Attr attr = w3Doc.createAttribute("id");
		attr.setValue(folderId);
		folderElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("itemid");
		attr.setValue(String.valueOf(itemId));
		folderElement.setAttributeNode(attr);
		this.itemId++;

		String pathName = "";

		if (!folderId.isEmpty()) {
			Folder folder = Factory.Folder.fetchInstance(this.os, folderId, null);
			pathName = folder.get_PathName();
		}
		attr = w3Doc.createAttribute("pathname");
		attr.setValue(pathName);
		folderElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("searchsubfolders");
		attr.setValue(includeSubfolders);
		folderElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("view");
		attr.setValue("editable");
		folderElement.setAttributeNode(attr);

//		ObjectStore os = Factory.ObjectStore.fetchInstance(dom, this.objectStoreName, null);
		String osSymbolicName = Factory.ObjectStore.fetchInstance(dom, this.objectStoreName, null).get_Name();

		Element osElement = ((org.w3c.dom.Document) w3Doc).createElement("objectstore");
		attr = w3Doc.createAttribute("id");
		attr.setValue(osSymbolicName);
		osElement.setAttributeNode(attr);

		folderElement.appendChild(osElement);
		foldersElement.appendChild(folderElement);

	}

	public void addSearchClauses(Element searchCriteriaElement) {
		Element searchClausesElement = ((org.w3c.dom.Document) w3Doc).createElement("searchclauses");

		addSearchClause(searchClausesElement);

		searchCriteriaElement.appendChild(searchClausesElement);

		logger.debug("XML Search clauses created");

	}

	public void addSearchClause(Element searchClausesElement) {
		Element searchClauseElement = ((org.w3c.dom.Document) w3Doc).createElement("searchclause");
		Attr attr = w3Doc.createAttribute("join");

		attr.setValue(this.propertyLogicalOperator);
		searchClauseElement.setAttributeNode(attr);

		if (!selectPropertiesArray.isEmpty() && !this.objectType.equals("folder"))
			addSelect(searchClauseElement, selectPropertiesArray);
		else {
			if (this.objectType.equals("document"))
				addSelect(searchClauseElement, defaultSelectProperties);
			else if (this.objectType.equals("folder"))
				addSelect(searchClauseElement, defaultSelectFolderProperties);
		}

		addFrom(searchClauseElement);
		addWhere(searchClauseElement);
		addSubclasses(searchClauseElement);
		addContent(searchClauseElement);

		searchClausesElement.appendChild(searchClauseElement);

		logger.debug("XML Search clause created");

	}

	public void addSelect(Element searchClauseElement, List<String> propsArray) {
		Element selectElement = ((org.w3c.dom.Document) w3Doc).createElement("select");

		Element selectPropsElement = ((org.w3c.dom.Document) w3Doc).createElement("selectprops");

		for (int i = 0; i < propsArray.size(); i++) {
			addSelectProp(selectPropsElement, propsArray.get(i));
		}

		selectElement.appendChild(selectPropsElement);

		searchClauseElement.appendChild(selectElement);

		logger.debug("XML Select created");

	}

	public void addSelectProp(Element selectPropsElement, String selectProperty) {
		PropertyDescription property = propertiesMap.get(selectProperty);

		Element selectPropElement = ((org.w3c.dom.Document) w3Doc).createElement("selectprop");
		Attr attr = w3Doc.createAttribute("alignment");
		attr.setValue("left");
		selectPropElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("itemid");
		attr.setValue(Integer.toString(itemId));
		selectPropElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("name");
		attr.setValue(selectProperty);
		selectPropElement.setAttributeNode(attr);

		// OBJECT TYPE ES SIEMPRE DOCUMENT?
		attr = w3Doc.createAttribute("objecttype");
		attr.setValue(objectType);
		selectPropElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("sortlevel");
		attr.setValue("0");
		selectPropElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("sortorder");
		attr.setValue("none");
		selectPropElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("symname");
		attr.setValue(selectProperty);
		selectPropElement.setAttributeNode(attr);

		this.itemId++;

		selectPropsElement.appendChild(selectPropElement);

		logger.debug("XML Select prop created");

	}

	public void addFrom(Element searchClauseElement) {
		Element fromElement = ((org.w3c.dom.Document) w3Doc).createElement("from");

		Element classElement = ((org.w3c.dom.Document) w3Doc).createElement("class");
		Attr attr = w3Doc.createAttribute("symname");
		attr.setValue(this.objectType);
		classElement.setAttributeNode(attr);

		fromElement.appendChild(classElement);

		searchClauseElement.appendChild(fromElement);

		logger.debug("XML From created");

	}

	public void addWhere(Element searchClauseElement) {
		Element whereElement = ((org.w3c.dom.Document) w3Doc).createElement("where");

		logger.debug(propertyNamesArray);
		logger.debug("Property names array " + propertyNamesArray);

		if (!propertyNamesArray.isEmpty())
			addWhereClause(whereElement, 0);

		searchClauseElement.appendChild(whereElement);

		logger.debug("XML Where created");

	}

	public void addWhereClause(Element parent, int i) {
		Element logicalOperatorElement = ((org.w3c.dom.Document) w3Doc).createElement(this.propertyLogicalOperator);

		String propOperator = propertyOperatorsArray.get(i);
		if (propOperator.equals("startsWith") || propOperator.equals("endsWith"))
			propOperator = "like";

		Element propertyLogicalOperatorElement = ((org.w3c.dom.Document) w3Doc).createElement(propOperator);

		Element wherePropElement = ((org.w3c.dom.Document) w3Doc).createElement("whereprop");

		Attr attr = w3Doc.createAttribute("editproperty");
		attr.setValue("editable");
		wherePropElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("itemid");
		attr.setValue(String.valueOf(this.itemId));
		itemId++;
		wherePropElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("name");
		attr.setValue(propertyNamesArray.get(i));
		wherePropElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute(objectType);
		attr.setValue(this.objectType);
		wherePropElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("symname");
		attr.setValue(propertyNamesArray.get(i));
		wherePropElement.setAttributeNode(attr);

		String smartOperator = propertyOperatorsArray.get(i).toLowerCase();
		if (smartOperator == "startswith" || smartOperator == "endswith" || smartOperator == "inany"
				|| smartOperator == "notin") {
			attr = w3Doc.createAttribute("smartoperator");
			attr.setValue(smartOperator);
			wherePropElement.setAttributeNode(attr);
		}

		Element propDescElement = ((org.w3c.dom.Document) w3Doc).createElement("propdesc");

		PropertyDescription property = propertiesMap.get(propertyNamesArray.get(i));

		if (property == null)
			logger.error("Property " + propertyNamesArray.get(i) + " could not be found");

		attr = w3Doc.createAttribute("datatype");
		attr.setValue(getPropertyType(property));
		propDescElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("haschoices");
		attr.setValue(String.valueOf(hasChoices(property)));
		propDescElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("hasmarkings");
		attr.setValue("false");
		propDescElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("symname");
		attr.setValue(propertyNamesArray.get(i));
		propDescElement.setAttributeNode(attr);

		wherePropElement.appendChild(propDescElement);

		propertyLogicalOperatorElement.appendChild(wherePropElement);

		Element literalElement = ((org.w3c.dom.Document) w3Doc).createElement("literal");
//		literalElement.setTextContent("<\u003c![CDATA[" + propertyValuesArray.get(i) + "]]\u003E");
		String startOperator = "";
		String endOperator = "";

		switch (smartOperator) {
		case "startswith":
			endOperator = "%";
			break;
		case "endswith":
			startOperator = "%";
			break;
		case "like":
			startOperator = "%";
			endOperator = "%";
			break;
		case "notlike":
			startOperator = "%";
			endOperator = "%";
			break;
		default:
			break;
		}

		CDATASection cdata = w3Doc.createCDATASection(startOperator + propertyValuesArray.get(i) + endOperator);
		literalElement.appendChild(cdata);

		propertyLogicalOperatorElement.appendChild(literalElement);

		logicalOperatorElement.appendChild(propertyLogicalOperatorElement);

		if (i + 1 < propertyNamesArray.size())
			addWhereClause(logicalOperatorElement, i + 1);

		// append to logicalOperatorElement next property
		parent.appendChild(logicalOperatorElement);

		logger.debug("XML Where clause created");

	}

	public String getPropertyType(PropertyDescription property) {
		return "type" + property.get_DataType().toString().toLowerCase();
	}

	public String getPropertyTypeJSONFormat(PropertyDescription property) {
		String originalDataType = property.get_DataType().toString().toLowerCase();
		if (originalDataType.equals("date"))
			originalDataType = "timestamp";
		return "xs:" + originalDataType;
	}

	public boolean hasChoices(PropertyDescription property) {
		return property.get_ChoiceList() != null;
	}

	public void fillPropertiesMap() {
		propertiesMap = new HashMap<String, PropertyDescription>();
		// Construct property filter to ensure PropertyDefinitions property of CD is
		// returned as evaluated
		PropertyFilter pf = new PropertyFilter();
		pf.addIncludeType(0, null, Boolean.TRUE, FilteredPropertyType.ANY, null);

		logger.debug("Retrieving class definition " + this.className);

		ClassDescription objClassDesc = Factory.ClassDescription.fetchInstance(os, this.className, pf);
		PropertyDescription pds = null;
		PropertyDescriptionList pdl = objClassDesc.get_PropertyDescriptions();
		PropertyDescriptionList subpdl = objClassDesc.get_ProperSubclassPropertyDescriptions();
		Iterator<?> itr = pdl.iterator();
		while (itr.hasNext()) {
			pds = (PropertyDescription) itr.next();
			propertiesMap.put(pds.get_SymbolicName(), pds);
		}

		Iterator<?> itr2 = subpdl.iterator();
		while (itr2.hasNext()) {
			pds = (PropertyDescription) itr2.next();
			propertiesMap.put(pds.get_SymbolicName(), pds);
		}

		logger.debug("Class definition was retrieved " + this.className);

	}

	public void addSubclasses(Element searchClauseElement) {
		Element subclassesElement = ((org.w3c.dom.Document) w3Doc).createElement("subclasses");

		// Construct property filter to ensure PropertyDefinitions property of CD is
		// returned as evaluated
		PropertyFilter pf = new PropertyFilter();
		pf.addIncludeType(0, null, Boolean.TRUE, FilteredPropertyType.ANY, null);

		ReplicableClassDefinitionImpl objClassDef = (ReplicableClassDefinitionImpl) Factory.ClassDefinition
				.fetchInstance(os, this.className, pf);

		if (!this.objectType.equals("folder") && !objClassDef.get_SymbolicName().equals("Document"))
			addSubclass(subclassesElement, objClassDef);

		searchClauseElement.appendChild(subclassesElement);
	}

	public void addSubclass(Element subclassesElement, ReplicableClassDefinitionImpl objClassDef) {
		Element subclassElement = ((org.w3c.dom.Document) w3Doc).createElement("subclass");

		logger.debug("CLASS NAME -----> " + this.className);
		logger.debug("CLASS DEFINITION -----> " + objClassDef.get_SymbolicName());

		Attr attr = w3Doc.createAttribute("editproperty");
		attr.setValue("editable");
		subclassElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("itemid");
		attr.setValue(String.valueOf(itemId));
		subclassElement.setAttributeNode(attr);
		this.itemId++;

		attr = w3Doc.createAttribute("symname");
		attr.setValue(objClassDef.get_SymbolicName());
		subclassElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("name");

		String escapedWord = HtmlEscape.escapeHtml(objClassDef.get_DisplayName(), HtmlEscapeType.DECIMAL_REFERENCES,
				HtmlEscapeLevel.LEVEL_2_ALL_NON_ASCII_PLUS_MARKUP_SIGNIFICANT);
		attr.setValue(escapedWord);
		logger.debug("Display name is " + objClassDef.get_DisplayName());

		subclassElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("objecttype");
		attr.setValue(this.objectType);
		subclassElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("includesubclasses");
		attr.setValue(this.includeSubclasses);
		subclassElement.setAttributeNode(attr);

		subclassesElement.appendChild(subclassElement);
	}

	public void addContent(Element searchClauseElement) {
		Element contentElement = ((org.w3c.dom.Document) w3Doc).createElement("content");

		Attr attr = w3Doc.createAttribute("dialect");
		attr.setValue("lucene");
		contentElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("contentsummary");
		attr.setValue(showContentSummary);
		contentElement.setAttributeNode(attr);

		// Hardcoded to inner
		attr = w3Doc.createAttribute("jointype");
		attr.setValue("inner");
		contentElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("rank");
		attr.setValue("false");
		contentElement.setAttributeNode(attr);

		Element itemElement = ((org.w3c.dom.Document) w3Doc).createElement("item");
		attr = w3Doc.createAttribute("editproperty");
		attr.setValue("editable");
		itemElement.setAttributeNode(attr);

		attr = w3Doc.createAttribute("itemid");
		attr.setValue(String.valueOf(itemId));
		itemElement.setAttributeNode(attr);
		this.itemId++;

		attr = w3Doc.createAttribute("groupaction");
		if (this.operator.equals("and"))
			attr.setValue("all");
		else if (this.operator.equals("or"))
			attr.setValue("any");
		else {
			logger.error("El operador de la XTQuery no es ni and ni or sino -> " + operator);
			System.exit(0);
		}
		itemElement.setAttributeNode(attr);

		Element termsElement = ((org.w3c.dom.Document) w3Doc).createElement("terms");

		List<String> keywordsArray = Arrays.asList(this.keyword.split(" "));
		for (String key : keywordsArray)
			addTerm(termsElement, key);

		itemElement.appendChild(termsElement);

		contentElement.appendChild(itemElement);

		searchClauseElement.appendChild(contentElement);
//		addItem();

	}

	public void addTerm(Element termsElement, String key) {
		Element termElement = ((org.w3c.dom.Document) w3Doc).createElement("term");
		termElement.setTextContent(key);
		termsElement.appendChild(termElement);
	}

	public void writeToXML() {
		Element docElement = ((org.w3c.dom.Document) w3Doc).createElement("row");
		rootElement.appendChild(docElement);
		Attr attr = w3Doc.createAttribute("xmlns");
		attr.setValue("http://filenet.com/namespaces/wcm/apps/1.0");
		docElement.setAttributeNode(attr);
	}

	public void createXML(String path) {
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		try {
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(w3Doc);
			StreamResult result = new StreamResult(new File(path));
			transformer.transform(source, result);
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		} catch (TransformerException e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		}
	}

	public void generateXML(String xmlPath) throws IOException, EngineRuntimeException {
		logger.debug("Creating XML");
		startXML();
		basicXMLInfo();
		addSearchType();
		addObjectStores();
		addSearchCriteria();
		createXML(xmlPath);

		logger.debug("XML Created");
	}

	@SuppressWarnings("unchecked")
	public void generateJSON(String jsonPath) {
		logger.debug("Creating JSON");

		JSONObject obj = new JSONObject();
		JSONObject macrosObject = new JSONObject();

		JSONObject textSearchCriteriaDetails = new JSONObject();
		textSearchCriteriaDetails.put("itemId", null);
		textSearchCriteriaDetails.put("editProperty", "editable");
		textSearchCriteriaDetails.put("text", keyword);
		textSearchCriteriaDetails.put("contentTypes", null);
		textSearchCriteriaDetails.put("proximityDistance", 1024);
		String criteriaOperatorName;
		switch (operator) {
		case "and":
			criteriaOperatorName = "all";
			break;
		case "or":
			criteriaOperatorName = "any";
			break;
		default:
			criteriaOperatorName = "";
		}
		textSearchCriteriaDetails.put("operator", criteriaOperatorName);
		obj.put("textSearchCriteria", textSearchCriteriaDetails);

		JSONArray fileTypesArray = new JSONArray();
		String docTypeValue = "";
		for (String docType : docTypesValuesArray) {
			switch (Integer.valueOf(docType)) {
			case 5:
				docTypeValue = "word";
				break;
			case 6:
				docTypeValue = "excel";
				break;
			case 7:
				docTypeValue = "powerpoint";
				break;
			case 8:
				docTypeValue = "pdf";
				break;
			}

			fileTypesArray.add(docTypeValue);
		}

		if (!this.objectType.equals("folder"))
			macrosObject.put("fileTypes", fileTypesArray);

		obj.put("macros", macrosObject);

		JSONArray searchCriteriaArray = new JSONArray();
		PropertyDescription prop;

		for (int i = 0; i < propertyNamesArray.size(); i++) {

			prop = propertiesMap.get(propertyNamesArray.get(i));

			logger.debug("*********");
			logger.debug(prop.getClass().getTypeName());
			logger.debug(prop.getClass().getName());
			logger.debug(prop.getClass().getCanonicalName());
			logger.debug(prop.getClassName());
			logger.debug(prop.get_DataType());
			logger.debug(prop.get_DisplayName());
			logger.debug(prop.get_Cardinality().toString());
			logger.debug("*********");

			JSONObject searchCriteriaDetails = new JSONObject();
			searchCriteriaDetails.put("itemId", "");
			JSONArray displayValuesArray = new JSONArray();
			searchCriteriaDetails.put("displayValues", displayValuesArray);
			searchCriteriaDetails.put("hasSelectedOperators", false);
			searchCriteriaDetails.put("hidden", prop.get_IsHidden());
			searchCriteriaDetails.put("dataType", getPropertyTypeJSONFormat(prop));

			JSONArray valuesArray = new JSONArray();
			valuesArray.add(propertyValuesArray.get(i));
			valuesArray.add("");
			searchCriteriaDetails.put("values", valuesArray);
			searchCriteriaDetails.put("name", prop.get_DisplayName());
			searchCriteriaDetails.put("selectedOperator", getJSONOperator(propertyOperatorsArray.get(i)));
			searchCriteriaDetails.put("readOnly", prop.get_IsReadOnly());
			searchCriteriaDetails.put("id", propertyNamesArray.get(i));
			searchCriteriaDetails.put("cardinality", prop.get_Cardinality().toString());
			// TODO Check if required
//			searchCriteriaDetails.put("required",prop.get_IsValueRequired());
			searchCriteriaDetails.put("required", false);

			searchCriteriaArray.add(searchCriteriaDetails);
		}
		// fin de bucle

		obj.put("searchCriteria", searchCriteriaArray);

		JSONObject resultsDisplayObject = new JSONObject();
		resultsDisplayObject.put("sortAsc", true);
		JSONArray columnsArray = new JSONArray();
		columnsArray.add("{NAME}");
		columnsArray.add("ContentSize");
		columnsArray.add("LastModifier");
		columnsArray.add("DateLastModified");
		columnsArray.add("MajorVersionNumber");

		JSONArray folderColumnsArray = new JSONArray();
		folderColumnsArray.add("{NAME}");
		folderColumnsArray.add("LastModifier");
		folderColumnsArray.add("DateLastModified");

		if (this.objectType.equals("folder"))
			resultsDisplayObject.put("columns", folderColumnsArray);
		else
			resultsDisplayObject.put("columns", columnsArray);

		resultsDisplayObject.put("showContentSummary", Boolean.parseBoolean(showContentSummary));
		resultsDisplayObject.put("sortBy", "{NAME}");
		resultsDisplayObject.put("honorNameProperty", true);

		obj.put("resultsDisplay", resultsDisplayObject);

		try (FileWriter file = new FileWriter(jsonPath)) {
			file.write(obj.toJSONString());
			logger.debug("JSON created");
		} catch (IOException e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		}
	}

	public String getJSONOperator(String originalOperator) {
		logger.debug("Original operator is " + originalOperator);
		String newOperator = "";
		switch (originalOperator) {
		case "lte":
			newOperator = "LESSOREQUAL";
			break;
		case "gt":
			newOperator = "GREATER";
			break;
		case "gte":
			newOperator = "GREATEROREQUAL";
			break;
		case "startsWith":
			newOperator = "STARTSWITH";
			break;
		case "eq":
			newOperator = "EQUAL";
			break;
		case "nlike":
			newOperator = "NOTLIKE";
			break;
		case "like":
			newOperator = "LIKE";
			break;
		case "endsWith":
			newOperator = "ENDSWITH";
			break;
		case "lt":
			newOperator = "LESS";
			break;
		case "isnull":
			newOperator = "NULL";
			break;
		case "isnotnull":
			newOperator = "NOTNULL";
			break;
		case "neq":
			newOperator = "NOTEQUAL";
			break;
		default:
			break;
		}

		logger.debug("New operator is " + newOperator);

		return newOperator;
	}

	public void readXTQuery(String path) {

		logger.debug("Reading XT Query");

		File fXmlFile = new File(path);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;
		try {
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fXmlFile);
			doc.getDocumentElement().normalize();

			XPath xpath = XPathFactory.newInstance().newXPath();

			String xPathExpression = "//setting[@key='objectStoreName']";

			objectStoreName = ((NodeList) xpath.evaluate("//setting[@key='objectStoreName']", doc,
					XPathConstants.NODESET)).item(0).getTextContent();
			objectType = ((NodeList) xpath.evaluate("//setting[@key='objectType']", doc, XPathConstants.NODESET))
					.item(0).getTextContent();

			if (((NodeList) xpath.evaluate("//setting[@key='folderId']", doc, XPathConstants.NODESET)).item(0) != null)
				folderId = ((NodeList) xpath.evaluate("//setting[@key='folderId']", doc, XPathConstants.NODESET))
						.item(0).getTextContent();

			includeSubfolders = ((NodeList) xpath.evaluate("//setting[@key='includeSubfolders']", doc,
					XPathConstants.NODESET)).item(0).getTextContent();
			className = ((NodeList) xpath.evaluate("//setting[@key='className']", doc, XPathConstants.NODESET)).item(0)
					.getTextContent();
			includeSubclasses = ((NodeList) xpath.evaluate("//setting[@key='includeSubclasses']", doc,
					XPathConstants.NODESET)).item(0).getTextContent();

			if (((NodeList) xpath.evaluate("//setting[@key='keyword']", doc, XPathConstants.NODESET)).item(0) != null)
				keyword = ((NodeList) xpath.evaluate("//setting[@key='keyword']", doc, XPathConstants.NODESET)).item(0)
						.getTextContent();

			version = ((NodeList) xpath.evaluate("//setting[@key='version']", doc, XPathConstants.NODESET)).item(0)
					.getTextContent();
			propertyLogicalOperator = ((NodeList) xpath.evaluate("//setting[@key='propertyLogicalOperator']", doc,
					XPathConstants.NODESET)).item(0).getTextContent();
			operator = ((NodeList) xpath.evaluate("//setting[@key='operator']", doc, XPathConstants.NODESET)).item(0)
					.getTextContent();
			exactWords = ((NodeList) xpath.evaluate("//setting[@key='exactWords']", doc, XPathConstants.NODESET))
					.item(0).getTextContent();
			caseSensitive = ((NodeList) xpath.evaluate("//setting[@key='caseSensitive']", doc, XPathConstants.NODESET))
					.item(0).getTextContent();
			showContentSummary = ((NodeList) xpath.evaluate("//setting[@key='showContentSummary']", doc,
					XPathConstants.NODESET)).item(0).getTextContent();

			selectPropertiesArray = new ArrayList<>();
			NodeList selectPropertiesNodeList = ((NodeList) xpath.evaluate("//array[@key='selectProperties']/value",
					doc, XPathConstants.NODESET));
			for (int i = 0; i < selectPropertiesNodeList.getLength(); i++)
				selectPropertiesArray.add(selectPropertiesNodeList.item(i).getTextContent());

			propertyNamesArray = new ArrayList<>();
			NodeList propertyNamesNodeList = ((NodeList) xpath.evaluate("//array[@key='propertyNames']/value", doc,
					XPathConstants.NODESET));
			for (int i = 0; i < propertyNamesNodeList.getLength(); i++)
				propertyNamesArray.add(propertyNamesNodeList.item(i).getTextContent());

			propertyOperatorsArray = new ArrayList<>();
			NodeList propertyOperatorsNodeList = ((NodeList) xpath.evaluate("//array[@key='propertyOperators']/value",
					doc, XPathConstants.NODESET));
			for (int i = 0; i < propertyOperatorsNodeList.getLength(); i++)
				propertyOperatorsArray.add(propertyOperatorsNodeList.item(i).getTextContent());

			propertyValuesArray = new ArrayList<>();
			NodeList propertyValuesNodeList = ((NodeList) xpath.evaluate("//array[@key='propertyValues']/value", doc,
					XPathConstants.NODESET));
			for (int i = 0; i < propertyValuesNodeList.getLength(); i++)
				propertyValuesArray.add(propertyValuesNodeList.item(i).getTextContent());

			propertyDisplayValuesArray = new ArrayList<>();
			NodeList propertyDisplayValuesNodeList = ((NodeList) xpath
					.evaluate("//array[@key='propertyDisplayValues']/value", doc, XPathConstants.NODESET));
			for (int i = 0; i < propertyDisplayValuesNodeList.getLength(); i++)
				propertyDisplayValuesArray.add(propertyDisplayValuesNodeList.item(i).getTextContent());

			docTypesValuesArray = new ArrayList<>();
			NodeList docTypesValuesNodeList = ((NodeList) xpath.evaluate("//array[@key='docTypes']/value", doc,
					XPathConstants.NODESET));
			for (int i = 0; i < docTypesValuesNodeList.getLength(); i++)
				docTypesValuesArray.add(docTypesValuesNodeList.item(i).getTextContent());

//			logger.debug("Root element : " + doc.getDocumentElement().getNodeName());
//			logger.debug("Object store : " + objectStoreName);
//			logger.debug("Object type : " + objectType);
//			logger.debug("Folder id : " + folderId);
//			logger.debug("Include subfolders : " + includeSubfolders);
//			logger.debug("Class name : " + className);
//			logger.debug("Include subclasses : " + includeSubclasses);
//			logger.debug("Version : " + version);
//			logger.debug("Property logical operator : " + propertyLogicalOperator);
//			logger.debug("------------------------------------------------------------");
//			logger.debug("---------------------------ARRAYS---------------------------");
//			logger.debug("------------------------------------------------------------");
//
//			logger.debug("Select properties array : " + selectPropertiesArray.toString());
//			logger.debug("Property names array : " + propertyNamesArray.toString());
//			logger.debug("Property operators array : " + propertyOperatorsArray.toString());
//			logger.debug("Property values array : " + propertyValuesArray.toString());
//			logger.debug("Property display values array : " + propertyDisplayValuesArray.toString());
//			logger.debug("Doc types values array : " + docTypesValuesArray.toString());

		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		} catch (SAXException e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		} catch (XPathExpressionException e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		}
		logger.debug("Finished reading XT Query");

	}

}

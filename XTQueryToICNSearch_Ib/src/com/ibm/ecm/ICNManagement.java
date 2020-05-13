package com.ibm.ecm;

import java.io.File;
import java.io.FileInputStream;

import org.apache.log4j.Logger;

import com.filenet.api.collection.AccessPermissionList;
import com.filenet.api.collection.ContentElementList;
import com.filenet.api.constants.AccessRight;
import com.filenet.api.constants.AccessType;
import com.filenet.api.constants.AutoClassify;
import com.filenet.api.constants.CheckinType;
import com.filenet.api.constants.RefreshMode;
import com.filenet.api.core.Connection;
import com.filenet.api.core.ContentTransfer;
import com.filenet.api.core.Document;
import com.filenet.api.core.Domain;
import com.filenet.api.core.Factory;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.security.AccessPermission;
import com.filenet.api.security.Group;
import com.filenet.api.security.User;

public class ICNManagement {

	private Connection con;
	private Domain dom;
	private ObjectStore objStore;

	private String objStoreName;
	private String advancedSearchName;
	private String userOwner;
	private String XMLFile;
	private String JSONFile;
	private String AdminUser;
	private String AdminGroup;
	private String Id = null;
	private Logger logger = null;

	public ICNManagement(Connection con, Domain dom, String objStoreName, String advancedSearchName, String userOwner,
			String XMLfile, String JSONfile, String AdminUser, String AdminGroup, Logger logger) {

		this.con = con;
		this.dom = dom;
		this.objStore = Factory.ObjectStore.getInstance(dom, objStoreName);

		this.advancedSearchName = advancedSearchName;
		this.objStoreName = objStoreName;
		this.userOwner = userOwner;
		this.XMLFile = XMLfile;
		this.JSONFile = JSONfile;
		this.AdminUser = AdminUser;
		this.AdminGroup = AdminGroup;

		this.logger = Logger.getLogger(ICNManagement.class.getName());

	}

	public String getId() {
		return this.Id;
	}

	public void createStoredSearch() {

		int DOC_CREATOR = AccessRight.READ_ACL.getValue() | AccessRight.CHANGE_STATE.getValue()
				| AccessRight.CREATE_INSTANCE.getValue() | AccessRight.VIEW_CONTENT.getValue()
				| AccessRight.MINOR_VERSION.getValue() | AccessRight.UNLINK.getValue() | AccessRight.LINK.getValue()
				| AccessRight.WRITE.getValue() | AccessRight.READ.getValue() | AccessRight.RESERVED12.getValue()
				| AccessRight.RESERVED13.getValue();

		final int ACCESS_MANAGER = AccessRight.WRITE_OWNER.getValue() | AccessRight.WRITE_ACL.getValue()
				| AccessRight.READ_ACL.getValue() | AccessRight.DELETE.getValue() | AccessRight.PUBLISH.getValue()
				| AccessRight.CHANGE_STATE.getValue() | AccessRight.CREATE_INSTANCE.getValue()
				| AccessRight.VIEW_CONTENT.getValue() | AccessRight.MINOR_VERSION.getValue()
				| AccessRight.UNLINK.getValue() | AccessRight.LINK.getValue() | AccessRight.MAJOR_VERSION.getValue()
				| AccessRight.WRITE.getValue() | AccessRight.READ.getValue();

		User fuser = null;
		User fadminuser = null;
		Group fadmingroup = null;

		try {
			fuser = Factory.User.fetchInstance(this.con, this.userOwner, null);
			fadminuser = Factory.User.fetchInstance(this.con, this.AdminUser, null);
			fadmingroup = Factory.Group.fetchInstance(this.con, this.AdminGroup, null);

			AccessPermission ap1 = Factory.AccessPermission.createInstance();
			ap1.set_GranteeName(fuser.get_DistinguishedName());
			ap1.set_AccessType(AccessType.ALLOW);
			ap1.set_AccessMask(ACCESS_MANAGER);

			AccessPermission ap2 = Factory.AccessPermission.createInstance();
			ap2.set_GranteeName(fadminuser.get_DistinguishedName());
			ap2.set_AccessType(AccessType.ALLOW);
			ap2.set_AccessMask(ACCESS_MANAGER);

			AccessPermission ap3 = Factory.AccessPermission.createInstance();
			ap3.set_GranteeName(fadmingroup.get_DistinguishedName());
			ap3.set_AccessType(AccessType.ALLOW);
			ap3.set_AccessMask(ACCESS_MANAGER);

			AccessPermissionList apl = Factory.AccessPermission.createList();

			apl.add(ap1);
			apl.add(ap2);
			apl.add(ap3);

			Document doc = Factory.Document.createInstance(objStore, "StoredSearch");
			doc.getProperties().putValue("DocumentTitle", this.advancedSearchName);
			doc.getProperties().putValue("SearchType", 2);
			doc.getProperties().putValue("SearchingObjectStores", this.objStoreName);
			doc.getProperties().putValue("SearchingObjectType", 1);
			doc.getProperties().putValue("CmSearchSchemaVersion", 3);
			doc.getProperties().putValue("IcnAutoRun", false);
			doc.getProperties().putValue("IcnShowInTree", false);
			doc.getProperties().putValue("ApplicationName", "Navigator");
			doc.getProperties().putValue("Description", this.advancedSearchName);
			doc.set_MimeType("application/x-filenet-searchtemplate");

			File xmlFile = new File(this.XMLFile);
			File JSONFile = new File(this.JSONFile);

			FileInputStream fileJSON = new FileInputStream(JSONFile.getAbsolutePath());
			FileInputStream fileXML = new FileInputStream(xmlFile.getAbsolutePath());

			ContentElementList contentList = Factory.ContentTransfer.createList();

			ContentTransfer ctObject = Factory.ContentTransfer.createInstance();
			ctObject.setCaptureSource(fileXML);
			ctObject.set_ContentType("application/x-filenet-searchtemplate");

			ContentTransfer ctObject2 = Factory.ContentTransfer.createInstance();
			ctObject2.setCaptureSource(fileJSON);
			ctObject2.set_ContentType("application/json");

			contentList.add(ctObject);
			contentList.add(ctObject2);

			doc.set_ContentElements(contentList);
			doc.set_Permissions(apl);

			doc.checkin(AutoClassify.DO_NOT_AUTO_CLASSIFY, CheckinType.MAJOR_VERSION);
			doc.save(RefreshMode.REFRESH);

			this.Id = doc.get_Id().toString();

		} catch (Exception exc) {
			logger.error(exc);
		}
	}
}

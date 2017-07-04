/**
 * Copyright GE
 */
package com.ge.treasury.myfunding.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.ge.treasury.myfunding.constants.AkanaConstant;
import com.ge.treasury.myfunding.constants.ControllersConstants;
import com.ge.treasury.myfunding.controllers.LovController;
import com.ge.treasury.myfunding.dao.LovDao;
import com.ge.treasury.myfunding.dao.TransactionTypeDetails;
import com.ge.treasury.myfunding.domain.BaseDomainObject;
import com.ge.treasury.myfunding.domain.User;
import com.ge.treasury.myfunding.domain.myfunding.Deal;
import com.ge.treasury.myfunding.domain.myfunding.DealExample;
import com.ge.treasury.myfunding.domain.myfunding.Document;
import com.ge.treasury.myfunding.domain.myfunding.Entity;
import com.ge.treasury.myfunding.domain.myfunding.Team;
import com.ge.treasury.myfunding.domain.myfunding.TeamExample;
import com.ge.treasury.myfunding.domain.myfunding.Transaction;
import com.ge.treasury.myfunding.domain.myfunding.TransactionExample;
import com.ge.treasury.myfunding.domain.myfunding.TransactionExample.Criteria;
import com.ge.treasury.myfunding.dto.DealActivityDTO;
import com.ge.treasury.myfunding.dto.DealDetailDTO;
import com.ge.treasury.myfunding.dto.TransactionDetailsDTO;
import com.ge.treasury.myfunding.exceptions.BusinessException;
import com.ge.treasury.myfunding.exceptions.DBException;
import com.ge.treasury.myfunding.exceptions.SystemException;
import com.ge.treasury.myfunding.mapper.myfunding.DealActivityMapper;
import com.ge.treasury.myfunding.mapper.myfunding.DealMapper;
import com.ge.treasury.myfunding.mapper.myfunding.DocumentMapper;
import com.ge.treasury.myfunding.mapper.myfunding.EntityMapper;
import com.ge.treasury.myfunding.mapper.myfunding.MyFundingMapper;
import com.ge.treasury.myfunding.mapper.myfunding.TeamMapper;
import com.ge.treasury.myfunding.mapper.myfunding.TransactionMapper;
import com.ge.treasury.myfunding.utils.DealStatusHelper;
import com.ge.treasury.myfunding.utils.MyFundingLogger;

/**
 * Contains the implementation of MyFundingRequestManagerService methods
 * 
 * @author MyFunding Dev Team
 *
 */
@Service
public class MyFundingRequestServiceManagerImpl implements MyFundingRequestManagerService {

	@Autowired
	private DealMapper dealMapper;
	@Autowired
	private TransactionMapper transactionMapper;
	@Autowired
	private TeamMapper teamMapper;
	@Autowired
	private DocumentMapper documentMapper;
	@Autowired
	private EntityMapper entityMapper;
	@Autowired
	private LovDao lovDao;
	@Autowired
	private MyFundingMapper myFundingMapper;
	@Autowired
	private BoxServiceImpl boxServiceImpl;

	@Autowired
	private MDMService mdmService;

	@Autowired
	private LovController lovController;
	
	@Autowired
	private DealActivityMapper activityMapper;

	// sonar changes starts
	// public static RestTemplate tokenTemplate = null;
	private static RestTemplate tokenTemplate;

	// public static Map<String, String> queryParams = null;
	private static Map<String, String> queryParams;
	// sonar changes ends

	public static final String DAY1 = "DAY1";
	public static final String DAY2 = "DAY2";

	@Override
	@Transactional
	public Deal createDeal(Deal deal, User user) throws SystemException, DBException {
		// sonar fix starts
		// have created new dealLocalvariable instead of deal
		Deal dealLocal = deal;
		try {
			// set defaults

			if (dealLocal.getDealId() == null) {
				int primaryKey = dealMapper.getDealId();
				Date date = new Date();
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-");
				dealLocal.setDealId(primaryKey);
				String str = sdf.format(date) + primaryKey;
				dealLocal.setDealDisplay(str);
				dealLocal = (Deal) setdefaults(dealLocal, user, ControllersConstants.CREATE);
				// Please uncomment below section once the MDS service is up
				String errorMessages = mdmService.calculateUSDAmmount(dealLocal);
				setDealDayCode(dealLocal);
				dealMapper.insert(dealLocal);
				MyFundingLogger.logDebug(this, "deal created:" + primaryKey);
				//DealActivity dealActivity = new DealActivity();
                insertDealActivity(dealLocal, user, ControllersConstants.CREATE);

				if (null != dealLocal) {

					if (null != dealLocal.getTeams() && !dealLocal.getTeams().isEmpty()) {

						for (Team team : dealLocal.getTeams()) {
							team.setDealId(dealLocal.getDealId());
							team = (Team) setdefaults(team, user, ControllersConstants.UPDATE);

							teamMapper.insert(team);
							MyFundingLogger.logDebug(this, "team created:" + team.getDealTeamActorId());
						}
					}
					// Code to update the document details
					updateDocument(dealLocal, user);

				}

			} else {
				dealLocal.setLastUpdateTimestamp(new Date());
				dealLocal.setLastUpdateUser(user.getSso());
				// Please uncomment below section once the MDS service is up
				String errorMessages = mdmService.calculateUSDAmmount(dealLocal);
				dealMapper.updateByPrimaryKeySelective(dealLocal);
				MyFundingLogger.logDebug(this, "deal updated:" + dealLocal.getDealId());
			}

		} catch (SystemException e) {
			MyFundingLogger.logInfo(e, e.getMessage());
			throw new SystemException(e.getMessage(), e);
		} catch (DBException e) {
			MyFundingLogger.logInfo(e, e.getMessage());
			throw new DBException(e.getMessage(), e);
		}
		isDealPageReadOnly(dealLocal, user);
		return dealLocal;
		// sonar fix ends
	}

	@Override
	public Deal updateDeal(Deal deal, User user) throws DBException, SystemException {
		// sonar fix starts
		// replaced deal variable with dealLocal
		Deal dealLocal = deal;
		String dealStatusCode = dealLocal.getDealStatusCode();
		try {
			dealLocal = (Deal) setdefaults(dealLocal, user, ControllersConstants.UPDATE);
			if (null != dealLocal.getDealStatusCode()
					&& ControllersConstants.DEALSTATUS_SUBMIT.equalsIgnoreCase(dealLocal.getDealStatusCode())) {
				dealLocal.setDealRequestTimestamp(new Date());
				dealLocal.setReadOnly(true);
			}
			// Please uncomment below section once the MDS service is up
			String errorMessages = mdmService.calculateUSDAmmount(dealLocal);
			setDealDayCode(dealLocal);
			dealMapper.updateByPrimaryKey(dealLocal);
			MyFundingLogger.logDebug(this, "deal Updated:" + dealLocal.getDealId());

			if (null != dealLocal.getTeams() && !dealLocal.getTeams().isEmpty()) {
				for (Team team : dealLocal.getTeams()) {
					if (team.getDealTeamActorId() == null) {
						Team team1 = new Team();
						team1 = (Team) setdefaults(team, user, ControllersConstants.CREATE);
						team1.setDealId(dealLocal.getDealId());
						team1.setTeamActorStatusCode(ControllersConstants.TEAMSTATUS_REVIEWPENDING);
						team1.setDealTeamActorCode(team.getDealTeamActorCode());
						team1.setStatus(team.getStatus());
						teamMapper.insert(team1);
						
						MyFundingLogger.logDebug(this, "team update:" + team.getDealTeamActorId());
					} else {
						//insertTeamActivity(deal,user,ControllersConstants.CHECK);
						team = (Team) setdefaults(team, user, ControllersConstants.UPDATE);
						
						teamMapper.updateByPrimaryKey(team);
						insertTeamActivity(dealLocal, team,user, ControllersConstants.CREATE);
						MyFundingLogger.logDebug(this, "team update:" + team.getDealTeamActorId());
						
					}
				}
			}
			// Adding this logic to insert three records in team record table
			// only on deal submit to pipeline
			if (null != dealLocal.getDealStatusCode()
					&& ControllersConstants.DEALSTATUS_SUBMIT.equalsIgnoreCase(dealLocal.getDealStatusCode())) {
				if (null != user && null != user.getAppRole()
						&& user.getAppRole().equalsIgnoreCase(ControllersConstants.REQUESTER_ROLE)) {
					Team team = new Team();
					team = (Team) setdefaults(team, user, ControllersConstants.CREATE);
					team.setDealId(dealLocal.getDealId());
					team.setStatus(ControllersConstants.TEAMSTATUS_ACTIVE);

					team.setTeamActorStatusCode(ControllersConstants.TEAMSTATUS_REVIEWPENDING);
					team.setDealTeamActorCode(ControllersConstants.TEAMCODE_FO);
					teamMapper.insert(team);
					MyFundingLogger.logDebug(this,
							"team created for Frontoffice user with team id:" + team.getDealTeamActorId());
				}

			}

			updateDocument(dealLocal, user);

			if (null != dealLocal.getTransactions() && !dealLocal.getTransactions().isEmpty()) {
				for (Transaction transaction : dealLocal.getTransactions()) {
					transaction.setDealId(dealLocal.getDealId());
					this.updateTransaction(transaction, user);
				}
			}
			
			
			//deal activity record
			if (null != dealStatusCode){
			    insertDealActivity(dealLocal, user, ControllersConstants.CHECK);
			}
		} catch (SystemException e) {
			throw new SystemException(e.getMessage(), e);
		} catch (DBException e) {
			throw new DBException(e.getMessage(), e);
		}
		// Logic to make page read only
		if (!("reject".equals(deal.getAction()) || ("cancel".equals(deal.getAction())))) {
			isDealPageReadOnly(dealLocal, user);
		}
		return dealLocal;
		// sonar fix ends
	}

	public void updateDocument(Deal deal, User user) {
		if (null != deal.getDocuments() && !deal.getDocuments().isEmpty()) {
			for (Document document : deal.getDocuments()) {
				document.setDealId(deal.getDealId());
				document = (Document) setdefaults(document, user, ControllersConstants.CREATE);
				document.setFolderId(AkanaConstant.getFolderId());
				if (null == document.getDocumentId()) {
					if ("Y".equalsIgnoreCase(document.getIsActive()))
						documentMapper.insert(document);
					MyFundingLogger.logDebug(this,
							"Document Inserted Successfully in MyFunding Data base for Deal Id :  "
									+ document.getDealId());
				} else if (null != document.getIsActive() && "N".equalsIgnoreCase(document.getIsActive().toString())
						&& null != document.getFileId()) {
					// Logic to delete the document
					boxServiceImpl.deleteFile(document.getFileId());
					documentMapper.updateByPrimaryKey(document);
				} else {
					documentMapper.updateByPrimaryKey(document);
					MyFundingLogger.logDebug(this, "document updated:" + document.getDocumentId());
				}
			}
		}
	}

	@Override
	public Deal findDealById(Integer dealId) throws DBException {

		return dealMapper.selectByPrimaryKey(dealId);

	}

	@Override
	public Deal getDealById(final Integer dealId) {
		Deal deal = null;

		final Map<String, Object> searchMap = new HashMap<String, Object>();

		searchMap.put("dealId", dealId);

		final List<Deal> dealList = myFundingMapper.findDeal(searchMap);

		if (!dealList.isEmpty()) {
			deal = dealList.get(0);
		}

		return deal;
	}

	@Override
	public List<Transaction> createTransaction(Transaction transaction, User user) throws DBException, SystemException {
		// sonar fix starts
		//// replaced transaction variable with transacationLocal
		Transaction transactionLocal = (Transaction) setdefaults(transaction, user, ControllersConstants.CREATE);
		// Adding below hard coded values as this values are mandatory
		try {
			// Please uncomment below section once the MDS service is up
			if (transactionLocal.getAmount() == null) {
				transactionLocal.setAmount(0.0);
			} else {
				String errorMessage = mdmService.calculateUSDAmmount(transactionLocal);
			}
			//transactionLocal.setIsWssManaged("N");
			updateTransactionDayCode(transactionLocal);

			if (ControllersConstants.TRANSACTION_EVENT_ADMENDMENT.equals(transactionLocal.getEventTypeCode())) {
				
				// Fetching new transaction id from sequence
				int orgTransactionId = transactionMapper.getDealTransactionId();

				// Inserting inner original transaction
				Transaction orgTransaction = transactionLocal.getOrgTransaction();
				setdefaults(orgTransaction, user, ControllersConstants.CREATE);
				orgTransaction.setDealTransactionId(orgTransactionId);
				updateTransactionDayCode(orgTransaction);
				transactionMapper.insert(orgTransaction);

				// Inserting outer new transaction
				transactionLocal.setOrigDealTransID(orgTransactionId);
				transactionMapper.insert(transactionLocal);

			} else {
				transactionMapper.insert(transactionLocal);
			}

			updateDealUSDAmount(transactionLocal);
			updateDealDayCode(transactionLocal);

			MyFundingLogger.logDebug(this, "transaction created:" + transactionLocal.getDealTransactionId());
			if (null != transactionLocal.getEntities() && !transactionLocal.getEntities().isEmpty()) {
				for (Entity entity : transactionLocal.getEntities()) {
					if (null != entity && null != entity.getGoldLe() && null != entity.getBusinessUnitName()
							&& null != entity.getBusinessEntityCode()) {
						entity.setDealTransactionId(transactionLocal.getDealTransactionId());
						entity = (Entity) setdefaults(entity, user, ControllersConstants.CREATE);
						entityMapper.insert(entity);
						MyFundingLogger.logDebug(this, "entity created:" + entity.getBusinessEntityId());
					}
				}
			}

			// Method to update the document Object
			updateTransactionDocument(transactionLocal, user);

			TransactionExample query = new TransactionExample();
			query.createCriteria().andDealIdEqualTo(transactionLocal.getDealId());
			// sonar fix ends
			return transactionMapper.selectByExample(query);
		} catch (DBException db) {
			throw new DBException("Exception while Creating the Transaction " + db.getMessage(), db);
		} catch (SystemException e) {
			throw new SystemException(e.getMessage(), e);
		}

		// return transaction;
	}

	@Override
	public Transaction updateTransaction(Transaction transaction, User user) throws DBException, SystemException {
		// sonar fixes start
		// replaced transaction variable with transacationLocal
		Transaction transactionLocal = transaction;
		try {

			transactionLocal = (Transaction) setdefaults(transactionLocal, user, ControllersConstants.UPDATE);
			// Please uncomment below section once the MDS service is up
			String errorMessage = mdmService.calculateUSDAmmount(transactionLocal);
			updateTransactionDayCode(transactionLocal);

			if (ControllersConstants.TRANSACTION_EVENT_ADMENDMENT.equals(transactionLocal.getEventTypeCode())) {
			    Transaction orgTransaction = transactionLocal.getOrgTransaction();
			    mdmService.calculateUSDAmmount(orgTransaction);
			    updateTransactionDayCode(orgTransaction);
				transactionMapper.updateByPrimaryKey(orgTransaction);
				transactionMapper.updateByPrimaryKey(transactionLocal);

			} else {
				transactionMapper.updateByPrimaryKey(transactionLocal);
			}

			updateDealUSDAmount(transactionLocal);
			updateDealDayCode(transactionLocal);

			MyFundingLogger.logDebug(this, "transaction updated:" + transactionLocal.getDealTransactionId());

			for (Entity entity : transactionLocal.getEntities()) {
				if (null != entity && null == entity.getBusinessEntityId() && null != entity.getGoldLe()
						&& null != entity.getBusinessUnitName() && null != entity.getBusinessEntityCode()) {
					entity.setDealTransactionId(transactionLocal.getDealTransactionId());
					entity = (Entity) setdefaults(entity, user, ControllersConstants.CREATE);
					entityMapper.insert(entity);
					MyFundingLogger.logDebug(this, "entity created:" + entity.getBusinessEntityId());
				} else if (null != entity && null != entity.getGoldLe() && null != entity.getBusinessUnitName()
						&& null != entity.getBusinessEntityCode()) {
					entity.setDealTransactionId(transactionLocal.getDealTransactionId());
					entity = (Entity) setdefaults(entity, user, ControllersConstants.UPDATE);
					entityMapper.updateByPrimaryKey(entity);
					MyFundingLogger.logDebug(this, "entity updated:" + entity.getBusinessEntityId());
				}

			}
			// Method to update the document Object
			updateTransactionDocument(transactionLocal, user);

		} catch (DBException e) {
			throw new DBException(e.getMessage(), e);
		} catch (SystemException e) {
			throw new SystemException(e.getMessage(), e);
		}

		return transactionLocal;
		// sonar changes ends
	}

	private void updateDealUSDAmount(Transaction transaction) {
		if (!ControllersConstants.TRANSACTION_DEBTCASHPOOL.equals(transaction.getSubTypeCode())) {
			Deal deal = getDealById(transaction.getDealId());

			Deal dealForUpdate = new Deal();
			dealForUpdate.setDealId(transaction.getDealId());
			// Please uncomment below section once the MDS service is up
			String errorMessages = mdmService.calculateUSDAmmount(deal);
			dealForUpdate.setUsdEquivalentAmount(deal.getUsdEquivalentAmount());
			dealForUpdate.setLastUpdateUser(transaction.getLastUpdateUser());
			dealForUpdate.setLastUpdateTimestamp(transaction.getLastUpdateTimestamp());

			dealMapper.updateByPrimaryKeySelective(dealForUpdate);
		}
	}

	@Override
	public List<Transaction> findTransaction(final Map<String, String> searchMap) throws DBException {

		final TransactionExample te = new TransactionExample();

		final Criteria c = te.createCriteria();

		if (StringUtils.isNotEmpty(searchMap.get("deal_id"))) {
			c.andDealIdEqualTo(Integer.parseInt(searchMap.get("deal_id")));// will
			// check
			// into
			// deal_id
			// columns
		}
		if (StringUtils.isNotEmpty(searchMap.get("dealName"))) {
			c.andDealNameExists(searchMap.get("dealName")); // will check into
			// DEAL_ID-DEAL_NAME
			// columns
		}

		if (StringUtils.isNotEmpty(searchMap.get("dealStatusCode"))) {
			if (!lovDao.existsLookupCode(searchMap.get("dealStatusCode"), "DEALRQST_STATUS")) {
				throw new DBException("Invalid Deal Status Code");
			}
			c.andDealStatusExists(searchMap.get("dealStatusCode")); // T_MYFUND_LOOKUP.LOOKUP_TYPE
			// =
			// 'DEALRQST_STATUS'
		}

		if (StringUtils.isNotEmpty(searchMap.get("dealPriorityCode"))) {
			if (!lovDao.existsLookupCode(searchMap.get("dealPriorityCode"), "DEALPRIORITY_TYPE")) {
				throw new DBException("Invalid Deal Priority Code");
			}

			c.andDealPriorityExists(searchMap.get("dealPriorityCode")); // T_MYFUND_LOOKUP.LOOKUP_TYPE
			// =
			// 'DEALPRIORITY_TYPE'
		}

		if (StringUtils.isNotEmpty(searchMap.get("foOwner"))) {
			c.andFoOwnerEqualToInsensitive(searchMap.get("foOwner")); // Deal.Transaction.fo_owner
		}

		if (StringUtils.isNotEmpty(searchMap.get("dealEntiry"))) {
			c.andGoldLeCdrExists(searchMap.get("dealEntiry")); // Deal.Transaction.Business_Entiry.gold_le/cdr_code
		}

		/*
		 * final String dealSort = searchMap.get("dealSort"); //1 -
		 * AFFIRM_DUE_DATE, 2 - DEAL_REQUEST_TIMESTAMP
		 * if(StringUtils.isNotEmpty( dealSort )) { if( "1".equals(dealSort) ) {
		 * de.setOrderByClause("AFFIRM_DUE_DATE " + searchMap.get("ascDesc")); }
		 * else if ( "2".equals(dealSort) ) {
		 * de.setOrderByClause("DEAL_REQUEST_TIMESTAMP " +
		 * searchMap.get("ascDesc")); } }
		 */
		return transactionMapper.selectByExample(te);
	}

	public BaseDomainObject setdefaults(BaseDomainObject fundingObject, User user, String operation) {
		if (null != operation && "create".equalsIgnoreCase(operation)) {
			fundingObject.setCreateTimestamp(new Date());
			fundingObject.setCreateUser(user.getSso());
		}
		fundingObject.setLastUpdateTimestamp(new Date());
		fundingObject.setLastUpdateUser(user.getSso());

		return fundingObject;
	}
	
    public void insertDealActivity(Deal dealAct, User user, String operation) {
        DealActivityDTO dealActivity = new DealActivityDTO();
        
        String dealStatusCode = dealAct.getDealStatusCode();
        if (null != operation && "check".equalsIgnoreCase(operation)) {
            DealActivityDTO searchActivity= new DealActivityDTO();
            searchActivity.setDealId(dealAct.getDealId());
            searchActivity.setAction(dealStatusCode);
            List<DealActivityDTO> fetchactivity = activityMapper.selectByDealIdAndAction(searchActivity);
            if(fetchactivity == null || fetchactivity.size()==0){
                operation = ControllersConstants.CREATE;
            }
        }
         if (null != operation && "create".equalsIgnoreCase(operation)) {
        	 //String roleActivity = user.getAppRole()+"_REVIEWPENDING";
             dealActivity.setSso(user.getSso());
             dealActivity.setUserName(user.getUserDetails());
             dealActivity.setUserRole(user.getAppRole());
             dealActivity.setCreateTimeStamp(new Date());
             dealActivity.setCreateUser(user.getSso());
             dealActivity.setLastUpdateTimestamp(new Date());
             dealActivity.setLastUpdateUser(user.getSso());
             dealActivity.setDealId(dealAct.getDealId());
             //String dealStatusCode = dealAct.getDealStatusCode();
            // String role = user.getAppRole();
             //dealActivity.setAction(roleActivity);
             dealActivity.setAction(dealStatusCode);
             if (dealStatusCode.equals(ControllersConstants.DEALSTATUS_SUBMIT)){
                 dealActivity.setActComment(ControllersConstants.DEALSTATUS_SUBMIT_DISPLAY);
             }
             else if (dealStatusCode.equals(ControllersConstants.DEALSTATUS_DRAFT)){
                 dealActivity.setActComment(ControllersConstants.DEALSTATUS_DRAFT_DISPLAY);
             }
             else if (dealStatusCode.equals(ControllersConstants.DEALSTATUS_COMPLETE)){
                 dealActivity.setActComment(ControllersConstants.DEALSTATUS_COMPLETE_DISPLAY);
             }
             else if (dealStatusCode.equals(ControllersConstants.DEALSTATUS_CANCEL)){
                 dealActivity.setActComment(ControllersConstants.DEALSTATUS_CANCEL_DISPLAY);
             }
             else if (dealStatusCode.equals(ControllersConstants.DEALSTATUS_PENDINGSU)){
                 dealActivity.setActComment(ControllersConstants.DEALSTATUS_PENDINGSU_DISPLAY);
             }
             else if (dealStatusCode.equals(ControllersConstants. DEALSTATUS_PENDINGAFF)){
                 dealActivity.setActComment(ControllersConstants.DEALSTATUS_PENDINGAFF_DISPLAY);
             }
             else if (dealStatusCode.equals(ControllersConstants.DEALSTATUS_DOCCERT)){
                 dealActivity.setActComment(ControllersConstants.DEALSTATUS_DOCCERT_DISPLAY);
             }
             else{
                 dealActivity.setActComment("Undefined");
             }
             //dealActivity.setActComment(role+" - review pending");
             activityMapper.insert(dealActivity);
         }
         //dealActivity = (DealActivity) setdefaults(dealActivity,user,operation);

         //return dealActivity;
     }
   
    public void insertTeamActivity(Deal dealAct, Team team,User user, String operation) {
        DealActivityDTO dealActivity = new DealActivityDTO();
        
        String roleActivity = team.getDealTeamActorCode()+"_REVIEWPENDING";
        if (null != operation && "check".equalsIgnoreCase(operation)) {
            DealActivityDTO searchActivity= new DealActivityDTO();
            searchActivity.setDealId(dealAct.getDealId());
            searchActivity.setAction(roleActivity);
            List<DealActivityDTO> fetchactivity = activityMapper.selectByDealIdAndAction(searchActivity);
            if(fetchactivity == null || fetchactivity.size()==0){
                operation = ControllersConstants.CREATE;
            }
        }
         if (null != operation && "create".equalsIgnoreCase(operation)) {
             dealActivity.setSso(user.getSso());
             dealActivity.setUserName(user.getUserDetails());
             dealActivity.setUserRole(user.getAppRole());
             dealActivity.setCreateTimeStamp(new Date());
             dealActivity.setCreateUser(user.getSso());
             dealActivity.setLastUpdateTimestamp(new Date());
             dealActivity.setLastUpdateUser(user.getSso());
             dealActivity.setDealId(dealAct.getDealId());
             String role = team.getDealTeamActorCode();
             dealActivity.setAction(roleActivity);
            
             //dealActivity.setActComment(role+" - Completed review");
             
            	dealActivity.setActComment(role+" - review pending");
            
             
             activityMapper.insert(dealActivity);
         }
         //dealActivity = (DealActivity) setdefaults(dealActivity,user,operation);

         //return dealActivity;
   }
   

	@Override
	public List<Deal> findDeal(Map<String, String> searchMap) throws DBException {

		final DealExample de = new DealExample();

		final com.ge.treasury.myfunding.domain.myfunding.DealExample.Criteria c = de.createCriteria();

		if (StringUtils.isNotEmpty(searchMap.get("dealName"))) {
			c.andNameLikeInsensitive(searchMap.get("dealName")); // will check
			// into
			// DEAL_ID-DEAL_NAME
			// columns
		}

		if (StringUtils.isNotEmpty(searchMap.get("dealStatusCode"))) {
			if (!lovDao.existsLookupCode(searchMap.get("dealStatusCode"), "DEALRQST_STATUS")) {
				throw new DBException("Invalid Deal Status Code");
			}
			c.andDealStatusCodeEqualTo(searchMap.get("dealStatusCode")); // T_MYFUND_LOOKUP.LOOKUP_TYPE
			// =
			// 'DEALRQST_STATUS'
		}

		if (StringUtils.isNotEmpty(searchMap.get("dealPriorityCode"))) {
			if (!lovDao.existsLookupCode(searchMap.get("dealPriorityCode"), "DEALPRIORITY_TYPE")) {
				throw new DBException("Invalid Deal Priority Code");
			}

			c.andDealPriorityEqualTo(searchMap.get("dealPriorityCode")); // T_MYFUND_LOOKUP.LOOKUP_TYPE
			// =
			// 'DEALPRIORITY_TYPE'
		}

		if (StringUtils.isNotEmpty(searchMap.get("foOwner"))) {
			c.andFoOwnerExists(searchMap.get("foOwner")); // Deal.Transaction.fo_owner
		}

		if (StringUtils.isNotEmpty(searchMap.get("dealEntiry"))) {
			c.andGoldLeCdrExists(searchMap.get("dealEntiry")); // Deal.Transaction.Business_Entiry.gold_le/cdr_code
		}

		final String dealSort = searchMap.get("dealSort"); // 1 -
		// AFFIRM_DUE_DATE,
		// 2 -
		// DEAL_REQUEST_TIMESTAMP
		if (StringUtils.isNotEmpty(dealSort)) {
			if ("1".equals(dealSort)) {
				de.setOrderByClause("AFFIRM_DUE_DATE " + searchMap.get("ascDesc"));
			} else if ("2".equals(dealSort)) {
				de.setOrderByClause("DEAL_REQUEST_TIMESTAMP " + searchMap.get("ascDesc"));
			}
		}

		return dealMapper.selectByExample(de);
	}

	@Override
	public Document createDocument(Document document, User user) throws DBException {
		// sonar fix starts
		Document documentLocal = document;
		documentLocal = (Document) setdefaults(documentLocal, user, ControllersConstants.CREATE);
		documentMapper.insert(documentLocal);
		return documentLocal;
		// sonar fix ends
	}

	@Override
	public Document updateDocument(Document document, User user) throws DBException {
		// sonar fix starts
		Document documentLocal = document;
		documentLocal = (Document) setdefaults(documentLocal, user, ControllersConstants.UPDATE);
		documentMapper.updateByPrimaryKey(documentLocal);
		return documentLocal;
		// sonar fix ends
	}

	/*
	 * @Override public Document findDocumentById(Integer docId) { return
	 * documentMapper.selectByPrimaryKey(docId); }
	 */

	@Override
	public Transaction findTransactionById(Integer transactionId) {

		return transactionMapper.getTransactionDetails(transactionId);
	}

	@Override
	public List<Transaction> getTransactionsByDealid(final Integer dealId) {
		TransactionExample example = new TransactionExample();
		example.createCriteria().andDealIdEqualTo(dealId);

		return transactionMapper.selectByExample(example);
	}

	@Override
	public boolean deleteFile(String fileId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Deal getDealDetailsById(final Integer dealId, User user) {

		Deal deal = dealMapper.getDealDetailsById(dealId);

		// Logic to make the page readOnly
		isDealPageReadOnly(deal, user);

		return deal;
	}

	@Override
	public int getTransactionID() {
		return transactionMapper.getDealTransactionId();
	}

	@Override
	public TransactionTypeDetails getTransactionTypeDetails(String type) {

		TransactionTypeDetails transTypeDetails = new TransactionTypeDetails();
		transTypeDetails.setTransactionID(transactionMapper.getDealTransactionId());

		if ((ControllersConstants.TRANSACTION_DEBTCASHPOOL.equalsIgnoreCase(type))
				|| (ControllersConstants.TRANSACTION_DEBTRCA.equalsIgnoreCase(type))
				|| (ControllersConstants.TRANSACTION_DEBTTERMLOAN.equalsIgnoreCase(type))) {
			transTypeDetails.setTransactionType("TRANSACTION_DEBT");
		} else {
			transTypeDetails.setTransactionType("TRANSACTION_EQUITY");
		}

		transTypeDetails.setEntityList(lovController.getEntityTypes(type));
		/*
		 * List<String> entityLookup = lovController.getEntityTypes(type);
		 * 
		 * 
		 * List<String> cashpoolEntList = new ArrayList<String>(); List<String>
		 * termRCAEntList = new ArrayList<String>(); for(Lookup lookup:
		 * entityLookup){ if((lookup.getDescription().contains("Cashpool"))){
		 * cashpoolEntList.add(lookup.getLookupCode()); } else{
		 * termRCAEntList.add(lookup.getLookupCode()); } }
		 * 
		 * if(ControllersConstants.TRANSACTION_DEBTCASHPOOL.equalsIgnoreCase(
		 * type)){ transTypeDetails.setEntityList(cashpoolEntList); } else{
		 * transTypeDetails.setEntityList(termRCAEntList); }
		 */

		return transTypeDetails;
	}

	private boolean isDraft(final String statusCode) {
		return ControllersConstants.DEALSTATUS_DRAFT.equals(statusCode);
	}

	private boolean isSubmitComplete(final String statusCode) {
		return ControllersConstants.DEALSTATUS_SUBMIT.equals(statusCode)
				|| ControllersConstants.DEALSTATUS_COMPLETE.equals(statusCode);
	}

	private boolean hasTeamRecord(final List<Team> teams, final String role) {
		return (AffirmationServiceImpl.getTeamByRole(teams, role)) != null;
	}

	private void isDealPageReadOnly(Deal deal, User user) {
		// sonar changes starts
		Deal dealTemp = deal;
		if (dealTemp != null) {
			String userRole = user.getAppRole();
			String dealStatusCode = dealTemp.getDealStatusCode();

			if (ControllersConstants.REQUESTER_ROLE.equalsIgnoreCase(userRole)) {
				if (dealTemp.getCreateUser().equals(user.getSso())) {
					if (isDraft(dealStatusCode)) {
						dealTemp.setReadOnly(false);
					} else if (!isSubmitComplete(dealStatusCode)) {
						dealTemp = null;
					}
				} else {
					dealTemp = null;
				}
			} else if (ControllersConstants.TAX_ROLE.equalsIgnoreCase(userRole)
					|| ControllersConstants.TRANSFERPRICING_ROLE.equalsIgnoreCase(userRole)
					|| ControllersConstants.LEGAL_ROLE.equalsIgnoreCase(userRole)
					|| ControllersConstants.OPS_ROLE.equalsIgnoreCase(userRole)
					|| ControllersConstants.AFFIRMATION_ROLE.equalsIgnoreCase(userRole)) {
				dealTemp.setReadOnly(true);

			} else if (ControllersConstants.FO_ROLE.equalsIgnoreCase(userRole)) {
				dealTemp.setReadOnly(false);

			} else {
				throw new BusinessException("Invalid Role.");
			}

		}
		// sonar changes ends
	}

	@Override
	public void updateDocuments(Deal deal, List<MultipartFile> files, User user) {
		// TODO Auto-generated method stub
	}

	public void updateTransactionDocument(Transaction transactionLocal, User user) {
		if (null != transactionLocal.getDocuments() && !transactionLocal.getDocuments().isEmpty()) {
			for (Document document : transactionLocal.getDocuments()) {
				document.setDealId(transactionLocal.getDealId());
				// document.setDealTransactionId(transactionLocal.getDealTransactionId());
				document = (Document) setdefaults(document, user, ControllersConstants.CREATE);
				document.setFolderId(AkanaConstant.getFolderId());
				if (null == document.getDocumentId()) {
					if ("Y".equalsIgnoreCase(document.getIsActive())) {
						document.setDealTransactionId(transactionLocal.getDealTransactionId());
						documentMapper.insert(document);
						MyFundingLogger.logDebug(this,
								"Document Inserted Successfully in MyFunding Data base for Transaction Id :  "
										+ document.getDealTransactionId());
					}
				} else if (null != document.getIsActive() && "N".equalsIgnoreCase(document.getIsActive().toString())
						&& null != document.getFileId()) {
					// Logic to delete the document
					boxServiceImpl.deleteFile(document.getFileId());
					documentMapper.updateByPrimaryKey(document);
				} else {
					documentMapper.updateByPrimaryKey(document);
					MyFundingLogger.logDebug(this, "document updated:" + document.getDocumentId());
				}
			}
		}
	}

	private void updateTransactionDayCode(Transaction transaction) {
		String typeCode = transaction.getTypeCode();
		String subTypeCode = transaction.getSubTypeCode();
		String eventTypeCode = transaction.getEventTypeCode();

		if ("TRANSACTION_DEBT".equalsIgnoreCase(typeCode)) {
			if (ControllersConstants.TRANSACTION_DEBTRCA.equalsIgnoreCase(subTypeCode)) {
				if (("EVENT_NEW".equalsIgnoreCase(eventTypeCode))
						|| ("EVENT_ASSIGNMENT".equalsIgnoreCase(eventTypeCode))) {
					transaction.setDayType(DAY1);
				}
				// EVENT_DRAWDOWN, EVENT_PREPAYMENT, EVENT_EARLYTERMINATION
				else if (("EVENT_DRAWDOWN".equalsIgnoreCase(eventTypeCode))
						|| ("EVENT_PREPAYMENT".equalsIgnoreCase(eventTypeCode))
						|| ("EVENT_EARLYTERMINATION".equalsIgnoreCase(eventTypeCode))) {
					transaction.setDayType(DAY2);
				}
				else if("EVENT_AMENDMENT".equalsIgnoreCase(eventTypeCode)){
				// TODO: some logic needed; if p.amount is greater then c.amount
				// then DAY2
				// transaction.setDayType(DAY1);
				    Transaction orgTransaction = transaction.getOrgTransaction();
				    if (orgTransaction != null) {
				        double transAmount = transaction.getAmount();
				        double orgAmount = orgTransaction.getAmount();
				        if(transaction.getUsdEquivalentAmount() != null){
				            transAmount = transaction.getUsdEquivalentAmount();
				        }
				        if(orgTransaction.getUsdEquivalentAmount() != null){
				            orgAmount = orgTransaction.getUsdEquivalentAmount();
                        }
				        if(transAmount > orgAmount){
				            transaction.setDayType(DAY2);
				        }
				        else{
				            transaction.setDayType(DAY1);
				        }
				        }
				    else{
				            transaction.setDayType(DAY1);
				        }
				}
				else {
					transaction.setDayType(DAY1);
				}
            } else if (ControllersConstants.TRANSACTION_DEBTTERMLOAN.equalsIgnoreCase(subTypeCode)) {
                if (("EVENT_NEW".equalsIgnoreCase(eventTypeCode))
                        || ("EVENT_ASSIGNMENT".equalsIgnoreCase(eventTypeCode))
                        ) {
                    transaction.setDayType(DAY1);
                } else if ("EVENT_AMENDMENT".equalsIgnoreCase(eventTypeCode)) {
                    // if p.amount is greater then c.amount
                    // then DAY2
                    // transaction.setDayType(DAY1);
                        Transaction orgTransaction = transaction.getOrgTransaction();
                        if (orgTransaction != null) {
                            double transAmount = transaction.getAmount();
                            double orgAmount = orgTransaction.getAmount();
                            if(transaction.getUsdEquivalentAmount() != null){
                                transAmount = transaction.getUsdEquivalentAmount();
                            }
                            if(orgTransaction.getUsdEquivalentAmount() != null){
                                orgAmount = orgTransaction.getUsdEquivalentAmount();
                            }
                            if(transAmount > orgAmount){
                                transaction.setDayType(DAY2);
                            }
                            else{
                                transaction.setDayType(DAY1);
                            }
                            }
                        else{
                                transaction.setDayType(DAY1);
                            }
                    } 
				else if ("EVENT_PREPAYMENT".equalsIgnoreCase(eventTypeCode)) {
					transaction.setDayType(DAY2);
				} else if ("EVENT_EARLYTERMINATION".equalsIgnoreCase(eventTypeCode)) {
					// fixed>> DAY1, floating>> DAY2
					if ("Fixed".equalsIgnoreCase(transaction.getInterestType())) {
						transaction.setDayType(DAY1);
					} else if ("Floating".equalsIgnoreCase(transaction.getInterestType())) {
						transaction.setDayType(DAY2);
					}
				} else {
					transaction.setDayType(DAY1);
				}
			} else if (ControllersConstants.TRANSACTION_DEBTCASHPOOL.equalsIgnoreCase(subTypeCode)) {
				if ("EVENT_NEW".equalsIgnoreCase(eventTypeCode)) {
					transaction.setDayType(DAY1);
				}
				// TODO : Terminate Connection ..??
				/*
				 * else
				 * if("Terminate Connection".equalsIgnoreCase(eventTypeCode)){
				 * transaction.setDayType(DAY2); }
				 */
				else {
					transaction.setDayType(DAY1);
				}
			} else {
				transaction.setDayType(DAY1);
			}
		} else if (!ControllersConstants.TRANSACTION_EQUITY.equalsIgnoreCase(typeCode)) {
			transaction.setDayType(DAY1);
		}
	}

	private void setDealDayCode(Deal deal) {
		deal.setDayCode(DAY1);
		if (deal.getTransactions() != null) {
			for (Transaction transaction : deal.getTransactions()) {
				if (DAY2.equalsIgnoreCase(transaction.getDayType())) {
					deal.setDayCode(DAY2);
					break;
				}
			}
		}
	}

	private void updateDealDayCode(Transaction transaction) {
		Deal deal = getDealById(transaction.getDealId());
		if (deal != null) {
		    
		    Deal dealForUpdate = new Deal();
            dealForUpdate.setDealId(transaction.getDealId());
            dealForUpdate.setDayCode(DAY1);
	        if (deal.getTransactions() != null) {
	            for (Transaction t : deal.getTransactions()) {
	                if (DAY2.equalsIgnoreCase(t.getDayType())) {
	                    dealForUpdate.setDayCode(DAY2);
	                    break;
	                }
	            }
	            if(!(deal.getDayCode().equals(dealForUpdate.getDayCode()))){
	                dealMapper.updateByPrimaryKeySelective(dealForUpdate);
	            }
	        }
		}
	}

	@Override
	public DealDetailDTO getDealDetailsView(final Integer dealId, User user) {

		// Deal deal = dealMapper.getDealDetailsByIdInactiveTeam(dealId);
		Deal deal = dealMapper.getDealDetailsById(dealId);
		boolean hasAffirmation = false;
		Map<String, String> snuMap = new HashMap<String, String>();

		DealDetailDTO dealDetails = new DealDetailDTO();
		dealDetails.setDeal(deal);
		dealDetails.setUser(user);
		dealDetails = getDealView(dealDetails);

		for (Team team : deal.getTeams()) {

			if (ControllersConstants.TEAMCODE_AFFIRMATION.equals(team.getDealTeamActorCode())) {
				hasAffirmation = true;
			}

			if (ControllersConstants.TEAMCODE_TAX.equals(team.getDealTeamActorCode())) {
				snuMap.put(ControllersConstants.TEAMCODE_TAX, (team.getTeamActorStatusCode()));
			} else if (ControllersConstants.TEAMCODE_LEGAL.equals(team.getDealTeamActorCode())) {
				snuMap.put(ControllersConstants.TEAMCODE_LEGAL, (team.getTeamActorStatusCode()));
			} else if (ControllersConstants.TEAMCODE_TP.equals(team.getDealTeamActorCode())) {
				snuMap.put(ControllersConstants.TEAMCODE_TP, (team.getTeamActorStatusCode()));
			} else if (ControllersConstants.TEAMCODE_OPS.equals(team.getDealTeamActorCode())) {
				snuMap.put(ControllersConstants.TEAMCODE_OPS, (team.getTeamActorStatusCode()));
			}

		}

		dealDetails.setDealStatusTrain(DealStatusHelper.getDealStatusTrain(deal, hasAffirmation, snuMap));

		return dealDetails;
	}

	private DealDetailDTO getDealView(DealDetailDTO dealDetail) {

		String dealStatus = dealDetail.getDeal().getDealStatusCode();
		String userRole = dealDetail.getUser().getAppRole();
		Integer dealId = dealDetail.getDeal().getDealId();

		if (!(dealStatus.equals(ControllersConstants.DEALSTATUS_DRAFT))
				&& !(dealStatus.equals(ControllersConstants.DEALSTATUS_COMPLETE))
				&& !(dealStatus.equals(ControllersConstants.DEALSTATUS_CANCEL))) {

			if (!(userRole.equalsIgnoreCase((ControllersConstants.FO_ROLE)))
					&& !(userRole.equalsIgnoreCase((ControllersConstants.REQUESTER_ROLE)))) {

				for (Team team : dealDetail.getDeal().getTeams()) {
					Integer teamDealId = team.getDealId();
					String teamActorCode = team.getDealTeamActorCode().substring(
							(team.getDealTeamActorCode().indexOf('_') + 1), team.getDealTeamActorCode().length());

					// Flags for pipelineDetails
					if ((teamDealId.equals(dealId)) && teamActorCode.equals(userRole)) {
						if (team.getTeamActorStatusCode().equals(ControllersConstants.TEAMSTATUS_REVIEWPENDING)) {
							dealDetail.setPipelineReadOnly(false);
						} else {
							dealDetail.setPipelineReadOnly(true);
						}
					}
				}

				dealDetail.setDealGridReadOnly(true);
				dealDetail.setShowActionButtonForFO(false);
				calculateActionButtonForFO(dealDetail);

			} else if (userRole.equalsIgnoreCase(ControllersConstants.REQUESTER_ROLE)) {
				dealDetail.setReadOnly(true);
				calculateActionButtonForFO(dealDetail);
			} else {

				// Render New Page

				dealDetail.setDealGridReadOnly(false);
				dealDetail.setReadOnly(false);

				// setting value of action button
				calculateActionButtonForFO(dealDetail);
			}
		} else {
			if (userRole.equalsIgnoreCase(ControllersConstants.REQUESTER_ROLE)) {
				dealDetail.setReadOnly(false);
			} else {
				dealDetail.setReadOnly(true);
			}
		}
		return dealDetail;
	}

	private void calculateActionButtonForFO(DealDetailDTO dealDetailDTO) {
		String dealStatus = dealDetailDTO.getDeal().getDealStatusCode();
		Integer dealId = dealDetailDTO.getDeal().getDealId();
		int pendingReviewCount = 0;
		boolean isAffirationUserAvailable = false;
		boolean isAffirmationComplete = false;
		boolean hasSUCode = false;

		for (Team team : dealDetailDTO.getDeal().getTeams()) {
			Integer tempteamDealId = team.getDealId();
			String tempTeamActorCode = team.getDealTeamActorCode();

			// Check for Affirmation Actor Code
			if (tempTeamActorCode.equals(ControllersConstants.TEAMCODE_AFFIRMATION)) {
				isAffirationUserAvailable = true;

				// Status for Affirmation Actor Code
				if (team.getTeamActorStatusCode().equals(ControllersConstants.TEAMSTATUS_REVIEWCOMPLETE)
						&& team.getStatus().equals(ControllersConstants.TEAMSTATUS_ACTIVE)) {
					isAffirmationComplete = true;
				}

			}

			// Active Actors other than Affirmation and FO
			if ((tempteamDealId.equals(dealId))
					&& !(tempTeamActorCode.equals(ControllersConstants.TEAMCODE_FO)
							|| tempTeamActorCode.equals(ControllersConstants.TEAMCODE_AFFIRMATION))
					&& team.getStatus().equals(ControllersConstants.TEAMSTATUS_ACTIVE)) {
				// any of the 4 S&U states present
				hasSUCode = true;

				// Check for any pending status for S&U Actors
				if (team.getTeamActorStatusCode().equals(ControllersConstants.TEAMSTATUS_REVIEWPENDING)) {
					pendingReviewCount = pendingReviewCount + 1;
				}
			}
		}

		if (dealStatus.equals(ControllersConstants.DEALSTATUS_SUBMIT)) {
			dealDetailDTO.setShowActionButtonForFO(true);
			dealDetailDTO.setActionButtonLabelName(ControllersConstants.SUBMITTOSU);
			dealDetailDTO.setActionButtonValue(ControllersConstants.DEALSTATUS_PENDINGSU);
		}

		if (dealStatus.equals(ControllersConstants.DEALSTATUS_PENDINGSU)) {

			// Has S&U Actors with no pending status
			if (hasSUCode && pendingReviewCount == 0) {
				dealDetailDTO.setShowActionButtonForFO(true);

				if (isAffirationUserAvailable) {
					dealDetailDTO.setActionButtonLabelName(ControllersConstants.SUBMITTOAFF);
					dealDetailDTO.setActionButtonValue(ControllersConstants.DEALSTATUS_PENDINGAFF);

				} else {
					dealDetailDTO.setActionButtonLabelName(ControllersConstants.SUBMITTODOC);
					dealDetailDTO.setActionButtonValue(ControllersConstants.DEALSTATUS_DOCCERT);

				}
			}
		}

		if (dealStatus.equals(ControllersConstants.DEALSTATUS_PENDINGAFF)) {

			if (isAffirmationComplete) {
				dealDetailDTO.setShowActionButtonForFO(true);
				dealDetailDTO.setActionButtonLabelName(ControllersConstants.SUBMITTODOC);
				dealDetailDTO.setActionButtonValue(ControllersConstants.DEALSTATUS_DOCCERT);
			}
		}

		if (dealStatus.equals(ControllersConstants.DEALSTATUS_DOCCERT)) {
			List<Transaction> transactionList = dealDetailDTO.getDeal().getTransactions();

			for (Transaction t : transactionList) {
				if (null != t.getExecutionStatusCode()) {
					if (t.getIsWssManaged().equals(ControllersConstants.YES) && (t.getWssTradeID() != null)) {
						dealDetailDTO.setShowActionButtonForFO(true);
						dealDetailDTO.setActionButtonLabelName(ControllersConstants.SUBMITTOCOMP);
						dealDetailDTO.setActionButtonValue(ControllersConstants.DEALSTATUS_COMPLETE);
					} else if (t.getIsWssManaged().equals(ControllersConstants.NO)) {
						dealDetailDTO.setShowActionButtonForFO(true);
						dealDetailDTO.setActionButtonLabelName(ControllersConstants.SUBMITTOCOMP);
						dealDetailDTO.setActionButtonValue(ControllersConstants.DEALSTATUS_COMPLETE);
					}
				}
			}
		}

		// Rejected button population
		if (dealStatus.equals(ControllersConstants.DEALSTATUS_SUBMIT)) {
			dealDetailDTO.setRejectButtonValue(ControllersConstants.DEALSTATUS_DRAFT);
		} else if (dealStatus.equals(ControllersConstants.DEALSTATUS_PENDINGSU)) {
			dealDetailDTO.setRejectButtonValue(ControllersConstants.DEALSTATUS_SUBMIT);
		} else if (dealStatus.equals(ControllersConstants.DEALSTATUS_PENDINGAFF)) {
			dealDetailDTO.setRejectButtonValue(ControllersConstants.DEALSTATUS_PENDINGSU);
		} else if (dealStatus.equals(ControllersConstants.DEALSTATUS_DOCCERT)) {
			dealDetailDTO.setRejectButtonValue(ControllersConstants.DEALSTATUS_PENDINGAFF);
		}
	}

	@Override
	public TransactionDetailsDTO checkTransactionByRole(TransactionDetailsDTO transactionDetailsDTO) {
		String dealStatus = transactionDetailsDTO.getDeal().getDealStatusCode();
		String userRole = transactionDetailsDTO.getUser().getAppRole();
		List<Team> teamList = transactionDetailsDTO.getDeal().getTeams();

		if (dealStatus.equals(ControllersConstants.DEALSTATUS_COMPLETE) || dealStatus.equals(ControllersConstants.DEALSTATUS_CANCEL)) {
			transactionDetailsDTO.setReadOnly(true);
			transactionDetailsDTO.setHideTaxAndOpsFlag(false);
		} else if (dealStatus.equals(ControllersConstants.DEALSTATUS_DRAFT)) {
			if (userRole.equalsIgnoreCase(ControllersConstants.REQUESTER_ROLE)) {
				transactionDetailsDTO.setHideTaxAndOpsFlag(true);
				transactionDetailsDTO.setReadOnly(false);
			} else {
				transactionDetailsDTO.setReadOnly(true);
			}
		} else if (userRole.equalsIgnoreCase(ControllersConstants.FO_ROLE)) {
			transactionDetailsDTO.setReadOnly(false);
			transactionDetailsDTO.setHideTaxAndOpsFlag(false);
		} else if (userRole.equals(ControllersConstants.AFFIRMATION_ROLE)) {
			transactionDetailsDTO.setReadOnly(true);
		} else if (dealStatus.equals(ControllersConstants.DEALSTATUS_SUBMIT)) {
			if (userRole.equalsIgnoreCase(ControllersConstants.REQUESTER_ROLE)) {
				transactionDetailsDTO.setReadOnly(true);
				transactionDetailsDTO.setHideTaxAndOpsFlag(true);
			} else {
				transactionDetailsDTO.setReadOnly(true);
			}
		} else if (dealStatus.equals(ControllersConstants.DEALSTATUS_PENDINGSU)) {
			if (userRole.equalsIgnoreCase(ControllersConstants.REQUESTER_ROLE)) {
				transactionDetailsDTO.setReadOnly(true);
				transactionDetailsDTO.setHideTaxAndOpsFlag(true);
			} else if (teamList != null) {
				for (Team team : teamList) {
					String teamActorCode = team.getDealTeamActorCode();
					String teamStatusCode = team.getTeamActorStatusCode();

					if (userRole.equals(ControllersConstants.TAX_ROLE)
							&& teamActorCode.equals(ControllersConstants.TEAMCODE_TAX)) {
							if(teamStatusCode.equals(ControllersConstants.TEAMSTATUS_REVIEWPENDING)) {
								transactionDetailsDTO.setHideTaxAndOpsFlag(false);
								transactionDetailsDTO.setTaxUser(true);
							} else {
							transactionDetailsDTO.setReadOnly(true);
							}
					} else if (userRole.equals(ControllersConstants.TRANSFERPRICING_ROLE)
							&& teamActorCode.equals(ControllersConstants.TEAMCODE_TP)) {
							if(teamStatusCode.equals(ControllersConstants.TEAMSTATUS_REVIEWPENDING)) {
								transactionDetailsDTO.setHideTaxAndOpsFlag(false);
								transactionDetailsDTO.setTPUser(true);
							} else {
								transactionDetailsDTO.setReadOnly(true);
							}
					} else if (userRole.equals(ControllersConstants.LEGAL_ROLE)
							&& teamActorCode.equals(ControllersConstants.TEAMCODE_LEGAL)) {
							if(teamStatusCode.equals(ControllersConstants.TEAMSTATUS_REVIEWPENDING)) {
								transactionDetailsDTO.setHideTaxAndOpsFlag(false);
								transactionDetailsDTO.setLegalUser(true);
							} else {
								transactionDetailsDTO.setReadOnly(true);
							}
					} else if (userRole.equals(ControllersConstants.OPS_ROLE)
							&& teamActorCode.equals(ControllersConstants.TEAMCODE_OPS)) {
							if(teamStatusCode.equals(ControllersConstants.TEAMSTATUS_REVIEWPENDING)) {
								transactionDetailsDTO.setHideTaxAndOpsFlag(false);
								transactionDetailsDTO.setOpsUser(true);
							} else {
								transactionDetailsDTO.setReadOnly(true);
							}
					}
				}
			} else {
				transactionDetailsDTO.setReadOnly(true);
			}

		} else {
			transactionDetailsDTO.setReadOnly(true);
		}

		return transactionDetailsDTO;
	}

	@Override
	public List<Team> getTeamsByDealId(Integer dealId) {
		TeamExample query = new TeamExample();
		query.createCriteria().andDealIdEqualTo(dealId);
		return teamMapper.selectByExample(query);
	}

	@Override
	public DealDetailDTO rejectDeal(Integer dealId, User user) {
		// Code to get the deal
		Deal deal = dealMapper.getDealDetailsById(dealId);
		String dealStatus = deal.getDealStatusCode();
		// Code to set the reject deal Status in deal Object
		if (dealStatus.equals(ControllersConstants.DEALSTATUS_SUBMIT)) {
			deal.setDealStatusCode(ControllersConstants.DEALSTATUS_DRAFT);
		} else if (dealStatus.equals(ControllersConstants.DEALSTATUS_PENDINGSU)) {
			deal.setDealStatusCode(ControllersConstants.DEALSTATUS_SUBMIT);
			// Added logic to push all the team status to pending review
			updateTeamActorStatus(deal.getTeams());
		} else if (dealStatus.equals(ControllersConstants.DEALSTATUS_PENDINGAFF)) {
			deal.setDealStatusCode(ControllersConstants.DEALSTATUS_PENDINGSU);
		} else if (dealStatus.equals(ControllersConstants.DEALSTATUS_DOCCERT)) {
			deal.setDealStatusCode(ControllersConstants.DEALSTATUS_PENDINGAFF);
		}
		// Method to update the deal Object
		updateDeal(deal, user);
		return getDealDetailsView(dealId, user);
	}

	public void updateTeamActorStatus(List<Team> dealActorList) {
		if (!dealActorList.isEmpty()) {
			for (Team dealActor : dealActorList) {
				if (!dealActor.getDealTeamActorCode().equalsIgnoreCase(ControllersConstants.TEAMCODE_FO))
					dealActor.setTeamActorStatusCode(ControllersConstants.TEAMSTATUS_REVIEWPENDING);
				
			}
		}
	}

	@Override
	public Transaction getTransactionSample(Integer trans) {

		return transactionMapper.getTransactionDetails(trans);
	}

	@Override
	public Deal getDealTransSample(Integer deal) {

		return dealMapper.getDealTransDetailsById(deal);
	}

	@Override
	public void createTransNew(Transaction transaction) {

		if (null != transaction.getOrgTransaction()) {
			if (null != transaction.getOrgTransaction().getDealTransactionId()) {
				transactionMapper.insert(transaction.getOrgTransaction());// deal_transaction_id
																			// should
																			// have
																			// value
																			// |
																			// We
																			// don't
																			// need
																			// a
																			// seq
																			// <<this
																			// is
																			// parent>>
				transactionMapper.insertSelective(transaction);// will have the
																// parent_id in
																// the
																// original_deal_trans_id
																// attribute |
																// We need
																// sequence
																// <<this is
																// child>>
			}
		} else if (null != transaction.getDealTransactionId()) {
			transactionMapper.insert(transaction);// We don't need a sequence
		}

	}
	
   
}

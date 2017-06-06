/**
 * Copyright GE
 */
package com.ge.treasury.myfunding.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

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
import com.ge.treasury.myfunding.domain.myfunding.Lookup;
import com.ge.treasury.myfunding.domain.myfunding.Team;
import com.ge.treasury.myfunding.domain.myfunding.Transaction;
import com.ge.treasury.myfunding.domain.myfunding.TransactionExample;
import com.ge.treasury.myfunding.domain.myfunding.TransactionExample.Criteria;
import com.ge.treasury.myfunding.exceptions.BusinessException;
import com.ge.treasury.myfunding.exceptions.DBException;
import com.ge.treasury.myfunding.exceptions.SystemException;
import com.ge.treasury.myfunding.mapper.myfunding.DealMapper;
import com.ge.treasury.myfunding.mapper.myfunding.DocumentMapper;
import com.ge.treasury.myfunding.mapper.myfunding.EntityMapper;
import com.ge.treasury.myfunding.mapper.myfunding.MyFundingMapper;
import com.ge.treasury.myfunding.mapper.myfunding.TeamMapper;
import com.ge.treasury.myfunding.mapper.myfunding.TransactionMapper;
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

	//sonar changes starts
	//public static RestTemplate tokenTemplate = null;
	private static RestTemplate tokenTemplate;

	//public static Map<String, String> queryParams = null;
	private static Map<String, String> queryParams;
	//sonar changes ends


	@Override
	@Transactional
	public Deal createDeal(Deal deal, User user) throws SystemException,DBException {
				//sonar fix starts
		//have created new dealLocalvariable instead of deal
		Deal dealLocal=deal;
		try {
			// set defaults

			if (dealLocal.getDealId() == null) {
				int primaryKey = dealMapper.getDealId();
				Date date = new Date();
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-");
				dealLocal.setDealId(primaryKey);
				String str = sdf.format(date) + primaryKey;
				dealLocal.setDealDisplay(str);
				dealLocal = (Deal) setdefaults(dealLocal, user,ControllersConstants.CREATE);
				dealLocal.setUsdEquivalentAmount(mdmService.calculateUSDAmmount(dealLocal));
				dealMapper.insert(dealLocal);
				MyFundingLogger.logDebug(this, "deal created:" + primaryKey);

				if (null != dealLocal) {

					if (null != dealLocal.getTeams() && !dealLocal.getTeams().isEmpty()) {

						for (Team team : dealLocal.getTeams()) {
							team.setDealId(dealLocal.getDealId());
							team = (Team) setdefaults(team, user,ControllersConstants.UPDATE);

							teamMapper.insert(team);
							MyFundingLogger.logDebug(this, "team created:" + team.getDealTeamActorId());
						}
					}
					//Code to update the document details
					updateDocument(dealLocal, user);

				}
				
			} else {
				dealLocal.setLastUpdateTimestamp(new Date());
				dealLocal.setLastUpdateUser(user.getSso());
				dealLocal.setUsdEquivalentAmount(mdmService.calculateUSDAmmount(dealLocal));
				dealMapper.updateByPrimaryKeySelective(dealLocal);
				MyFundingLogger.logDebug(this, "deal updated:" + dealLocal.getDealId());
			}

		} catch (SystemException e) {
			MyFundingLogger.logInfo(e, e.getMessage());
            throw new SystemException(e.getMessage(), e);
        }catch (DBException e) {
			MyFundingLogger.logInfo(e, e.getMessage());
            throw new DBException(e.getMessage(), e);
        }
		isDealPageReadOnly(dealLocal, user);
		return dealLocal;
		//sonar fix ends
	}

	@Override
	public Deal updateDeal(Deal deal, User user) throws DBException,SystemException {
		//sonar fix starts
		//replaced deal variable with dealLocal
		Deal dealLocal=deal;
		try{
		dealLocal = (Deal) setdefaults(dealLocal, user,ControllersConstants.UPDATE);
		if(null!=dealLocal.getDealStatusCode() && "DEALSTATUS_SUBMIT".equalsIgnoreCase(dealLocal.getDealStatusCode()))
		{
			dealLocal.setDealRequestTimestamp(new Date());
			dealLocal.setReadOnly(true);
		}
		dealLocal.setUsdEquivalentAmount(mdmService.calculateUSDAmmount(dealLocal));
		dealMapper.updateByPrimaryKey(dealLocal);
		MyFundingLogger.logDebug(this, "deal Updated:" + dealLocal.getDealId());

		if (null != dealLocal.getTeams() && !dealLocal.getTeams().isEmpty()) {
			for (Team team : dealLocal.getTeams()) {
				team.setDealId(dealLocal.getDealId());

				team = (Team) setdefaults(team, user,ControllersConstants.UPDATE);

				teamMapper.updateByPrimaryKey(team);
				MyFundingLogger.logDebug(this, "team update:" + team.getDealTeamActorId());
			}
		}
		// Adding this logic to insert three records in team record table only on deal submit to pipeline
		if(null!=dealLocal.getDealStatusCode() && "DEALSTATUS_SUBMIT".equalsIgnoreCase(dealLocal.getDealStatusCode()))
		{
			Team team = new Team();
			team = (Team) setdefaults(team, user,ControllersConstants.CREATE);
			team.setDealId(dealLocal.getDealId());
			team.setStatus("ACTIVE"); // Hardcoded as of now - will revist later

			team.setTeamActorStatusCode(ControllersConstants.TEAMSTATUS_REVIEWPENDING);
			team.setDealTeamActorCode(ControllersConstants.TEAMCODE_TAX);
			teamMapper.insert(team);
			MyFundingLogger.logDebug(this, "team created for Tax user with team id:" + team.getDealTeamActorId());

			team.setTeamActorStatusCode(ControllersConstants.TEAMSTATUS_REVIEWPENDING);
			team.setDealTeamActorCode(ControllersConstants.TEAMCODE_FO);
			teamMapper.insert(team);
			MyFundingLogger.logDebug(this, "team created for Frontoffice user with team id:" + team.getDealTeamActorId());

			team.setTeamActorStatusCode(ControllersConstants.TEAMSTATUS_REVIEWPENDING);
			team.setDealTeamActorCode(ControllersConstants.TEAMCODE_TP);
			teamMapper.insert(team);
			MyFundingLogger.logDebug(this, "team created for Transfer Pricing user with team id:" + team.getDealTeamActorId());
		}

		//updateDocuments(dealLocal, files, user);
		updateDocument(dealLocal,  user);

		if (null != dealLocal.getTransactions() && !dealLocal.getTransactions().isEmpty()) {
			for (Transaction transaction : dealLocal.getTransactions()) {
				transaction.setDealId(dealLocal.getDealId());
				this.updateTransaction(transaction, user);
			}
		}
		}catch (SystemException e) {
            throw new SystemException(e.getMessage(), e);
        }catch (DBException e) {
            throw new DBException(e.getMessage(), e);
        }
		//Logic to make page read only
		isDealPageReadOnly(dealLocal, user);		
		return dealLocal;
		//sonar fix ends
	}

	
	public void updateDocument(Deal deal, User user) {
		if (null != deal.getDocuments() && !deal.getDocuments().isEmpty()) {
			for (Document document : deal.getDocuments()) {
				document.setDealId(deal.getDealId());
				document = (Document) setdefaults(document, user,ControllersConstants.CREATE);
				document.setFolderId(AkanaConstant.getFolderId());
				if(null==document.getDocumentId())
				{
					if("Y".equalsIgnoreCase(document.getIsActive()))
					     documentMapper.insert(document);
					MyFundingLogger.logDebug(this, "Document Inserted Successfully in MyFunding Data base for Deal Id :  " + document.getDealId());
				}
				else if(null!= document.getIsActive() && "N".equalsIgnoreCase(document.getIsActive().toString()) && null != document.getFileId())
				{
					// Logic to delete the document
					boxServiceImpl.deleteFile(document.getFileId());
					documentMapper.updateByPrimaryKey(document);
				}
				else{
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

		if(!dealList.isEmpty()) {
			deal = dealList.get(0);
		}

		return deal;
	}

	@Override
	public List<Transaction> createTransaction(Transaction transaction, User user) throws DBException,SystemException {
	//sonar fix starts
		////replaced transaction variable with transacationLocal
		Transaction transactionLocal = (Transaction) setdefaults(transaction, user,ControllersConstants.CREATE);
		// Adding below hard coded values as this values are mandatory
		try{
		    if(transactionLocal.getAmount()==null){
		        transactionLocal.setAmount(0.0);
		    } else {
		        transactionLocal.setUsdEquivalentAmount(mdmService.calculateUSDAmmount(transactionLocal));		        
		    }
		transactionLocal.setIsWssManaged("Y");

		transactionMapper.insert(transactionLocal);

        updateDealUSDAmount(transactionLocal);

		MyFundingLogger.logDebug(this, "transaction created:" + transactionLocal.getDealTransactionId());
		if (null != transactionLocal.getEntities() && !transactionLocal.getEntities().isEmpty()) {
			for (Entity entity : transactionLocal.getEntities()) {
				if (null != entity && null != entity.getGoldLe() &&  null != entity.getBusinessUnitName() && null != entity.getBusinessEntityCode()){
					entity.setDealTransactionId(transactionLocal.getDealTransactionId());
					if (null != entity.getEntityCurrency() && null != entity.getEntityCurrency().getCode()) {
					entity.setCurrencyCode(entity.getEntityCurrency().getCode());
					}
					entity = (Entity) setdefaults(entity, user,ControllersConstants.CREATE);
					entityMapper.insert(entity);
					MyFundingLogger.logDebug(this, "entity created:" + entity.getBusinessEntityId());
				}
			}
		}

		//Method to update the document Object
		updateTransactionDocument(transactionLocal, user);

		TransactionExample query = new TransactionExample();
		query.createCriteria().andDealIdEqualTo(transactionLocal.getDealId());
        //sonar fix ends
		return transactionMapper.selectByExample(query);
		}catch (DBException db) {
            throw new DBException("Exception while Creating the Transaction "+db.getMessage(), db);
        }catch (SystemException e) {
            throw new SystemException(e.getMessage(), e);
        }

		//return transaction;
	}

	@Override
	public Transaction updateTransaction(Transaction transaction, User user ) throws DBException,SystemException {
		//sonar fixes start
		//replaced transaction variable with transacationLocal
		Transaction transactionLocal=transaction;
		try{

			transactionLocal = (Transaction) setdefaults(transactionLocal, user,ControllersConstants.UPDATE);
			transactionLocal.setUsdEquivalentAmount(mdmService.calculateUSDAmmount(transactionLocal));
			transactionMapper.updateByPrimaryKey(transactionLocal);

			updateDealUSDAmount(transactionLocal);

			MyFundingLogger.logDebug(this, "transaction updated:" + transactionLocal.getDealTransactionId());

			for (Entity entity : transactionLocal.getEntities()) {
				if (null != entity && null == entity.getBusinessEntityId() && null != entity.getGoldLe() &&  null != entity.getBusinessUnitName() && null != entity.getBusinessEntityCode()){
					entity.setDealTransactionId(transactionLocal.getDealTransactionId());
					if (null != entity.getEntityCurrency() && null != entity.getEntityCurrency().getCode()) {
					entity.setCurrencyCode(entity.getEntityCurrency().getCode());
					}
					entity = (Entity) setdefaults(entity, user,ControllersConstants.CREATE);
					entityMapper.insert(entity);
					MyFundingLogger.logDebug(this, "entity created:" + entity.getBusinessEntityId());
				}else if (null != entity && null != entity.getGoldLe() &&  null != entity.getBusinessUnitName() && null != entity.getBusinessEntityCode()){
					entity.setDealTransactionId(transactionLocal.getDealTransactionId());
					if (null != entity.getEntityCurrency() && null != entity.getEntityCurrency().getCode()) {
						entity.setCurrencyCode(entity.getEntityCurrency().getCode());
						}
					entity = (Entity) setdefaults(entity, user,ControllersConstants.UPDATE);
					entityMapper.updateByPrimaryKey(entity);
					MyFundingLogger.logDebug(this, "entity updated:" + entity.getBusinessEntityId());
				}

			}
			//Method to update the document Object
			updateTransactionDocument(transactionLocal, user);

		} catch (DBException e) {
			throw new DBException(e.getMessage(), e);
		} catch (SystemException e) {
            throw new SystemException(e.getMessage(), e);
        }

		return transactionLocal;
		//sonar changes ends
	}

	private void updateDealUSDAmount(Transaction transaction) {
        if(! ControllersConstants.TRANSACTION_DEBTCASHPOOL.equals( transaction.getSubTypeCode() )){
            Deal deal = getDealById(transaction.getDealId());
            
            Deal dealForUpdate = new Deal();
            dealForUpdate.setDealId(transaction.getDealId());
            dealForUpdate.setUsdEquivalentAmount(mdmService.calculateUSDAmmount(deal));
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
		if(null!= operation && "create".equalsIgnoreCase(operation))
		{
			fundingObject.setCreateTimestamp(new Date());
			fundingObject.setCreateUser(user.getSso());
		}
		fundingObject.setLastUpdateTimestamp(new Date());
		fundingObject.setLastUpdateUser(user.getSso());
		

		return fundingObject;
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
		//sonar fix starts
		Document documentLocal=document;
		documentLocal = (Document) setdefaults(documentLocal, user ,ControllersConstants.CREATE);
		documentMapper.insert(documentLocal);
		return documentLocal;
		//sonar fix ends
	}

	@Override
	public Document updateDocument(Document document, User user) throws DBException {
		//sonar fix starts
		Document documentLocal=document;
		documentLocal = (Document) setdefaults(documentLocal, user,ControllersConstants.UPDATE);
		documentMapper.updateByPrimaryKey(documentLocal);
		return documentLocal;
		//sonar fix ends
	}

	/*@Override
	public Document findDocumentById(Integer docId) {
		return documentMapper.selectByPrimaryKey(docId);
	}*/

	@Override
	public Transaction findTransactionById(Integer transactionId) {

		return transactionMapper.selectByPrimaryKey(transactionId);
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
	   isDealPageReadOnly(deal,user); 
	    
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
        
        if((ControllersConstants.TRANSACTION_DEBTCASHPOOL.equalsIgnoreCase(type))
                || (ControllersConstants.TRANSACTION_DEBTRCA.equalsIgnoreCase(type))
                || (ControllersConstants.TRANSACTION_DEBTTERMLOAN.equalsIgnoreCase(type))){
            transTypeDetails.setTransactionType("TRANSACTION_DEBT");
        }
        else{
            transTypeDetails.setTransactionType("TRANSACTION_EQUITY");
        }
        
        transTypeDetails.setEntityList(lovController.getEntityTypes(type));
        /*List<String> entityLookup = lovController.getEntityTypes(type);
        

        List<String> cashpoolEntList = new ArrayList<String>();
        List<String> termRCAEntList = new ArrayList<String>();
        for(Lookup lookup: entityLookup){
            if((lookup.getDescription().contains("Cashpool"))){
                cashpoolEntList.add(lookup.getLookupCode());
            }
            else{
                termRCAEntList.add(lookup.getLookupCode());
            }
        }
        
        if(ControllersConstants.TRANSACTION_DEBTCASHPOOL.equalsIgnoreCase(type)){
            transTypeDetails.setEntityList(cashpoolEntList);
        }
        else{
            transTypeDetails.setEntityList(termRCAEntList);
        }*/
        
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
	
	private void isDealPageReadOnly(Deal deal , User user)
	{
		 //sonar changes starts
		Deal dealTemp=deal;
	    if(dealTemp!=null) {
            String userRole = user.getAppRole();
            String dealStatusCode = dealTemp.getDealStatusCode();
            
            if(ControllersConstants.REQUESTER_ROLE.equalsIgnoreCase(userRole)) {
                if(dealTemp.getCreateUser().equals(user.getSso())) {
                    if( isDraft(dealStatusCode) ) {
                    	dealTemp.setReadOnly(false);
                    } else if (! isSubmitComplete(dealStatusCode) ) {
                    	dealTemp = null;
                    }
                } else {
                	dealTemp = null;
                }
            }else if(ControllersConstants.TAX_ROLE.equalsIgnoreCase(userRole)
                    || ControllersConstants.TRANSFERPRICING_ROLE.equalsIgnoreCase(userRole)){
            	dealTemp.setReadOnly(true);
               
            }else if(ControllersConstants.FO_ROLE.equalsIgnoreCase(userRole)){
            	dealTemp.setReadOnly(false);
            	
            } else {
                throw new BusinessException("Invalid Role.");
            }

	    }
	  //sonar changes ends
	}

	@Override
	public void updateDocuments(Deal deal, List<MultipartFile> files, User user) {
		// TODO Auto-generated method stub
	}

	public void updateTransactionDocument(Transaction transactionLocal , User user)
	{
		if (null != transactionLocal.getDocuments() && !transactionLocal.getDocuments().isEmpty()) {
			for (Document document : transactionLocal.getDocuments()) {
					document.setDealId(transactionLocal.getDealId());
					//document.setDealTransactionId(transactionLocal.getDealTransactionId());
					document = (Document) setdefaults(document, user,ControllersConstants.CREATE);
					document.setFolderId(AkanaConstant.getFolderId());
					if(null==document.getDocumentId())
					{
						if("Y".equalsIgnoreCase(document.getIsActive()))
						{
						    document.setDealTransactionId(transactionLocal.getDealTransactionId());
						    documentMapper.insert(document);
						    MyFundingLogger.logDebug(this, "Document Inserted Successfully in MyFunding Data base for Transaction Id :  " + document.getDealTransactionId());
						}
					}
					else if(null!= document.getIsActive() && "N".equalsIgnoreCase(document.getIsActive().toString())  && null != document.getFileId() )
					{
						// Logic to delete the document
						boxServiceImpl.deleteFile(document.getFileId());
						documentMapper.updateByPrimaryKey(document);
					}
					else{
						documentMapper.updateByPrimaryKey(document);
						MyFundingLogger.logDebug(this, "document updated:" + document.getDocumentId());
					}
				}
				}
	}
}

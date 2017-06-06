/**
 * Copyright GE
 */
package com.ge.treasury.myfunding.controllers;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.ge.treasury.myfunding.constants.ControllersConstants;
import com.ge.treasury.myfunding.controllers.model.PaginatedResultList;
import com.ge.treasury.myfunding.dao.TransactionTypeDetails;
import com.ge.treasury.myfunding.domain.User;
import com.ge.treasury.myfunding.domain.myfunding.Deal;
import com.ge.treasury.myfunding.domain.myfunding.Document;
import com.ge.treasury.myfunding.domain.myfunding.Transaction;
import com.ge.treasury.myfunding.dto.DealDetailDTO;
import com.ge.treasury.myfunding.dto.TransactionDetailsDTO;
import com.ge.treasury.myfunding.exceptions.BusinessException;
import com.ge.treasury.myfunding.exceptions.DBException;
import com.ge.treasury.myfunding.exceptions.SystemException;
import com.ge.treasury.myfunding.exceptions.ValidationFailedException;
import com.ge.treasury.myfunding.helper.SearchMapValidator;
import com.ge.treasury.myfunding.service.MyFundingRequestManagerService;
import com.ge.treasury.myfunding.utils.MessageValidator;
import com.ge.treasury.myfunding.utils.StringHelper;
import com.ge.treasury.myfunding.validation.DocumentValidator;
import com.ge.treasury.myfunding.validation.RequestGroupValidator;
import com.ge.treasury.myfunding.validation.TransactionValidator;
import com.google.gson.JsonParseException;

/**
 * REST services for create/edit/obtain request groups
 * 
 * @author MyFunding Dev Team
 *
 */
@Controller
@RequestMapping("/api/myfunding/v1")
public class MyFundingRequestController extends BaseController {
    
    @Autowired
    MessageValidator messageValidator;

    @Autowired
    MyFundingRequestManagerService fundingService;

    /**
     * @param request
     * @param deal
     * @return
     * @throws ValidationFailedException
     */
    @RequestMapping(value = "/deals", method = RequestMethod.POST, produces = "application/json", consumes = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Deal saveDeal(HttpServletRequest request, @RequestBody Deal deal) throws ValidationFailedException,DBException {
    	Deal dealResult = null;
        try{
    	User user = getValidatedUserFromSession(request);
        BindingResult errors = new BeanPropertyBindingResult(deal,
                "deal");
        RequestGroupValidator validator = new RequestGroupValidator(
                messageValidator);
        validator.validate(deal, errors);
        
        
        if (errors.hasErrors()) {
            throw new ValidationFailedException(errors, 0);
        } else {
            deal.setDealStatusCode("DEALSTATUS_DRAFT");
            dealResult = fundingService.createDeal(deal, user);
        }
    	} catch (JsonParseException je) {
    		throw new SystemException("Unable to Parse JSON String "+je.getMessage(), je);
    	}
        catch (DBException db) {
            throw new DBException("Exception while Creating the Deal "+db.getMessage(), db);
        }
        catch (SystemException e) {
            throw new SystemException(e.getMessage(), e);
        }finally {
        	if (dealResult != null) {
        setLogPerfMessage(request,
                "Deal id " + dealResult.getDealId()
                        + " created");
        	}
        }
        return dealResult;
        
    }


    /**
     * @param request
     * @param id
     * @return
     */
    @RequestMapping(value = "/deals/{id}", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Deal getDealById(HttpServletRequest request, @PathVariable(value = "id") String id) {

        final String clean = StringHelper.getValidatedStringAlphaOnly(id);
        Integer dealId = Integer.parseInt(clean);
        Map<String, String> searchMap = new HashMap<String, String>();
        searchMap.put(ControllersConstants.DEAL_ID, clean);

        Deal dealResult = null;
        try {
            dealResult = fundingService.findDealById(dealId);
        } catch (BusinessException dbe) {
            throw new BusinessException(dbe.getMessage(), dbe);
        } finally {
            if (dealResult != null) {
                setLogPerfMessage(request, "Deal id " + dealResult.getDealId() + " found");
            } else {
                setLogPerfMessage(request, "deal id " + clean + " not found");
            }
        }

        return dealResult;
    }

    /**
     * @param request
     * @param transaction
     * @return
     * @throws ValidationFailedException
     */
/*    @RequestMapping(value = "/transactions", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.OK)*/
    @RequestMapping(value = "/transactions", method = RequestMethod.POST, produces = "application/json", consumes = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Transaction saveTransaction(HttpServletRequest request, @RequestBody Transaction transaction)
            throws ValidationFailedException {
        User user = getValidatedUserFromSession(request);
        

        // Add validation to confirm mandatory fields, dealId MUST BE PRESENT
        BindingResult errors = new BeanPropertyBindingResult(transaction, "transaction");
        TransactionValidator validator = new TransactionValidator(messageValidator);
        validator.validate(transaction, errors);
        try{
        if (errors.hasErrors()) {
            throw new ValidationFailedException(errors, 0);
        } else {
            fundingService.createTransaction(transaction, user);
        }
        } catch (DBException be) {
            throw new DBException("Exception while Creating the Transaction", be);
        } finally {
        	if (transaction.getDealTransactionId() != null) {
                setLogPerfMessage(request, "Transaction Id: " + transaction.getDealTransactionId() + " Deal id: "
                + transaction.getDealId() + " created");
        	}
        }

        return transaction;
    }
    
    /**
     * @param request
     * @param transaction
     * @return
     * @throws ValidationFailedException
     */
    @RequestMapping(value = "/documents", method = RequestMethod.POST, consumes = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Document saveDocument(HttpServletRequest request, @RequestBody Document document)
            throws ValidationFailedException {
        User user = (User) request.getSession().getAttribute("User");

        // Add validation to confirm mandatory fields, dealId MUST BE PRESENT
        BindingResult errors = new BeanPropertyBindingResult(document, "document");
        DocumentValidator validator = new DocumentValidator(messageValidator);
        validator.validate(document, errors);

        if (errors.hasErrors()) {
            throw new ValidationFailedException(errors, 0);
        } else {
            fundingService.createDocument(document, user);
        }

        setLogPerfMessage(request,
                "Transaction Id: " + document.getDocumentId() + " Deal id: " + document.getDealId() + " created");

        return document;
    }

    /* /*call to service method updateDeal */

    /*@RequestMapping(value = "/edits", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.OK)*/
    @RequestMapping(value = "/edits", method = RequestMethod.POST, produces = "application/json", consumes = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Deal editDeal(HttpServletRequest request,@RequestBody Deal deal) throws ValidationFailedException {
        User user = getValidatedUserFromSession(request);
        
        Deal dealResult = null;
      
        BindingResult errors = new BeanPropertyBindingResult(deal, "deal");
        RequestGroupValidator validator = new RequestGroupValidator(messageValidator);
        validator.validate(deal, errors);
        
        validator.validate(deal, errors);
        if (errors.hasErrors()) {
            throw new ValidationFailedException(errors, 0);
        } else {
            dealResult = fundingService.updateDeal(deal, user);
        }
        setLogPerfMessage(request, "Deal id " + dealResult.getDealId() + " updated");
        return dealResult;
    }
    /* call to service method updateTransaction */
/*
    @RequestMapping(value = "/editTransactions", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.OK)*/
    @RequestMapping(value = "/editTransactions", method = RequestMethod.POST, produces = "application/json", consumes = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Transaction editTransaction(HttpServletRequest request,  @RequestBody Transaction transaction )
            throws ValidationFailedException {
        User user = getValidatedUserFromSession(request);
        Transaction updatedTransaction = null;
       /* Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create();
        Transaction transaction = gson.fromJson(transactionString, Transaction.class);*/
        /*if(!"action".equalsIgnoreCase(transaction.getAction()))
        {
        addEntityCurrency(transaction);
        }*/
        BindingResult errors = new BeanPropertyBindingResult(transaction, "transaction");
        TransactionValidator validator = new TransactionValidator(messageValidator);
        validator.validate(transaction, errors);
        if (errors.hasErrors()) {
            throw new ValidationFailedException(errors, 0);
        } else {
            updatedTransaction = fundingService.updateTransaction(transaction, user );
        }
        setLogPerfMessage(request, "Transaction Id: " + updatedTransaction.getDealTransactionId() + " Deal id: "
                + updatedTransaction.getDealId() + " edit");
        return updatedTransaction;
    }

    /*
     * editDocument call to service method updateDocument
     */

    @RequestMapping(value = "/editDocuments", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Document editDocument(HttpServletRequest request, @RequestBody Document document)
            throws ValidationFailedException {
    	//sonar changes starts
    	Document documentTemp=document;
        User user = getValidatedUserFromSession(request);
        BindingResult errors = new BeanPropertyBindingResult(documentTemp, "document");
        DocumentValidator validator = new DocumentValidator(messageValidator);
        validator.validate(documentTemp, errors);

        if (errors.hasErrors()) {
            throw new ValidationFailedException(errors, 0);
        } else {
        	documentTemp = fundingService.updateDocument(documentTemp, user);
        }
        setLogPerfMessage(request, "document id " + documentTemp.getDocumentId() + " updated");
        return documentTemp;
        //sonar changes ends
    }

    /**
     * @param request
     * @param id
     * @return call to service method findTransactionById
     */
    @RequestMapping(value = "/transaction/{id}", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public TransactionDetailsDTO getTransactionById(HttpServletRequest request, @PathVariable(value = "id") String id) {

        final String clean = StringHelper.getValidatedStringAlphaOnly(id);
        User user = (User) request.getSession().getAttribute("User");
        Integer transactionId = Integer.parseInt(clean);
        Map<String, String> searchMap = new HashMap<String, String>();
        searchMap.put(ControllersConstants.TRANSACTION_ID, clean);

        Transaction transactionResult = null;
        TransactionDetailsDTO transactionDetailsDTO = null;
        try {
             transactionDetailsDTO = new TransactionDetailsDTO();
               transactionDetailsDTO.setTransaction(fundingService.findTransactionById(transactionId));
               if(null!=transactionDetailsDTO.getTransaction() &&  null!=transactionDetailsDTO.getTransaction().getDealId())
               {
            	   transactionDetailsDTO.setDeal(fundingService.findDealById(transactionDetailsDTO.getTransaction().getDealId()));
               } 
             transactionDetailsDTO.setUser(user);
        } catch (BusinessException dbe) {
            throw new BusinessException(dbe.getMessage(), dbe);
        } finally {
            if (transactionResult != null) {
                setLogPerfMessage(request, "Transaction id " + transactionResult.getDealTransactionId() + " found");
            } else {
                setLogPerfMessage(request, "DealTransactionId id " + clean + " not found");
            }
        }

        return transactionDetailsDTO;
    }


    /**
     * @param request
     * @param id
     * @return call to service method findDocumentById
     */
    @RequestMapping(value = "/document/{id}", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Document getDocumentById(HttpServletRequest request, @PathVariable(value = "id") String id) {

        final String clean = StringHelper.getValidatedStringAlphaOnly(id);
        Integer documentId = Integer.parseInt(clean);
        Map<String, String> searchMap = new HashMap<String, String>();
        searchMap.put(ControllersConstants.DOCUMENT_ID, clean);

        Document documentResult = null;
        try {
            //documentResult = fundingService.findDocumentById(documentId);
        } catch (BusinessException dbe) {
            throw new BusinessException(dbe.getMessage(), dbe);
        } finally {
            if (documentResult != null) {
                setLogPerfMessage(request, "Document id " + documentResult.getDocumentId()

                        + " found");
            } else {
                setLogPerfMessage(request, "DocumentId id " + clean + " not found");
            }
        }

        return documentResult;
    }

    /* call to service method: List<Deal> findDeal */

    @RequestMapping(value = "/deal/requester", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public PaginatedResultList<Deal> getDealRequesterDashboard(

            HttpServletRequest request) {

        User user = (User) request.getSession().getAttribute("User");

        PaginatedResultList<Deal> result = new PaginatedResultList<Deal>();

        List<Deal> dealResponse = null;

        Map<String, String> searchMap = SearchMapValidator.setRequestSearchDefaults(user, request);
        try {
            dealResponse = fundingService.findDeal(searchMap);
        } catch (BusinessException dbe) {
            throw new BusinessException(dbe.getMessage(), dbe);
        }
        result.setResultList(dealResponse);
        result.setTotalRecords(dealResponse == null || dealResponse.isEmpty() ? 0 : dealResponse.get(0).getDealId());
        setLogPerfMessage(request,
                ((dealResponse == null || dealResponse.isEmpty()) ? 0 : dealResponse.size()) + " Records found");

        return result;

    }

    /* call to service method: List<Transaction> findTransaction */

    @RequestMapping(value = "/transaction/requester", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public PaginatedResultList<Transaction> getTransactionRequesterDashboard(

            HttpServletRequest request) {

        User user = (User) request.getSession().getAttribute("User");

        PaginatedResultList<Transaction> result = new PaginatedResultList<Transaction>();

        List<Transaction> transactionResponse = null;

        Map<String, String> searchMap = SearchMapValidator.setRequestSearchDefaults(user, request);
        try {
            transactionResponse = fundingService.findTransaction(searchMap);
        } catch (BusinessException dbe) {
            throw new BusinessException(dbe.getMessage(), dbe);
        }
        result.setResultList(transactionResponse);
        result.setTotalRecords(transactionResponse == null || transactionResponse.isEmpty() ? 0
                : transactionResponse.get(0).getDealId());
        setLogPerfMessage(request,
                ((transactionResponse == null || transactionResponse.isEmpty()) ? 0 : transactionResponse.size())
                        + " Records found");

        return result;

    }
    
    
    
    /**retruns list of transaction assosiated for a given dealID  */

    @RequestMapping(value = "/getTransactionsByDealID/{dealID}", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public PaginatedResultList<Transaction> getTransactionsByDealID(HttpServletRequest request, @PathVariable(value = "dealID") String dealID) {

      
        Map<String, String> searchMap = new HashMap<String, String>();
        searchMap.put(ControllersConstants.DEAL_ID, dealID);
        
        PaginatedResultList<Transaction> result = new PaginatedResultList<Transaction>();

        List<Transaction> transactionResponse = null;

        try {
            transactionResponse = fundingService.findTransaction(searchMap);
        } catch (BusinessException dbe) {
            throw new BusinessException(dbe.getMessage(), dbe);
        }
        result.setResultList(transactionResponse);
        result.setTotalRecords((transactionResponse == null || transactionResponse.isEmpty()) ? 0 : transactionResponse.size());
        
        setLogPerfMessage(request,
                ((transactionResponse == null || transactionResponse.isEmpty()) ? 0 : transactionResponse.size())
                        + " Records found");

        return result;

    }

    /**
     * @param request
     * @param dealID
     * @return Deal
     */
    @RequestMapping(value = "/getDealDetailsById/{dealID}", method = RequestMethod.GET, produces = "application/json")
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody Deal getDealDetailsById(HttpServletRequest request, @PathVariable(value = "dealID") String dealID) {
         return fundingService.getDealDetailsById(Integer.parseInt(dealID), getUserFromSession(request));
    }
    
    /**
     * @param request
     * @param id
     * @return
     */
    @RequestMapping(value = "/getTransactionID", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public int getTransactionID(HttpServletRequest request) {
        int transactionID = fundingService.getTransactionID();
        return transactionID;
    }
    
    /**
     * @param request
     * @param dealID
     * @return Deal
     */
    @RequestMapping(value = "/getTransactionTypeDetails/{type}", method = RequestMethod.GET, produces = "application/json")
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody TransactionTypeDetails getTransactionTypeDetails(@PathVariable(value = "type") String type) {
         return fundingService.getTransactionTypeDetails(type);
    }
    
    /**
     * @param request
     * @param dealID
     * @return Deal
     */
    @RequestMapping(value = "/getDealDetailsByDealId/{dealID}", method = RequestMethod.GET, produces = "application/json")
    @ResponseStatus(HttpStatus.OK)
    public @ResponseBody DealDetailDTO getDealDetailsByDealId(HttpServletRequest request, @PathVariable(value = "dealID") String dealId) {
    	final String clean = StringHelper.getValidatedStringAlphaOnly(dealId);
        User user = (User) request.getSession().getAttribute("User");
        Integer dealID = Integer.parseInt(clean);
      
        DealDetailDTO dealDetailDTO = null;
        try {
        	dealDetailDTO = new DealDetailDTO();
        	dealDetailDTO.setDeal(fundingService.getDealDetailsById(dealID, user));
        	dealDetailDTO.setUser(user);
        } catch (BusinessException dbe) {
            throw new BusinessException(dbe.getMessage(), dbe);
        } finally {
            if (dealDetailDTO != null) {
                setLogPerfMessage(request, "Transaction id " + dealDetailDTO.getDeal().getDealId() + " found");
            } else {
                setLogPerfMessage(request, "Deal Id " + clean + " not found");
            }
        }

        return dealDetailDTO;
    }

}

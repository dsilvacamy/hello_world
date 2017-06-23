package com.ge.treasury.myfunding.dto;

import java.util.Date;

public class DealActivityDTO {
	private Integer activityID;
	private Integer dealId;
	private String sso;
	private String userName;
	private String userRole;
	private String action;
	private String actComment;
	private Date createTimeStamp;
	private String createUser;
	private Date lastUpdateTimestamp;
	private String lastUpdateUser;
	
	
	public Integer getActivityID() {
		return activityID;
	}
	public void setActivityID(Integer activityID) {
		this.activityID = activityID;
	}
	public Integer getDealId() {
		return dealId;
	}
	public void setDealId(Integer dealId) {
		this.dealId = dealId;
	}
	public String getSso() {
		return sso;
	}
	public void setSso(String sso) {
		this.sso = sso;
	}
	public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
	}
	public String getUserRole() {
		return userRole;
	}
	public void setUserRole(String userRole) {
		this.userRole = userRole;
	}
	public String getAction() {
		return action;
	}
	public void setAction(String action) {
		this.action = action;
	}
	public String getActComment() {
		return actComment;
	}
	public void setActComment(String actComment) {
		this.actComment = actComment;
	}
	public Date getCreateTimeStamp() {
		return createTimeStamp;
	}
	public void setCreateTimeStamp(Date createTimeStamp) {
		this.createTimeStamp = createTimeStamp;
	}
	public String getCreateUser() {
		return createUser;
	}
	public void setCreateUser(String createUser) {
		this.createUser = createUser;
	}
	public Date getLastUpdateTimestamp() {
		return lastUpdateTimestamp;
	}
	public void setLastUpdateTimestamp(Date lastUpdateTimestamp) {
		this.lastUpdateTimestamp = lastUpdateTimestamp;
	}
	public String getLastUpdateUser() {
		return lastUpdateUser;
	}
	public void setLastUpdateUser(String lastUpdateUser) {
		this.lastUpdateUser = lastUpdateUser;
	}
	
	
	@Override
	public String toString() {
		return "DealActivityDTO [activityID=" + activityID + ", dealId="
				+ dealId + ", sso=" + sso + ", userName=" + userName
				+ ", userRole=" + userRole + ", action=" + action
				+ ", actComment=" + actComment + ", createTimeStamp="
				+ createTimeStamp + ", createUser=" + createUser
				+ ", lastUpdateTimestamp=" + lastUpdateTimestamp
				+ ", lastUpdateUser=" + lastUpdateUser + "]";
	}
	
}

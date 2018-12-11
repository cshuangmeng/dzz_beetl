package com.yixiang.api.user.pojo;

import java.util.Date;

public class UserCharging {

	private Integer id;
	private Integer userId;
	private Integer chargingId;
	private String remark;
	private Integer state;
	private Date followTime;
	private Date cancelTime;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getUserId() {
		return userId;
	}

	public void setUserId(Integer userId) {
		this.userId = userId;
	}

	public Integer getChargingId() {
		return chargingId;
	}

	public void setChargingId(Integer chargingId) {
		this.chargingId = chargingId;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark == null ? null : remark.trim();
	}

	public Integer getState() {
		return state;
	}

	public void setState(Integer state) {
		this.state = state;
	}

	public Date getFollowTime() {
		return followTime;
	}

	public void setFollowTime(Date followTime) {
		this.followTime = followTime;
	}

	public Date getCancelTime() {
		return cancelTime;
	}

	public void setCancelTime(Date cancelTime) {
		this.cancelTime = cancelTime;
	}
}
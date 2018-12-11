package com.yixiang.api.util.pojo;

import java.math.BigDecimal;
import java.util.Date;

public class ShareLog {

	private Integer id;
	private Integer userId;
	private Integer deviceId;
	private Integer category;
	private String ip;
	private BigDecimal lat;
	private BigDecimal lng;
	private Integer relateId;
	private Date createTime;

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

	public Integer getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(Integer deviceId) {
		this.deviceId = deviceId;
	}

	public Integer getCategory() {
		return category;
	}

	public void setCategory(Integer category) {
		this.category = category;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip == null ? null : ip.trim();
	}

	public BigDecimal getLat() {
		return lat;
	}

	public void setLat(BigDecimal lat) {
		this.lat = lat;
	}

	public BigDecimal getLng() {
		return lng;
	}

	public void setLng(BigDecimal lng) {
		this.lng = lng;
	}

	public Integer getRelateId() {
		return relateId;
	}

	public void setRelateId(Integer relateId) {
		this.relateId = relateId;
	}

	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}
	
	// 分享类别
	public static enum CATEGORY_TYPE_ENUM {
		STATION(1), ARTICLE(2), CAR(3);
		private Integer category;

		private CATEGORY_TYPE_ENUM(Integer category) {
			this.category = category;
		}

		public Integer getCategory() {
			return category;
		}
	}

}

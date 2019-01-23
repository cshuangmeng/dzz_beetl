package com.yixiang.api.charging.pojo;

import java.math.BigDecimal;
import java.util.Map;

import com.yixiang.api.util.DataUtil;

public class EquipmentInfo {

	private Integer id;
	private String stationId;
	private String equipmentId;
	private String manufactureId;
	private String manufactureName;
	private String equipmentName;
	private String equipmentModel;
	private String productionDate;
	private Integer equipmentType;
	private BigDecimal lat;
	private BigDecimal lng;
	private Float power;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getStationId() {
		return stationId;
	}

	public void setStationId(String stationId) {
		this.stationId = stationId == null ? null : stationId.trim();
	}

	public String getEquipmentId() {
		return equipmentId;
	}

	public void setEquipmentId(String equipmentId) {
		this.equipmentId = equipmentId == null ? null : equipmentId.trim();
	}

	public String getManufactureId() {
		return manufactureId;
	}

	public void setManufactureId(String manufactureId) {
		this.manufactureId = manufactureId == null ? null : manufactureId.trim();
	}

	public String getManufactureName() {
		return manufactureName;
	}

	public void setManufactureName(String manufactureName) {
		this.manufactureName = manufactureName == null ? null : manufactureName.trim();
	}

	public String getEquipmentName() {
		return equipmentName;
	}

	public void setEquipmentName(String equipmentName) {
		this.equipmentName = equipmentName == null ? null : equipmentName.trim();
	}

	public String getEquipmentModel() {
		return equipmentModel;
	}

	public void setEquipmentModel(String equipmentModel) {
		this.equipmentModel = equipmentModel == null ? null : equipmentModel.trim();
	}

	public String getProductionDate() {
		return productionDate;
	}

	public void setProductionDate(String productionDate) {
		this.productionDate = productionDate == null ? null : productionDate.trim();
	}

	public Integer getEquipmentType() {
		return equipmentType;
	}

	public void setEquipmentType(Integer equipmentType) {
		this.equipmentType = equipmentType;
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

	public Float getPower() {
		return power;
	}

	public void setPower(Float power) {
		this.power = power;
	}
	
	public Map<String,Object> toStandardFormat(){
		return DataUtil.mapOf("EquipmentID",equipmentId,"ManufacturerID",manufactureId,"ManufacturerName",manufactureName
				,"EquipmentModel",equipmentModel,"ProductionDate",productionDate,"EquipmentType",equipmentType
				,"EquipmentLng",lng,"EquipmentLat",lat,"Power",power,"EquipmentName",equipmentName);
	}
	
}
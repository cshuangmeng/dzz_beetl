package com.yixiang.api.charging.pojo;

public class ConnectorInfo {

	private Integer id;
	private String stationId;
	private String equipmentId;
	private String connectorId;
	private String connectorName;
	private String connectorType;
	private Integer voltageUpper;
	private Integer voltageLower;
	private Float power;
	private Integer current;
	private String parkNo;
	private Integer nationalStandard;
	private Integer state;
	private Integer parkState;
	private Integer lockState;

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

	public String getConnectorId() {
		return connectorId;
	}

	public void setConnectorId(String connectorId) {
		this.connectorId = connectorId == null ? null : connectorId.trim();
	}

	public String getConnectorName() {
		return connectorName;
	}

	public void setConnectorName(String connectorName) {
		this.connectorName = connectorName == null ? null : connectorName.trim();
	}

	public String getConnectorType() {
		return connectorType;
	}

	public void setConnectorType(String connectorType) {
		this.connectorType = connectorType == null ? null : connectorType.trim();
	}

	public Integer getVoltageUpper() {
		return voltageUpper;
	}

	public void setVoltageUpper(Integer voltageUpper) {
		this.voltageUpper = voltageUpper;
	}

	public Integer getVoltageLower() {
		return voltageLower;
	}

	public void setVoltageLower(Integer voltageLower) {
		this.voltageLower = voltageLower;
	}

	public Float getPower() {
		return power;
	}

	public void setPower(Float power) {
		this.power = power;
	}

	public Integer getCurrent() {
		return current;
	}

	public void setCurrent(Integer current) {
		this.current = current;
	}

	public String getParkNo() {
		return parkNo;
	}

	public void setParkNo(String parkNo) {
		this.parkNo = parkNo == null ? null : parkNo.trim();
	}

	public Integer getNationalStandard() {
		return nationalStandard;
	}

	public void setNationalStandard(Integer nationalStandard) {
		this.nationalStandard = nationalStandard;
	}

	public Integer getState() {
		return state;
	}

	public void setState(Integer state) {
		this.state = state;
	}

	public Integer getParkState() {
		return parkState;
	}

	public void setParkState(Integer parkState) {
		this.parkState = parkState;
	}

	public Integer getLockState() {
		return lockState;
	}

	public void setLockState(Integer lockState) {
		this.lockState = lockState;
	}
}
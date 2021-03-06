package com.yixiang.api.charging.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.yixiang.api.charging.mapper.ConnectorInfoMapper;
import com.yixiang.api.charging.pojo.ConnectorInfo;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class ConnectorInfoComponent {

	@Autowired
	private ConnectorInfoMapper connectorInfoMapper;
	
	//获取充电设备接口信息
	public List<ConnectorInfo> getConnectorInfoByStationId(String stationId){
		if(StringUtils.isNotBlank(stationId)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("station_id", stationId);
			return selectByExample(example);
		}
		return null;
	}
	
	//获取充电设备接口信息
	public List<ConnectorInfo> getConnectorInfoByEquipmentId(String equipmentId){
		if(StringUtils.isNotBlank(equipmentId)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("equipment_id", equipmentId);
			return selectByExample(example);
		}
		return null;
	}
	
	//获取充电设备接口信息
	public ConnectorInfo getConnectorInfoByConnectorId(String connectorId){
		if(StringUtils.isNotEmpty(connectorId)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("connector_id", connectorId);
			List<ConnectorInfo> result=selectByExample(example);
			return result.size()>0?result.get(0):null;
		}
		return null;
	}

	//计算结果集大小
	public long countByExample(QueryExample example) {
		return connectorInfoMapper.countByExample(example);
	}

	//保存
	public int insertSelective(ConnectorInfo record) {
		return connectorInfoMapper.insertSelective(record);
	}

	//获取结果集
	public List<ConnectorInfo> selectByExample(QueryExample example) {
		return connectorInfoMapper.selectByExample(example);
	}

	//更新
	public int updateByExampleSelective(ConnectorInfo record, QueryExample example) {
		return connectorInfoMapper.updateByExampleSelective(record, example);
	}
	
}

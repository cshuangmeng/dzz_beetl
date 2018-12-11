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
	public ConnectorInfo getConnectorInfoByConnectorId(String connectorId){
		if(StringUtils.isNotEmpty(connectorId)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("connector_id", connectorId);
			List<ConnectorInfo> result=connectorInfoMapper.selectByExample(example);
			return result.size()>0?result.get(0):null;
		}
		return null;
	}
	
}

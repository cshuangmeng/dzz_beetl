package com.yixiang.api.charging.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.yixiang.api.charging.mapper.EquipmentInfoMapper;
import com.yixiang.api.charging.pojo.EquipmentInfo;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class EquipmentInfoComponent {

	@Autowired
	private EquipmentInfoMapper equipmentInfoMapper;
	
	//获取充电设备
	public List<EquipmentInfo> getEquipmentInfoByStationId(String stationId){
		if(StringUtils.isNotBlank(stationId)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("station_id", stationId);
			return selectByExample(example);
		}
		return null;
	}
	
	//获取结果集大小
	public long countByExample(QueryExample example) {
		return equipmentInfoMapper.countByExample(example);
	}

	//保存
	public int insertSelective(EquipmentInfo record) {
		return equipmentInfoMapper.insertSelective(record);
	}

	//获取结果集
	public List<EquipmentInfo> selectByExample(QueryExample example) {
		return equipmentInfoMapper.selectByExample(example);
	}

	//更新
	public int updateByExampleSelective(EquipmentInfo record, QueryExample example) {
		return equipmentInfoMapper.updateByExampleSelective(record, example);
	}

}

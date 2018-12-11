package com.yixiang.api.charging.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.yixiang.api.charging.pojo.EquipmentInfo;
import com.yixiang.api.util.pojo.QueryExample;

public interface EquipmentInfoMapper {

	long countByExample(QueryExample example);

	int insertSelective(EquipmentInfo record);

	List<EquipmentInfo> selectByExample(QueryExample example);

	int updateByExampleSelective(@Param("record") EquipmentInfo record, @Param("example") QueryExample example);

}
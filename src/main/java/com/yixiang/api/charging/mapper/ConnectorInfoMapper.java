package com.yixiang.api.charging.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.yixiang.api.charging.pojo.ConnectorInfo;
import com.yixiang.api.util.pojo.QueryExample;

public interface ConnectorInfoMapper {

	long countByExample(QueryExample example);

	int insertSelective(ConnectorInfo record);

	List<ConnectorInfo> selectByExample(QueryExample example);

	int updateByExampleSelective(@Param("record") ConnectorInfo record, @Param("example") QueryExample example);

}
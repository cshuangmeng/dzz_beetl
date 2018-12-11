package com.yixiang.api.util.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.yixiang.api.util.pojo.NaviLog;
import com.yixiang.api.util.pojo.QueryExample;

public interface NaviLogMapper {

	long countByExample(QueryExample example);

	int insertSelective(NaviLog record);

	List<NaviLog> selectByExample(QueryExample example);

	int updateByExampleSelective(@Param("record") NaviLog record, @Param("example") QueryExample example);

}

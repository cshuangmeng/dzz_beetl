package com.yixiang.api.util.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.yixiang.api.util.pojo.OpenAppLog;
import com.yixiang.api.util.pojo.QueryExample;

public interface OpenAppLogMapper {

	long countByExample(QueryExample example);

	int insertSelective(OpenAppLog record);

	List<OpenAppLog> selectByExample(QueryExample example);

	int updateByExampleSelective(@Param("record") OpenAppLog record, @Param("example") QueryExample example);

}

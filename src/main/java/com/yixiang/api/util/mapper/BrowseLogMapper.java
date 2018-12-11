package com.yixiang.api.util.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.yixiang.api.util.pojo.BrowseLog;
import com.yixiang.api.util.pojo.QueryExample;

public interface BrowseLogMapper {

	long countByExample(QueryExample example);

	int insertSelective(BrowseLog record);

	List<BrowseLog> selectByExample(QueryExample example);

	int updateByExampleSelective(@Param("record") BrowseLog record, @Param("example") QueryExample example);

}

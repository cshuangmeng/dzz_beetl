package com.yixiang.api.util.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.yixiang.api.util.pojo.QueryExample;
import com.yixiang.api.util.pojo.ShareLog;

public interface ShareLogMapper {

	long countByExample(QueryExample example);

	int insertSelective(ShareLog record);

	List<ShareLog> selectByExample(QueryExample example);

	int updateByExampleSelective(@Param("record") ShareLog record, @Param("example") QueryExample example);

}

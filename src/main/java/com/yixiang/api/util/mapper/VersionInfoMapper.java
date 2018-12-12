package com.yixiang.api.util.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.yixiang.api.util.pojo.QueryExample;
import com.yixiang.api.util.pojo.VersionInfo;

public interface VersionInfoMapper {

	long countByExample(QueryExample example);

	int insertSelective(VersionInfo record);

	List<VersionInfo> selectByExample(QueryExample example);

	int updateByExampleSelective(@Param("record") VersionInfo record, @Param("example") QueryExample example);

}

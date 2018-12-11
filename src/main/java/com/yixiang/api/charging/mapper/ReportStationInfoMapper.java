package com.yixiang.api.charging.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.yixiang.api.charging.pojo.ReportStationInfo;
import com.yixiang.api.util.pojo.QueryExample;

public interface ReportStationInfoMapper {

	long countByExample(QueryExample example);

	int insertSelective(ReportStationInfo record);

	List<ReportStationInfo> selectByExample(QueryExample example);

	int updateByExampleSelective(@Param("record") ReportStationInfo record, @Param("example") QueryExample example);

}
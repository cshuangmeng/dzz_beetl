package com.yixiang.api.util.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.yixiang.api.util.pojo.LabelInfo;
import com.yixiang.api.util.pojo.QueryExample;

public interface LabelInfoMapper {

	long countByExample(QueryExample example);

	int insertSelective(LabelInfo record);

	List<LabelInfo> selectByExample(QueryExample example);

	int updateByExampleSelective(@Param("record") LabelInfo record, @Param("example") QueryExample example);

}

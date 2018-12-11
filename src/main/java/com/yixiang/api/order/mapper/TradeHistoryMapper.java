package com.yixiang.api.order.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.yixiang.api.order.pojo.TradeHistory;
import com.yixiang.api.util.pojo.QueryExample;

public interface TradeHistoryMapper {

	long countByExample(QueryExample example);

	int insertSelective(TradeHistory record);

	List<TradeHistory> selectByExample(QueryExample example);

	int updateByExampleSelective(@Param("record") TradeHistory record, @Param("example") QueryExample example);

}
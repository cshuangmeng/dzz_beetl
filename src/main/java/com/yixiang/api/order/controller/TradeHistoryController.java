package com.yixiang.api.order.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.order.service.TradeHistoryComponent;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/trade/history")
public class TradeHistoryController {

	@Autowired
	private TradeHistoryComponent tradeHistoryComponent;
	
	//获取用户流水记录
	@RequestMapping("/list")
	public Result queryTradeHistoryList(@RequestParam(defaultValue="0") Integer page
			,@RequestParam(required=false) Integer type){
		Map<String,Object> result=tradeHistoryComponent.queryTradeHistoryList(page, type);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
}

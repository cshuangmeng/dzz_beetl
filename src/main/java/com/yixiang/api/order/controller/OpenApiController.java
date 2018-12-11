package com.yixiang.api.order.controller;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.order.service.CouponInfoComponent;
import com.yixiang.api.order.service.OrderInfoComponent;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/open")
public class OpenApiController {

	@Autowired
	private OrderInfoComponent orderInfoComponent;
	@Autowired
	private CouponInfoComponent couponInfoComponent;
	
	Logger log=LoggerFactory.getLogger(getClass());
	
	//聚能充回调充电账单
	@RequestMapping("/nitfication_order")
	public void syncChargingBill(HttpServletRequest request,HttpServletResponse response){
		boolean result=orderInfoComponent.syncChargingBill();
		try {
			response.getWriter().print(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//聚能充刷新AccessToken
	@RequestMapping("/query_token")
	public Map<String,Object> queryToken(){
		Map<String,Object> result=orderInfoComponent.queryToken();
		log.info(JSONObject.toJSONString(result));
		return result;
	}
	
	//积分墙获取兑换比率
	@RequestMapping("/jfq/rate")
	public Result getExchangeRate(){
		JSONObject config=JSONObject.parseObject(Redis.use().get("jfq_exchange_config"));
		Result.putValue(DataUtil.mapOf("rate",config.get("rate")));
		return Result.getThreadObject();
	}
	
	//积分墙生成充值兑换码
	@RequestMapping("/jfq/exchange")
	public Result buildRechargeRedeemCode(){
		Map<String,Object> result=couponInfoComponent.buildRechargeRedeemCode();
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
}

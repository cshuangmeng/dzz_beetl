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
import com.yixiang.api.charging.service.ChargeOperatorComponent;
import com.yixiang.api.charging.service.ChargingStationComponent;
import com.yixiang.api.order.service.CouponInfoComponent;
import com.yixiang.api.order.service.OrderInfoComponent;
import com.yixiang.api.util.ChargeClientBuilder;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/open")
public class OpenApiController {

	@Autowired
	private OrderInfoComponent orderInfoComponent;
	@Autowired
	private CouponInfoComponent couponInfoComponent;
	@Autowired
	private ChargingStationComponent chargingStationComponent;
	@Autowired
	private ChargeClientBuilder chargeClientBuilder;
	@Autowired
	private ChargeOperatorComponent chargeOperatorComponent;
	
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
	public Map<String,Object> refreshToken(){
		Map<String,Object> result=chargeClientBuilder.refreshToken();
		log.info(JSONObject.toJSONString(result));
		return result;
	}
	
	//聚能充推送充电站状态
	@RequestMapping("/notification_stationStatus")
	public void pushStationStatus(){
		chargingStationComponent.pushStationStatus();
	}
	
	//合作方刷新AccessToken
	@RequestMapping("/co/query_token")
	public Map<String,Object> refreshToken(HttpServletRequest request){
		Map<String,Object> result=chargeOperatorComponent.refreshToken(request);
		result=chargeOperatorComponent.setResponseData(result);
		log.info(JSONObject.toJSONString(result));
		return result;
	}
	
	//合作方分页读取充电站信息
	@RequestMapping("/co/query_stations_info")
	public Map<String,Object> queryChargingStations(HttpServletRequest request){
		Map<String,Object> result=chargeOperatorComponent.queryChargingStations(request);
		result=chargeOperatorComponent.setResponseData(result);
		log.info(JSONObject.toJSONString(result));
		return result;
	}
	
	//合作方获取充电桩信息
	@RequestMapping("/co/query_station_status")
	public Map<String,Object> queryStationConnectors(HttpServletRequest request){
		Map<String,Object> result=chargeOperatorComponent.queryStationConnectors(request);
		result=chargeOperatorComponent.setResponseData(result);
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

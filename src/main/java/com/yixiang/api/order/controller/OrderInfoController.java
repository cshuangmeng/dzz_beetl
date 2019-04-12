package com.yixiang.api.order.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.order.pojo.OrderInfo;
import com.yixiang.api.order.service.OrderInfoComponent;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/order/charge")
public class OrderInfoController {

	@Autowired
	private OrderInfoComponent orderInfoComponent;
	
	//进行中的订单
	@RequestMapping("/ing")
	public Result queryChargingOrder(@RequestParam(defaultValue="0")Integer reset){
		Map<String,Object> result=orderInfoComponent.queryChargingOrder(reset);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//订单支付
	@RequestMapping("/pay")
	public Result pay(@RequestParam Integer orderId,@RequestParam Float price,@RequestParam(required=false)Integer couponId){
		Map<String,Object> result=orderInfoComponent.pay(orderId, price, couponId, true);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//启动充电
	@RequestMapping("/start")
	public Result startCharging(@RequestParam String code){
		Map<String,Object> result=orderInfoComponent.startCharging(code);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//查询充电状态
	@RequestMapping("/state")
	public Result queryChargingState(@RequestParam Integer orderId){
		Map<String,Object> result=orderInfoComponent.queryChargingState(orderId,true);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//结束充电
	@RequestMapping("/stop")
	public Result stopCharging(@RequestParam Integer orderId){
		orderInfoComponent.stopCharging(orderId,true);
		return Result.getThreadObject();
	}
	
	//获取账单
	@RequestMapping("/bill")
	public Result queryChargingBill(@RequestParam Integer orderId){
		OrderInfo result=orderInfoComponent.queryChargingBill(orderId,true);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//获取充电订单列表
	@RequestMapping("/list")
	public Result queryOrderList(@RequestParam(defaultValue="0") Integer page, @RequestParam(required=false) String state){
		Map<String,Object> result=orderInfoComponent.queryOrderList(page, state);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//充电启动超时
	@RequestMapping("/timeout")
	public Result orderChargeTimeout(@RequestParam(required=false)Integer orderId){
		orderInfoComponent.orderChargeTimeout(orderId);
		return Result.getThreadObject();
	}
	
}

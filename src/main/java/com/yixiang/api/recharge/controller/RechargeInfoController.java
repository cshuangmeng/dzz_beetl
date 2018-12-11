package com.yixiang.api.recharge.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.recharge.service.RechargeInfoComponent;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/recharge/request")
public class RechargeInfoController {

	@Autowired
	private RechargeInfoComponent rechargeInfoComponent;
	
	//生成充值请求
	@RequestMapping("/build")
	public Result buildRechargeRequest(@RequestParam Integer templateId,@RequestParam Integer payWay){
		Map<String, Object> result=rechargeInfoComponent.buildRechargeRequest(templateId, payWay);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//充值退款
	@RequestMapping("/refund")
	public Result refundRecharge(String reason){
		Map<String,Object> result=rechargeInfoComponent.refundRecharge(reason);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//退款说明文案
	@RequestMapping("/explain")
	public Result getRefundExplain(String reason){
		Map<String,Object> result=rechargeInfoComponent.getRefundExplain();
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
}

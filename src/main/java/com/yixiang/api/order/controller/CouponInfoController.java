package com.yixiang.api.order.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.order.service.CouponInfoComponent;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/coupon")
public class CouponInfoController {

	@Autowired
	private CouponInfoComponent couponInfoComponent;
	
	//我的优惠券列表
	@RequestMapping("/list")
	public Result queryMyCoupons(@RequestParam(defaultValue="1")Integer page,@RequestParam(required=false)Integer state){
		Map<String,Object> result=couponInfoComponent.queryMyCoupons(page, state);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//使用充值兑换码
	@RequestMapping("/code/use")
	public Result useRedeemCode(@RequestParam String code){
		couponInfoComponent.useRedeemCode(code);
		return Result.getThreadObject();
	}
	
	//匹配可用优惠券
	@RequestMapping("/match")
	public Result matchCoupons(@RequestParam(defaultValue="1")Integer category){
		Map<String,Object> result=couponInfoComponent.matchCoupons(category);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
}

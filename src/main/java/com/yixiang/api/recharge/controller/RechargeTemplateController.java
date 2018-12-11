package com.yixiang.api.recharge.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.recharge.pojo.RechargeTemplate;
import com.yixiang.api.recharge.service.RechargeTemplateComponent;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/recharge/template")
public class RechargeTemplateController {

	@Autowired
	private RechargeTemplateComponent rechargeTemplateComponent;
	
	//下发充值模板
	@RequestMapping("/list")
	public Result getRechargeTemplates(){
		List<RechargeTemplate> result=rechargeTemplateComponent.getRechargeTemplates();
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
}

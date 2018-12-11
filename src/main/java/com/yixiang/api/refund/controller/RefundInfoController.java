package com.yixiang.api.refund.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.refund.service.RefundInfoComponent;

@RestController
@RequestMapping("/refund")
public class RefundInfoController {

	@Autowired
	private RefundInfoComponent refundInfoComponent;
	
	//执行退款请求
	@RequestMapping("/execute")
	public void executeRefund(){
		refundInfoComponent.executeRefund();
	}
	
	//查询退款进度
	@RequestMapping("/check")
	public void checkRefund(){
		refundInfoComponent.checkRefund();
	}
	
}

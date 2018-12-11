package com.yixiang.api.order.controller;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.yixiang.api.order.service.AliPayCallbackComponent;
import com.yixiang.api.order.service.TencentPayCallbackComponent;
import com.yixiang.api.util.XMLUtil;

@Controller
@RequestMapping("/notify")
public class ThirdPayNotifyController {

	@Autowired
	private TencentPayCallbackComponent tencentPayCallbackComponent;
	@Autowired
	private AliPayCallbackComponent aliPayCallbackComponent;
	
	//微信支付回调
	@RequestMapping("/tencent")
	public void operateWeiXinPayCallback(HttpServletRequest request,HttpServletResponse response){
		Map<String,Object> resultMap=tencentPayCallbackComponent.operatePaySuccess(request);
		String xml=XMLUtil.toXml(resultMap, "xml");
		try {
			response.getWriter().print(xml);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//支付宝支付回调
	@RequestMapping("/alipay")
	public void operateAliPayCallback(HttpServletRequest request,HttpServletResponse response){
		String str=aliPayCallbackComponent.operatePaySuccess(request);
		try {
			response.getWriter().print(str);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	 
}

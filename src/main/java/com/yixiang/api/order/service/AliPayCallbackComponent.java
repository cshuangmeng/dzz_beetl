package com.yixiang.api.order.service;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.internal.util.AlipaySignature;
import com.yixiang.api.recharge.service.RechargeInfoComponent;
import com.yixiang.api.refund.pojo.RefundSummary;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.PayClientBuilder;

@Service
public class AliPayCallbackComponent {

	@Autowired
	private RechargeInfoComponent rechargeInfoComponent;
	@Autowired
	private PayClientBuilder payClientBuilder;
	
	private static Logger log = LoggerFactory.getLogger(AliPayCallbackComponent.class);
	
	//微信支付成功回调
	public String operatePaySuccess(HttpServletRequest request){
		try {
			//验证交易是否成功
			String tradeStatus = request.getParameter("trade_status");
			if(StringUtils.isEmpty(request.getParameter("refund_status"))&&StringUtils.isNotEmpty(tradeStatus)&&tradeStatus.equals("TRADE_SUCCESS")){
				//拼装请求参数
				Enumeration<?> pNames = request.getParameterNames();
	            Map<String, String> params = new HashMap<String, String>();
	            while (pNames.hasMoreElements()) {
	                String pName = (String) pNames.nextElement();
	                params.put(pName, request.getParameter(pName));
	            }
	            //校验签名是否正确
	            boolean signVerified = AlipaySignature.rsaCheckV1(params, payClientBuilder.ALIPAY_PUBLIC_KEY,Constants.UTF8,Constants.RSA2);
	            log.info("接收到支付宝支付回调请求,校验结果:"+signVerified+",params="+JSONObject.toJSONString(params));
	            if(signVerified){
	            	String tradeNo=params.get("out_trade_no").toString();
	            	String outTradeNo=params.get("trade_no").toString();
	            	Float totalFee=Float.valueOf(params.get("total_amount").toString());
	            	Integer orderType=Integer.valueOf(params.get("passback_params").toString().split(",")[0]);
					//判断订单类型执行相应的回调处理流程
					if(orderType.equals(RefundSummary.ORDER_TYPE_ENUM.RECHARGE.getType())){//充值
						rechargeInfoComponent.paySuccessCallback(tradeNo,outTradeNo, totalFee);
	            	}
	            	log.info("订单回调处理成功,trade_no="+tradeNo+",out_trade_no="+outTradeNo+",total_fee="+totalFee);
					return "success";
	            }
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "fail";
	}
	
}

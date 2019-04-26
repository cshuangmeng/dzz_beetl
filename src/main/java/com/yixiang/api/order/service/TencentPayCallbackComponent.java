package com.yixiang.api.order.service;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.yixiang.api.recharge.service.RechargeInfoComponent;
import com.yixiang.api.refund.pojo.RefundSummary;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.SignUtil;
import com.yixiang.api.util.ThreadCache;
import com.yixiang.api.util.XMLUtil;
import com.yixiang.api.util.pojo.PayInfo;

@Service
public class TencentPayCallbackComponent {

	@Autowired
	private RechargeInfoComponent rechargeInfoComponent;
	
	private static Logger log = LoggerFactory.getLogger(TencentPayCallbackComponent.class);
	
	//微信支付成功回调
	public Map<String,Object> operatePaySuccess(HttpServletRequest request){
		try {
			//读取请求体
			Object body=ThreadCache.getData(Constants.REQUEST_BODY);
			//保存订单数据
			if(!DataUtil.isEmpty(body)){
				String bodyString=body.toString();
				Map<String,Object> params=XMLUtil.readParamsFromXML(bodyString);
				//校验签名是否正确
				String sign=params.get("sign").toString();
				params.remove("sign");
				String[] datas=params.get("attach").toString().split(",");
	            PayInfo info=PayInfo.create().initSellerAccount(Constants.WEIXINPAY, Integer.valueOf(datas[1]), Integer.valueOf(datas[2]));
				String signVerified=SignUtil.signValue(params, "MD5", info.getTencentPartnerKey());
				log.info("接收到微信支付回调请求,校验结果:"+(signVerified.equalsIgnoreCase(sign))
						+",params="+JSONObject.toJSONString(params));
				if(signVerified.equalsIgnoreCase(sign)){
					if(params.get("return_code").toString().equalsIgnoreCase("SUCCESS")){
						if(params.get("result_code").toString().equalsIgnoreCase("SUCCESS")){
							String outTradeNo=params.get("transaction_id").toString();
							String tradeNo=params.get("out_trade_no").toString();
							Float totalFee = Float.valueOf(params.get("total_fee").toString())/100;
							Integer orderType=Integer.valueOf(datas[0]);
							//判断订单类型执行相应的回调处理流程
							if(orderType.equals(RefundSummary.ORDER_TYPE_ENUM.RECHARGE.getType())){//充值
								rechargeInfoComponent.paySuccessCallback(tradeNo,outTradeNo,totalFee);
							}
							log.info("订单回调处理成功,trade_no="+tradeNo+",out_trade_no="+outTradeNo+",total_fee="+totalFee);
							return DataUtil.mapOf("return_code", "SUCCESS","return_msg", "OK");
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return DataUtil.mapOf("return_code", "FAIL","return_msg", "OK");
	}
	
}

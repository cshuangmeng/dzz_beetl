package com.yixiang.api.util;

import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.assertj.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.feilong.core.DatePattern;
import com.jfinal.weixin.sdk.utils.HttpUtils;

@Component
@PropertySource(value="classpath:config.properties")
public class PayClientBuilder {

	// 支付宝支付回调
	@Value("${alipay_notify_url}")
	public String ALIPAY_NOTIFY_URL;
	// 支付宝网关
	@Value("${alipay_gateway}")
	public String ALIPAY_GATEWAY;
	// 支付宝应用ID
	@Value("${alipay_app_id}")
	public String ALIPAY_APP_ID;
	// 支付宝商户私钥
	@Value("${alipay_private_key}")
	public String ALIPAY_PRIVATE_KEY;
	// 应用公钥
	@Value("${app_public_key}")
	public String APP_PUBLIC_KEY;
	// 支付宝公钥
	@Value("${alipay_public_key}")
	public String ALIPAY_PUBLIC_KEY;

	// 微信应用ID
	@Value("${tencent_app_id}")
	public String TENCENT_APP_ID;
	// 微信支付商户ID
	@Value("${tencent_partner}")
	public String TENCENT_PARTNER;
	// 微信支付商户私钥
	@Value("${tencent_partner_key}")
	public String TENCENT_PARTNER_KEY;
	// 微信支付回调
	@Value("${tencent_notify_url}")
	public String TENCENT_NOTIFY_URL;
	// 微信退款证书
	@Value("${tencent_weixin_pay_cert}")
	public String TENCENT_PARTNER_CERT;
	// 微信APP交易类型
	@Value("${tencent_trade_type_app}")
	public String TENCENT_TRADE_TYPE_APP;
	// 微信小程序交易类型
	@Value("${tencent_trade_type_mini}")
	public String TENCENT_TRADE_TYPE_MINI;
	// 微信退款接口
	@Value("${tencent_refund}")
	public String TENCENT_REFUND;
	// 微信退款查询接口
	@Value("${tencent_refund_query}")
	public String TENCENT_REFUND_QUERY;
	// 微信订单查询接口
	@Value("${tencent_order_query}")
	public String TENCENT_ORDER_QUERY;
	// 微信预支付
	@Value("${tencent_prepay_url}")
	public String TENCENT_PREPAY_URL;

	private Logger log = LoggerFactory.getLogger(PayClientBuilder.class);

	// 获得默认API调用客户端
	private AlipayClient alipayClient = null;

	private AlipayClient getAlipayClient() {
		if (null == alipayClient) {
			alipayClient = new DefaultAlipayClient(ALIPAY_GATEWAY, ALIPAY_APP_ID, ALIPAY_PRIVATE_KEY, Constants.JSON,
					Constants.UTF8, ALIPAY_PUBLIC_KEY, Constants.RSA2);
		}
		return alipayClient;
	}

	// 统一下单
	public Map<String, Object> prepay(Integer payWay, String tradeNo, Float amount, String subject
			, String body, String attach, String ip, String openId) {
		if (StringUtils.isNotEmpty(openId)) {
			return tencentPrepayForMini(tradeNo, amount, subject, attach, ip, openId);
		} else if (Constants.ALIPAY.equals(payWay)) {
			return alipayPrepayForApp(tradeNo, amount, subject, body, attach);
		} else if (Constants.WEIXINPAY.equals(payWay)) {
			return tencentPrepayForApp(tradeNo, amount, subject, attach, ip);
		}
		return null;
	}
	
	// 生成二维码付款码
	public String qrCodePay(Integer payWay, String tradeNo, Float amount,
			String subject, String body, String ip, String attach,Integer minutes) {
		if (Constants.ALIPAY.equals(payWay)) {
			return alipayQRCodePay(tradeNo, amount, subject, body, attach, minutes);
		} else if (Constants.WEIXINPAY.equals(payWay)) {
			return tencentQRCodePay(tradeNo, amount, subject, attach, ip, minutes);
		}
		return null;
	}
	
	// 退款
	public String refund(Integer payWay, String outTradeNo, Float totalFee, Float refundAmount,
			String refundReason, String tradeNo) {
		if (Constants.ALIPAY.equals(payWay)) {
			return alipayRefund(outTradeNo, refundAmount, refundReason, tradeNo);
		} else if (Constants.WEIXINPAY.equals(payWay)) {
			return tencentRefund(outTradeNo, totalFee, refundAmount);
		}
		return ResponseCode.CodeEnum.PAY_WAY_INCORRECT.name;
	}

	// 订单查询
	public JSONObject tradeQuery(Integer payWay, String outTradeNo) {
		if (Constants.ALIPAY.equals(payWay)) {
			return alipayTradeQuery(outTradeNo);
		} else if (Constants.WEIXINPAY.equals(payWay)) {
			return tencentTradeQuery(outTradeNo);
		}
		return null;
	}

	// 查询退款
	public JSONObject refundQuery(Integer payWay, String outTradeNo, String tradeNo) {
		if (Constants.ALIPAY.equals(payWay)) {
			return alipayRefundQuery(outTradeNo, tradeNo);
		} else if (Constants.WEIXINPAY.equals(payWay)) {
			return tencentRefundQuery(outTradeNo);
		}
		return null;
	}

	// 支付宝退款
	private String alipayRefund(String outTradeNo, Float refundAmount, String refundReason, String tradeNo) {
		try {
			AlipayClient alipayClient = getAlipayClient();
			AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
			request.setBizContent("{\"trade_no\":\"" + outTradeNo + "\"" + ",\"refund_amount\":" + refundAmount
					+ ",\"refund_reason\":\"" + refundReason + "\"" + ",\"out_request_no\":\"" + tradeNo + "\"}");
			AlipayTradeRefundResponse response = alipayClient.execute(request);
			log.info("发起支付宝退款请求：tradeNo is " + outTradeNo + ",outRequestNo is " + tradeNo + ",response is "
					+ response.getBody().toString());
			return response.isSuccess()?Constants.SUCCESS:response.getSubMsg();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Constants.FAIL;
	}

	// 微信退款
	private String tencentRefund(String outTradeNo, Float totalFee, Float refundFee) {
		DateFormat df_out_refund_no_prefix = new SimpleDateFormat("yyyyMMddHHmmssSSS");
		String out_refund_no = df_out_refund_no_prefix.format(DateUtil.now());
		SortedMap<String, Object> param = new TreeMap<String, Object>();
		param.put("appid", TENCENT_APP_ID);
		param.put("mch_id", TENCENT_PARTNER);
		param.put("nonce_str", DataUtil.createLetters(32));
		param.put("transaction_id", outTradeNo);
		param.put("out_refund_no", out_refund_no);
		param.put("total_fee", String.valueOf(Math.round(totalFee.floatValue() * 100)));
		param.put("refund_fee", String.valueOf(Math.round(refundFee.floatValue() * 100)));
		param.put("op_user_id", TENCENT_PARTNER);
		param.put("sign", SignUtil.signValue(param, Constants.MD5, TENCENT_PARTNER_KEY).toUpperCase());
		String response = HttpUtils.postSSL(TENCENT_REFUND, XMLUtil.toXml(param, "xml"), TENCENT_PARTNER_CERT,
				TENCENT_PARTNER);
		log.info("发起微信退款请求：transaction_id is " + outTradeNo + ",out_refund_no is " + out_refund_no + ",response is "
				+ response);
		String refundResponse=Constants.FAIL;
		if (StringUtils.isNotEmpty(response)) {
			Map<String, Object> responseMap = XMLUtil.readParamsFromXML(response);
			if (responseMap.get("return_code").toString().equalsIgnoreCase("SUCCESS")) {
				if (responseMap.get("result_code").toString().equalsIgnoreCase("SUCCESS")) {
					refundResponse=Constants.SUCCESS;
				}else{
					refundResponse=responseMap.get("err_code_des").toString();
				}
			}else{
				refundResponse=responseMap.get("return_msg").toString();
			}
		}
		return refundResponse;
	}

	// 支付宝退款查询
	private JSONObject alipayRefundQuery(String outTradeNo, String tradeNo) {
		try {
			AlipayClient alipayClient = getAlipayClient();
			AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
			request.setBizContent("{\"trade_no\":\"" + outTradeNo + "\",\"out_request_no\":\"" + tradeNo + "\"}");
			AlipayTradeFastpayRefundQueryResponse response = alipayClient.execute(request);
			if (response.isSuccess()) {
				return JSONObject.parseObject(response.getBody())
						.getJSONObject("alipay_trade_fastpay_refund_query_response");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// 微信退款查询
	private JSONObject tencentRefundQuery(String outTradeNo) {
		HashMap<String, Object> param = new HashMap<String, Object>();
		param.put("appid", TENCENT_APP_ID);
		param.put("mch_id", TENCENT_PARTNER);
		param.put("nonce_str", DataUtil.createLetters(32));
		param.put("transaction_id", outTradeNo);
		param.put("sign", SignUtil.signValue(param, "MD5", TENCENT_PARTNER_KEY).toUpperCase());
		String response = HttpUtils.post(TENCENT_REFUND_QUERY, XMLUtil.toXml(param, "xml"));
		// 解析返回数据
		if (StringUtils.isNotEmpty(response)) {
			return JSONObject.parseObject(JSONObject.toJSONString(XMLUtil.readParamsFromXML(response)));
		}
		return null;
	}

	// 支付宝查询交易
	private JSONObject alipayTradeQuery(String outTradeNo) {
		try {
			AlipayClient alipayClient = getAlipayClient();
			AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
			request.setBizContent("{\"trade_no\":\"" + outTradeNo + "\"}");
			AlipayTradeQueryResponse response = alipayClient.execute(request);
			if (response.isSuccess()) {
				return JSONObject.parseObject(response.getBody()).getJSONObject("alipay_trade_query_response");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// 微信查询交易
	private JSONObject tencentTradeQuery(String outTradeNo) {
		HashMap<String, Object> param = new HashMap<String, Object>();
		param.put("appid", TENCENT_APP_ID);
		param.put("mch_id", TENCENT_PARTNER);
		param.put("nonce_str", DataUtil.createLetters(32));
		param.put("transaction_id", outTradeNo);
		String response = HttpUtils.post(TENCENT_ORDER_QUERY, XMLUtil.toXml(param, "xml"));
		// 解析返回数据
		if (StringUtils.isNotEmpty(response)) {
			return JSONObject.parseObject(JSONObject.toJSONString(XMLUtil.readParamsFromXML(response)));
		}
		return null;
	}

	// 支付宝统一下单
	private Map<String, Object> alipayPrepayForApp(String outTradeNo, Float amount, String subject
			, String body, String attach) {
		try {
			AlipayClient alipayClient=getAlipayClient();
			AlipayTradeAppPayRequest request=new AlipayTradeAppPayRequest();
			request.setNotifyUrl(ALIPAY_NOTIFY_URL);
			// 支付业务请求参数
			AlipayTradeAppPayModel model=new AlipayTradeAppPayModel();
			if(StringUtils.isNotEmpty(attach)){
				model.setPassbackParams(URLEncoder.encode(attach, Constants.UTF8));
			}
			model.setSubject(subject);
			model.setBody(body);
			model.setOutTradeNo(outTradeNo);
			model.setTotalAmount(String.valueOf(amount));
			model.setProductCode("QUICK_MSECURITY_PAY");
			request.setBizModel(model);
			AlipayTradeAppPayResponse response=alipayClient.sdkExecute(request);
			return DataUtil.mapOf("orderStr",response.getBody());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// 微信统一下单
	private Map<String, Object> tencentPrepayForApp(String outTradeNo, Float amount, String subject
			, String attach, String ip) {
		try {
			HashMap<String, Object> param = new HashMap<String, Object>();
			param.put("appid", TENCENT_APP_ID);
			param.put("mch_id", TENCENT_PARTNER);
			param.put("body", subject);
			param.put("total_fee", Math.round(amount.floatValue() * 100));// 单位为分
			param.put("nonce_str", DataUtil.createLetters(32));
			param.put("out_trade_no", outTradeNo);
			param.put("spbill_create_ip", ip);
			param.put("time_start", DateFormatUtils.format(DateUtil.now(), DatePattern.TIMESTAMP));
			param.put("trade_type", TENCENT_TRADE_TYPE_APP);
			param.put("notify_url", TENCENT_NOTIFY_URL);
			if(StringUtils.isNotEmpty(attach)){
				param.put("attach", attach);
			}
			param.put("sign", SignUtil.signValue(param, Constants.MD5, TENCENT_PARTNER_KEY).toUpperCase());
			Map<String, Object> resultMap = new HashMap<String, Object>();
			String response = HttpUtils.post(TENCENT_PREPAY_URL, XMLUtil.toXml(param, "xml"));
			// 解析返回数据
			if (null != response) {
				Map<String, Object> responseMap = XMLUtil.readParamsFromXML(response);
				if (responseMap.get("return_code").toString().equalsIgnoreCase("SUCCESS")) {
					if (responseMap.get("result_code").toString().equalsIgnoreCase("SUCCESS")) {
						// 设置返回值
						resultMap.put("appid", responseMap.get("appid"));
						resultMap.put("partnerid", responseMap.get("mch_id"));
						resultMap.put("prepayid", responseMap.get("prepay_id"));
						resultMap.put("package", "Sign=WXPay");
						resultMap.put("noncestr", DataUtil.createLetters(32));
						resultMap.put("timestamp", String.valueOf(new Date().getTime() / 1000));
						resultMap.put("sign", SignUtil.signValue(resultMap, "MD5", TENCENT_PARTNER_KEY).toUpperCase());
					}
				}
			}
			return resultMap;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// 微信小程序统一下单
	private Map<String, Object> tencentPrepayForMini(String outTradeNo, Float amount, String subject
			, String attach, String ip, String openId) {
		try {
			HashMap<String, Object> param = new HashMap<String, Object>();
			param.put("appid", TENCENT_APP_ID);
			param.put("mch_id", TENCENT_PARTNER);
			param.put("body", subject);
			param.put("total_fee", Math.round(amount.floatValue() * 100));// 单位为分
			param.put("nonce_str", DataUtil.createLetters(32));
			param.put("out_trade_no", outTradeNo);
			param.put("spbill_create_ip", ip);
			param.put("time_start", DateFormatUtils.format(DateUtil.now(), DatePattern.TIMESTAMP));
			param.put("trade_type", TENCENT_TRADE_TYPE_MINI);
			param.put("notify_url", TENCENT_NOTIFY_URL);
			param.put("openid", openId);
			if(StringUtils.isNotEmpty(attach)){
				param.put("attach", attach);
			}
			param.put("sign", SignUtil.signValue(param, "MD5", TENCENT_PARTNER_KEY).toUpperCase());
			Map<String, Object> resultMap = new HashMap<String, Object>();
			String response = HttpUtils.post(TENCENT_PREPAY_URL, XMLUtil.toXml(param, "xml"));
			// 解析返回数据
			if (null != response) {
				Map<String, Object> responseMap = XMLUtil.readParamsFromXML(response);
				if (responseMap.get("return_code").toString().equalsIgnoreCase("SUCCESS")) {
					if (responseMap.get("result_code").toString().equalsIgnoreCase("SUCCESS")) {
						// 设置返回值
						resultMap.put("appId", responseMap.get("appid"));
						resultMap.put("package", "prepay_id=" + responseMap.get("prepay_id"));
						resultMap.put("nonceStr", DataUtil.createLetters(32));
						resultMap.put("timeStamp", String.valueOf(new Date().getTime() / 1000));
						resultMap.put("signType", "MD5");
						resultMap.put("paySign",SignUtil.signValue(resultMap, "MD5", TENCENT_PARTNER_KEY).toUpperCase());
					}
				}
			}
			return resultMap;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	//微信二维码支付
	private String tencentQRCodePay(String outTradeNo, Float amount, String subject
			, String attach, String ip, Integer minutes){
		String codeUrl=null;
		try {
			HashMap<String, Object> param = new HashMap<String, Object>();
			param.put("appid", TENCENT_APP_ID);
			param.put("mch_id", TENCENT_PARTNER);
			param.put("body", subject);
			param.put("total_fee", Math.round(amount.floatValue() * 100));// 单位为分
			param.put("nonce_str", DataUtil.createLetters(32));
			param.put("out_trade_no", outTradeNo);
			param.put("spbill_create_ip", ip);
			param.put("time_start", DateFormatUtils.format(DateUtil.now(), DatePattern.TIMESTAMP));
			param.put("time_expire", DateFormatUtils.format(DateUtils.addMinutes(new Date(), minutes),DatePattern.TIMESTAMP));
			param.put("trade_type", "NATIVE");
			param.put("notify_url", TENCENT_NOTIFY_URL);
			if(StringUtils.isNotEmpty(attach)){
				param.put("attach", attach);
			}
			param.put("sign", SignUtil.signValue(param, "MD5", TENCENT_PARTNER_KEY).toUpperCase());
			String response = HttpUtils.post(TENCENT_PREPAY_URL, XMLUtil.toXml(param, "xml"));
			// 解析返回数据
			if (null != response) {
				Map<String, Object> responseMap = XMLUtil.readParamsFromXML(response);
				if (responseMap.get("return_code").toString().equalsIgnoreCase("SUCCESS")) {
					if (responseMap.get("result_code").toString().equalsIgnoreCase("SUCCESS")) {
						codeUrl=responseMap.get("code_url").toString();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return codeUrl;
	}
	
	//支付宝二维码支付
	private String alipayQRCodePay(String outTradeNo, Float amount, String subject
			, String body, String attach, Integer minutes){
		String codeUrl=null;
		try {
			AlipayClient alipayClient=getAlipayClient();
			AlipayTradePrecreateRequest request=new AlipayTradePrecreateRequest();
			request.setNotifyUrl(ALIPAY_NOTIFY_URL); // 支付宝服务器主动通知商户服务
			Map<String, Object> pcont = new HashMap<String, Object>();
			// 支付业务请求参数
			pcont.put("out_trade_no", outTradeNo); // 商户订单号
			pcont.put("total_amount", String.valueOf(amount));// 交易金额
			pcont.put("timeout_express", minutes+"m");
			pcont.put("subject", subject); // 订单标题
			pcont.put("body", body);// 对交易或商品的描述
			if(StringUtils.isNotEmpty(attach)){
				pcont.put("passback_params", URLEncoder.encode(attach, Constants.UTF8));
			}
			request.setBizContent(JSONObject.toJSONString(pcont));
			AlipayTradePrecreateResponse response=alipayClient.execute(request);
			if(response.isSuccess()){
				codeUrl=response.getQrCode();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return codeUrl;
	}

}

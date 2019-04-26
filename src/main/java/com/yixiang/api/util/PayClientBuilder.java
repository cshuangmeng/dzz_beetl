package com.yixiang.api.util;

import java.net.URLEncoder;
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
import com.yixiang.api.util.pojo.PayInfo;

@Component
@PropertySource(value = "classpath:config.properties")
public class PayClientBuilder {

	// 支付宝网关
	@Value("${alipay_gateway}")
	public String ALIPAY_GATEWAY;
	// 微信APP交易类型
	@Value("${tencent_trade_type_app}")
	public String TENCENT_TRADE_TYPE_APP;
	// 微信小程序交易类型
	@Value("${tencent_trade_type_mini}")
	public String TENCENT_TRADE_TYPE_MINI;
	// 微信二维码交易类型
	@Value("${tencent_trade_type_native}")
	public String TENCENT_TRADE_TYPE_NATIVE;
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

	// 统一下单
	public Map<String, Object> prepay(PayInfo info) {
		if (StringUtils.isNotEmpty(info.getOpenId())) {
			return tencentPrepayForMini(info);
		} else if (Constants.ALIPAY.equals(info.getPayWay())) {
			return alipayPrepayForApp(info);
		} else if (Constants.WEIXINPAY.equals(info.getPayWay())) {
			return tencentPrepayForApp(info);
		}
		return null;
	}

	// 生成二维码付款码
	public String qrCodePay(PayInfo info) {
		if (Constants.ALIPAY.equals(info.getPayWay())) {
			return alipayQRCodePay(info);
		} else if (Constants.WEIXINPAY.equals(info.getPayWay())) {
			return tencentQRCodePay(info);
		}
		return null;
	}

	// 退款
	public String refund(PayInfo info) {
		if (Constants.ALIPAY.equals(info.getPayWay())) {
			return alipayRefund(info);
		} else if (Constants.WEIXINPAY.equals(info.getPayWay())) {
			return tencentRefund(info);
		}
		return ResponseCode.CodeEnum.PAY_WAY_INCORRECT.name;
	}

	// 订单查询
	public JSONObject tradeQuery(PayInfo info) {
		if (Constants.ALIPAY.equals(info.getPayWay())) {
			return alipayTradeQuery(info);
		} else if (Constants.WEIXINPAY.equals(info.getPayWay())) {
			return tencentTradeQuery(info);
		}
		return null;
	}

	// 查询退款
	public JSONObject refundQuery(PayInfo info) {
		if (Constants.ALIPAY.equals(info.getPayWay())) {
			return alipayRefundQuery(info);
		} else if (Constants.WEIXINPAY.equals(info.getPayWay())) {
			return tencentRefundQuery(info);
		}
		return null;
	}

	// 支付宝退款
	private String alipayRefund(PayInfo info) {
		try {
			AlipayClient alipayClient = info.getAlipayClient(ALIPAY_GATEWAY);
			AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
			request.setBizContent("{\"trade_no\":\"" + info.getOutTradeNo() + "\"" + ",\"refund_amount\":"
					+ info.getRefundFee() + ",\"refund_reason\":\"" + info.getRefundReason() + "\""
					+ ",\"out_request_no\":\"" + info.getRefundNo() + "\"}");
			AlipayTradeRefundResponse response = alipayClient.execute(request);
			log.info("发起支付宝退款请求：tradeNo is " + info.getOutTradeNo() + ",outRequestNo is " + info.getRefundNo()
					+ ",response is " + response.getBody().toString());
			return response.isSuccess() ? Constants.SUCCESS : response.getSubMsg();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Constants.FAIL;
	}

	// 微信退款
	private String tencentRefund(PayInfo info) {
		SortedMap<String, Object> param = new TreeMap<String, Object>();
		param.put("appid", info.getTencentAppId());
		param.put("mch_id", info.getTencentPartner());
		param.put("nonce_str", DataUtil.createLetters(32));
		param.put("transaction_id", info.getOutTradeNo());
		param.put("out_refund_no", info.getRefundNo());
		param.put("total_fee", String.valueOf(Math.round(info.getAmount() * 100)));
		param.put("refund_fee", String.valueOf(Math.round(info.getRefundFee() * 100)));
		param.put("op_user_id", info.getTencentPartner());
		param.put("sign", SignUtil.signValue(param, Constants.MD5, info.getTencentPartnerKey()).toUpperCase());
		String response = HttpUtils.postSSL(TENCENT_REFUND, XMLUtil.toXml(param, "xml"), info.getTencentPartnerCert(),
				info.getTencentPartner());
		log.info("发起微信退款请求：transaction_id is " + info.getOutTradeNo() + ",out_refund_no is " + info.getRefundNo()
				+ ",response is " + response);
		String refundResponse = Constants.FAIL;
		if (StringUtils.isNotEmpty(response)) {
			Map<String, Object> responseMap = XMLUtil.readParamsFromXML(response);
			if (responseMap.get("return_code").toString().equalsIgnoreCase("SUCCESS")) {
				if (responseMap.get("result_code").toString().equalsIgnoreCase("SUCCESS")) {
					refundResponse = Constants.SUCCESS;
				} else {
					refundResponse = responseMap.get("err_code_des").toString();
				}
			} else {
				refundResponse = responseMap.get("return_msg").toString();
			}
		}
		return refundResponse;
	}

	// 支付宝退款查询
	private JSONObject alipayRefundQuery(PayInfo info) {
		try {
			AlipayClient alipayClient = info.getAlipayClient(ALIPAY_GATEWAY);
			AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
			request.setBizContent("{\"trade_no\":\"" + info.getOutTradeNo() + "\",\"out_request_no\":\""
					+ info.getRefundNo() + "\"}");
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
	private JSONObject tencentRefundQuery(PayInfo info) {
		HashMap<String, Object> param = new HashMap<String, Object>();
		param.put("appid", info.getTencentAppId());
		param.put("mch_id", info.getTencentPartner());
		param.put("nonce_str", DataUtil.createLetters(32));
		param.put("transaction_id", info.getOutTradeNo());
		param.put("sign", SignUtil.signValue(param, "MD5", info.getTencentPartnerKey()).toUpperCase());
		String response = HttpUtils.post(TENCENT_REFUND_QUERY, XMLUtil.toXml(param, "xml"));
		// 解析返回数据
		if (StringUtils.isNotEmpty(response)) {
			return JSONObject.parseObject(JSONObject.toJSONString(XMLUtil.readParamsFromXML(response)));
		}
		return null;
	}

	// 支付宝查询交易
	private JSONObject alipayTradeQuery(PayInfo info) {
		try {
			AlipayClient alipayClient = info.getAlipayClient(ALIPAY_GATEWAY);
			AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
			request.setBizContent("{\"trade_no\":\"" + info.getOutTradeNo() + "\"}");
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
	private JSONObject tencentTradeQuery(PayInfo info) {
		HashMap<String, Object> param = new HashMap<String, Object>();
		param.put("appid", info.getTencentAppId());
		param.put("mch_id", info.getTencentPartner());
		param.put("nonce_str", DataUtil.createLetters(32));
		param.put("transaction_id", info.getOutTradeNo());
		param.put("sign", SignUtil.signValue(param, "MD5", info.getTencentPartnerKey()).toUpperCase());
		String response = HttpUtils.post(TENCENT_ORDER_QUERY, XMLUtil.toXml(param, "xml"));
		// 解析返回数据
		if (StringUtils.isNotEmpty(response)) {
			return JSONObject.parseObject(JSONObject.toJSONString(XMLUtil.readParamsFromXML(response)));
		}
		return null;
	}

	// 支付宝统一下单
	private Map<String, Object> alipayPrepayForApp(PayInfo info) {
		try {
			AlipayClient alipayClient = info.getAlipayClient(ALIPAY_GATEWAY);
			AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();
			request.setNotifyUrl(info.getNotifyUrl());
			// 支付业务请求参数
			AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
			if (StringUtils.isNotEmpty(info.getAttach())) {
				model.setPassbackParams(URLEncoder.encode(info.getAttach(), Constants.UTF8));
			}
			model.setSubject(info.getSubject());
			model.setBody(info.getBody());
			model.setOutTradeNo(info.getTradeNo());
			model.setTotalAmount(String.valueOf(info.getAmount()));
			model.setProductCode("QUICK_MSECURITY_PAY");
			request.setBizModel(model);
			AlipayTradeAppPayResponse response = alipayClient.sdkExecute(request);
			return DataUtil.mapOf("orderStr", response.getBody());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// 微信统一下单
	private Map<String, Object> tencentPrepayForApp(PayInfo info) {
		try {
			HashMap<String, Object> param = new HashMap<String, Object>();
			param.put("appid", info.getTencentAppId());
			param.put("mch_id", info.getTencentPartner());
			param.put("body", info.getSubject());
			param.put("total_fee", Math.round(info.getAmount() * 100));// 单位为分
			param.put("nonce_str", DataUtil.createLetters(32));
			param.put("out_trade_no", info.getTradeNo());
			param.put("spbill_create_ip", info.getIp());
			param.put("time_start", DateFormatUtils.format(DateUtil.now(), DatePattern.TIMESTAMP));
			param.put("trade_type", TENCENT_TRADE_TYPE_APP);
			param.put("notify_url", info.getNotifyUrl());
			if (StringUtils.isNotEmpty(info.getAttach())) {
				param.put("attach", info.getAttach());
			}
			param.put("sign", SignUtil.signValue(param, Constants.MD5, info.getTencentPartnerKey()).toUpperCase());
			Map<String, Object> resultMap = new HashMap<String, Object>();
			String response = HttpUtils.post(TENCENT_PREPAY_URL, XMLUtil.toXml(param, "xml"));
			log.info(XMLUtil.toXml(param, "xml") + "," + response);
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
						resultMap.put("sign",
								SignUtil.signValue(resultMap, "MD5", info.getTencentPartnerKey()).toUpperCase());
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
	private Map<String, Object> tencentPrepayForMini(PayInfo info) {
		try {
			HashMap<String, Object> param = new HashMap<String, Object>();
			param.put("appid", info.getTencentAppId());
			param.put("mch_id", info.getTencentPartner());
			param.put("body", info.getSubject());
			param.put("total_fee", Math.round(info.getAmount() * 100));// 单位为分
			param.put("nonce_str", DataUtil.createLetters(32));
			param.put("out_trade_no", info.getTradeNo());
			param.put("spbill_create_ip", info.getIp());
			param.put("time_start", DateFormatUtils.format(DateUtil.now(), DatePattern.TIMESTAMP));
			param.put("trade_type", TENCENT_TRADE_TYPE_MINI);
			param.put("notify_url", info.getNotifyUrl());
			param.put("openid", info.getOpenId());
			if (StringUtils.isNotEmpty(info.getAttach())) {
				param.put("attach", info.getAttach());
			}
			param.put("sign", SignUtil.signValue(param, "MD5", info.getTencentPartnerKey()).toUpperCase());
			Map<String, Object> resultMap = new HashMap<String, Object>();
			String response = HttpUtils.post(TENCENT_PREPAY_URL, XMLUtil.toXml(param, "xml"));
			log.info(XMLUtil.toXml(param, "xml") + "," + response);
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
						resultMap.put("paySign",
								SignUtil.signValue(resultMap, "MD5", info.getTencentPartnerKey()).toUpperCase());
					}
				}
			}
			return resultMap;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// 微信二维码支付
	private String tencentQRCodePay(PayInfo info) {
		String codeUrl = null;
		try {
			HashMap<String, Object> param = new HashMap<String, Object>();
			param.put("appid", info.getTencentAppId());
			param.put("mch_id", info.getTencentPartner());
			param.put("body", info.getSubject());
			param.put("total_fee", Math.round(info.getAmount() * 100));// 单位为分
			param.put("nonce_str", DataUtil.createLetters(32));
			param.put("out_trade_no", info.getTradeNo());
			param.put("spbill_create_ip", info.getIp());
			param.put("time_start", DateFormatUtils.format(DateUtil.now(), DatePattern.TIMESTAMP));
			param.put("time_expire",
					DateFormatUtils.format(DateUtils.addMinutes(new Date(), info.getMinutes()), DatePattern.TIMESTAMP));
			param.put("trade_type", TENCENT_TRADE_TYPE_NATIVE);
			param.put("notify_url", info.getNotifyUrl());
			if (StringUtils.isNotEmpty(info.getAttach())) {
				param.put("attach", info.getAttach());
			}
			param.put("sign", SignUtil.signValue(param, "MD5", info.getTencentPartnerKey()).toUpperCase());
			String response = HttpUtils.post(TENCENT_PREPAY_URL, XMLUtil.toXml(param, "xml"));
			// 解析返回数据
			if (null != response) {
				Map<String, Object> responseMap = XMLUtil.readParamsFromXML(response);
				if (responseMap.get("return_code").toString().equalsIgnoreCase("SUCCESS")) {
					if (responseMap.get("result_code").toString().equalsIgnoreCase("SUCCESS")) {
						codeUrl = responseMap.get("code_url").toString();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return codeUrl;
	}

	// 支付宝二维码支付
	private String alipayQRCodePay(PayInfo info) {
		String codeUrl = null;
		try {
			AlipayClient alipayClient = info.getAlipayClient(ALIPAY_GATEWAY);
			AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
			request.setNotifyUrl(info.getNotifyUrl()); // 支付宝服务器主动通知商户服务
			Map<String, Object> pcont = new HashMap<String, Object>();
			// 支付业务请求参数
			pcont.put("out_trade_no", info.getTradeNo()); // 商户订单号
			pcont.put("total_amount", String.valueOf(info.getAmount()));// 交易金额
			pcont.put("timeout_express", info.getMinutes() + "m");
			pcont.put("subject", info.getSubject()); // 订单标题
			pcont.put("body", info.getBody());// 对交易或商品的描述
			if (StringUtils.isNotEmpty(info.getAttach())) {
				pcont.put("passback_params", URLEncoder.encode(info.getAttach(), Constants.UTF8));
			}
			request.setBizContent(JSONObject.toJSONString(pcont));
			AlipayTradePrecreateResponse response = alipayClient.execute(request);
			if (response.isSuccess()) {
				codeUrl = response.getQrCode();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return codeUrl;
	}

}

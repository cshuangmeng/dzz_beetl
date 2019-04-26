package com.yixiang.api.util.pojo;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.util.Constants;

public class PayInfo {

	// 支付宝支付回调
	private String notifyUrl;
	// 支付宝应用ID
	private String alipayAppId;
	// 支付宝商户私钥
	private String alipayPrivateKey;
	// 支付宝公钥
	private String alipayPublicKey;
	// 微信应用ID
	private String tencentAppId;
	// 微信支付商户ID
	private String tencentPartner;
	// 微信支付商户私钥
	private String tencentPartnerKey;
	// 微信退款证书
	private String tencentPartnerCert;
	// 第三方交易单号
	private String outTradeNo;
	// 我方交易单号
	private String tradeNo;
	// 退款单号
	private String refundNo;
	// 支付方式
	private Integer payWay;
	// 交易金额
	private Float amount;
	// 商品名称
	private String subject;
	// 商品描述
	private String body;
	// 附加参数
	private String attach;
	// 交易终端IP
	private String ip;
	// 微信公众用户openId
	private String openId;
	// 待支付有效时间,单位分钟
	private Integer minutes;
	// 退款金额
	private Float refundFee;
	// 退款原因
	private String refundReason;

	// 创建实例
	public static PayInfo create() {
		return new PayInfo();
	}

	// 初始化卖家信息
	public PayInfo initSellerAccount(Integer payWay, Integer account, Integer source) {
		JSONObject json = JSONObject.parseObject(Redis.use().get("seller_account_" + account));
		if (payWay.equals(Constants.WEIXINPAY)) {
			json = json.getJSONObject("tencent");
			if (source.equals(ACCOUNT_SOURCE_ENUM.APP.getSource())) {
				this.tencentAppId = json.getString("app_id");
			} else if (source.equals(ACCOUNT_SOURCE_ENUM.MINI.getSource())) {
				this.tencentAppId = json.getString("mini_id");
			}
			this.tencentPartner = json.getString("mch_id");
			this.tencentPartnerKey = json.getString("mch_key");
			this.tencentPartnerCert = json.getString("refund_cert");
			this.notifyUrl = json.getString("notify_url");
		} else if (payWay.equals(Constants.ALIPAY)) {
			json = json.getJSONObject("alipay");
			this.alipayAppId = json.getString("app_id");
			this.alipayPrivateKey = json.getString("private_key");
			this.alipayPublicKey = json.getString("public_key");
			this.notifyUrl = json.getString("notify_url");
		}
		return this;
	}

	public AlipayClient getAlipayClient(String ALIPAY_GATEWAY) {
		return new DefaultAlipayClient(ALIPAY_GATEWAY, this.alipayAppId, this.alipayPrivateKey, Constants.JSON,
				Constants.UTF8, this.alipayPublicKey, Constants.RSA2);
	}

	public String getNotifyUrl() {
		return notifyUrl;
	}

	public void setNotifyUrl(String notifyUrl) {
		this.notifyUrl = notifyUrl;
	}

	public String getAlipayAppId() {
		return alipayAppId;
	}

	public void setAlipayAppId(String alipayAppId) {
		this.alipayAppId = alipayAppId;
	}

	public String getAlipayPrivateKey() {
		return alipayPrivateKey;
	}

	public void setAlipayPrivateKey(String alipayPrivateKey) {
		this.alipayPrivateKey = alipayPrivateKey;
	}

	public String getAlipayPublicKey() {
		return alipayPublicKey;
	}

	public void setAlipayPublicKey(String alipayPublicKey) {
		this.alipayPublicKey = alipayPublicKey;
	}

	public String getTencentAppId() {
		return tencentAppId;
	}

	public void setTencentAppId(String tencentAppId) {
		this.tencentAppId = tencentAppId;
	}

	public String getTencentPartner() {
		return tencentPartner;
	}

	public void setTencentPartner(String tencentPartner) {
		this.tencentPartner = tencentPartner;
	}

	public String getTencentPartnerKey() {
		return tencentPartnerKey;
	}

	public void setTencentPartnerKey(String tencentPartnerKey) {
		this.tencentPartnerKey = tencentPartnerKey;
	}

	public String getTencentPartnerCert() {
		return tencentPartnerCert;
	}

	public void setTencentPartnerCert(String tencentPartnerCert) {
		this.tencentPartnerCert = tencentPartnerCert;
	}

	public String getOutTradeNo() {
		return outTradeNo;
	}

	public void setOutTradeNo(String outTradeNo) {
		this.outTradeNo = outTradeNo;
	}

	public String getTradeNo() {
		return tradeNo;
	}

	public void setTradeNo(String tradeNo) {
		this.tradeNo = tradeNo;
	}

	public String getRefundNo() {
		return refundNo;
	}

	public void setRefundNo(String refundNo) {
		this.refundNo = refundNo;
	}

	public Integer getPayWay() {
		return payWay;
	}

	public void setPayWay(Integer payWay) {
		this.payWay = payWay;
	}

	public Float getAmount() {
		return amount;
	}

	public void setAmount(Float amount) {
		this.amount = amount;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getAttach() {
		return attach;
	}

	public void setAttach(String attach) {
		this.attach = attach;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getOpenId() {
		return openId;
	}

	public void setOpenId(String openId) {
		this.openId = openId;
	}

	public Integer getMinutes() {
		return minutes;
	}

	public void setMinutes(Integer minutes) {
		this.minutes = minutes;
	}

	public Float getRefundFee() {
		return refundFee;
	}

	public void setRefundFee(Float refundFee) {
		this.refundFee = refundFee;
	}

	public String getRefundReason() {
		return refundReason;
	}

	public void setRefundReason(String refundReason) {
		this.refundReason = refundReason;
	}

	// 卖家账户
	public static enum SELLER_ACCOUNT_ENUM {
		YIXIANG(1), DZZ(2);
		private Integer account;

		private SELLER_ACCOUNT_ENUM(Integer account) {
			this.account = account;
		}

		public Integer getAccount() {
			return account;
		}
	}

	// 购买渠道
	public static enum ACCOUNT_SOURCE_ENUM {
		APP(1), MINI(2);
		private Integer source;

		private ACCOUNT_SOURCE_ENUM(Integer source) {
			this.source = source;
		}

		public Integer getSource() {
			return source;
		}
	}

}

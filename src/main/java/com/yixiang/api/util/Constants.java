package com.yixiang.api.util;

public class Constants {

	// 跨域访问域名
	public final static String TRUST_CROSS_ORIGINS = "*";
	// 请求body内容存储名称
	public final static String REQUEST_BODY = "request_body_data";
	// 请求参数存储名称
	public final static String HTTP_PARAM = "http_param";
	// 用户信息存储名称
	public final static String USER = "user_data";
	// 请求ip字段
	public final static String IP = "ip";
	// 请求手机号字段
	public final static String PHONE = "phone";
	// 请求软件名称字段
	public final static String SYSTEM = "system";
	// 请求设备字段
	public final static String IMEI = "imei";
	// 请求微信openid字段
	public final static String WXOPENID = "wxOpenId";
	// 微信小程序登录session存储名称
	public final static String WXA_SESSION = "wxa-sessionid";
	// 微信小程序登录session存储前缀
	public final static String WXA_SESSION_PREFIX = "wxa:session:";
	// 聚能充接口调用Token前缀
	public final static String JNC_TOKEN = "jnc-api-token";
	// 字符编码
	public static final String UTF8 = "utf-8";
	// 签名类型
	public static final String RSA2 = "RSA2";
	// 数据格式
	public static final String JSON = "json";
	// 加密格式
	public static final String MD5 = "MD5";
	// 操作成功
	public static final String SUCCESS = "SUCCESS";
	// 操作失败
	public static final String FAIL = "FAIL";
	// 充电状态检查调度任务名称前缀
	public static final String STATE_JOB_PREFIX = "check_charging_state_job_";
	// 充电状态检查调度任务组前缀
	public static final String STATE_GROUP_PREFIX = "check_charging_state_group_";
	// 自动扣款检查调度任务名称前缀
	public static final String PAY_JOB_PREFIX = "pay_charging_order_job";
	// 自动扣款检查调度任务组前缀
	public static final String PAY_GROUP_PREFIX = "pay_charging_order_group";
	// 行驶距离单位
	public static final String DISTANCE_UNIT = "公里";
	// 车价单位
	public static final String CAR_PRICE_UNIT = "万";
	// 国家电网
	public static final String GJDW_PROVIDER_ID = "2";
	// 空字符串
	public static final String EMPTY = null;
	// 微信支付
	public static final Integer WEIXINPAY = 1;
	// 支付宝支付
	public static final Integer ALIPAY = 2;
	// 余额支付
	public static final Integer BALANCEPAY = 3;
	// 是
	public final static Integer YES = 1;
	// 否
	public final static Integer NO = 0;

}

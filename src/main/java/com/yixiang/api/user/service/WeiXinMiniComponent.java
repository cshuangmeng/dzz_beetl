package com.yixiang.api.user.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.jfinal.aop.Duang;
import com.jfinal.weixin.sdk.api.ApiConfigKit;
import com.jfinal.weixin.sdk.api.ApiResult;
import com.jfinal.weixin.sdk.api.JsTicket;
import com.jfinal.weixin.sdk.api.JsTicketApi;
import com.jfinal.weixin.sdk.api.JsTicketApi.JsApiType;
import com.jfinal.weixin.sdk.cache.IAccessTokenCache;
import com.jfinal.wxaapp.api.WxaUserApi;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.PropertiesUtil;
import com.yixiang.api.util.ResponseCode;
import com.yixiang.api.util.Result;
import com.yixiang.api.util.SignUtil;
import com.yixiang.api.util.ThreadCache;

@Service
public class WeiXinMiniComponent {

	// 微信小程序用户接口api
	protected WxaUserApi wxaUserApi = Duang.duang(WxaUserApi.class);
	Logger log=LoggerFactory.getLogger(getClass());

	//小程序登陆接口
	public Map<String,Object> login(String jsCode) {
		if (StringUtils.isEmpty(jsCode)) {
			Result.putValue(ResponseCode.CodeEnum.REQUIRED_PARAM_NULL);
			log.info("微信code为空,code="+jsCode);
			return null;
		}
		// 获取SessionKey
		ApiResult apiResult = wxaUserApi.getSessionKey(jsCode);
		// 返回{"session_key":"nzoqhc3OnwHzeTxJs+inbQ==","expires_in":2592000,"openid":"oVBkZ0aYgDMDIywRdgPW8-joxXc4"}
		if (!apiResult.isSucceed()) {
			Result.putValue(ResponseCode.CodeEnum.FAIL);
			log.info("请求失败,response="+apiResult.getJson());
			return null;
		}
		// 利用 appId 与 accessToken 建立关联，支持多账户
		IAccessTokenCache accessTokenCache = ApiConfigKit.getAccessTokenCache();
		String sessionId = apiResult.getStr("openid");
		accessTokenCache.set(Constants.WXA_SESSION_PREFIX + sessionId, apiResult.getJson());
		return DataUtil.mapOf("openId",sessionId);
	}

	//服务端解密小程序用户信息接口
	@SuppressWarnings("unchecked")
	public JSONObject info(String signature,String rawData,String encryptedData,String iv) {
		// 利用 appId 与 accessToken 建立关联，支持多账户
		Map<String,Object> param=(Map<String,Object>)ThreadCache.getData(Constants.HTTP_PARAM);
		IAccessTokenCache accessTokenCache = ApiConfigKit.getAccessTokenCache();
		if(DataUtil.isEmpty(param.get(Constants.WXOPENID))){
			Result.putValue(ResponseCode.CodeEnum.REQUIRED_PARAM_NULL);
			log.info("请求头wxa-sessionid为空,wxOpenId="+param.get(Constants.WXOPENID));
			return null;
		}
		String sessionId = param.get(Constants.WXOPENID).toString();
		String sessionJson = accessTokenCache.get(Constants.WXA_SESSION_PREFIX + sessionId);
		if (StringUtils.isEmpty(sessionJson)) {
			Result.putValue(ResponseCode.CodeEnum.FAIL);
			log.info("用户wxa_session为空sessionId="+sessionId+",sessionJson="+sessionJson);
			return null;
		}
		ApiResult sessionResult = ApiResult.create(sessionJson);
		// 获取sessionKey
		String sessionKey = sessionResult.get("session_key");
		// 用户信息校验
		boolean check = wxaUserApi.checkUserInfo(sessionKey, rawData, signature);
		if (!check) {
			Result.putValue(ResponseCode.CodeEnum.FAIL);
			log.info("用户信息校验失败,sessionId="+sessionId+",sessionJson="+sessionJson);
			return null;
		}
		// 服务端解密用户信息
		ApiResult apiResult = wxaUserApi.getUserInfo(sessionKey, encryptedData, iv);
		if (!apiResult.isSucceed()) {
			Result.putValue(ResponseCode.CodeEnum.FAIL);
			log.info("服务端解密用户信息失败,sessionId="+sessionId+",sessionJson="+sessionJson);
			return null;
		}
		JSONObject json=JSONObject.parseObject(apiResult.getJson());
		return json;
	}
	
	// JS－SDK签名生成
	public HashMap<String, String> getRequestSign(String url) {
		ApiConfigKit.setThreadLocalAppId(PropertiesUtil.getProperty("wx_mini_appid"));
		IAccessTokenCache accessTokenCache = ApiConfigKit.getAccessTokenCache();
		String key = ApiConfigKit.getAppId() + ':' + JsApiType.jsapi.name();
		JsTicket ticket=JsTicketApi.getTicket(JsApiType.jsapi);
		if(null==ticket||!ticket.isSucceed()||!ticket.isAvailable()){
			log.info("微信jsticket不可用,ticket"+(null!=ticket?ticket.getJson():null));
			Result.putValue(ResponseCode.CodeEnum.FAIL);
			return null;
		}
		if(StringUtils.isEmpty(accessTokenCache.get(key))){
			accessTokenCache.set(key, ticket.getCacheJson());
		}
		HashMap<String, String> params = new HashMap<String, String>();
		HashMap<String, String> resultMap = new HashMap<String, String>();
		String noncestr = DataUtil.createStrings(16);
		long timestamp = new Date().getTime();
		//去掉#号以及后面的内容
		url=url.substring(0, url.indexOf("#")>=0?url.indexOf("#"):url.length());
		params.put("url", url);
		params.put("noncestr", noncestr);
		params.put("timestamp", String.valueOf(timestamp/1000));
		params.put("jsapi_ticket", ticket.getTicket());
		resultMap.put("appId", ApiConfigKit.getApiConfig().getAppId());
		resultMap.put("nonceStr", noncestr);
		resultMap.put("timestamp", params.get("timestamp"));
		resultMap.put("signature", SignUtil.signValue(params,"SHA1"));
		return resultMap;
	}
	
}

package com.yixiang.api.user.service;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.jfinal.weixin.sdk.api.ApiConfig;
import com.jfinal.weixin.sdk.api.ApiConfigKit;
import com.jfinal.weixin.sdk.api.ApiResult;
import com.jfinal.weixin.sdk.api.SnsAccessToken;
import com.jfinal.weixin.sdk.api.SnsAccessTokenApi;
import com.jfinal.weixin.sdk.api.SnsApi;
import com.jfinal.weixin.sdk.cache.IAccessTokenCache;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.PropertiesUtil;
import com.yixiang.api.util.ResponseCode;
import com.yixiang.api.util.Result;
import com.yixiang.api.util.ThreadCache;

@Service
public class WeiXinAppComponent {

	Logger log=LoggerFactory.getLogger(getClass());

	//APP开放账号登陆接口
	public Map<String,Object> login(String code) {
		if (StringUtils.isEmpty(code)) {
			Result.putValue(ResponseCode.CodeEnum.REQUIRED_PARAM_NULL);
			log.info("微信code为空,code="+code);
			return null;
		}
		// 获取SessionKey
		ApiConfig ac=ApiConfigKit.getApiConfig(PropertiesUtil.getProperty("wx_app_appid"));
		SnsAccessToken apiResult = SnsAccessTokenApi.getSnsAccessToken(ac.getAppId(), ac.getAppSecret(), code);
		// 返回
		if (null!=apiResult.getErrorCode()) {
			Result.putValue(ResponseCode.CodeEnum.FAIL);
			log.info("请求失败,response="+apiResult.getJson());
			return null;
		}
		// 利用 appId 与 accessToken 建立关联，支持多账户
		IAccessTokenCache accessTokenCache = ApiConfigKit.getAccessTokenCache();
		accessTokenCache.set(Constants.WXA_SESSION_PREFIX + apiResult.getOpenid(), apiResult.getJson());
		return DataUtil.mapOf("openId",apiResult.getOpenid());
	}

	//服务端解密APP开放账号用户信息接口
	@SuppressWarnings("unchecked")
	public JSONObject info() {
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
		SnsAccessToken sessionResult = new SnsAccessToken(sessionJson);
		ApiResult result=SnsApi.getUserInfo(sessionResult.getAccessToken(),sessionResult.getOpenid());
		if (!result.isSucceed()) {
			Result.putValue(ResponseCode.CodeEnum.FAIL);
			log.info("请求失败,response="+result.getJson());
			return null;
		}
		return JSONObject.parseObject(result.getJson());
	}

}

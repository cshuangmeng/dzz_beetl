package com.yixiang.api.util;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PushUtil {

	public final static String PUSH_URL = PropertiesUtil.getProperty("jiguang_push_url");
	public final static String APPKEY = PropertiesUtil.getProperty("jiguang_app_key");
	public final static String APPSECRET = PropertiesUtil.getProperty("jiguang_app_secret");
	public final static String PLATFORM_ANDROID = "android";
	public final static String PLATFORM_IOS = "ios";
	
	static Logger log=LoggerFactory.getLogger(PushUtil.class);

	public static void main(String[] args) {
		try {
			String json = "{'platform': 'all'"
					+ ",'notification': {'android': {'alert': '测试一个苹果安卓','title': 'Send to Android'"
					+ ",'big_pic_path': 'http://d.hiphotos.baidu.com/image/pic/item/32fa828ba61ea8d3f622db539a0a304e251f5860.jpg','style':3,'alert_type':4"
					+ ",'builder_id': 1,'extras': {'newsid': 321}},'ios': {'alert': 'Hi, JPush!'"
					+ ",'sound': 'default','badge': '+1','extras': {'newsid': 321}}}"
					+ ",'options': {'time_to_live': 86400,'apns_production': true}}";
			System.out.println(json);
			System.out.println(push("160a3797c8514ff01ac", JSONObject.parseObject(json)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// 推送通知
	private static String sendPush(JSONObject params) {
		String result = null;
		try {
			byte[] auth = new String(APPKEY + ":" + APPSECRET).getBytes();
			OkHttpClient client = new OkHttpClient().newBuilder().connectTimeout(10, TimeUnit.SECONDS)
					.writeTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build();
			RequestBody body = RequestBody.create(MediaType.parse("application/json"), params.toJSONString());
			Request request = new Request.Builder().url(PUSH_URL)
					.addHeader("Authorization", "Basic " + Base64.encodeBase64String(auth)).post(body).build();
			Response response = client.newCall(request).execute();
			result = response.body().string();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	// 推送消息
	public static boolean push(List<String> registrationIds, JSONObject params) {
		params.put("audience", DataUtil.mapOf("registration_id", registrationIds));
		String response = sendPush(params);
		log.info("params="+params+",response="+response);
		return !JSONObject.parseObject(response).containsKey("error");
	}

	// 推送消息
	public static boolean push(String registrationId, JSONObject params) {
		params.put("audience", DataUtil.mapOf("registration_id", Arrays.asList(registrationId)));
		String response = sendPush(params);
		log.info("params="+params+",response="+response);
		return !JSONObject.parseObject(response).containsKey("error");
	}

}

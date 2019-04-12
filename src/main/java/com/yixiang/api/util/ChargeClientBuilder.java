package com.yixiang.api.util;

import java.net.URLEncoder;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.translate.UnicodeUnescaper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONObject;
import com.feilong.core.DatePattern;
import com.feilong.core.date.DateUtil;
import com.feilong.core.net.ParamUtil;
import com.jfinal.plugin.redis.Redis;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Component
@PropertySource(value="classpath:config.properties")
public class ChargeClientBuilder {

	//运营商标识
	@Value("${operator_id}")
	private String OPERATOR_ID;
	//运营商密钥
	@Value("${operator_secret}")
    private String OPERATOR_SECRET;
	//消息密钥
	@Value("${data_secret}")
    private String DATA_SECRET;
	//消息密钥初始化向量
	@Value("${data_secret_iv}")
    private String DATA_SECRET_IV;
	//签名密钥
	@Value("${sig_secret}")
    private String SIG_SECRET;
	//用户的open_id
	@Value("${app_key}")
    private String APP_KEY;
	//用户的密钥
	@Value("${app_secret}")
	private String APP_SECRET;
	//第三方的用户标识
	@Value("${partner_user_id}")
	private String PARTNER_USER_ID;
	//获取Token接口
	@Value("${query_token_url}")
	private String QUERY_TOKEN_URL;
	//获取充电站列表信息接口
	@Value("${query_stations_info_url}")
	private String QUERY_STATIONS_INFO_URL;
	//获取充电桩信息接口
	@Value("${query_station_single_status_url}")
	private String QUERY_STATION_SINGLE_STATUS_URL;
	//启动充电接口
	@Value("${query_start_charge_url}")
	private String QUERY_START_CHARGE_URL;
	//充电状态查询接口
	@Value("${query_equip_charge_status_url}")
	private String QUERY_EQUIP_CHARGE_STATUS_URL;
	//结束充电接口
	@Value("${query_stop_charge_url}")
	private String QUERY_STOP_CHARGE_URL;
	//获取账单信息接口
	@Value("${check_charge_orders_url}")
	private String CHECK_CHARGE_ORDERS_URL;
	
	private OkHttpClient httpClient=null;
	
	private Logger log=LoggerFactory.getLogger(ChargeClientBuilder.class);
	
	private OkHttpClient getOkHttpClient(){
		if(null==httpClient){
	        httpClient = new okhttp3.OkHttpClient().newBuilder()
	        		.retryOnConnectionFailure(true)
	        		.connectTimeout(60, TimeUnit.SECONDS)
	        		.writeTimeout(60, TimeUnit.SECONDS)
	        		.readTimeout(60, TimeUnit.SECONDS)
	        		.build();
		}
		return httpClient;
	}
	
	//创建AccessToken供第三发应用调用
	public Map<String,Object> refreshToken(){
		Map<String,Object> result=null;
		try {
			JSONObject config=JSONObject.parseObject(Redis.use().get("jnc_to_me_config"));
			String token=DataUtil.buildUUID().toUpperCase();
			Map<String,Object> dataMap=DataUtil.mapOf("OperatorID",config.getString("operator_id"),"SuccStat",Constants.NO,"AccessToken",token
					,"TokenAvailableTime",config.getIntValue("token_expire_seconds"),"FailReason",Constants.NO);
			// 加密Data
	        String data=JSONObject.toJSONString(dataMap);
	        log.info("加密前的data数据："+data);
	        data = encrypt(data, config.getString("data_secret"), config.getString("data_secret_iv"));
	        log.info("加密后的data数据："+data);
			String sign=hashMac(config.getString("operator_id")+data,config.getString("sign_secret"));
			result=DataUtil.mapOf("Ret",Constants.NO,"Data",data,"Msg","请求成功","Sig",sign);
		} catch (Exception e) {
			e.printStackTrace();
			result=DataUtil.mapOf("Ret",500,"Msg","系统错误");
		}
		log.info("响应数据为："+result);
		return result;
	}
	
	//解析推送过来的充电桩状态数据
	public String getPushStationStatusData(){
		try {
			Object body=ThreadCache.getData(Constants.REQUEST_BODY);
			if(DataUtil.isEmpty(body)||!DataUtil.isJSONObject(body.toString())){
				log.info("数据格式不正确,body="+body);
				return null;
			}
			log.info("解密前的data数据："+body);
			JSONObject response=JSONObject.parseObject(body.toString());
			// 解密Data
			JSONObject config=JSONObject.parseObject(Redis.use().get("jnc_to_me_config"));
			String data=response.getString("Data");
            data=decrypt(data, config.getString("data_secret"), config.getString("data_secret_iv"));
            log.info("解密后的data数据："+data);
            return data;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	//获取Token
	public String queryToken() {
		try {
			//检查token是否过期
			String token=Redis.use().get(Constants.JNC_TOKEN);
			if(StringUtils.isEmpty(token)){
				String time = DateUtil.toString(new Date(), DatePattern.TIMESTAMP);
		        String nonceStr = DataUtil.createNums(4);
		        // 加密Data
		        String data="{\"OperatorID\":\""+OPERATOR_ID+"\",\"OperatorSecret\":\""+OPERATOR_SECRET+"\"}";
		        log.info("加密前的data数据："+data);
		        data = encrypt(data,DATA_SECRET, DATA_SECRET_IV);
		        log.info("加密后的data数据："+data);
				String sign=hashMac(OPERATOR_ID+data+time+nonceStr,SIG_SECRET);
				log.info("签名为："+sign);
				String params = JSONObject.toJSONString(DataUtil.mapOf("OperatorID",OPERATOR_ID,"TimeStamp",time,"Seq",nonceStr
							,"Data",data,"Sig",sign));
				log.info("最终请求参数为："+params);
				data=doPost(QUERY_TOKEN_URL, DataUtil.mapOf("Authorization", "Bearer "+token), "application/json", params);
				log.info("响应内容为："+data);
				JSONObject json=JSONObject.parseObject(data);
				if(json.getInteger("Ret").equals(ResponseCode.CodeEnum.SUCCESS.getValue())){
		            data=json.getString("Data");
		            data=decrypt(data, DATA_SECRET, DATA_SECRET_IV);
		            log.info("解密后的data数据："+data);
		            //存入Redis
		            json=JSONObject.parseObject(data);
		            token=json.getString("AccessToken");
		            Redis.use().setex(Constants.JNC_TOKEN, json.getInteger("TokenAvailableTime")-600, token);
				}
			}
			return token;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	//获取充电站列表
	public String queryStationsInfo(Integer pageNo,Integer pageSize) {
		try {
			String time = DateUtil.toString(new Date(), DatePattern.TIMESTAMP);
	        String nonceStr = DataUtil.createNums(4);
	        // 加密Data
	        String data="{\"PageNo\":"+pageNo+",\"PageSize\":"+pageSize+"}";
	        log.info("加密前的data数据："+data);
	        data = encrypt(data,DATA_SECRET, DATA_SECRET_IV);
	        log.info("加密后的data数据："+data);
			String sign=hashMac(OPERATOR_ID+data+time+nonceStr,SIG_SECRET);
			log.info("签名为："+sign);
			String params = JSONObject.toJSONString(DataUtil.mapOf("OperatorID",OPERATOR_ID,"TimeStamp",time,"Seq",nonceStr
						,"Data",data,"Sig",sign));
			log.info("最终请求参数为："+params);
            // 获取响应内容
            data=doPost(QUERY_STATIONS_INFO_URL, DataUtil.mapOf("Authorization", "Bearer "+queryToken()), "application/json", params);
            log.info("响应内容为："+data);
            data=JSONObject.parseObject(data).getString("Data");
            data=decrypt(data, DATA_SECRET, DATA_SECRET_IV);
            log.info("解密后的data数据："+data);
            return data;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	//获取充电桩信息
	public String queryStationSingleStatus(String stationId) {
		try {
			String time = DateUtil.toString(new Date(), DatePattern.TIMESTAMP);
	        String nonceStr = DataUtil.createNums(4);
	        // 加密Data
	        String data="{\"StationID\":\""+stationId+"\"}";
	        log.info("加密前的data数据："+data);
	        data = encrypt(data,DATA_SECRET, DATA_SECRET_IV);
	        log.info("加密后的data数据："+data);
			String sign=hashMac(OPERATOR_ID+data+time+nonceStr,SIG_SECRET);
			log.info("签名为："+sign);
			String params = JSONObject.toJSONString(DataUtil.mapOf("OperatorID",OPERATOR_ID,"TimeStamp",time,"Seq",nonceStr
						,"Data",data,"Sig",sign));
			log.info("最终请求参数为："+params);
			data=doPost(QUERY_STATION_SINGLE_STATUS_URL, DataUtil.mapOf("Authorization", "Bearer "+queryToken()), "application/json", params);
            log.info("响应内容为："+data);
            data=JSONObject.parseObject(data).getString("Data");
            data=decrypt(data, DATA_SECRET, DATA_SECRET_IV);
            log.info("解密后的data数据："+data);
            return data;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	//启动充电
	public String startCharge(String stationId) {
		try {
			String data="{\"scan_id\": \""+stationId+"\"}";
			data=encrypt(data, DATA_SECRET, DATA_SECRET_IV);
			//其他参数
			Map<String,String> content=DataUtil.mapOf("data", URLEncoder.encode(data, "UTF-8"));
			String url=QUERY_START_CHARGE_URL;
			log.info("请求url为："+url+",body="+ParamUtil.toQueryStringUseSingleValueMap(content));
			data=doPostRetry(url, content);
			data=new UnicodeUnescaper().translate(data);
            log.info("响应内容为："+data);
            return data;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	//结束充电
	public String stopCharge(String chargeId) {
		try {
			String data="{\"record_id\": \""+chargeId+"\"}";
			data=encrypt(data, DATA_SECRET, DATA_SECRET_IV);
			//其他参数
			Map<String,String> content=DataUtil.mapOf("data", URLEncoder.encode(data, "UTF-8"));
			String url=QUERY_STOP_CHARGE_URL;
			log.info("请求url为："+url+",body="+ParamUtil.toQueryStringUseSingleValueMap(content));
			data=doPostRetry(url, content);
			data=new UnicodeUnescaper().translate(data);
            log.info("响应内容为："+data);
            return data;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	//充电状态查询
	public String queryChargeState(String chargeId) {
		try {
			String data="{\"record_id\": \""+chargeId+"\"}";
			data=encrypt(data, DATA_SECRET, DATA_SECRET_IV);
			//其他参数
			Map<String,String> content=DataUtil.mapOf("data", URLEncoder.encode(data, "UTF-8"));
			String url=QUERY_EQUIP_CHARGE_STATUS_URL;
			log.info("请求url为："+url+",body="+ParamUtil.toQueryStringUseSingleValueMap(content));
			data=doPost(url, content);
			data=new UnicodeUnescaper().translate(data);
            log.info("响应内容为："+data);
            return data;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	//获取账单信息
	public String checkChargeOrders(String chargeId) {
		try {
			String data="{\"record_id\": \""+chargeId+"\"}";
			data=encrypt(data, DATA_SECRET, DATA_SECRET_IV);
			//其他参数
			Map<String,String> content=DataUtil.mapOf("data", URLEncoder.encode(data, "UTF-8"));
			String url=CHECK_CHARGE_ORDERS_URL;
			log.info("请求url为："+url+",body="+ParamUtil.toQueryStringUseSingleValueMap(content));
			data=doPost(url, content);
			data=new UnicodeUnescaper().translate(data);
            log.info("响应内容为："+data);
            return data;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	//检查请求内容是否调用成功,否则进行重试
	public String doPostRetry(String url,Map<String,String> content){
		String response=null;
		try {
			response=doPost(url, content);
			log.info("响应内容为："+response);
			JSONObject json=DataUtil.isJSONObject(response)?JSONObject.parseObject(response):null;
			if(null==json||DataUtil.isEmpty(json.get("success"))||!json.getBooleanValue("success")){
				int interval=Integer.parseInt(Redis.use().get("dzz_retry_interval"));
				int count=Integer.parseInt(Redis.use().get("dzz_retry_count"));
				for(int i=1;i<=count;i++){
					Thread.sleep(interval*1000);
					log.info("开始重试第"+i+"次:"+url+":"+content);
					response=doPost(url, content);
					log.info("第"+i+"次重试的响应结果为:"+response);
					json=DataUtil.isJSONObject(response)?JSONObject.parseObject(response):null;
					if(null!=json&&!DataUtil.isEmpty(json.get("success"))&&json.getBooleanValue("success")){
						break;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}
	
	//发送POST请求
	public String doPost(String url,Map<String,String> params) {
		String responseStr=null;
		try {
			FormBody.Builder body=new FormBody.Builder();
			if(null!=params&&params.size()>0){
				params.entrySet().stream().forEach(header->{
					body.add(header.getKey(), header.getValue());
            	});
            }
            Request request = new Request.Builder().url(url).post(body.build()).build();
            Response response = getOkHttpClient().newCall(request).execute();
            ResponseBody responseBody=response.body();
            responseStr=responseBody.string();
            responseBody.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return responseStr;
	}
	
	//发送POST请求
	public String doPost(String url,Map<String,String> headers,String contentType,String data) {
		String responseStr=null;
		try {
            RequestBody body = RequestBody.create(MediaType.parse(contentType), data);
            Request.Builder builder=new Request.Builder();
            if(null!=headers&&headers.size()>0){
            	headers.entrySet().stream().forEach(header->{
            		builder.addHeader(header.getKey(), header.getValue());
            	});
            }
            Request request = builder.url(url).post(body).build();
            Response response = getOkHttpClient().newCall(request).execute();
            ResponseBody responseBody=response.body();
            responseStr=responseBody.string();
            responseBody.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return responseStr;
	}
	
	public String hashMac(String sStr, String key) throws Exception{
        SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), "HmacMD5");
        Mac mac = Mac.getInstance("HmacMD5");
        mac.init(signingKey);
        return byte2hex(mac.doFinal(sStr.getBytes()));
    }
	
	private String byte2hex(byte[] b){
        StringBuilder hs = new StringBuilder();
        String stmp;
        for (int n = 0; b!=null && n < b.length; n++) {
            stmp = Integer.toHexString(b[n] & 0XFF);
            if (stmp.length() == 1)
                hs.append('0');
            hs.append(stmp);
        }
        return hs.toString().toUpperCase();
    }
	
	public String encrypt(String sSrc, String sKey, String ivStr) throws Exception {
        if (sKey == null) {
        	log.info("Key为空null");
            return null;
        }
        // 判断Key是否为16位
        if (sKey.length() != 16) {
        	log.info("Key长度不是16位");
            return null;
        }
        if (ivStr == null) {
        	log.info("ivStr为空null");
            return null;
        }
        // 判断Key是否为16位
        if (ivStr.length() != 16) {
        	log.info("ivStr长度不是16位");
            return null;
        }
        byte[] raw = sKey.getBytes("utf-8");
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");//"算法/模式/补码方式"
        IvParameterSpec iv = new IvParameterSpec(ivStr.getBytes());
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);
        byte[] encrypted = cipher.doFinal(sSrc.getBytes("utf-8"));
        return new String(new Base64().encode(encrypted));
    }
	
	// 解密
    public String decrypt(String sSrc, String sKey, String ivStr) {
        try {
            // 判断Key是否正确
            if (sKey == null) {
            	log.info("Key为空null");
                return null;
            }
            // 判断Key是否为16位
            if (sKey.length() != 16) {
            	log.info("Key长度不是16位");
                return null;
            }
            if (ivStr == null) {
            	log.info("ivStr为空null");
                return null;
            }
            // 判断Key是否为16位
            if (ivStr.length() != 16) {
            	log.info("ivStr长度不是16位");
                return null;
            }

            byte[] raw = sKey.getBytes("utf-8");
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec iv = new IvParameterSpec(ivStr.getBytes());
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
            byte[] encrypted1 = new Base64().decode(sSrc);
            try {
                byte[] original = cipher.doFinal(encrypted1);
                String originalString = new String(original,"utf-8");
                return originalString;

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
	
}

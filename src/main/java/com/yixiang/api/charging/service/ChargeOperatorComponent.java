package com.yixiang.api.charging.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.feilong.core.DatePattern;
import com.feilong.core.date.DateUtil;
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.charging.pojo.ChargingStation;
import com.yixiang.api.util.ChargeClientBuilder;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.OSSUtil;
import com.yixiang.api.util.ResponseCode;
import com.yixiang.api.util.ThreadCache;
import com.yixiang.api.util.pojo.AreaInfo;
import com.yixiang.api.util.pojo.QueryExample;
import com.yixiang.api.util.service.AreaInfoComponent;

@Service
public class ChargeOperatorComponent {
	
	@Autowired
	private ChargeClientBuilder chargeClientBuilder;
	@Autowired
	private ChargingStationComponent chargingStationComponent;
	@Autowired
	private EquipmentInfoComponent equipmentInfoComponent;
	@Autowired
	private ConnectorInfoComponent connectorInfoComponent;
	@Autowired
	private AreaInfoComponent areaInfoComponent;
	
	private final static String OPERATOR_CONFIG="operatorConfig";
	Logger log=LoggerFactory.getLogger(getClass());
	
	//刷新Token
	public Map<String,Object> refreshToken(HttpServletRequest request){
		//校验请求数据是否正确
		JSONObject response=checkDataAvailable();
		if(null==response){
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.PARAMS_INCORRECT.getState());
		}
		//校验运营商是否存在
		JSONObject config=getOperatorConfig(response.getString("OperatorID"));
		if(null==config){
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.OPERATOR_NOT_EXISTS.getState());
		}
		//校验签名
		if(!checkSignCorrect(response, config)){
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.SIGN_INCORRECT.getState());
		}
		//校验运营商密钥是否正确
		String data=chargeClientBuilder.decrypt(response.getString("Data"), config.getString("data_secret"), config.getString("data_secret_iv"));
		log.info("解密后的数据为:"+data);
		if(StringUtils.isBlank(data)||!DataUtil.isJSONObject(data)||!JSONObject.parseObject(data).containsKey("OperatorSecret")
				||!JSONObject.parseObject(data).getString("OperatorSecret").equals(config.getString("operator_secret"))){
			log.info("传递的OperatorSecret参数不正确,body="+response+",operator_secret="+config.getString("operator_secret"));
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.SECRET_INCORRECT.getState());
		}
		//获取token信息
		String token=Redis.use().get(config.getString("operator_token_key"));
		if(StringUtils.isBlank(token)){
			token=DataUtil.buildUUID().toUpperCase();
			Redis.use().setex(config.getString("operator_token_key"), config.getIntValue("token_expire_seconds"), token);
		}
		return DataUtil.mapOf("OperatorID",config.getString("operator_id"),"SuccStat",Constants.NO,"AccessToken",token
				,"TokenAvailableTime",config.getIntValue("token_expire_seconds"),"FailReason",Constants.NO);
	}
	
	//获取合作方Token
	public String queryToken(JSONObject config) {
		try {
			//检查token是否过期
			String token=Redis.use().get(config.getString("client_token_key"));
			if(StringUtils.isBlank(token)){
				String time = DateUtil.toString(new Date(), DatePattern.TIMESTAMP);
		        String nonceStr = DataUtil.createNums(4);
		        // 加密Data
		        String data="{\"OperatorID\":\""+config.getString("operator_id")+"\",\"OperatorSecret\":\""+config.getString("operator_secret")+"\"}";
		        log.info("加密前的data数据："+data);
		        data = chargeClientBuilder.encrypt(data, config.getString("data_secret"), config.getString("data_secret_iv"));
		        log.info("加密后的data数据："+data);
				String sign=chargeClientBuilder.hashMac(config.getString("operator_id")+data+time+nonceStr, config.getString("sign_secret"));
				log.info("签名为："+sign);
				String params=JSONObject.toJSONString(DataUtil.mapOf("OperatorID",config.getString("operator_id")
						,"TimeStamp",time,"Seq",nonceStr,"Data",data,"Sig",sign));
				log.info("最终请求参数为："+params);
				data=chargeClientBuilder.doPost(config.getString("refresh_token_url"), null, "application/json", params);
				log.info("响应内容为："+data);
				JSONObject json=JSONObject.parseObject(data);
				if(json.getInteger("Ret").equals(ResponseCode.CodeEnum.SUCCESS.getValue())){
		            data=json.getString("Data");
		            data=chargeClientBuilder.decrypt(data, config.getString("data_secret"), config.getString("data_secret_iv"));
		            log.info("解密后的data数据："+data);
		            //存入Redis
		            json=JSONObject.parseObject(data);
		            token=json.getString("AccessToken");
		            Redis.use().setex(config.getString("client_token_key"), config.getInteger("token_expire_seconds"), token);
				}
			}
			return token;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	//分页读取充电站信息
	public Map<String,Object> queryChargingStations(HttpServletRequest request){
		//校验请求数据是否正确
		JSONObject response=checkDataAvailable();
		if(null==response){
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.PARAMS_INCORRECT.getState());
		}
		//校验运营商是否存在
		JSONObject config=getOperatorConfig(response.getString("OperatorID"));
		if(null==config){
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.OPERATOR_NOT_EXISTS.getState());
		}
		//校验token是否正确
		if(!checkTokenCorrect(request, response.getString("OperatorID"))){
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.TOKEN_INCORRECT.getState());
		}
		//校验签名
		if(!checkSignCorrect(response, config)){
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.SIGN_INCORRECT.getState());
		}
		//校验业务数据是否正确
		String data=chargeClientBuilder.decrypt(response.getString("Data"), config.getString("data_secret"), config.getString("data_secret_iv"));
		log.info("解密后的数据为:"+data);
		int pageNo=1;
		int pageSize=config.getIntValue("default_page_size");
		QueryExample example=new QueryExample();
		example.and().andEqualTo("source", Constants.YES).andEqualTo("user_id", Constants.NO).andNotEqualTo("station_id", "")
			.andEqualTo("state", ChargingStation.STATION_STATE_ENUM.ENABLED.getState());
		if(StringUtils.isNotBlank(data)&&DataUtil.isJSONObject(data)){
			JSONObject param=JSONObject.parseObject(data);
			if(param.containsKey("PageNo")){
				pageNo=param.getIntValue("PageNo");
			}
			if(param.containsKey("PageSize")){
				pageSize=param.getIntValue("PageSize");
			}
			if(config.containsKey("max_page_size")&&config.getIntValue("max_page_size")>0&&pageSize>config.getIntValue("max_page_size")){
				pageSize=config.getIntValue("max_page_size");
			}
			if(StringUtils.isNotBlank(param.getString("LastQueryTime"))){
				example.and().andGreaterThanOrEqualTo("update_time", param.getString("LastQueryTime"));
			}
		}
		long total=chargingStationComponent.countByExample(example);
		long pageCount=total%pageSize>0?total/pageSize+1:total/pageSize;
		example.setOffset((pageNo>0?pageNo-1:0)*pageSize);
		example.setLimit(pageSize);
		example.setOrderByClause("update_time desc,id desc");
		//转换成标准格式
		JSONObject oss=JSONObject.parseObject(Redis.use().get("charging_oss_config"));
		List<Map<String,Object>> stations=chargingStationComponent.selectByExample(example).stream().map(i->{
			AreaInfo area=areaInfoComponent.getAreaInfo(i.getAreaId());
			List<Map<String,Object>> equipments=equipmentInfoComponent.getEquipmentInfoByStationId(i.getStationId()).stream().map(j->{
				List<Map<String,Object>> connectors=connectorInfoComponent.getConnectorInfoByEquipmentId(j.getEquipmentId())
						.stream().map(k->k.toStandardFormat()).collect(Collectors.toList());
				Map<String,Object> equipment=j.toStandardFormat();
				equipment.put("ConnectorInfos", connectors);
				return equipment;
			}).collect(Collectors.toList());
			Map<String,Object> station=i.toStandardFormat();
			if(null!=area){
				station.put("AreaCode", area.getAreaCode());
			}
			if(StringUtils.isNotBlank(i.getDetailImgs())){
				station.put("Pictures", OSSUtil.joinOSSFileUrl(oss, i.getDetailImgs().split(",")));
			}
			station.put("EquipmentInfos", equipments);
			return station;
		}).collect(Collectors.toList());
		return DataUtil.mapOf("PageNo",pageNo,"PageCount",pageCount,"ItemSize",total,"StationInfos",stations
				,"SuccStat",Constants.NO,"FailReason",Constants.NO);
	}
	
	//获取充电桩信息
	public Map<String,Object> queryStationConnectors(HttpServletRequest request){
		//校验请求数据是否正确
		JSONObject response=checkDataAvailable();
		if(null==response){
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.PARAMS_INCORRECT.getState());
		}
		//校验运营商是否存在
		JSONObject config=getOperatorConfig(response.getString("OperatorID"));
		if(null==config){
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.OPERATOR_NOT_EXISTS.getState());
		}
		//校验token是否正确
		if(!checkTokenCorrect(request, response.getString("OperatorID"))){
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.TOKEN_INCORRECT.getState());
		}
		//校验签名
		if(!checkSignCorrect(response, config)){
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.SIGN_INCORRECT.getState());
		}
		//校验业务数据是否正确
		String data=chargeClientBuilder.decrypt(response.getString("Data"), config.getString("data_secret"), config.getString("data_secret_iv"));
		log.info("解密后的数据为:"+data);
		if(StringUtils.isBlank(data)||!DataUtil.isJSONObject(data)||!JSONObject.parseObject(data).containsKey("StationID")){
			log.info("传递的OperatorSecret参数不正确,body="+response+",operator_secret="+config.getString("operator_secret"));
			return DataUtil.mapOf("SuccStat",Constants.YES,"FailReason",FAIL_REASON_ENUM.DATA_INCORRECT.getState());
		}
		String stationId=JSONObject.parseObject(data).getString("StationID");
		List<Map<String,Object>> connectors=connectorInfoComponent.getConnectorInfoByStationId(stationId)
				.stream().map(i->i.toStandardFormat()).collect(Collectors.toList());
		return DataUtil.mapOf("ConnectorInfos",connectors,"SuccStat",Constants.NO,"FailReason",Constants.NO);
	}
	
	//推送充电桩状态
	public void pushStationStatus(JSONObject json){
		JSONArray array=JSONObject.parseObject(Redis.use().get("me_to_operator_config")).getJSONArray("secrets");
		for(int i=0;i<array.size();i++){
			try {
				JSONObject config=array.getJSONObject(i);
				String time=DateUtil.toString(new Date(), DatePattern.TIMESTAMP);
		        String nonceStr=DataUtil.createNums(4);
		        // 加密Data
		        String data=json.toJSONString();
		        log.info("加密前的data数据："+data);
		        data=chargeClientBuilder.encrypt(data, config.getString("data_secret"), config.getString("data_secret_iv"));
		        log.info("加密后的data数据："+data);
				String sign=chargeClientBuilder.hashMac(config.getString("operator_id")+data+time+nonceStr,config.getString("sign_secret"));
				log.info("签名为："+sign);
				String params=JSONObject.toJSONString(DataUtil.mapOf("OperatorID",config.getString("operator_id")
						,"TimeStamp",time,"Seq",nonceStr,"Data",data,"Sig",sign));
				log.info("最终请求参数为："+params);
		        // 获取响应内容
		        data=chargeClientBuilder.doPost(config.getString("push_station_status_url")
		        		, DataUtil.mapOf("Authorization", "Bearer "+queryToken(config)), "application/json", params);
		        log.info("响应内容为："+data);
		        data=JSONObject.parseObject(data).getString("Data");
		        data=chargeClientBuilder.decrypt(data, config.getString("data_secret"), config.getString("data_secret_iv"));
		        log.info("解密后的data数据："+data);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	//校验数据格式是否正确
	public JSONObject checkDataAvailable(){
		Object body=ThreadCache.getData(Constants.REQUEST_BODY);
		if(DataUtil.isEmpty(body)||!DataUtil.isJSONObject(body.toString())){
			log.info("数据格式不正确,body="+body);
			return null;
		}
		JSONObject response=JSONObject.parseObject(body.toString());
		if(StringUtils.isBlank(response.getString("OperatorID"))||StringUtils.isBlank(response.getString("Data"))
				||StringUtils.isBlank(response.getString("TimeStamp"))||StringUtils.isBlank(response.getString("Seq"))
				||StringUtils.isBlank(response.getString("Sig"))){
			log.info("数据格式不正确,body="+body);
			return null;
		}
		return response;
	}
	
	//校验token是否正确
	public boolean checkTokenCorrect(HttpServletRequest request,String operatorId){
		String authorization=request.getHeader("Authorization");
		JSONObject config=getOperatorConfig(operatorId);
		String token=null!=config?Redis.use().get(config.getString("operator_token_key")):null;
		if(StringUtils.isBlank(token)||StringUtils.isBlank(authorization)||!authorization.equals("Bearer "+token)){
			log.info("token不正确,token="+token+",authorization="+authorization);
			return false;
		}
		return true;
	}
	
	//校验签名是否正确
	public boolean checkSignCorrect(JSONObject response,JSONObject config){
		//校验签名
		String sign=null;
		try {
			sign=chargeClientBuilder.hashMac(config.getString("operator_id")+response.getString("Data")+response.getString("TimeStamp")
				+response.getString("Seq"), config.getString("sign_secret"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		if(StringUtils.isBlank(sign)||!sign.equals(response.getString("Sig"))){
			log.info("签名不正确,body="+response+",sign="+sign);
			return false;
		}
		return true;
	}
	
	//获取运营商配置
	public JSONObject getOperatorConfig(String operatorId){
		//校验运营商是否存在
		JSONObject config=JSONObject.parseObject(Redis.use().get("operator_to_me_config"));
		Optional<Object> ip=config.getJSONArray("secrets").stream().filter(i->JSONObject.parseObject(i.toString())
				.getString("operator_id").equals(operatorId)).findFirst();
		if(!ip.isPresent()){
			log.info("运营商不存在,OperatorId="+operatorId+",config="+config);
			return null;
		}
		config=JSONObject.parseObject(ip.get().toString());
		ThreadCache.setData(OPERATOR_CONFIG, config);
		return config;
	}
	
	//组装返回信息
	public Map<String,Object> setResponseData(Map<String,Object> dataMap){
		if((int)dataMap.get("SuccStat")==Constants.YES){
			if((int)dataMap.get("FailReason")==FAIL_REASON_ENUM.PARAMS_INCORRECT.getState()){
				return DataUtil.mapOf("Ret",4003,"Msg","POST参数不合法，缺少必需参数");
			}else if((int)dataMap.get("FailReason")==FAIL_REASON_ENUM.OPERATOR_NOT_EXISTS.getState()){
				return DataUtil.mapOf("Ret",4005,"Msg","无此运营商");
			}else if((int)dataMap.get("FailReason")==FAIL_REASON_ENUM.SECRET_INCORRECT.getState()){
				return DataUtil.mapOf("Ret",4006,"Msg","密钥错误");
			}else if((int)dataMap.get("FailReason")==FAIL_REASON_ENUM.SIGN_INCORRECT.getState()){
				return DataUtil.mapOf("Ret",4001,"Msg","签名错误");
			}else if((int)dataMap.get("FailReason")==FAIL_REASON_ENUM.TOKEN_INCORRECT.getState()){
				return DataUtil.mapOf("Ret",4002,"Msg","Token错误");
			}else if((int)dataMap.get("FailReason")==FAIL_REASON_ENUM.DATA_INCORRECT.getState()){
				return DataUtil.mapOf("Ret",4004,"Msg","请求的业务参数不合法");
			}else{
				return DataUtil.mapOf("Ret",dataMap.get("FailReason"),"Msg","未知错误");
			}
		}else{
			try {
				// 加密Data
				JSONObject config=(JSONObject)ThreadCache.getData(OPERATOR_CONFIG);
		        String data=JSONObject.toJSONString(dataMap);
		        log.info("加密前的data数据："+data);
		        data = chargeClientBuilder.encrypt(data, config.getString("data_secret"), config.getString("data_secret_iv"));
		        log.info("加密后的data数据："+data);
				String sign=chargeClientBuilder.hashMac(config.getString("operator_id")+data,config.getString("sign_secret"));
				Map<String,Object> result=DataUtil.mapOf("Ret",Constants.NO,"Data",data,"Msg","请求成功","Sig",sign);
				log.info("响应数据为："+result);
				return result;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return DataUtil.mapOf("Ret",500,"Msg","系统错误");
		}
	}
	
    // 失败原因
 	public static enum FAIL_REASON_ENUM {
 		NORMAL(0), OPERATOR_NOT_EXISTS(1), SECRET_INCORRECT(2), PARAMS_INCORRECT(3), SIGN_INCORRECT(4), TOKEN_INCORRECT(5), DATA_INCORRECT(6);
 		private Integer state;

 		private FAIL_REASON_ENUM(Integer state) {
 			this.state = state;
 		}

 		public Integer getState() {
 			return state;
 		}
 	}
    
}

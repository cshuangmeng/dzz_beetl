package com.yixiang.api.home.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.charging.service.ChargingStationComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.pojo.ActivityAd;
import com.yixiang.api.util.service.ActivityAdComponent;

@Service
public class HomeComponent {
	
	@Autowired
	private ActivityAdComponent activityAdComponent;
	@Autowired
	private ChargingStationComponent chargingStationComponent;

	//获取首页数据
	public Map<String,Object> getHomeData(BigDecimal lng,BigDecimal lat){
		//获取首页活动广告配置
		Integer category=ActivityAd.CATEGORY_TYPE_ENUM.HOME.getType();
		List<Map<Object,Object>> ads=activityAdComponent.queryActivityAds(category,0,null);
		//根据当前位置获取附近充电桩
		List<Map<Object,Object>> system=null;
		List<Map<Object,Object>> personal=null;
		if(lng.compareTo(new BigDecimal(0))>0&&lat.compareTo(new BigDecimal(0))>0){
			system=chargingStationComponent.queryNearbyStations(lng, lat, false, 1, false);
			system.stream().forEach(i->i.put("isPrivate", Constants.NO));
			personal=chargingStationComponent.queryNearbyStations(lng, lat, true, 1, false);
			personal.stream().forEach(i->i.put("isPrivate", Constants.YES));
		}
		//其他文案
		JSONObject json=JSONObject.parseObject(Redis.use().get("home_nearby_station_config"));
		String distanceTip=json.getString("distance_tip");
		return DataUtil.mapOf("ads",ads,"publish",system,"personal",personal,"distanceTip",distanceTip);
	}
	
}

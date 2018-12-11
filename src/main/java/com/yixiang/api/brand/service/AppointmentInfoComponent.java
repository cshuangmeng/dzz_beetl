package com.yixiang.api.brand.service;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.jfinal.plugin.redis.Redis;
import com.jfinal.weixin.sdk.utils.HttpUtils;
import com.yixiang.api.brand.mapper.AppointmentInfoMapper;
import com.yixiang.api.brand.pojo.AppointmentInfo;
import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.user.service.UserInfoComponent;
import com.yixiang.api.util.pojo.AreaInfo;
import com.yixiang.api.util.service.AreaInfoComponent;

@Service
public class AppointmentInfoComponent {

	@Autowired
	private AppointmentInfoMapper appointmentInfoMapper;
	@Autowired
	private UserInfoComponent userInfoComponent;
	@Autowired
	private AreaInfoComponent areaInfoComponent;
	
	//保存预约用户信息
	public void saveAppointmentInfo(AppointmentInfo info){
		info.setCreateTime(new Date());
		UserInfo user=userInfoComponent.attemptLogin();
		if(null!=user){
			info.setUserId(user.getId());
		}
		//记录用户所在地区
		if(null!=info.getLat()&&info.getLat().floatValue()>0&&null!=info.getLng()&&info.getLng().floatValue()>0){
			String url=Redis.use().get("map_baidu_geocoder").toString()+info.getLat()+","+info.getLng();
			JSONObject json=JSONObject.parseObject(HttpUtils.get(url));
			Integer areaCode=json.getJSONObject("result").getJSONObject("addressComponent").getInteger("adcode");
			AreaInfo area=areaInfoComponent.queryAreaInfoByAreaCode(areaCode);
			if(null!=area){
				info.setAreaId(area.getId());
				AreaInfo city=areaInfoComponent.getAreaInfo(info.getAreaId());
				AreaInfo province=null;
				if(null!=city){
					province=areaInfoComponent.getAreaInfo(city.getParentId());
				}
				info.setProvince(null!=province?province.getAreaName():null);
				info.setCity(null!=city?city.getAreaName():null);
			}
		}
		appointmentInfoMapper.insertSelective(info);
	}
	
}

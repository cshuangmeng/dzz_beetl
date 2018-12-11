package com.yixiang.api.util.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import com.jfinal.plugin.redis.Redis;
import com.jfinal.weixin.sdk.utils.HttpUtils;
import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.user.service.UserInfoComponent;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.ThreadCache;
import com.yixiang.api.util.mapper.OpenAppLogMapper;
import com.yixiang.api.util.pojo.AreaInfo;
import com.yixiang.api.util.pojo.OpenAppLog;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class OpenAppLogComponent {

	@Autowired
	private OpenAppLogMapper openAppLogMapper;
	@Autowired
	private UserInfoComponent userInfoComponent;
	@Autowired
	private AreaInfoComponent areaInfoComponent;

	// 保存打开APP记录
	@Transactional
	public void saveOpenAppLog() {
		// 尝试登录
		UserInfo user = userInfoComponent.attemptLogin();
		Map<String,Object> param=ThreadCache.getHttpData();
		BigDecimal lng=!DataUtil.isEmpty(param.get("lng"))?new BigDecimal(param.get("lng").toString()):new BigDecimal(0);
		BigDecimal lat=!DataUtil.isEmpty(param.get("lat"))?new BigDecimal(param.get("lat").toString()):new BigDecimal(0);
		OpenAppLog log = new OpenAppLog();
		log.setCreateTime(new Date());
		log.setIp(ThreadCache.getIp());
		log.setLat(lat);
		log.setLng(lng);
		if (null != user) {
			log.setUserId(user.getId());
			log.setDeviceId(user.getDeviceId());
			try {
				//更新用户所在地区
				if(log.getLat().floatValue()>0&&log.getLng().floatValue()>0){
					String url=Redis.use().get("map_baidu_geocoder").toString()+log.getLat()+","+log.getLng();
					JSONObject json=JSONObject.parseObject(HttpUtils.get(url));
					Integer areaCode=json.getJSONObject("result").getJSONObject("addressComponent").getInteger("adcode");
					AreaInfo area=areaInfoComponent.queryAreaInfoByAreaCode(areaCode);
					if(null!=area){
						user.setAreaId(area.getId());
						userInfoComponent.updateUserInfo(user);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		insertSelective(log);
	}

	// 计算结果集大小
	public long countByExample(QueryExample example) {
		return openAppLogMapper.countByExample(example);
	}

	// 保存
	public int insertSelective(OpenAppLog record) {
		return openAppLogMapper.insertSelective(record);
	}

	// 获取结果集
	public List<OpenAppLog> selectByExample(QueryExample example) {
		return openAppLogMapper.selectByExample(example);
	}

	// 更新
	public int updateByExampleSelective(OpenAppLog record, QueryExample example) {
		return openAppLogMapper.updateByExampleSelective(record, example);
	}

}

package com.yixiang.api.util.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.user.service.UserInfoComponent;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.ThreadCache;
import com.yixiang.api.util.mapper.NaviLogMapper;
import com.yixiang.api.util.pojo.NaviLog;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class NaviLogComponent {

	@Autowired
	private NaviLogMapper naviLogMapper;
	@Autowired
	private UserInfoComponent userInfoComponent;

	// 保存导航记录
	public void saveNaviLog(Integer stationId) {
		// 尝试登录
		UserInfo user = userInfoComponent.attemptLogin();
		Map<String,Object> param=ThreadCache.getHttpData();
		BigDecimal lng=!DataUtil.isEmpty(param.get("lng"))?new BigDecimal(param.get("lng").toString()):new BigDecimal(0);
		BigDecimal lat=!DataUtil.isEmpty(param.get("lat"))?new BigDecimal(param.get("lat").toString()):new BigDecimal(0);
		NaviLog log = new NaviLog();
		log.setStationId(stationId);
		log.setCreateTime(new Date());
		log.setIp(ThreadCache.getIp());
		log.setLat(lat);
		log.setLng(lng);
		if (null != user) {
			log.setUserId(user.getId());
			log.setDeviceId(user.getDeviceId());
		}
		insertSelective(log);
	}

	// 计算结果集大小
	public long countByExample(QueryExample example) {
		return naviLogMapper.countByExample(example);
	}

	// 保存
	public int insertSelective(NaviLog record) {
		return naviLogMapper.insertSelective(record);
	}

	// 获取结果集
	public List<NaviLog> selectByExample(QueryExample example) {
		return naviLogMapper.selectByExample(example);
	}

	// 更新
	public int updateByExampleSelective(NaviLog record, QueryExample example) {
		return naviLogMapper.updateByExampleSelective(record, example);
	}

}

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
import com.yixiang.api.util.mapper.BrowseLogMapper;
import com.yixiang.api.util.pojo.BrowseLog;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class BrowseLogComponent {

	@Autowired
	private BrowseLogMapper browseLogMapper;
	@Autowired
	private UserInfoComponent userInfoComponent;

	// 保存浏览记录
	public void saveBrowseLog(Integer category, Integer relateId) {
		// 尝试登录
		UserInfo user = userInfoComponent.attemptLogin();
		Map<String,Object> param=ThreadCache.getHttpData();
		BigDecimal lng=!DataUtil.isEmpty(param.get("lng"))?new BigDecimal(param.get("lng").toString()):new BigDecimal(0);
		BigDecimal lat=!DataUtil.isEmpty(param.get("lat"))?new BigDecimal(param.get("lat").toString()):new BigDecimal(0);
		BrowseLog log = new BrowseLog();
		log.setCategory(category);
		log.setCreateTime(new Date());
		log.setIp(ThreadCache.getIp());
		log.setLat(lat);
		log.setLng(lng);
		log.setRelateId(relateId);
		if (null != user) {
			log.setUserId(user.getId());
			log.setDeviceId(user.getDeviceId());
		}
		insertSelective(log);
	}

	// 计算结果集大小
	public long countByExample(QueryExample example) {
		return browseLogMapper.countByExample(example);
	}

	// 保存
	public int insertSelective(BrowseLog record) {
		return browseLogMapper.insertSelective(record);
	}

	// 获取结果集
	public List<BrowseLog> selectByExample(QueryExample example) {
		return browseLogMapper.selectByExample(example);
	}

	// 更新
	public int updateByExampleSelective(BrowseLog record, QueryExample example) {
		return browseLogMapper.updateByExampleSelective(record, example);
	}

}

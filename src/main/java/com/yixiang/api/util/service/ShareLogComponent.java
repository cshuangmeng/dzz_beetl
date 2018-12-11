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
import com.yixiang.api.util.mapper.ShareLogMapper;
import com.yixiang.api.util.pojo.QueryExample;
import com.yixiang.api.util.pojo.ShareLog;

@Service
public class ShareLogComponent {

	@Autowired
	private ShareLogMapper shareLogMapper;
	@Autowired
	private UserInfoComponent userInfoComponent;

	// 保存分享记录
	public void saveShareLog(Integer category, Integer relateId) {
		// 尝试登录
		UserInfo user = userInfoComponent.attemptLogin();
		Map<String,Object> param=ThreadCache.getHttpData();
		BigDecimal lng=!DataUtil.isEmpty(param.get("lng"))?new BigDecimal(param.get("lng").toString()):new BigDecimal(0);
		BigDecimal lat=!DataUtil.isEmpty(param.get("lat"))?new BigDecimal(param.get("lat").toString()):new BigDecimal(0);
		ShareLog log = new ShareLog();
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

	// 获取结果集大小
	public long countByExample(QueryExample example) {
		return shareLogMapper.countByExample(example);
	}

	// 保存
	public int insertSelective(ShareLog record) {
		return shareLogMapper.insertSelective(record);
	}

	// 获取结果集
	public List<ShareLog> selectByExample(QueryExample example) {
		return shareLogMapper.selectByExample(example);
	}

	// 更新
	public int updateByExampleSelective(ShareLog record, QueryExample example) {
		return shareLogMapper.updateByExampleSelective(record, example);
	}

}

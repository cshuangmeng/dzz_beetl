package com.yixiang.api.charging.service;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.yixiang.api.charging.mapper.ReportStationInfoMapper;
import com.yixiang.api.charging.pojo.ReportStationInfo;
import com.yixiang.api.util.ThreadCache;

@Service
public class ReportStationInfoComponent {

	@Autowired
	private ReportStationInfoMapper reportStationInfoMapper;
	
	//保存故障上报信息
	public void saveReportStationInfo(ReportStationInfo info){
		info.setCreateTime(new Date());
		info.setUserId(ThreadCache.getCurrentUserInfo().getId());
		reportStationInfoMapper.insertSelective(info);
	}
	
}

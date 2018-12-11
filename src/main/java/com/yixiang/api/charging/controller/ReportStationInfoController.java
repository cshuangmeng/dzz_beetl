package com.yixiang.api.charging.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.charging.pojo.ReportStationInfo;
import com.yixiang.api.charging.service.ReportStationInfoComponent;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/charging/report")
public class ReportStationInfoController {

	@Autowired
	private ReportStationInfoComponent reportStationInfoComponent;
	
	//保存故障保修信息
	@RequestMapping("/save")
	public Result saveReportStationInfo(@ModelAttribute ReportStationInfo info){
		reportStationInfoComponent.saveReportStationInfo(info);
		return Result.getThreadObject();
	}
	
}

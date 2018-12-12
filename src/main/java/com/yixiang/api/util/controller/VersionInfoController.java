package com.yixiang.api.util.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.util.Result;
import com.yixiang.api.util.pojo.VersionInfo;
import com.yixiang.api.util.service.VersionInfoComponent;

@RestController
@RequestMapping("/util/version")
public class VersionInfoController {

	@Autowired
	private VersionInfoComponent versionInfoComponent;
	
	//获取升级提示信息
	@RequestMapping("/check")
	public Result getNewestVersionInfo(){
		VersionInfo version=versionInfoComponent.checkVersionInfo();
		if(Result.noError()){
			Result.putValue(version);
		}
		return Result.getThreadObject();
	}
	
}

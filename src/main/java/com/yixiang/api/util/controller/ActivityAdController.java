package com.yixiang.api.util.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.util.Constants;
import com.yixiang.api.util.Result;
import com.yixiang.api.util.service.ActivityAdComponent;

@RestController
@RequestMapping("/util/ad")
@CrossOrigin(methods=RequestMethod.POST,origins=Constants.TRUST_CROSS_ORIGINS)
public class ActivityAdController {

	@Autowired
	private ActivityAdComponent activityAdComponent;
	
	//下发广告
	@RequestMapping("/list")
	public Result getActivityAds(@RequestParam Integer category,@RequestParam(defaultValue="0")Integer page
			,@RequestParam(required=false)Integer limit){
		List<Map<Object,Object>> result=activityAdComponent.queryActivityAds(category,page,limit);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
}

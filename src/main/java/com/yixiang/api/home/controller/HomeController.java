package com.yixiang.api.home.controller;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.home.service.HomeComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/home")
@CrossOrigin(methods=RequestMethod.POST,origins=Constants.TRUST_CROSS_ORIGINS)
public class HomeController {

	@Autowired
	private HomeComponent homeComponent;
	
	//加载首页数据
	@RequestMapping("/index")
	public Result index(@RequestParam(defaultValue="0")BigDecimal lng,@RequestParam(defaultValue="0")BigDecimal lat){
		Map<String,Object> result=homeComponent.getHomeData(lng, lat);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
}

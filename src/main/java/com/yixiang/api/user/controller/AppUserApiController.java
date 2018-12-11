package com.yixiang.api.user.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;
import com.yixiang.api.user.service.WeiXinAppComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/wx/app/user")
@CrossOrigin(methods=RequestMethod.POST,origins=Constants.TRUST_CROSS_ORIGINS)
public class AppUserApiController {
	
	@Autowired
	private WeiXinAppComponent weiXinAppComponent;

	//用户登录,获取用户openId
	@RequestMapping("/login")
	public Result login(@RequestParam String code) {
		Map<String,Object> result=weiXinAppComponent.login(code);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//获取用户信息
	@RequestMapping("/info")
	public Result info() {
		JSONObject result=weiXinAppComponent.info();
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
}

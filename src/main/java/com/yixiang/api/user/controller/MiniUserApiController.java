package com.yixiang.api.user.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;
import com.yixiang.api.user.service.WeiXinMiniComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/wx/mini/user")
@CrossOrigin(methods=RequestMethod.POST,origins=Constants.TRUST_CROSS_ORIGINS)
public class MiniUserApiController {
	
	@Autowired
	private WeiXinMiniComponent weiXinComponent;

	//用户登录,获取用户openId
	@RequestMapping("/login")
	public Result login(@RequestParam String code) {
		Map<String,Object> result=weiXinComponent.login(code);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//获取用户信息
	@RequestMapping("/info")
	public Result info(@RequestParam String signature,@RequestParam String rawData
			,@RequestParam String encryptedData,@RequestParam String iv) {
		JSONObject result=weiXinComponent.info(signature, rawData, encryptedData, iv);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	// JS－SDK签名生成
	@RequestMapping("/sign")
	public Result getRequestSign(@RequestParam String url) {
		HashMap<String, String> result=weiXinComponent.getRequestSign(url);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
}

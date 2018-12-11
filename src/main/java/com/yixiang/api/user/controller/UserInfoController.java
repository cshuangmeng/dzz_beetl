package com.yixiang.api.user.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.yixiang.api.user.pojo.UserDevice;
import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.user.service.UserInfoComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/user/info")
@CrossOrigin(methods=RequestMethod.POST,origins=Constants.TRUST_CROSS_ORIGINS)
public class UserInfoController {

	@Autowired
	private UserInfoComponent userInfoComponent;
	
	//获取验证码
	@RequestMapping("/sendVerifyCode")
	public Result sendVerifyCode(@RequestParam(defaultValue="") String phone){
		userInfoComponent.sendCheckCode(phone);
		return Result.getThreadObject();
	}
	
	//用户登录
	@RequestMapping("/login")
	public Result login(@ModelAttribute UserInfo user,@ModelAttribute UserDevice device,@RequestParam String code){
		Map<String,Object> result=userInfoComponent.login(user, device, code);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//修改用户信息
	@RequestMapping("/edit")
	public Result editUserInfo(@ModelAttribute UserInfo user,@RequestParam(required=false)MultipartFile files){
		userInfoComponent.editUserInfo(user, files);
		return Result.getThreadObject();
	}
	
	//上传用户头像
	@RequestMapping("/upload")
	public Result editUserInfo(@RequestParam MultipartFile[] files){
		String[] result=userInfoComponent.publishUserMedia(files);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//用户信息
	@RequestMapping("")
	public Result info(@RequestParam String uuid){
		Map<String,Object> result=userInfoComponent.getUserInfo(uuid);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//用户主页信息
	@RequestMapping("/home")
	public Result home(){
		Map<String,Object> result=userInfoComponent.getUserHomeData();
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//用户打开APP回调
	@RequestMapping("/open/callback")
	public Result openAppSuccessCallback(){
		userInfoComponent.openAppSuccessCallback();
		return Result.getThreadObject();
	}
	
}

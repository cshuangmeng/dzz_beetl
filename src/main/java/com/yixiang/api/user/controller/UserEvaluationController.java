package com.yixiang.api.user.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.user.service.UserEvaluationComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/user/eval")
@CrossOrigin(methods=RequestMethod.POST,origins=Constants.TRUST_CROSS_ORIGINS)
public class UserEvaluationController {

	@Autowired
	private UserEvaluationComponent userEvaluationComponent;
	
	//评价用户
	@RequestMapping("")
	public Result idol(@RequestParam String uuid,@RequestParam Integer stars){
		userEvaluationComponent.evaluateUser(uuid, stars);
		return Result.getThreadObject();
	}
	
	//下发评论标签
	@RequestMapping("/stars")
	public Result getStarDescs(){
		List<String> result=userEvaluationComponent.getStarDescs();
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
}

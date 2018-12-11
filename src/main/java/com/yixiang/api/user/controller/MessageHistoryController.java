package com.yixiang.api.user.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.yixiang.api.user.pojo.MessageHistory;
import com.yixiang.api.user.service.MessageHistoryComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/user/message")
@CrossOrigin(methods=RequestMethod.POST,origins=Constants.TRUST_CROSS_ORIGINS)
public class MessageHistoryController {

	@Autowired
	private MessageHistoryComponent messageHistoryComponent;
	
	//获取历史留言列表
	@RequestMapping("/history")
	public Result queryMessageHistorys(){
		List<List<Map<String,Object>>> result=messageHistoryComponent.queryMessageHistorys();
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//获取历史留言列表
	@RequestMapping("/save")
	public Result publishNewMessage(@ModelAttribute MessageHistory message,@RequestParam(required=false)MultipartFile[] files){
		messageHistoryComponent.saveMessageHistory(message, files);
		return Result.getThreadObject();
	}
	
}

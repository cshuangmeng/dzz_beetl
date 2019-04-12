package com.yixiang.api.util.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.quartz.TaskService;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/quartz/job")
public class QuartzController {
	
	@Autowired
	private TaskService taskService;

	//删除充电状态检查任务
	@RequestMapping("/delStateJob")
	public Result deleteChargingStateJob(@RequestParam Long orderId){
		taskService.deleteJob(Constants.STATE_JOB_PREFIX+orderId, Constants.STATE_GROUP_PREFIX+orderId);
		return Result.getThreadObject();
	}
	
	//删除充电账单检查任务
	@RequestMapping("/delBillJob")
	public Result deleteChargeBillJob(@RequestParam Long orderId){
		taskService.deleteJob(Constants.BILL_JOB_PREFIX+orderId, Constants.BILL_GROUP_PREFIX+orderId);
		return Result.getThreadObject();
	}
	
}

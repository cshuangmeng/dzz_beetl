package com.yixiang.api.brand.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.brand.pojo.AppointmentInfo;
import com.yixiang.api.brand.service.AppointmentInfoComponent;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/brand/appoint")
public class AppointmentInfoController {

	@Autowired
	private AppointmentInfoComponent appointmentInfoComponent;
	
	//保存预约用户信息
	@RequestMapping("/save")
	public Result saveAppointmentInfo(@ModelAttribute AppointmentInfo info){
		appointmentInfoComponent.saveAppointmentInfo(info);
		return Result.getThreadObject();
	}
	
}

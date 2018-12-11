package com.yixiang.api.charging.controller;

import java.math.BigDecimal;
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

import com.yixiang.api.charging.pojo.ChargingStation;
import com.yixiang.api.charging.service.ChargingStationComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/charging/info")
@CrossOrigin(methods=RequestMethod.POST,origins=Constants.TRUST_CROSS_ORIGINS)
public class ChargingStationController {

	@Autowired
	private ChargingStationComponent chargingStationComponent;
	
	//搜索附近充电桩
	@RequestMapping("/nearby")
	public Result getNearbyChargingStations(@RequestParam(defaultValue="0")BigDecimal lng
			,@RequestParam(defaultValue="0")BigDecimal lat,@RequestParam(defaultValue="1")Integer page
			,@RequestParam(required=false)Integer source){
		List<Map<Object,Object>> result=chargingStationComponent.queryNearbyStations(lng, lat, null!=source?source>1:null, page, true);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//新建个人充电桩
	@RequestMapping("/save")
	public Result saveChargingStation(@ModelAttribute ChargingStation station,@RequestParam(required=false) MultipartFile[] files){
		chargingStationComponent.editChargingStation(station, files);
		return Result.getThreadObject();
	}
	
	//上传充电桩相关多媒体资源
	@RequestMapping("/upload")
	public Result publishArticleMedia(@RequestParam MultipartFile[] files){
		String[] result=chargingStationComponent.publishStationMedia(files);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//编辑个人充电桩
	@RequestMapping("/update")
	public Result updateChargingStation(@ModelAttribute ChargingStation station,@RequestParam(required=false) MultipartFile[] files){
		chargingStationComponent.editChargingStation(station, files);
		return Result.getThreadObject();
	}
	
	//充电桩详情
	@RequestMapping("/detail")
	public Result getChargingDetail(@RequestParam String uuid,@RequestParam(defaultValue="0")BigDecimal lng
			,@RequestParam(defaultValue="0")BigDecimal lat){
		Map<String,Object> result=chargingStationComponent.getChargingDetail(uuid, lng, lat);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//站点导航成功回调
	@RequestMapping("/navi/callback")
	public Result naviSuccessCallback(@RequestParam(required=false)String uuid){
		chargingStationComponent.naviSuccessCallback(uuid);
		return Result.getThreadObject();
	}
	
	//站点分享成功回调
	@RequestMapping("/share/callback")
	public Result shareSuccessCallback(@RequestParam(required=false)String uuid){
		chargingStationComponent.shareSuccessCallback(uuid);
		return Result.getThreadObject();
	}
	
}

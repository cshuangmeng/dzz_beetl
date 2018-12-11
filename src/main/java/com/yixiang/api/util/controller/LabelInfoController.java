package com.yixiang.api.util.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.util.Result;
import com.yixiang.api.util.pojo.LabelInfo;
import com.yixiang.api.util.service.LabelInfoComponent;

@RestController
@RequestMapping("/util/label")
public class LabelInfoController {

	@Autowired
	private LabelInfoComponent labelInfoComponent;
	
	//获取标签
	@RequestMapping("/list")
	private Result get(@RequestParam Integer parentId){
		List<LabelInfo> result=labelInfoComponent.queryLabelInfos(parentId);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
}

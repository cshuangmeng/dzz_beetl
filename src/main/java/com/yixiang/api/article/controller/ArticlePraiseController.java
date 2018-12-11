package com.yixiang.api.article.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yixiang.api.article.service.ArticlePraiseComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/article/praise")
@CrossOrigin(methods=RequestMethod.POST,origins=Constants.TRUST_CROSS_ORIGINS)
public class ArticlePraiseController {
	
	@Autowired
	private ArticlePraiseComponent articlePraiseComponent;
	
	//点赞
	@RequestMapping("/save")
	public Result praise(@RequestParam String uuid){
		articlePraiseComponent.praise(uuid);
		return Result.getThreadObject();
	}
	
	//取消点赞
	@RequestMapping("/cancel")
	public Result cancelPraise(@RequestParam String uuid){
		articlePraiseComponent.cancelPraise(uuid);
		return Result.getThreadObject();
	}

}

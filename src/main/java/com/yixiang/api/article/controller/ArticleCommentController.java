package com.yixiang.api.article.controller;

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

import com.yixiang.api.article.pojo.ArticleComment;
import com.yixiang.api.article.service.ArticleCommentComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.Result;

@RestController
@RequestMapping("/article/comment")
@CrossOrigin(methods=RequestMethod.POST,origins=Constants.TRUST_CROSS_ORIGINS)
public class ArticleCommentController {
	
	@Autowired
	private ArticleCommentComponent articleCommentComponent;
	
	//加载帖子评论列表
	@RequestMapping("/list")
	public Result queryArticleComments(@RequestParam String uuid,@RequestParam(defaultValue="1")Integer page){
		Map<String,Object> result=articleCommentComponent.getCommentsOfArticle(uuid, page);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//发布帖子评论
	@RequestMapping("/save")
	public Result publishNewArticleComment(@RequestParam String uuid,@ModelAttribute ArticleComment comment
			,@RequestParam(required=false)MultipartFile[] files){
		articleCommentComponent.saveArticleComment(uuid, comment, files);
		return Result.getThreadObject();
	}
	
	//下发评论标签
	@RequestMapping("/tags")
	public Result getCommentTags(){
		List<String> result=articleCommentComponent.getCommentTags();
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}

}

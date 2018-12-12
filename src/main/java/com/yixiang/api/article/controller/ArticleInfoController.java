package com.yixiang.api.article.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.article.pojo.ArticleInfo;
import com.yixiang.api.article.service.ArticleInfoComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.Result;

import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/article/info")
@CrossOrigin(methods=RequestMethod.POST,origins=Constants.TRUST_CROSS_ORIGINS)
public class ArticleInfoController {

	@Autowired
	private ArticleInfoComponent articleInfoComponent;
	
	//最新贴列表
	@ApiOperation(value="最新贴列表",httpMethod="POST",notes="可分页展示")
	@ApiImplicitParams({
		@ApiImplicitParam(dataType="int",name="brandId",value="品牌ID",required=false),
		@ApiImplicitParam(dataType="int",name="page",value="页码",required=false)})
	@RequestMapping("/new")
	public Result queryNewArticles(@RequestParam(required=false)Integer brandId,@RequestParam(defaultValue="1")Integer page){
		List<Map<String,Object>> result=articleInfoComponent.queryNewArticles(brandId, page);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//热门贴列表
	@ApiOperation(value="热门贴列表",httpMethod="POST",notes="可分页展示")
	@ApiImplicitParams({
		@ApiImplicitParam(dataType="int",name="brandId",value="品牌ID",required=false),
		@ApiImplicitParam(dataType="int",name="page",value="页码",required=false)})
	@RequestMapping("/hot")
	public Result queryHotArticles(@RequestParam(required=false)Integer brandId,@RequestParam(defaultValue="1")Integer page){
		List<Map<String,Object>> result=articleInfoComponent.queryHotArticles(brandId, page);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//问题贴列表
	@ApiOperation(value="问题贴列表",httpMethod="POST",notes="可分页展示")
	@ApiImplicitParams({
		@ApiImplicitParam(dataType="int",name="brandId",value="品牌ID",required=false),
		@ApiImplicitParam(dataType="int",name="page",value="页码",required=false)})
	@RequestMapping("/problem")
	public Result queryProblemArticles(@RequestParam(required=false)Integer brandId,@RequestParam(defaultValue="1")Integer page){
		List<Map<String,Object>> result=articleInfoComponent.queryProblemArticles(brandId, page);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//加载帖子列表
	@RequestMapping("/list")
	public Result queryMyArticles(@RequestParam Map<String,Object> param){
		List<Map<String,Object>> result=articleInfoComponent.queryUserArticles(param);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//文章热门搜索关键字
	@RequestMapping("/keys")
	public Result hotKeys(@RequestParam(required=false) String uuid){
		String keys=Redis.use().get("article_hot_keys");
		if(StringUtils.isNotEmpty(keys)){
			Result.putValue(Arrays.asList(keys.split(",")).stream().map(i->DataUtil.mapOf("key",i)).collect(Collectors.toList()));
		}
		return Result.getThreadObject();
	}
	
	//删除用户帖子
	@RequestMapping("/delete")
	public Result deleteArticle(@RequestParam String uuid){
		articleInfoComponent.deleteArticle(uuid);
		return Result.getThreadObject();
	}
	
	//加载帖子详情
	@RequestMapping("")
	public Result getArticleDetail(@RequestParam String uuid){
		Map<String,Object> result=articleInfoComponent.getArticleDetail(uuid);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//发布新帖子
	@RequestMapping("/save")
	public Result publishNewArticle(@ModelAttribute ArticleInfo article,@RequestParam(required=false)MultipartFile[] files
			,@RequestParam(required=false)MultipartFile img){
		articleInfoComponent.saveArticle(article, files, img);
		return Result.getThreadObject();
	}
	
	//上传帖子相关多媒体资源
	@RequestMapping("/upload")
	public Result publishArticleMedia(@RequestParam MultipartFile[] files){
		String[] result=articleInfoComponent.publishArticleMedia(files);
		if(Result.noError()){
			Result.putValue(result);
		}
		return Result.getThreadObject();
	}
	
	//文章分享成功回调
	@RequestMapping("/share/callback")
	public Result shareCallback(@RequestParam(required=false) String uuid){
		articleInfoComponent.shareSuccessCallback(uuid);
		return Result.getThreadObject();
	}
	
}

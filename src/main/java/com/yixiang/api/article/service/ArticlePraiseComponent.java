package com.yixiang.api.article.service;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yixiang.api.article.mapper.ArticlePraiseMapper;
import com.yixiang.api.article.pojo.ArticleInfo;
import com.yixiang.api.article.pojo.ArticlePraise;
import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.ResponseCode;
import com.yixiang.api.util.Result;
import com.yixiang.api.util.ThreadCache;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class ArticlePraiseComponent {

	@Autowired
	private ArticlePraiseMapper articlePraiseMapper;
	@Autowired
	private ArticleInfoComponent articleInfoComponent;
	
	Logger log=LoggerFactory.getLogger(getClass());
	
	//点赞
	@Transactional
	public void praise(String uuid){
		ArticleInfo article=articleInfoComponent.getArticleInfoByUUID(uuid);
		if(null==article||!article.getState().equals(ArticleInfo.ARTICLE_STATE_ENUM.TONGGUO.getState())){
			log.info("文章信息不存在,uuid="+uuid);
			Result.putValue(ResponseCode.CodeEnum.ARTICLE_NOT_EXISTS);
			return;
		}
		UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
		ArticlePraise info=getArticlePraise(user.getId(), article.getId());
		if(null!=info){
			if(info.getState().equals(Constants.YES)){
				log.info("不可重复点赞,用户："+user.getId()+",文章："+article.getUuid());
				Result.putValue(ResponseCode.CodeEnum.ALREADY_PRAISED_ARTICLE);
				return;
			}
			info.setState(Constants.YES);
			info.setPraiseTime(new Date());
			updateArticlePraise(info);
		}else{
			info=new ArticlePraise();
			info.setArticleId(article.getId());
			info.setUserId(user.getId());
			info.setState(Constants.YES);
			info.setPraiseTime(new Date());
			insertSelective(info);
		}
		//累加文章点赞数
		articleInfoComponent.addPraises(article.getId(), 1);
	}
	
	//取消点赞
	@Transactional
	public void cancelPraise(String uuid){
		ArticleInfo article=articleInfoComponent.getArticleInfoByUUID(uuid);
		if(null==article||!article.getState().equals(ArticleInfo.ARTICLE_STATE_ENUM.TONGGUO.getState())){
			log.info("文章信息不存在,uuid="+uuid);
			Result.putValue(ResponseCode.CodeEnum.ARTICLE_NOT_EXISTS);
			return;
		}
		UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
		ArticlePraise info=getArticlePraise(user.getId(), article.getId());
		if(null==info){
			log.info("尚未点赞该文章,用户："+user.getId()+",文章："+article.getId());
			Result.putValue(ResponseCode.CodeEnum.PRAISE_NOT_YET);
			return;
		}
		if(info.getState().equals(Constants.NO)){
			log.info("不可重复取消点赞,用户："+user.getId()+",文章："+article.getId());
			Result.putValue(ResponseCode.CodeEnum.ALREADY_CANCELED_PRAISE);
			return;
		}
		info.setState(Constants.NO);
		info.setCancelTime(new Date());
		updateArticlePraise(info);
		//累加文章点赞数
		articleInfoComponent.addPraises(article.getId(), -1);
	}
	
	//获取文章点赞信息
	public ArticlePraise getArticlePraise(Integer id){
		if(null!=id&&id>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", id);
			List<ArticlePraise> praises=selectByExample(example);
			return praises.size()>0?praises.get(0):null;
		}
		return null;
	}
	
	//获取文章点赞信息
	public ArticlePraise getArticlePraise(Integer userId,Integer articleId){
		if(null!=userId&&userId>0&&null!=articleId&&articleId>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("user_id", userId).andEqualTo("article_id", articleId);
			List<ArticlePraise> praises=selectByExample(example);
			return praises.size()>0?praises.get(0):null;
		}
		return null;
	}
	
	//更新文章点赞信息
	public int updateArticlePraise(ArticlePraise praise){
		if(null!=praise.getId()&&praise.getId()>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", praise.getId());
			return updateByExampleSelective(praise, example);
		}
		return 0;
	}
	
	//计算结果集大小
	public long countByExample(QueryExample example) {
		return articlePraiseMapper.countByExample(example);
	}

	//保存
	public int insertSelective(ArticlePraise record) {
		return articlePraiseMapper.insertSelective(record);
	}

	//获取结果集
	public List<ArticlePraise> selectByExample(QueryExample example) {
		return articlePraiseMapper.selectByExample(example);
	}

	//更新
	public int updateByExampleSelective(ArticlePraise record, QueryExample example) {
		return articlePraiseMapper.updateByExampleSelective(record, example);
	}

}

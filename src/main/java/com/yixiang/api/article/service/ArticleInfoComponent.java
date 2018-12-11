package com.yixiang.api.article.service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSONObject;
import com.feilong.core.Validator;
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.article.mapper.ArticleInfoMapper;
import com.yixiang.api.article.pojo.ArticleInfo;
import com.yixiang.api.article.pojo.ArticlePraise;
import com.yixiang.api.brand.service.BrandCarComponent;
import com.yixiang.api.coin.service.CoinHistoryComponent;
import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.user.service.UserInfoComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.OSSUtil;
import com.yixiang.api.util.ResponseCode;
import com.yixiang.api.util.Result;
import com.yixiang.api.util.ThreadCache;
import com.yixiang.api.util.pojo.BrowseLog;
import com.yixiang.api.util.pojo.QueryExample;
import com.yixiang.api.util.pojo.ShareLog;
import com.yixiang.api.util.service.BrowseLogComponent;
import com.yixiang.api.util.service.ShareLogComponent;

@Service
public class ArticleInfoComponent {

	@Autowired
	private ArticleInfoMapper articleInfoMapper;
	@Autowired
	private UserInfoComponent userInfoComponent;
	@Autowired
	private BrandCarComponent brandCarComponent;
	@Autowired
	private ArticlePraiseComponent articlePraiseComponent;
	@Autowired
	private CoinHistoryComponent coinHistoryComponent;
	@Autowired
	private BrowseLogComponent browseLogComponent;
	@Autowired
	private ShareLogComponent shareLogComponent;
	
	Logger log=LoggerFactory.getLogger(getClass());
	
	//发布新帖子
	@Transactional
	public void saveArticle(ArticleInfo article,MultipartFile[] files,MultipartFile img){
		UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
		//多媒体资源
		String names=OSSUtil.uploadMedia("article_oss_config", files);
		if(StringUtils.isNotEmpty(names)){
			article.setMedia(names);
			article.setIcon(article.getMedia().split(",")[0]);
		}
		//帖子icon
		names=OSSUtil.uploadMedia("article_oss_config", img);
		if(StringUtils.isNotEmpty(names)){
			article.setIcon(names);
		}
		article.setUserId(user.getId());
		article.setUuid(DataUtil.buildUUID());
		article.setSource(ArticleInfo.ARTICLE_SOURCE_ENUM.PERSONAL.getSource());
		article.setCreateTime(new Date());
		insertSelective(article);
		//赠送会员积分
		coinHistoryComponent.giveCoins(user.getId(), user.getCoins(), "publish_article");
	}
	
	//上传帖子图片资源
	public String[] publishArticleMedia(MultipartFile[] files){
		String names=OSSUtil.uploadMedia("article_oss_config", files);
		return StringUtils.isNotEmpty(names)?names.split(","):null;
	}
	
	//加载帖子详情
	public Map<String,Object> getArticleDetail(String uuid){
		ArticleInfo article=getArticleInfoByUUID(uuid);
		if(null==article||!article.getState().equals(ArticleInfo.ARTICLE_STATE_ENUM.TONGGUO.getState())){
			log.info("文章信息不存在,uuid="+uuid);
			Result.putValue(ResponseCode.CodeEnum.ARTICLE_NOT_EXISTS);
			return null;
		}
		//组装文章信息
		List<String> media=null;
		JSONObject json=JSONObject.parseObject(Redis.use().get("article_oss_config"));
		String domain=json.getString("domain")+"/"+json.getString("imgDir")+"/";
		if(StringUtils.isNotEmpty(article.getMedia())){
			media=Arrays.asList(article.getMedia().split(",")).stream().filter(i->StringUtils.isNotEmpty(i))
					.map(i->domain+i).collect(Collectors.toList());
		}
		//是否已点赞
		Integer isPraise=0;
		UserInfo current=userInfoComponent.attemptLogin();
		if(null!=current){
			ArticlePraise praise=articlePraiseComponent.getArticlePraise(current.getId(), article.getId());
			isPraise=null!=praise&&praise.getState().equals(Constants.YES)?1:0;
		}
		Map<String,Object> articleMap=DataUtil.mapOf("uuid",uuid,"source",article.getSource(),"title",article.getTitle(),"content",article.getContent()
				,"createTime",DataUtil.getTimeFormatText(article.getCreateTime()),"visitors",article.getVisitors()
				,"comments",article.getComments(),"praises",article.getPraises(),"media",media,"isPraise",isPraise
				,"icon",StringUtils.isNotEmpty(article.getIcon())?domain+article.getIcon():article.getIcon());
		//组装用户信息
		UserInfo user=userInfoComponent.getUserInfo(article.getUserId(),false);
		json=JSONObject.parseObject(Redis.use().get("user_oss_config"));
		String domain1=json.getString("domain")+"/"+json.getString("imgDir")+"/";
		Map<String,Object> userMap=null;
		if(null!=user){
			userMap=DataUtil.mapOf("uuid",user.getUuid(),"userName",user.getUserName()
					,"headImg",StringUtils.isNotEmpty(user.getHeadImg())?domain1+user.getHeadImg():user.getHeadImg());
		}
		//组装分享配置
		json=JSONObject.parseObject(Redis.use().get("article_share_config"));
		Map<String,Object> shareMap=DataUtil.mapOf("title",article.getTitle().length()>json.getIntValue("title_length")
				?article.getTitle().substring(0, json.getIntValue("title_length")):article.getTitle(),"content"
				,article.getContent().length()>json.getIntValue("desc_length")?article.getContent().substring(0, json.getIntValue("desc_length"))
				:article.getContent(),"img",articleMap.get("icon"),"url",(article.getSource().equals(ArticleInfo.ARTICLE_SOURCE_ENUM.SYSTEM.getSource())
				?json.getString("system_article_info_h5"):json.getString("personal_article_info_h5"))+article.getUuid());
		if(DataUtil.isEmpty(shareMap.get("title"))){
			shareMap.put("title", shareMap.get("content"));
		}
		//累加文章阅读数
		addVisitors(article.getId(), 1);
		//记录浏览日志
		browseLogComponent.saveBrowseLog(BrowseLog.CATEGORY_TYPE_ENUM.ARTICLE.getCategory(), article.getId());
		return DataUtil.mapOf("article",articleMap,"user",userMap,"car",brandCarComponent.getBrandCarDetail(article.getCarId()),"shareMap",shareMap);
	}
	
	//加载用户帖子
	public List<Map<String,Object>> queryUserArticles(String uuid,Integer page){
		UserInfo user=userInfoComponent.getUserInfoByUUID(uuid);
		if(null==user){
			log.info("用户信息不存在,uuid="+uuid);
			Result.putValue(ResponseCode.CodeEnum.USER_NOT_EXISTS);
			return null;
		}
		JSONObject json=JSONObject.parseObject(Redis.use().get("user_article_list_config"));
		Map<String,Object> param=DataUtil.mapOf("userId",user.getId(),"offset",(page-1)*json.getInteger("size")
				,"limit",json.getInteger("size"),"states",Arrays.asList(ArticleInfo.ARTICLE_STATE_ENUM.DAISHENHE.getState()
						,ArticleInfo.ARTICLE_STATE_ENUM.TONGGUO.getState(),ArticleInfo.ARTICLE_STATE_ENUM.BUTONGGUO.getState()));
		List<Map<String,Object>> result=queryArticles(param);
		UserInfo current=userInfoComponent.attemptLogin();
		JSONObject share=JSONObject.parseObject(Redis.use().get("article_share_config"));
		result.stream().forEach(r->{
			//是否已点赞
			Integer isPraise=0;
			if(null!=current){
				ArticleInfo article=getArticleInfoByUUID(r.get("uuid").toString());
				ArticlePraise praise=articlePraiseComponent.getArticlePraise(current.getId(), article.getId());
				isPraise=null!=praise&&praise.getState().equals(Constants.YES)?1:0;
				r.put("isPraise", isPraise);
			}
			//组装分享配置
			String title=!DataUtil.isEmpty(r.get("title"))?r.get("title").toString():"";
			String content=!DataUtil.isEmpty(r.get("content"))?r.get("content").toString():"";
			String icon=!DataUtil.isEmpty(r.get("icon"))?r.get("icon").toString():"";
			Integer source=Integer.valueOf(r.get("source").toString());
			Map<String,Object> shareMap=DataUtil.mapOf("title",title.length()>share.getIntValue("title_length")
					?title.substring(0, share.getIntValue("title_length")):title,"content"
					,content.length()>share.getIntValue("desc_length")?content.substring(0, share.getIntValue("desc_length"))
					:content,"img",icon,"url",source.equals(ArticleInfo.ARTICLE_SOURCE_ENUM.SYSTEM.getSource())
					?share.getString("system_article_h5"):share.getString("personal_article_h5")+r.get("uuid"));
			if(DataUtil.isEmpty(shareMap.get("title"))){
				shareMap.put("title", shareMap.get("content"));
			}
			r.put("shareMap", shareMap);
		});
		return result;
	}
	
	//删除用户帖子
	@Transactional
	public void deleteArticle(String uuid){
		ArticleInfo article=getArticleInfoByUUID(uuid);
		if(null==article||!article.getState().equals(ArticleInfo.ARTICLE_STATE_ENUM.TONGGUO.getState())){
			log.info("文章信息不存在,uuid="+uuid);
			Result.putValue(ResponseCode.CodeEnum.ARTICLE_NOT_EXISTS);
			return;
		}
		UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
		if(!article.getUserId().equals(user.getId())){
			log.info("不能删除非本人的文章");
			Result.putValue(ResponseCode.CodeEnum.ARTICLE_NOT_MINE);
			return;
		}
		article.setState(ArticleInfo.ARTICLE_STATE_ENUM.GERENSHANCHU.getState());
		updateArticleInfo(article);
	}
	
	//分享文章成功回调
	@Transactional
	public void shareSuccessCallback(String uuid){
		ArticleInfo article=getArticleInfoByUUID(uuid);
		if(null==article||!article.getState().equals(ArticleInfo.ARTICLE_STATE_ENUM.TONGGUO.getState())){
			log.info("文章信息不存在,uuid="+uuid+",state="+(null!=article?article.getState():null));
			Result.putValue(ResponseCode.CodeEnum.ARTICLE_NOT_EXISTS);
			return;
		}
		UserInfo current=userInfoComponent.attemptLogin();
		if(null!=current){
			//赠送会员积分
			coinHistoryComponent.giveCoins(current.getId(), current.getCoins(), "share_article");
		}
		//记录分享日志
		shareLogComponent.saveShareLog(ShareLog.CATEGORY_TYPE_ENUM.ARTICLE.getCategory(), article.getId());
	}
	
	//加载品牌热帖
	public List<Map<String,Object>> queryHotBrandArticles(Integer brandId,Integer page){
		JSONObject json=JSONObject.parseObject(Redis.use().get("hot_brand_article_config"));
		Map<String,Object> param=DataUtil.mapOf("brandId",brandId,"offset",(page-1)*json.getInteger("size")
				,"limit",json.getInteger("size"),"state",ArticleInfo.ARTICLE_STATE_ENUM.TONGGUO.getState(),"orderBy","t.visitors desc");
		return queryArticles(param);
	}
	
	//加载最新帖子列表
	public List<Map<String,Object>> queryNewArticles(Integer brandId,Integer page){
		JSONObject json=JSONObject.parseObject(Redis.use().get("article_list_config"));
		Map<String,Object> param=DataUtil.mapOf("brandId",brandId,"offset",(page-1)*json.getInteger("size")
				,"limit",json.getInteger("size"),"state",ArticleInfo.ARTICLE_STATE_ENUM.TONGGUO.getState()
				,"category",ArticleInfo.ARTICLE_CATEGORY_ENUM.NORMAL.getCategory());
		return queryArticles(param);
	}
	
	//加载热门帖子列表
	public List<Map<String,Object>> queryHotArticles(Integer brandId,Integer page){
		JSONObject json=JSONObject.parseObject(Redis.use().get("article_list_config"));
		Map<String,Object> param=DataUtil.mapOf("brandId",brandId,"offset",(page-1)*json.getInteger("size")
				,"limit",json.getInteger("size"),"state",ArticleInfo.ARTICLE_STATE_ENUM.TONGGUO.getState()
				,"category",ArticleInfo.ARTICLE_CATEGORY_ENUM.NORMAL.getCategory()
				,"orderBy","t.top_time desc,t.visitors desc");
		return queryArticles(param);
	}
	
	//加载问题帖子列表
	public List<Map<String,Object>> queryProblemArticles(Integer brandId,Integer page){
		JSONObject json=JSONObject.parseObject(Redis.use().get("article_list_config"));
		Map<String,Object> param=DataUtil.mapOf("brandId",brandId,"offset",(page-1)*json.getInteger("size")
				,"limit",json.getInteger("size"),"state",ArticleInfo.ARTICLE_STATE_ENUM.TONGGUO.getState()
				,"category",ArticleInfo.ARTICLE_CATEGORY_ENUM.PROBLEM.getCategory());
		return queryArticles(param);
	}
	
	//依据条件查询文章
	public List<Map<String,Object>> queryArticles(Map<String,Object> param){
		JSONObject json=JSONObject.parseObject(Redis.use().get("article_oss_config"));
		String articleDomain=json.getString("domain")+"/"+json.getString("imgDir")+"/";
		json=JSONObject.parseObject(Redis.use().get("brand_oss_config"));
		String brandDomain=json.getString("domain")+"/"+json.getString("imgDir")+"/";
		json=JSONObject.parseObject(Redis.use().get("user_oss_config"));
		String userDomain=json.getString("domain")+"/"+json.getString("imgDir")+"/";
		List<Map<String,Object>> result=articleInfoMapper.queryArticles(param);
		JSONObject shareJson=JSONObject.parseObject(Redis.use().get("article_share_config"));
		result.stream().forEach(a->{
			if(Validator.isNotNullOrEmpty(a.get("carIcon"))){
				a.put("carIcon", brandDomain+a.get("carIcon"));
			}
			if(Validator.isNotNullOrEmpty(a.get("icon"))){
				a.put("icon", articleDomain+a.get("icon"));
			}
			if(Validator.isNotNullOrEmpty(a.get("headImg"))){
				a.put("headImg", userDomain+a.get("headImg"));
			}
			a.put("createTime", DataUtil.getTimeFormatText((Date)a.get("createTime")));
			a.put("carPrice", !DataUtil.isEmpty(a.get("carPrice"))?a.get("carPrice").toString()+Constants.CAR_PRICE_UNIT:a.get("carPrice"));
			//组装分享配置
			String title=!DataUtil.isEmpty(a.get("title"))?a.get("title").toString():"";
			String content=!DataUtil.isEmpty(a.get("content"))?a.get("content").toString():"";
			Integer source=Integer.valueOf(a.get("source").toString());
			Map<String,Object> shareMap=DataUtil.mapOf("title",title.length()>shareJson.getIntValue("title_length")
					?title.substring(0, shareJson.getIntValue("title_length")):title,"content"
					,content.length()>shareJson.getIntValue("desc_length")?content.substring(0, shareJson.getIntValue("desc_length"))
					:content,"img",a.get("icon"),"url",(source.equals(ArticleInfo.ARTICLE_SOURCE_ENUM.SYSTEM.getSource())
					?shareJson.getString("system_article_list_h5"):shareJson.getString("personal_article_list_h5"))+a.get("uuid"));
			if(DataUtil.isEmpty(shareMap.get("title"))){
				shareMap.put("title", shareMap.get("content"));
			}
			a.put("shareMap", shareMap);
		});
		return result;
	}
	
	//累加文章阅读数
	@Transactional
	public void addVisitors(Integer articleId,Integer interval){
		ArticleInfo article=getArticleInfo(articleId, true);
		if(null!=article){
			article.setVisitors(article.getVisitors()+interval);
			updateArticleInfo(article);
		}
	}
	
	//累加文章评论数
	@Transactional
	public void addComments(Integer articleId,Integer interval){
		ArticleInfo article=getArticleInfo(articleId, true);
		if(null!=article){
			article.setComments(article.getComments()+interval);
			updateArticleInfo(article);
		}
	}
	
	//累加文章点赞数
	@Transactional
	public void addPraises(Integer articleId,Integer interval){
		ArticleInfo article=getArticleInfo(articleId, true);
		if(null!=article){
			article.setPraises(article.getPraises()+interval);
			updateArticleInfo(article);
		}
	}
	
	//获取文章信息
	public ArticleInfo getArticleInfo(Integer id,boolean lock){
		if(null!=id&&id>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", id);
			example.setLock(lock);
			List<ArticleInfo> articles=selectByExample(example);
			return articles.size()>0?articles.get(0):null;
		}
		return null;
	}
	
	//获取文章信息
	public ArticleInfo getArticleInfoByUUID(String uuid){
		if(StringUtils.isNotEmpty(uuid)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("uuid", uuid);
			List<ArticleInfo> articles=selectByExample(example);
			return articles.size()>0?articles.get(0):null;
		}
		return null;
	}
	
	//更新文章信息
	public int updateArticleInfo(ArticleInfo article){
		if(null!=article.getId()&&article.getId()>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", article.getId());
			return updateByExampleSelective(article, example);
		}
		return 0;
	}
	
	//计算结果集大小
	public long countByExample(QueryExample example) {
		return articleInfoMapper.countByExample(example);
	}

	//保存
	public int insertSelective(ArticleInfo record) {
		return articleInfoMapper.insertSelective(record);
	}
	
	//获取结果集
	public List<ArticleInfo> selectByExample(QueryExample example) {
		return articleInfoMapper.selectByExample(example);
	}

	//更新
	public int updateByExampleSelective(ArticleInfo record, QueryExample example) {
		return articleInfoMapper.updateByExampleSelective(record, example);
	}

}

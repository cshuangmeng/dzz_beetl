package com.yixiang.api.util.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.OSSUtil;
import com.yixiang.api.util.mapper.ActivityAdMapper;
import com.yixiang.api.util.pojo.ActivityAd;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class ActivityAdComponent {
	
	@Autowired
	private ActivityAdMapper activityAdMapper;
	
	//获取活动广告
	public List<Map<Object,Object>> queryActivityAds(Integer category,Integer page,Integer limit){
		JSONObject json=JSONObject.parseObject(Redis.use().get("activity_oss_config"));
		List<Map<Object,Object>> ads=queryActivityAdsByCategory(category,page,limit).stream().map(a->{
			String destination=a.getDestination();
			Map<Object,Object> map=DataUtil.mapOf("display",a.getDisplay(),"title",a.getTitle(),"createTime",a.getCreateTime()
					,"img",OSSUtil.joinOSSFileUrl(a.getImg(),json));
			if(StringUtils.isNotEmpty(destination)&&StringUtils.isNotEmpty(a.getParameters())){
				if(a.getDisplay().equals(ActivityAd.DISPLAY_TYPE_ENUM.NATIVE.getType())){
					List<Map<Object,Object>> parameters=Arrays.asList(a.getParameters().split("&"))
							.stream().map(p->DataUtil.mapOf(p.split("=")[0],p.split("=")[1])).collect(Collectors.toList());
					map.put("parameters", parameters);
				}else if(a.getDisplay().equals(ActivityAd.DISPLAY_TYPE_ENUM.H5.getType())){
					destination+="?"+a.getParameters();
				}
			}
			map.put("destination",destination);
			return map;
		}).collect(Collectors.toList());
		return ads;
	}
	
	//获取活动广告
	public ActivityAd getActivityAd(Integer id){
		if(null!=id&&id>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", id);
			List<ActivityAd> list=selectByExample(example);
			return list.size()>0?list.get(0):null;
		}
		return null;
	}
	
	//获取活动广告
	public List<ActivityAd> queryActivityAdsByCategory(Integer category,Integer page,Integer limit){
		QueryExample example=new QueryExample();
		example.and().andEqualTo("category", category).andEqualTo("state", ActivityAd.AD_STATE_ENUM.ENABLED.getState());
		example.setOrderByClause("sort,create_time desc");
		if(null!=limit&&limit>0){
			example.setOffset(limit*(page>0?page-1:0));
			example.setLimit(limit);
		}
		return selectByExample(example);
	}
	
	//更新活动广告
	public int updateActivityAd(ActivityAd ad){
		if(null!=ad.getId()&&ad.getId()>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", ad.getId());
			return updateByExampleSelective(ad, example);
		}
		return 0;
	}

	//获取结果集大小
	public long countByExample(QueryExample example) {
		return activityAdMapper.countByExample(example);
	}

	//保存
	public int insertSelective(ActivityAd record) {
		return activityAdMapper.insertSelective(record);
	}

	//获取结果集
	public List<ActivityAd> selectByExample(QueryExample example) {
		return activityAdMapper.selectByExample(example);
	}

	//更新
	public int updateByExampleSelective(ActivityAd record, QueryExample example) {
		return activityAdMapper.updateByExampleSelective(record, example);
	}

}

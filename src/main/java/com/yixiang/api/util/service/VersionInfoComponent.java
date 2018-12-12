package com.yixiang.api.util.service;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.ThreadCache;
import com.yixiang.api.util.mapper.VersionInfoMapper;
import com.yixiang.api.util.pojo.QueryExample;
import com.yixiang.api.util.pojo.VersionInfo;

@Service
public class VersionInfoComponent {

	@Autowired
	private VersionInfoMapper versionInfoMapper;
	
	Logger log=LoggerFactory.getLogger(getClass());
	
	//获取版本信息
	public VersionInfo getVersionInfo(Integer id){
		if(null!=id&&id>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", id);
			List<VersionInfo> result=selectByExample(example);
			return result.size()>0?result.get(0):null;
		}
		return null;
	}
	
	//检查用户是否需要升级app病提示升级信息
	public VersionInfo checkVersionInfo(){
		Map<String,Object> http=ThreadCache.getHttpData();
		String system=!DataUtil.isEmpty(http.get(Constants.SYSTEM))?http.get(Constants.SYSTEM).toString():"";
		if(StringUtils.isEmpty(system)){
			log.info("未获取到客户端版本信息,放弃检查,system="+system);
			return null;
		}
		VersionInfo version=getNewestVersionInfo();
		if(null==version){
			log.info("暂未配置版本升级信息,放弃检查,version="+version+",system="+system);
			return null;
		}
		system=system.split("_").length>1?system.split("_")[1]:system;
		if(system.equals(version.getVersion())){
			log.info("已是最新版本,无需升级,"+JSONObject.toJSONString(version)+","+JSONObject.toJSONString(ThreadCache.getHttpData()));
			return null;
		}else{
			log.info("非最新版本,提示升级,"+JSONObject.toJSONString(version)+","+JSONObject.toJSONString(ThreadCache.getHttpData()));
			return version;
		}
	}
	
	//获取最新版本信息
	public VersionInfo getNewestVersionInfo(){
		QueryExample example=new QueryExample();
		example.and().andEqualTo("state", Constants.YES);
		example.setOrderByClause("create_time desc,id desc");
		List<VersionInfo> result=selectByExample(example);
		return result.size()>0?result.get(0):null;
	}
	
	//更新版本信息
	public void updateVersionInfo(VersionInfo version){
		if(null!=version&&null!=version.getId()&&version.getId()>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", version.getId());
			updateByExampleSelective(version, example);
		}
	}
	
	//获取结果集大小
	public long countByExample(QueryExample example) {
		return versionInfoMapper.countByExample(example);
	}

	//保存
	public int insertSelective(VersionInfo record) {
		return versionInfoMapper.insertSelective(record);
	}

	//获取结果集
	public List<VersionInfo> selectByExample(QueryExample example) {
		return versionInfoMapper.selectByExample(example);
	}

	//更新
	public int updateByExampleSelective(VersionInfo record, QueryExample example) {
		return versionInfoMapper.updateByExampleSelective(record, example);
	}

}

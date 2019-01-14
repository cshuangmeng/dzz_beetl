package com.yixiang.api.user.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSONObject;
import com.feilong.core.DatePattern;
import com.feilong.core.date.DateUtil;
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.user.mapper.MessageHistoryMapper;
import com.yixiang.api.user.pojo.MessageHistory;
import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.OSSUtil;
import com.yixiang.api.util.ThreadCache;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class MessageHistoryComponent {
	
	@Autowired
	private MessageHistoryMapper messageHistoryMapper;
	
	//发布留言
	@Transactional
	public void saveMessageHistory(MessageHistory message,MultipartFile[] files){
		UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
		if(null!=files){
			StringBuilder names=new StringBuilder();
			for(MultipartFile file:files){
				if(null!=file&&!file.isEmpty()){
					String saveName=new Date().getTime()+DataUtil.createNums(7);
					saveName+=file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
					try {
						if(OSSUtil.uploadFileToOSS(file.getInputStream(), saveName, "user_oss_config")){
							names.append(names.length()>0?","+saveName:saveName);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			message.setMedia(names.toString());
		}
		message.setUserId(user.getId());
		message.setCreateTime(new Date());
		insertSelective(message);
	}
	
	//加载历史留言
	public List<List<Map<String,Object>>> queryMessageHistorys(){
		UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
		QueryExample example=new QueryExample();
		example.and().andEqualTo("user_id", user.getId()).andIn("state", Arrays.asList(MessageHistory.MESSAGE_STATE_ENUM.DAISHENHE.getState()
				,MessageHistory.MESSAGE_STATE_ENUM.TONGGUO.getState(),MessageHistory.MESSAGE_STATE_ENUM.BUTONGGUO.getState()));
		example.setOrderByClause("if(top_id>0,concat(top_id,'_',ref_id),concat(id,'_0'))");
		List<MessageHistory> historys=selectByExample(example);
		Integer id=null;
		List<List<Map<String,Object>>> result=new ArrayList<List<Map<String,Object>>>();
		List<Map<String,Object>> list=new ArrayList<Map<String,Object>>();
		JSONObject json=JSONObject.parseObject(Redis.use().get("user_oss_config"));
		for (Iterator<MessageHistory> iterator = historys.iterator(); iterator.hasNext();) {
			MessageHistory history = iterator.next();
			Integer messageId=history.getTopId()>0?history.getTopId():history.getId();
			Map<String,Object> map=DataUtil.mapOf();
			if(null==id){
				id=messageId;
				result.add(list);
			}else if(!id.equals(messageId)){
				list=new ArrayList<Map<String,Object>>();
				result.add(list);
			}
			//拼装留言信息
			map.put("content", history.getContent());
			map.put("contentType", history.getContentType());
			map.put("source", history.getSource());
			map.put("fullTime", history.getCreateTime());
			map.put("createTime", DateUtil.toString(history.getCreateTime(), DatePattern.COMMON_DATE_AND_TIME_WITHOUT_YEAR_AND_SECOND));
			map.put("media", OSSUtil.joinOSSFileUrl(json, history.getMedia().split(",")));
			list.add(map);
		}
		result.sort((a,b)->((Date)b.get(0).get("fullTime")).compareTo((Date)a.get(0).get("fullTime")));
		return result;
	}
	
	//获取管家留言信息
	public MessageHistory getMessageHistory(Integer id){
		if(null!=id&&id>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", id);
			List<MessageHistory> list=selectByExample(example);
			return list.size()>0?list.get(0):null;
		}
		return null;
	}
	
	//更新管家留言信息
	public int updateMessageHistory(MessageHistory info){
		if(null!=info.getId()&&info.getId()>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", info.getId());
			return updateByExampleSelective(info, example);
		}
		return 0;
	}

	//计算结果集大小
	public long countByExample(QueryExample example) {
		return messageHistoryMapper.countByExample(example);
	}

	//保存
	public int insertSelective(MessageHistory record) {
		return messageHistoryMapper.insertSelective(record);
	}

	//获取结果集
	public List<MessageHistory> selectByExample(QueryExample example) {
		return messageHistoryMapper.selectByExample(example);
	}

	//更新
	public int updateByExampleSelective(MessageHistory record, QueryExample example) {
		return messageHistoryMapper.updateByExampleSelective(record, example);
	}

}

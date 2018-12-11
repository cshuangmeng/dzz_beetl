package com.yixiang.api.util.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.yixiang.api.util.Constants;
import com.yixiang.api.util.mapper.LabelInfoMapper;
import com.yixiang.api.util.pojo.LabelInfo;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class LabelInfoComponent {

	@Autowired
	private LabelInfoMapper labelInfoMapper;
	
	//获取指定标签
	public List<LabelInfo> queryLabelInfos(Integer parentId){
		if(null!=parentId&&parentId>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("parent_id", parentId).andEqualTo("state", Constants.YES);
			example.setOrderByClause("sort,create_time desc");
			return labelInfoMapper.selectByExample(example);
		}
		return null;
	}
	
}

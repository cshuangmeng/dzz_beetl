package com.yixiang.api.article.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.yixiang.api.article.pojo.ArticlePraise;
import com.yixiang.api.util.pojo.QueryExample;

public interface ArticlePraiseMapper {

	long countByExample(QueryExample example);

	int insertSelective(ArticlePraise record);

	List<ArticlePraise> selectByExample(QueryExample example);

	int updateByExampleSelective(@Param("record") ArticlePraise record, @Param("example") QueryExample example);

}
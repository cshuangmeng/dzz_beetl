package com.yixiang.api.brand.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.yixiang.api.brand.pojo.BrandCar;
import com.yixiang.api.util.pojo.QueryExample;

public interface BrandCarMapper {

	long countByExample(QueryExample example);

	int insertSelective(BrandCar record);

	List<BrandCar> selectByExample(QueryExample example);

	int updateByExampleSelective(@Param("record") BrandCar record, @Param("example") QueryExample example);

}
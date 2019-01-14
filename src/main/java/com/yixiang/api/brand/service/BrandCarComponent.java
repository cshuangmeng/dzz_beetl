package com.yixiang.api.brand.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.brand.mapper.BrandCarMapper;
import com.yixiang.api.brand.pojo.BrandCar;
import com.yixiang.api.brand.pojo.BrandInfo;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.OSSUtil;
import com.yixiang.api.util.pojo.BrowseLog;
import com.yixiang.api.util.pojo.QueryExample;
import com.yixiang.api.util.pojo.ShareLog;
import com.yixiang.api.util.service.BrowseLogComponent;
import com.yixiang.api.util.service.ShareLogComponent;

@Service
public class BrandCarComponent {

	@Autowired
	private BrandCarMapper brandCarMapper;
	@Autowired
	private BrandInfoComponent brandInfoComponent;
	@Autowired
	private BrowseLogComponent browseLogComponent;
	@Autowired
	private ShareLogComponent shareLogComponent;
	
	Logger log=LoggerFactory.getLogger(getClass());
	
	//下发车型查询可选条件
	public JSONObject getBrandCarQueryItems(){
		JSONObject json=JSONObject.parseObject(Redis.use().get("brand_car_query_items"));
		return json;
	}
	
	//根据查询条件检索车型
	public List<Map<Object,Object>> queryBrandCars(String price,String car,String batteryLife
			,Integer source,Integer brandId,Integer page){
		QueryExample example=new QueryExample();
		example.and().andEqualTo("source", source).andEqualTo("brand_id", brandId);
		if(source.equals(BrandCar.CAR_SOURCE_ENUM.NEW_CAR.getSource())){
			if(StringUtils.isNotEmpty(price)){
				if(price.contains("-")){
					example.and().andBetween("price", Float.valueOf(price.split("-")[0]), Float.valueOf(price.split("-")[1]));
				}else{
					example.and().andGreaterThanOrEqualTo("price", price);
				}
			}
			if(StringUtils.isNotEmpty(batteryLife)){
				if(price.contains("-")){
					example.and().andBetween("battery_life", Float.valueOf(batteryLife.split("-")[0]), Float.valueOf(batteryLife.split("-")[1]));
				}else{
					example.and().andGreaterThanOrEqualTo("battery_life", batteryLife);
				}
			}
			if(StringUtils.isNotEmpty(car)){
				example.and().andIn("car_type", Arrays.asList(car.split(",")));
			}
		}
		Integer limit=JSONObject.parseObject(Redis.use().get("brand_car_list_config")).getInteger("size");
		Integer offset=(page>0?page-1:0)*limit;
		example.and().andEqualTo("state", BrandCar.CAR_STATE_ENUM.ENABLED.getState());
		example.setOrderByClause("create_time desc");
		example.setOffset(offset);
		example.setLimit(limit);
		//拼装返回结果
		List<Map<Object,Object>> result=selectByExample(example).stream().map(c->joinBrandCarMap(c)).collect(Collectors.toList());
		return result;
	}
	
	//获取车型详情
	public Map<Object,Object> getBrandCarDetail(Integer id){
		BrandCar car=getBrandCar(id);
		if(null==car||!car.getState().equals(BrandCar.CAR_STATE_ENUM.ENABLED.getState())){
			log.info("未找到车型信息或车型状态不正常,BrandCar.id="+id+",BrandCar.state="+(null!=car?car.getState():null));
			return null;
		}
		Map<Object,Object> result=joinBrandCarMap(car);
		//分享配置
		JSONObject share=JSONObject.parseObject(Redis.use().get("share_config"));
		if(share.containsKey("brand_car_share")){
			share=share.getJSONObject("brand_car_share");
			share.put("title", String.format(share.getString("title"), car.getCar(), car.getPrice()));
			result.put("shareMap", share);
		}
		//记录浏览日志
		browseLogComponent.saveBrowseLog(BrowseLog.CATEGORY_TYPE_ENUM.CAR.getCategory(), car.getId());
		return result;
	}
	
	//分享车型成功回调
	@Transactional
	public void shareSuccessCallback(Integer id){
		BrandCar car=getBrandCar(id);
		if(null==car||!car.getState().equals(BrandCar.CAR_STATE_ENUM.ENABLED.getState())){
			log.info("未找到车型信息或车型状态不正常,BrandCar.id="+id+",BrandCar.state="+(null!=car?car.getState():null));
			return;
		}
		//记录分享日志
		shareLogComponent.saveShareLog(ShareLog.CATEGORY_TYPE_ENUM.CAR.getCategory(), car.getId());
	}
	
	//获取精选车型
	public List<Map<Object,Object>> querySpecialCars(Integer source){
		JSONObject json=JSONObject.parseObject(Redis.use().get("special_brand_car_config"));
		QueryExample example=new QueryExample();
		example.and().andEqualTo("is_special", Constants.YES).andEqualTo("state", BrandCar.CAR_STATE_ENUM.ENABLED.getState())
			.andEqualTo("source", source);
		example.setOrderByClause("top_time desc,sort,id");
		example.setLimit(json.getInteger("size"));
		List<Map<Object,Object>> result=selectByExample(example).stream().map(c->joinBrandCarMap(c)).collect(Collectors.toList());
		return result;
	}
	
	//获取所有车型
	public List<Map<Object,Object>> queryAllCars(Map<String,Object> param){
		int page=!DataUtil.isEmpty(param.get("page"))?Integer.parseInt(param.get("page").toString()):0;
		int limit=!DataUtil.isEmpty(param.get("limit"))?Integer.parseInt(param.get("limit").toString())
				:JSONObject.parseObject(Redis.use().get("brand_car_list_config")).getIntValue("size");
		limit=limit>0?limit:0;
		param.put("state", BrandCar.CAR_STATE_ENUM.ENABLED.getState());
		param.put("orderBy","sort,id");
		param.put("offset", (page>0?page-1:0)*limit);
		param.put("limit", limit);
		List<Map<Object,Object>> result=selectByParam(param).stream().map(c->joinBrandCarMap(c)).collect(Collectors.toList());
		return result;
	}
	
	//获取所有车型并按照首字母品牌分组排序
	public List<Map<Object,Object>> queryAllBrandCars(){
		List<Map<Object,Object>> brandList=brandInfoComponent.queryAllBrands(); 
		List<Map<Object,Object>> cars=queryAllCars(DataUtil.mapOf());
		//按照品牌分组车型
		Map<Integer,List<Map<Object,Object>>> carsMap=cars.stream()
				.collect(Collectors.groupingBy(e->Integer.valueOf(e.get("brandId").toString()),Collectors.toList()));
		List<Map<Object,Object>> brands=carsMap.entrySet().stream().map(e->{
			Map<Object,Object> brand=brandList.stream().filter(b->Integer.valueOf(b.get("id").toString()).equals(e.getKey())).findFirst().get();
			return DataUtil.mapOf("brand",brand.get("brand"),"id",e.getKey(),"icon",brand.get("icon")
					,"firstLetter",DataUtil.getPinYinHeadChar(brand.get("brand").toString()).toUpperCase().charAt(0),"cars",e.getValue());
		}).collect(Collectors.toList());
		//按照首字母分组品牌
		Map<String,List<Map<Object,Object>>> brandsMap=brands.stream()
				.collect(Collectors.groupingBy(e->e.get("firstLetter").toString(),Collectors.toList()));
		List<Map<Object,Object>> result=brandsMap.keySet().stream()
				.map(e->DataUtil.mapOf("firstLetter",e,"dataset",brandsMap.get(e))).collect(Collectors.toList());
		return result;
	}
	
	//拼装返回结果
	public Map<Object,Object> joinBrandCarMap(BrandCar c){
		JSONObject json=JSONObject.parseObject(Redis.use().get("brand_oss_config"));
		BrandInfo brand=brandInfoComponent.getBrandInfo(c.getBrandId());
		return DataUtil.mapOf("id",c.getId(),"car",c.getCar(),"price",c.getPrice()+Constants.CAR_PRICE_UNIT
				,"shopPrice",c.getShopPrice()+Constants.CAR_PRICE_UNIT,"groupPrice",c.getGroupPrice()+Constants.CAR_PRICE_UNIT
				,"batteryLife",c.getBatteryLife()+Constants.DISTANCE_UNIT,"label",c.getLabel(),"brand",brand.getBrand()
				,"category",c.getCategory(),"color", StringUtils.isNotEmpty(c.getColor())?c.getColor().split(","):null,"brandId",c.getBrandId()
				,"detailImgs", StringUtils.isNotEmpty(c.getDetailImgs())?Arrays.asList(c.getDetailImgs().split(",")).stream()
					.map(o->DataUtil.mapOf("img",OSSUtil.joinOSSFileUrl(o, json))).collect(Collectors.toList()):null
				,"paramImgs", StringUtils.isNotEmpty(c.getParamImgs())?Arrays.asList(c.getParamImgs().split(",")).stream()
					.map(o->DataUtil.mapOf("img",OSSUtil.joinOSSFileUrl(o, json))).collect(Collectors.toList()):null
				,"icon",StringUtils.isNotEmpty(c.getIcon())?OSSUtil.joinOSSFileUrl(c.getIcon(), json):null
				,"banner", StringUtils.isNotEmpty(c.getBanner())?Arrays.asList(c.getBanner().split(",")).stream()
					.map(o->DataUtil.mapOf("img",OSSUtil.joinOSSFileUrl(o, json))).collect(Collectors.toList()):null);
	}
	
	//获取汽车信息
	public BrandCar getBrandCar(Integer id){
		if(null!=id&&id>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", id);
			List<BrandCar> cars=selectByExample(example);
			return cars.size()>0?cars.get(0):null;
		}
		return null;
	}
	
	//更新汽车信息
	public int updateBrandCar(BrandCar car){
		if(null!=car.getId()&&car.getId()>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", car.getId());
			return updateByExampleSelective(car, example);
		}
		return 0;
	}
	
	//计算结果集大小
	public long countByExample(QueryExample example) {
		return brandCarMapper.countByExample(example);
	}

	//保存
	public int insertSelective(BrandCar car) {
		return brandCarMapper.insertSelective(car);
	}

	//获取结果集
	public List<BrandCar> selectByExample(QueryExample example) {
		return brandCarMapper.selectByExample(example);
	}
	
	//获取结果集
	public List<BrandCar> selectByParam(Map<String,Object> param) {
		return brandCarMapper.selectByParam(param);
	}

	//更新
	public int updateByExampleSelective(BrandCar car, QueryExample example) {
		return brandCarMapper.updateByExampleSelective(car, example);
	}

}

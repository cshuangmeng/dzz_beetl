package com.yixiang.api.charging.service;

import java.math.BigDecimal;
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
import com.feilong.core.date.DateUtil;
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.charging.mapper.ChargingStationMapper;
import com.yixiang.api.charging.pojo.ChargingStation;
import com.yixiang.api.charging.pojo.ConnectorInfo;
import com.yixiang.api.coin.service.CoinHistoryComponent;
import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.user.service.UserChargingComponent;
import com.yixiang.api.user.service.UserInfoComponent;
import com.yixiang.api.util.ChargeClientBuilder;
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
import com.yixiang.api.util.service.NaviLogComponent;
import com.yixiang.api.util.service.ShareLogComponent;

@Service
public class ChargingStationComponent {

	@Autowired
	private ChargingStationMapper chargingStationMapper;
	@Autowired
	private ChargingCommentComponent chargingCommentComponent;
	@Autowired
	private UserChargingComponent userChargingComponent;
	@Autowired
	private UserInfoComponent userInfoComponent;
	@Autowired
	private CoinHistoryComponent coinHistoryComponent;
	@Autowired
	private BrowseLogComponent browseLogComponent;
	@Autowired
	private NaviLogComponent naviLogComponent;
	@Autowired
	private ShareLogComponent shareLogComponent;
	@Autowired
	private ChargeClientBuilder chargeClientBuilder;
	@Autowired
	private ConnectorInfoComponent connectorInfoComponent;
	@Autowired
	private ChargeOperatorComponent chargeOperatorComponent;
	
	Logger log=LoggerFactory.getLogger(getClass());
	
	//加载充电桩详情
	public Map<String,Object> getChargingDetail(String uuid,BigDecimal lng,BigDecimal lat){
		ChargingStation station=getChargingStationByUUID(uuid);
		if(null==station||!station.getState().equals(ChargingStation.STATION_STATE_ENUM.ENABLED.getState())){
			log.info("充电桩不存在或状态不可用,uuid="+uuid+",state="+(null!=station?station.getState():null));
			Result.putValue(ResponseCode.CodeEnum.STATION_NOT_EXISTS);
			return null;
		}
		//停车费
		String parkingPrice=station.getParkingPrice();
		Integer parkingIsUnderground=station.getIsUnderground();
		//电费
		String electricityPrice=station.getElectricityPrice();
		//充电桩详情图片
		List<String> detailImgs=null;
		JSONObject json=JSONObject.parseObject(Redis.use().get("charging_oss_config"));
		String domain=json.getString("domain")+"/"+json.getString("imgDir")+"/";
		if(StringUtils.isNotEmpty(station.getDetailImgs())){
			detailImgs=Arrays.asList(station.getDetailImgs().split(",")).stream().filter(i->StringUtils.isNotEmpty(i))
					.map(i->domain+i).collect(Collectors.toList());
		}
		//评论总数
		Long commentTotal=chargingCommentComponent.getChargingCommentTotal(station.getId());
		//收藏状态
		Integer isCollect=0;
		UserInfo user=userInfoComponent.attemptLogin();
		if(null!=user){
			isCollect=null!=userChargingComponent.getUserCharging(user.getId(), station.getId())?1:0;
		}
		//最近完成充电用户信息
		JSONObject stationJson=JSONObject.parseObject(Redis.use().get("station_detail_config"));
		QueryExample example=new QueryExample();
		if(null!=user&&StringUtils.isNotEmpty(user.getPhone())){
			example.and().andNotEqualTo("phone", user.getPhone());
		}
		example.setOrderByClause("login_time desc");
		example.setLimit(stationJson.getInteger("used_notice_size"));
		List<Map<Object,Object>> useNotices=userInfoComponent.selectByExample(example).stream().map(u->{
			String phone=u.getPhone().replaceAll(stationJson.getString("phone_format_pattern"), stationJson.getString("phone_replace_txt"));
			String content=String.format(stationJson.getString("used_notice_template"), phone);
			String time=DataUtil.getTimeFormatText(u.getLoginTime());
			return DataUtil.mapOf("content",content,"time",time);
		}).collect(Collectors.toList());
		//组装返回结果
		Map<String,Object> result=DataUtil.mapOf("isPrivate",station.getIsPrivate(),"title",station.getTitle(),"address",station.getAddress()
				,"fastNum",station.getFastNum(),"slowNum",station.getSlowNum(),"distance",DataUtil.getDistanceFormatText(lat.doubleValue()
				,lng.doubleValue(),station.getLat().doubleValue(),station.getLng().doubleValue()),"provider",station.getProvider()
				,"openTime",station.getOpenTime(),"payWay",station.getPayWay(),"parkingPrice",parkingPrice,"commentTotal",commentTotal
				,"lng",station.getLng(),"lat",station.getLat(),"electricityPrice",electricityPrice,"uuid",station.getUuid()
				,"serviceFee",station.getServiceFee()
				,"headImg",StringUtils.isNotEmpty(station.getHeadImg())?domain+station.getHeadImg():station.getHeadImg()
				,"times",station.getTimes(),"phone",station.getTelephone(),"detailImgs",detailImgs,"useNotices",useNotices
				,"freeNum",setFreeNum(station.getFastNum(),station.getSlowNum()),"parkingIsUnderground",parkingIsUnderground
				,"isUnderground",station.getIsUnderground(),"isCollect",isCollect,"remark",station.getRemark());
		//组装分享配置
		json=JSONObject.parseObject(Redis.use().get("station_share_config"));
		Map<String,Object> shareMap=DataUtil.mapOf("title",station.getTitle().length()>json.getIntValue("title_length")
				?station.getTitle().substring(0, json.getIntValue("title_length")):station.getTitle(),"content"
				,station.getAddress().length()>json.getIntValue("desc_length")?station.getAddress().substring(0, json.getIntValue("desc_length"))
				:station.getAddress(),"img",result.get("headImg"),"url",(station.getUserId()>0
				?json.getString("personal_station_h5"):json.getString("system_station_h5"))+station.getUuid());
		if(DataUtil.isEmpty(shareMap.get("title"))){
			shareMap.put("title", shareMap.get("content"));
		}
		result.put("shareMap", shareMap);
		//记录浏览日志
		browseLogComponent.saveBrowseLog(BrowseLog.CATEGORY_TYPE_ENUM.STATION.getCategory(), station.getId());
		return result;
	}
	
	//新建个人充电桩
	@Transactional
	public void editChargingStation(ChargingStation station,MultipartFile[] detailImgs){
		UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
		//检查用户充电桩数量是否已超限额
		JSONObject json=JSONObject.parseObject(Redis.use().get("user_charging_config"));
		if(StringUtils.isEmpty(station.getUuid())&&json.containsKey("max")){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("user_id", user.getId()).andEqualTo("state", ChargingStation.STATION_STATE_ENUM.ENABLED.getState());
			if(selectByExample(example).size()>=json.getInteger("max")){
				log.info("个人充电桩数量已超限额,userId="+user.getId());
				Result.putValue(ResponseCode.CodeEnum.CHARGING_EXCEED_MAX);
				return;
			}
		}
		//更新充电桩校验
		if(StringUtils.isNotEmpty(station.getUuid())){
			ChargingStation cs=getChargingStationByUUID(station.getUuid());
			if(null==cs||!cs.getState().equals(ChargingStation.STATION_STATE_ENUM.ENABLED.getState())){
				log.info("充电桩不存在或状态不可用,uuid="+station.getUuid()+",state="+(null!=station?station.getState():null));
				Result.putValue(ResponseCode.CodeEnum.STATION_NOT_EXISTS);
				return;
			}
			if(!user.getId().equals(cs.getUserId())){
				log.info("不能修改非本人的充电桩,uuid="+station.getUuid());
				Result.putValue(ResponseCode.CodeEnum.STATION_NOT_MINE);
				return;
			}
			station.setId(cs.getId());
		}
		//上传详情图片
		String names=OSSUtil.uploadMedia("charging_oss_config", detailImgs);
		if(StringUtils.isNotEmpty(station.getDetailImgs())){
			station.setHeadImg(station.getDetailImgs().split(",")[0]);
		}else{
			station.setDetailImgs(names);
			station.setHeadImg(station.getDetailImgs().split(",")[0]);
		}
		if(StringUtils.isEmpty(station.getUuid())){//保存
			station.setUserId(user.getId());
			station.setUuid(DataUtil.buildUUID());
			station.setCreateTime(new Date());
			insertSelective(station);
		}else{//更新
			station.setUuid(null);
			station.setUserId(null);
			station.setState(null);
			station.setUpdateTime(new Date());
			updateChargingStation(station);
		}
	}
	
	//上传帖子图片资源
	public String[] publishStationMedia(MultipartFile[] files){
		String names=OSSUtil.uploadMedia("charging_oss_config", files);
		return StringUtils.isNotEmpty(names)?names.split(","):null;
	}
	
	//搜索附近充电桩
	@Transactional
	public List<Map<Object,Object>> queryNearbyStations(BigDecimal lng,BigDecimal lat,Boolean userStation
			,Integer page,boolean isActive){
		JSONObject json=JSONObject.parseObject(Redis.use().get("home_nearby_station_config"));
		List<ChargingStation> stations=queryChargingStations(DataUtil.mapOf("nearby",json.getInteger("distance")
				,"lng",lng.setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue()
				,"lat",lat.setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue()
				,"state",ChargingStation.STATION_STATE_ENUM.ENABLED.getState()
				,"userStation",userStation,"offset",(page-1)*json.getInteger(isActive?"list_size":"home_size")
				,"limit",json.getInteger(isActive?"list_size":"home_size")));
		//组织返回结果
		json=JSONObject.parseObject(Redis.use().get("charging_oss_config"));
		String domain=json.getString("domain")+"/"+json.getString("imgDir")+"/";
		List<Map<Object,Object>> result=stations.stream().map(s->{
			//停车费
			String parkingPrice=s.getParkingPrice();
			//评论总数
			Long commentTotal=chargingCommentComponent.getChargingCommentTotal(s.getId());
			return DataUtil.mapOf("uuid",s.getUuid(),"title",s.getTitle(),"address",s.getAddress(),"provider",s.getProvider()
					,"distance",DataUtil.getDistanceFormatText(s.getDistance().doubleValue()),"payWay",s.getPayWay()
					,"openTime",s.getOpenTime(),"fastNum",s.getFastNum(),"slowNum",s.getSlowNum(),"parkingPrice",parkingPrice
					,"electricityPrice",s.getElectricityPrice(),"serviceFee",s.getServiceFee()
					,"lng",s.getLng(),"lat",s.getLat(),"isPrivate",s.getIsPrivate(),"isUnderground",s.getIsUnderground()
					,"headImg",StringUtils.isNotEmpty(s.getHeadImg())?domain+s.getHeadImg():s.getHeadImg()
					,"freeNum",setFreeNum(s.getFastNum(),s.getSlowNum()),"commentTotal",commentTotal,"source",s.getSource());
		}).collect(Collectors.toList());
		//用户主动触发搜索行为
		if(isActive){
			UserInfo current=userInfoComponent.attemptLogin();
			if(null!=current){
				//更新用户使用充电次数,暂是定位搜索一次算一次
				userInfoComponent.addTimes(current.getId(), 1);
			}
		}
		return result;
	}
	
	//计算空闲充电桩数量,因为缺少实时数据,暂用假数据代替
	public Integer setFreeNum(Integer fastNum,Integer slowNum){
		Integer hour=Integer.valueOf(DateUtil.toString(new Date(), "h"));
		Integer total=fastNum+slowNum;
		Integer freeNum=0;
		if(total>0){
			return hour>total?1:Math.round(total/hour.floatValue());
		}
		return freeNum;
	}
	
	//导航成功回调
	@Transactional
	public void naviSuccessCallback(String uuid){
		ChargingStation station=getChargingStationByUUID(uuid);
		if(null==station||!station.getState().equals(ChargingStation.STATION_STATE_ENUM.ENABLED.getState())){
			log.info("充电桩不存在或状态不可用,uuid="+uuid+",state="+(null!=station?station.getState():null));
			Result.putValue(ResponseCode.CodeEnum.STATION_NOT_EXISTS);
			return;
		}
		UserInfo current=userInfoComponent.attemptLogin();
		if(null!=current){
			//赠送会员积分
			coinHistoryComponent.giveCoins(current.getId(), current.getCoins(), "navigate_station");
		}
		//记录导航日志
		naviLogComponent.saveNaviLog(station.getId());
	}
	
	//分享充电桩成功回调
	@Transactional
	public void shareSuccessCallback(String uuid){
		ChargingStation station=getChargingStationByUUID(uuid);
		if(null==station||!station.getState().equals(ChargingStation.STATION_STATE_ENUM.ENABLED.getState())){
			log.info("充电桩不存在或状态不可用,uuid="+uuid+",state="+(null!=station?station.getState():null));
			Result.putValue(ResponseCode.CodeEnum.STATION_NOT_EXISTS);
			return;
		}
		//记录分享日志
		shareLogComponent.saveShareLog(ShareLog.CATEGORY_TYPE_ENUM.STATION.getCategory(), station.getId());
	}
	
	//搜索充电桩
	public List<ChargingStation> queryChargingStations(Map<String,Object> param){
		return chargingStationMapper.queryChargingStations(param);
	}
	
	//推送充电站状态信息
	public void pushStationStatus(){
		String data=chargeClientBuilder.getPushStationStatusData();
		if(!DataUtil.isJSONObject(data)||!JSONObject.parseObject(data).containsKey("ConnectorStatusInfo")){
			log.info("数据格式不正确,放弃更新,data="+data);
			return;
		}
		//更新充电站状态
		JSONObject json=JSONObject.parseObject(data).getJSONObject("ConnectorStatusInfo");
		if(StringUtils.isNotBlank(json.getString("StationID"))&&StringUtils.isNotBlank(json.getString("Status"))){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("station_id", json.getString("StationID"));
			ChargingStation update=new ChargingStation();
			update.setState(json.getInteger("Status"));
			updateByExampleSelective(update, example);
		}
		//更新充电桩状态
		if(StringUtils.isNotBlank(json.getString("ConnectorID"))&&StringUtils.isNotBlank(json.getString("ConnectorStatus"))){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("connector_id", json.getString("ConnectorID"));
			ConnectorInfo update=new ConnectorInfo();
			update.setState(json.getInteger("ConnectorStatus"));
			connectorInfoComponent.updateByExampleSelective(update, example);
		}
		//同步到其他合作方
		chargeOperatorComponent.pushStationStatus(JSONObject.parseObject(data));
	}
	
	//获取充电站信息
	public ChargingStation getChargingStation(Integer id){
		if(null!=id&&id>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", id);
			List<ChargingStation> stations=selectByExample(example);
			return stations.size()>0?stations.get(0):null;
		}
		return null;
	}
	
	//获取充电站信息
	public ChargingStation getChargingStationByUUID(String uuid){
		if(StringUtils.isNotEmpty(uuid)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("uuid", uuid);
			List<ChargingStation> stations=selectByExample(example);
			return stations.size()>0?stations.get(0):null;
		}
		return null;
	}
	
	//获取充电站信息
	public ChargingStation getChargingStationByStationId(String stationId){
		if(StringUtils.isNotEmpty(stationId)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("station_id", stationId);
			List<ChargingStation> stations=selectByExample(example);
			return stations.size()>0?stations.get(0):null;
		}
		return null;
	}
	
	//获取用户充电站信息
	public List<ChargingStation> getChargingStationsByUserId(Integer userId){
		if(null!=userId&&userId>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("user_id", userId);
			return selectByExample(example);
		}
		return null;
	}
	
	//更新充电站信息
	public int updateChargingStation(ChargingStation station){
		if(null!=station.getId()&&station.getId()>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", station.getId());
			return updateByExampleSelective(station, example);
		}
		return 0;
	}
	
	//计算结果集大小
	public long countByExample(QueryExample example) {
		return chargingStationMapper.countByExample(example);
	}

	//保存
	public int insertSelective(ChargingStation record) {
		return chargingStationMapper.insertSelective(record);
	}

	//获取结果集
	public List<ChargingStation> selectByExample(QueryExample example) {
		return chargingStationMapper.selectByExample(example);
	}
	
	//更新
	public int updateByExampleSelective(ChargingStation record, QueryExample example) {
		return chargingStationMapper.updateByExampleSelective(record, example);
	}

}

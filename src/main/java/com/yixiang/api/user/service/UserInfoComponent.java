package com.yixiang.api.user.service;

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
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.charging.service.ChargingStationComponent;
import com.yixiang.api.order.service.CouponInfoComponent;
import com.yixiang.api.user.mapper.UserInfoMapper;
import com.yixiang.api.user.pojo.CarInfo;
import com.yixiang.api.user.pojo.UserDevice;
import com.yixiang.api.user.pojo.UserEvaluation;
import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.OSSUtil;
import com.yixiang.api.util.PushUtil;
import com.yixiang.api.util.ResponseCode;
import com.yixiang.api.util.Result;
import com.yixiang.api.util.SmsUtil;
import com.yixiang.api.util.ThreadCache;
import com.yixiang.api.util.pojo.QueryExample;
import com.yixiang.api.util.service.ConfigComponent;
import com.yixiang.api.util.service.OpenAppLogComponent;

@Service
public class UserInfoComponent {
	
	@Autowired
	private UserInfoMapper userInfoMapper;
	@Autowired
	private UserDeviceComponent userDeviceComponent;
	@Autowired
	private ChargingStationComponent chargingStationComponent;
	@Autowired
	private CarInfoComponent carInfoComponent;
	@Autowired
	private FansInfoComponent fansInfoComponent;
	@Autowired
	private UserEvaluationComponent userEvaluationComponent;
	@Autowired
	private OpenAppLogComponent openAppLogComponent;
	@Autowired
	private CouponInfoComponent couponInfoComponent;
	@Autowired
	private ConfigComponent configComponent;
	
	Logger log=LoggerFactory.getLogger(getClass());
	
	//尝试登录
	public UserInfo attemptLogin(){
		Map<String,Object> param=ThreadCache.getHttpData();
		String phone=!DataUtil.isEmpty(param.get(Constants.PHONE))?param.get(Constants.PHONE).toString():null;
		String imei=!DataUtil.isEmpty(param.get(Constants.IMEI))?param.get(Constants.IMEI).toString():null;
		String system=!DataUtil.isEmpty(param.get(Constants.SYSTEM))?param.get(Constants.SYSTEM).toString():null;
		String openId=!DataUtil.isEmpty(param.get(Constants.WXOPENID))?param.get(Constants.WXOPENID).toString():null;
		if(StringUtils.isNotEmpty(phone)){
			if(StringUtils.isNotEmpty(imei)&&StringUtils.isNotEmpty(system)){//APP登录
				UserInfo user=getUserInfoByPhone(phone);
				if(null!=user&&user.getState().equals(UserInfo.USER_STATE_ENUM.YIJIHUO.getState())){
					UserDevice device=userDeviceComponent.getUserDeviceByImeiAndSystem(user.getId(),imei,system);
					if(null!=device&&user.getDeviceId().equals(device.getId())){
						return user;
					}
				}
			}else if(StringUtils.isNotEmpty(openId)){//微信登录
				UserInfo user=getUserInfoByPhone(phone);
				if(null!=user&&user.getState().equals(UserInfo.USER_STATE_ENUM.YIJIHUO.getState())){
					UserDevice device=userDeviceComponent.getUserDeviceByOpenId(user.getId(),openId);
					if(null!=device&&user.getDeviceId().equals(device.getId())){
						return user;
					}
				}
			}
		}
		return null;
	}
	
	//用户账户信息
	public Map<String,Object> getUserInfo(String uuid){
		UserInfo user=getUserInfoByUUID(uuid);
		Map<String,Object> result=null;
		if(null!=user){
			UserInfo current=attemptLogin();
			Integer isCollect=0;
			Integer isEval=0;
			Integer myself=0;
			if(null!=current){
				//是否关注该用户
				QueryExample example=new QueryExample();
				example.and().andEqualTo("follower_id", current.getId()).andEqualTo("user_id", user.getId()).andEqualTo("state", Constants.YES);
				isCollect=fansInfoComponent.selectByExample(example).size()>0?1:0;
				//是否评价该用户
				example=new QueryExample();
				example.and().andEqualTo("from_user_id", current.getId()).andEqualTo("to_user_id", user.getId())
					.andEqualTo("state", UserEvaluation.EVALUATION_STATE_ENUM.TONGGUO.getState());
				isEval=userEvaluationComponent.selectByExample(example).size()>0?1:0;
				myself=current.getId().equals(user.getId())?1:0;
			}
			JSONObject json=JSONObject.parseObject(Redis.use().get("user_oss_config"));
			String domain=json.getString("domain")+"/"+json.getString("imgDir")+"/";
			result=DataUtil.mapOf("uuid",uuid,"userName",user.getUserName(),"isCollect",isCollect,"isEval",isEval,"myself",myself
					,"headImg",StringUtils.isNotEmpty(user.getHeadImg())?domain+user.getHeadImg():user.getHeadImg()
					,"level",user.getLevelId(),"stars",user.getStars(),"coins",user.getCoins(),"balance",user.getBalance());
		}
		return result;
	}
	
	//用户信息配置
	public Map<String,Object> getUserInfoConfig(){
		return DataUtil.mapOf("rewardTip",Redis.use().get("add_charger_reward_tip"));
	}
	
	//用户主页
	public Map<String,Object> getUserHomeData(){
		UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
		Map<String,Object> result=getUserInfo(user.getUuid());
		result.put("times", user.getTimes());
		//加载个人充电桩
		JSONObject json=JSONObject.parseObject(Redis.use().get("charging_oss_config"));
		List<Map<Object,Object>> stations=chargingStationComponent.getChargingStationsByUserId(user.getId()).stream().map(c->{
			return DataUtil.mapOf("uuid",c.getUuid(),"headImg",OSSUtil.joinOSSFileUrl(c.getHeadImg(),json)
					,"title",c.getTitle(),"times",c.getTimes(),"source",c.getSource(),"electricityPrice",c.getElectricityPrice()
					,"serviceFee",c.getServiceFee(),"isPrivate",c.getIsPrivate());
		}).collect(Collectors.toList());
		result.put("stations", stations);
		//加载车辆信息
		List<CarInfo> cars=carInfoComponent.queryCarInfosByUserId(user.getId());
		if(cars.size()>0){
			result.put("car", cars.get(0).getCar());
		}
		//客服电话
		result.put("kf_phone", Redis.use().get("kf_phone"));
		//会员特权介绍页
		result.put("vipPrivilege", Redis.use().get("vip_privilege_h5"));
		//优惠券数量
		result.put("couponAmount", couponInfoComponent.getUserEnableCouponAmount(user.getId()));
		return result;
	}
	
	//用户登录
	@SuppressWarnings("unchecked")
	@Transactional
	public Map<String,Object> login(UserInfo user,UserDevice device,String code){
		//设置登录所需信息
		Map<String,Object> param=(Map<String,Object>)ThreadCache.getData(Constants.HTTP_PARAM);
		String phone=user.getPhone();
		if(StringUtils.isEmpty(phone)){
			phone=!DataUtil.isEmpty(param.get(Constants.PHONE))?param.get(Constants.PHONE).toString():null;
		}
		String imei=!DataUtil.isEmpty(param.get(Constants.IMEI))?param.get(Constants.IMEI).toString():null;
		String system=!DataUtil.isEmpty(param.get(Constants.SYSTEM))?param.get(Constants.SYSTEM).toString():null;
		String openId=!DataUtil.isEmpty(param.get(Constants.WXOPENID))?param.get(Constants.WXOPENID).toString():null;
		String platform=!DataUtil.isEmpty(param.get(Constants.PLATFORM))?param.get(Constants.PLATFORM).toString():null;
		user.setPhone(phone);
		device.setImei(imei);
		device.setSystem(system);
		device.setWxOpenId(openId);
		device.setPlatform(platform);
		if(StringUtils.isEmpty(user.getPhone())||((StringUtils.isEmpty(device.getSystem())||StringUtils.isEmpty(device.getImei()))
				&&StringUtils.isEmpty(device.getWxOpenId()))){
			log.info("用户手机号未输入,phone="+user.getPhone());
			Result.putValue(ResponseCode.CodeEnum.REQUIRED_PARAM_NULL);
			return null;
		}
		//校验验证码是否正确
		JSONObject json=JSONObject.parseObject(Redis.use().get("login_verify_code_config"));
		String key=json.getString("prefix")+user.getPhone();
		String userCode=Redis.use().get(key);
		if(StringUtils.isEmpty(userCode)||!userCode.equals(code)){
			log.info("验证码输入错误,phone="+user.getPhone()+",code="+code);
			Result.putValue(ResponseCode.CodeEnum.VERIFY_CODE_INCORRECT);
			return null;
		}
		UserInfo userInfo=getUserInfoByPhone(user.getPhone());
		if(null!=userInfo&&userInfo.getState().equals(UserInfo.USER_STATE_ENUM.YIDONGJIE.getState())){
			log.info("用户状态不正常,phone="+userInfo.getPhone()+",state="+userInfo.getState());
			Result.putValue(ResponseCode.CodeEnum.USER_PHONE_BLACK);
			return null;
		}
		//上传头像
		if(StringUtils.isNotEmpty(user.getHeadImg())){
			String headImg=OSSUtil.saveImgToOSS("user_oss_config", user.getHeadImg(), "jpg");
			user.setHeadImg(headImg);
		}
		boolean isActive=false;
		if(null==userInfo){//新用户
			userInfo=new UserInfo();
			userInfo.setPhone(user.getPhone());
			userInfo.setUserName(user.getUserName());
			userInfo.setHeadImg(user.getHeadImg());
			userInfo.setUuid(DataUtil.buildUUID());
			userInfo.setPwd(DataUtil.buildUUID());
			userInfo.setCreateTime(new Date());
			userInfo.setState(UserInfo.USER_STATE_ENUM.YIJIHUO.getState());
			userInfo.setActiveTime(userInfo.getLoginTime());
			userInfo.setLoginTime(userInfo.getCreateTime());
			userInfo.setLng(user.getLng());
			userInfo.setLat(user.getLat());
			insertSelective(userInfo);
			device.setUserId(userInfo.getId());
			device.setCreateTime(userInfo.getCreateTime());
			device.setLoginTime(userInfo.getLoginTime());
			userDeviceComponent.insertSelective(device);
			userInfo.setDeviceId(device.getId());
			updateUserInfo(userInfo);
			isActive=true;
		}else{//老用户
			UserDevice deviceInfo=null;
			if(StringUtils.isNotEmpty(device.getSystem())&&StringUtils.isNotEmpty(device.getImei())){
				deviceInfo=userDeviceComponent.getUserDeviceByImeiAndSystem(userInfo.getId(), device.getImei(), device.getSystem());
			}else{
				deviceInfo=userDeviceComponent.getUserDeviceByOpenId(userInfo.getId(), device.getWxOpenId());
			}
			if(null==deviceInfo){
				device.setUserId(userInfo.getId());
				device.setCreateTime(new Date());
				device.setLoginTime(device.getCreateTime());
				userDeviceComponent.insertSelective(device);
				deviceInfo=device;
			}else{//更新设备信息
				if(StringUtils.isNotEmpty(device.getRegistrationId())){
					deviceInfo.setRegistrationId(device.getRegistrationId());
				}
			}
			userInfo.setDeviceId(deviceInfo.getId());
			userInfo.setLoginTime(new Date());
			if(userInfo.getState().equals(UserInfo.USER_STATE_ENUM.DAIJIHUO.getState())){//待激活
				userInfo.setState(UserInfo.USER_STATE_ENUM.YIJIHUO.getState());
				userInfo.setActiveTime(userInfo.getLoginTime());
				isActive=true;
			}
			userInfo.setHeadImg(StringUtils.isNotEmpty(user.getHeadImg())?user.getHeadImg():userInfo.getHeadImg());
			updateUserInfo(userInfo);
			deviceInfo.setLoginTime(userInfo.getLoginTime());
			userDeviceComponent.updateUserDevice(deviceInfo);
		}
		json=JSONObject.parseObject(Redis.use().get("user_oss_config"));
		//激活用户赠送优惠券
		if(isActive){
			giveActiveCoupon(userInfo.getId());
		}
		//移除验证码
		if(null==configComponent.getConfig(key)){
			Redis.use().del(key);
		}
		return DataUtil.mapOf("uuid",userInfo.getUuid(),"password",userInfo.getPwd(),"userName",userInfo.getUserName()
				,"headImg",OSSUtil.joinOSSFileUrl(userInfo.getHeadImg(), json));
	}
	
	//激活用户赠送优惠券
	@Transactional
	public void giveActiveCoupon(Integer userId){
		//赠送优惠券
		String value=Redis.use().get("active_user_coupon");
		if(StringUtils.isNotEmpty(value)){
			Arrays.asList(value.split(",")).stream().forEach(i->couponInfoComponent.grantCoupon(userId, Integer.valueOf(i)));
		}
		//发送push通知
		value=Redis.use().get("active_user_push");
		if(StringUtils.isNotEmpty(value)){
			JSONObject json=JSONObject.parseObject(value);
			UserInfo user=getUserInfo(userId, false);
			UserDevice device=null!=user?userDeviceComponent.getUserDevice(user.getDeviceId()):null;
			if(null!=device&&StringUtils.isNotEmpty(device.getRegistrationId())){
				PushUtil.push(device.getRegistrationId(), json);
			}
		}
	}
	
	//用户打开APP回调
	@Transactional
	public void openAppSuccessCallback(){
		//记录分享日志
		openAppLogComponent.saveOpenAppLog();
	}
	
	//上传帖子图片资源
	public String[] publishUserMedia(MultipartFile[] files){
		String names=OSSUtil.uploadMedia("user_oss_config", files);
		return StringUtils.isNotEmpty(names)?names.split(","):null;
	}
	
	//修改用户信息
	@Transactional
	public void editUserInfo(UserInfo user,MultipartFile files){
		String headImg=OSSUtil.uploadMedia("user_oss_config", files);
		UserInfo update=(UserInfo)ThreadCache.getData(Constants.USER);
		//修改用户昵称
		if(StringUtils.isNotEmpty(user.getUserName())){
			update.setUserName(user.getUserName());
		}
		//修改用户头像
		if(StringUtils.isNotEmpty(user.getHeadImg())){
			update.setHeadImg(user.getHeadImg());
		}
		if(StringUtils.isNotEmpty(headImg)){
			update.setHeadImg(headImg);
		}
		updateUserInfo(update);
	}
	
	//下发验证码
	@SuppressWarnings("unchecked")
	public void sendCheckCode(String phone){
		Map<String,Object> param=(Map<String,Object>)ThreadCache.getData(Constants.HTTP_PARAM);
		if(StringUtils.isEmpty(phone)&&DataUtil.isEmpty(param.get(Constants.PHONE))){
			log.info("用户手机号未输入,phone="+phone);
			Result.putValue(ResponseCode.CodeEnum.REQUIRED_PARAM_NULL);
			return;
		}
		phone=StringUtils.isNotEmpty(phone)?phone:param.get(Constants.PHONE).toString();
		//检查验证码是否过期
		JSONObject json=JSONObject.parseObject(Redis.use().get("login_verify_code_config"));
		String key=json.getString("prefix")+phone;
		if(StringUtils.isNotEmpty(Redis.use().get(key))){
			log.info("验证码还未过期,不能重复获取");
			Result.putValue(ResponseCode.CodeEnum.VERIFY_CODE_LIVE);
			return;
		}
		String code=DataUtil.createNums(json.getIntValue("length"));
		//发送验证码
		if(!SmsUtil.sendVerifyCode(code, phone)){
			log.info("验证码发送失败,phone="+phone);
			Result.putValue(ResponseCode.CodeEnum.CODE_SEND_FAILED);
			return;
		}
		//将验证码存储到redis
		Redis.use().setex(key, json.getIntValue("live_seconds"), code);
	}
	
	//更新用户星级
	@Transactional
	public void updateStars(Integer userId,Integer stars){
		UserInfo user=getUserInfo(userId, true);
		if(null!=user){
			user.setStars(stars);
			updateUserInfo(user);
		}
	}
	
	//累加用户余额
	@Transactional
	public void addBalance(Integer userId,BigDecimal interval){
		UserInfo user=getUserInfo(userId, true);
		if(null!=user){
			user.setBalance(user.getBalance().add(interval));
			updateUserInfo(user);
		}
	}
	
	//累加用户会员积分
	@Transactional
	public void addCoins(Integer userId,Integer interval){
		UserInfo user=getUserInfo(userId, true);
		if(null!=user){
			user.setCoins(user.getCoins()+interval);
			updateUserInfo(user);
		}
	}
	
	//累加用户充电次数
	@Transactional
	public void addTimes(Integer userId,Integer interval){
		UserInfo user=getUserInfo(userId, true);
		if(null!=user){
			user.setTimes(user.getTimes()+interval);
			updateUserInfo(user);
		}
	}
	
	//获取用户信息
	public UserInfo getUserInfo(Integer id,boolean lock){
		if(null!=id&&id>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", id);
			example.setLock(lock);
			List<UserInfo> list=selectByExample(example);
			return list.size()>0?list.get(0):null;
		}
		return null;
	}
	
	//获取用户信息
	public UserInfo getUserInfoByPhone(String phone){
		if(StringUtils.isNotEmpty(phone)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("phone", phone);
			List<UserInfo> list=selectByExample(example);
			return list.size()>0?list.get(0):null;
		}
		return null;
	}
	
	//获取用户信息
	public UserInfo getUserInfoByOpenId(String openId){
		if(StringUtils.isNotEmpty(openId)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("wx_open_id", openId);
			List<UserInfo> list=selectByExample(example);
			return list.size()>0?list.get(0):null;
		}
		return null;
	}
	
	//获取用户信息
	public UserInfo getUserInfoByUUID(String uuid){
		if(StringUtils.isNotEmpty(uuid)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("uuid", uuid);
			List<UserInfo> list=selectByExample(example);
			return list.size()>0?list.get(0):null;
		}
		return null;
	}
	
	//更新用户信息
	@Transactional
	public int updateUserInfo(UserInfo info){
		if(null!=info.getId()&&info.getId()>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", info.getId());
			return updateByExampleSelective(info, example);
		}
		return 0;
	}

	//计算结果集大小
	public long countByExample(QueryExample example) {
		return userInfoMapper.countByExample(example);
	}

	//保存
	@Transactional
	public int insertSelective(UserInfo record) {
		return userInfoMapper.insertSelective(record);
	}

	//获取结果集
	public List<UserInfo> selectByExample(QueryExample example) {
		return userInfoMapper.selectByExample(example);
	}

	//更新
	@Transactional
	public int updateByExampleSelective(UserInfo record, QueryExample example) {
		return userInfoMapper.updateByExampleSelective(record, example);
	}

}

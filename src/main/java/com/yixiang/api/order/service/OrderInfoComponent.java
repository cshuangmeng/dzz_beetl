package com.yixiang.api.order.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.core.jmx.JobDataMapSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.feilong.core.DatePattern;
import com.feilong.core.date.DateUtil;
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.charging.pojo.ChargingStation;
import com.yixiang.api.charging.pojo.ConnectorInfo;
import com.yixiang.api.charging.service.ChargingStationComponent;
import com.yixiang.api.charging.service.ConnectorInfoComponent;
import com.yixiang.api.order.mapper.OrderInfoMapper;
import com.yixiang.api.order.pojo.CouponInfo;
import com.yixiang.api.order.pojo.OrderInfo;
import com.yixiang.api.order.pojo.TradeHistory;
import com.yixiang.api.quartz.CheckChargingStateJob;
import com.yixiang.api.quartz.PullChargeBillJob;
import com.yixiang.api.quartz.TaskService;
import com.yixiang.api.refund.pojo.RefundSummary;
import com.yixiang.api.refund.service.RefundSummaryComponent;
import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.user.service.UserInfoComponent;
import com.yixiang.api.util.ChargeClientBuilder;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.ResponseCode;
import com.yixiang.api.util.Result;
import com.yixiang.api.util.ThreadCache;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class OrderInfoComponent {

	@Autowired
	private OrderInfoMapper orderInfoMapper;
	@Autowired
	private ChargingStationComponent chargingStationComponent;
	@Autowired
	private ConnectorInfoComponent connectorInfoComponent;
	@Autowired
	private UserInfoComponent userInfoComponent;
	@Autowired
	private TradeHistoryComponent tradeHistoryComponent;
	@Autowired
	private RefundSummaryComponent refundSummaryComponent;
	@Autowired
	private ChargeClientBuilder chargeClientBuilder;
	@Autowired
	private CouponInfoComponent couponInfoComponent;
	@Autowired
	private TaskService taskService;
	
	Logger log=LoggerFactory.getLogger(getClass());
	
	//用户支付充电费用
	@Transactional
	public Map<String,Object> pay(Integer orderId,Float price,Integer couponId,boolean auth){
		//锁定用户账户
		UserInfo user=ThreadCache.getCurrentUserInfo();
		Map<String,Object> param=ThreadCache.getHttpData();
		//检查订单是否可操作
		OrderInfo order=getOrderInfo(orderId, true);
		if(null==order){
			log.info("订单信息不存在,orderId="+orderId);
			Result.putValue(ResponseCode.CodeEnum.ORDER_NOT_EXISTS);
			return null;
		}
		if(auth){
			user=userInfoComponent.getUserInfo(user.getId(), true);
			if(!user.getId().equals(order.getUserId())){
				log.info("不能操作非本人订单,orderId="+order.getId()+",login.userId="+user.getId()+",order.userId="+order.getUserId());
				Result.putValue(ResponseCode.CodeEnum.ORDER_NOT_MINE);
				return null;
			}
		}else{
			user=userInfoComponent.getUserInfo(order.getUserId(), true);
			price=order.getTotalPrice();
		}
		if(!order.getState().equals(OrderInfo.ORDER_STATE_ENUM.NO_PAY.getState())){
			log.info("订单在此状态下不支持支付,orderId="+order.getId()+",state="+order.getState());
			Result.putValue(ResponseCode.CodeEnum.ORDER_STATE_INCORRECT);
			return null;
		}
		//校验优惠券是否可用
		CouponInfo coupon=couponInfoComponent.getCouponInfo(couponId,true);
		if(null!=coupon){
			//重设订单总金额
			param.put("price", order.getTotalPrice());
			if(!couponInfoComponent.isCouponAvailable(param, coupon)){
				Result.putValue(ResponseCode.CodeEnum.COUPON_NOT_MATCH);
				return null;
			}
			//计算减免后的应付金额
			float payPrice=0;
			Float maxDiscount=coupon.getMaxDiscount();
			if(coupon.getCategory().equals(CouponInfo.COUPON_CATEGORY_ENUM.CHARGING.getCategory())){
				if(coupon.getReduceType().equals(CouponInfo.REDUCE_TYPE_ENUM.DISCOUNT.getType())){
					if(null!=maxDiscount&&order.getTotalPowerPrice()*(1-coupon.getAmount())>maxDiscount.floatValue()){
						payPrice=order.getTotalPowerPrice()-maxDiscount;
					}else{
						payPrice=order.getTotalPowerPrice()*coupon.getAmount();
					}
				}else if(coupon.getReduceType().equals(CouponInfo.REDUCE_TYPE_ENUM.REDUCE.getType())){
					payPrice=order.getTotalPowerPrice()-coupon.getAmount();
				}
				payPrice=payPrice>0?payPrice:0;
				payPrice+=order.getTotalServiceFee();
			}else if(coupon.getCategory().equals(CouponInfo.COUPON_CATEGORY_ENUM.SERVICEFEE.getCategory())){
				if(coupon.getReduceType().equals(CouponInfo.REDUCE_TYPE_ENUM.DISCOUNT.getType())){
					if(null!=maxDiscount&&order.getTotalServiceFee()*(1-coupon.getAmount())>maxDiscount.floatValue()){
						payPrice=order.getTotalServiceFee()-maxDiscount;
					}else{
						payPrice=order.getTotalServiceFee()*coupon.getAmount();
					}
				}else if(coupon.getReduceType().equals(CouponInfo.REDUCE_TYPE_ENUM.REDUCE.getType())){
					payPrice=order.getTotalServiceFee()-coupon.getAmount();
				}
				payPrice=payPrice>0?payPrice:0;
				payPrice+=order.getTotalPowerPrice();
			}
			//四舍五入
			payPrice=new BigDecimal(payPrice).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
			order.setTotalPrice(payPrice);
		}
		if(!order.getTotalPrice().equals(price)){
			log.info("订单应付金额不正确,price="+price+",order.totalPrice="+order.getTotalPrice());
			Result.putValue(ResponseCode.CodeEnum.PAY_PRICE_INCORRECT);
			return null;
		}
		Float balance=user.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
		if(balance.floatValue()<order.getTotalPrice()){
			log.info("用户余额不足,user.balance="+balance+",order.totalPrice="+order.getTotalPrice());
			Result.putValue(ResponseCode.CodeEnum.BALANCE_NOT_ENOUGH);
			return null;
		}
		//扣除账户金额
		BigDecimal interval=new BigDecimal(-order.getTotalPrice()).setScale(2, BigDecimal.ROUND_HALF_UP);
		userInfoComponent.addBalance(user.getId(), interval);
		tradeHistoryComponent.saveTradeHistory(user.getId(), order.getId(), TradeHistory.TRADE_TYPE_ENUM.CHARGE_PAY.getType()
				, interval.floatValue(), TradeHistory.TRADE_STATE_ENUM.YICHULI.getState(), null);
		refundSummaryComponent.saveRefundSummary(user.getId(), order.getId()
				, RefundSummary.ORDER_TYPE_ENUM.CHARGING.getType(), 0F, order.getTotalPrice());
		//修改订单状态
		OrderInfo update=new OrderInfo();
		update.setId(order.getId());
		update.setState(OrderInfo.ORDER_STATE_ENUM.NO_EVALUATE.getState());
		update.setPayWay(Constants.BALANCEPAY);
		update.setPayPrice(price);
		update.setTotalBalance(update.getPayPrice());
		update.setCouponId(null!=coupon?coupon.getId():0);
		update.setPayTime(new Date());
		updateOrderInfo(update);
		//修改优惠券状态
		if(null!=coupon){
			couponInfoComponent.updateCouponState(coupon.getId(), CouponInfo.COUPON_STATE_ENUM.USED.getState(), order.getId());
		}
		return DataUtil.mapOf("orderId",order.getId(),"tradeNo",order.getTradeNo());
	}
	
	//获取用户未完成的订单
	public Map<String,Object> queryChargingOrder(Integer reset){
		UserInfo user=ThreadCache.getCurrentUserInfo();
		List<OrderInfo> orders=queryOrders(user.getId(), OrderInfo.IN_PROGRESS_STATES, null, null);
		log.info("找到进行中的订单,orders="+JSONObject.toJSONString(orders));
		Map<String,Object> result=null;
		if(orders.size()>0){
			OrderInfo order=orders.get(0);
			//计算倒计时
			long residualTime=0;
			if(order.getState().equals(OrderInfo.ORDER_STATE_ENUM.PENDING.getState())){
				int payTimeOut=Integer.valueOf(Redis.use().get("order_charge_timeout"));
				Date now=new Date();
				long time1=now.getTime();
				long time2=DateUtils.addSeconds(reset>0?now:order.getCreateTime(), payTimeOut).getTime();
				residualTime=time2-time1>0?(time2-time1)/1000:0;
			}
			//获取充电桩信息
			ChargingStation station=chargingStationComponent.getChargingStation(order.getStationId());
			result=DataUtil.mapOf("orderId",order.getId(),"state",order.getState(),"stationId",order.getStationId()
					,"provider",order.getProvider(),"providerName",null!=station?station.getProvider():null
					,"timeout",residualTime,"retry",Redis.use().get("order_charge_retry"),"unit",Redis.use().get("order_charge_unit")
					,"interval",Redis.use().get("order_charge_interval"));
		}
		return result;
	}
	
	//启动充电
	@Transactional
	public Map<String,Object> startCharging(String code){
		UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
		//检查用户账户余额是否充足
		JSONObject auto=JSONObject.parseObject(Redis.use().get("auto_stop_config"));
		if(!auto.getBooleanValue("charge_switch")){
			log.info("未开启充电服务,流程结束");
			Result.putValue(ResponseCode.CodeEnum.CHARGE_SERVICE_UNAVAILABLE.getValue()
					,auto.getString("charge_close_tip"),null);
			return null;
		}
		JSONObject config=auto.getJSONObject("start_check");
		float minBalance=config.getFloatValue("other_min_balance");
		if(StringUtils.isNotEmpty(code)&&code.length()>=config.getIntValue("gjdw_code_length")){
			minBalance=config.getFloatValue("gjdw_min_balance");
		}
		if(user.getBalance().setScale(2, BigDecimal.ROUND_HALF_UP).floatValue()<minBalance){
			log.info("用户账户余额不足,minBalance="+minBalance+",balance="+user.getBalance()+",code="+code);
			return DataUtil.mapOf("dialogTips",config.getJSONArray("tip"));
		}
		//检查是否存在正在进行的订单
		List<OrderInfo> orders=queryOrders(user.getId(), OrderInfo.IN_PROGRESS_STATES, null, null);
		if(orders.size()>0){
			log.info("存在进行中的订单,orders="+JSONObject.toJSONString(orders));
			Result.putValue(ResponseCode.CodeEnum.EXISTS_CHARGING_ORDER);
			return null;
		}
		//启动充电
		String connectorId=analyzeConnectorCode(code);
		String response=chargeClientBuilder.startCharge(connectorId);
		JSONObject json=DataUtil.isJSONObject(response)?JSONObject.parseObject(response):null;
		if(null==json){
			log.info("启动充电请求失败,response="+response);
			Result.putValue(ResponseCode.CodeEnum.FAIL);
			return null;
		}
		boolean success=!DataUtil.isEmpty(json.get("success"))&&json.getBooleanValue("success");
		//保存订单
		OrderInfo order=new OrderInfo();
		order.setConnectorCode(code);
		if(!DataUtil.isEmpty(json.get("data"))&&DataUtil.isJSONObject(json.get("data").toString())
				&&!DataUtil.isEmpty(json.getJSONObject("data").get("charge_id"))){
			order.setChargeId(json.getJSONObject("data").getString("charge_id"));
		}
		order.setChargeState(!DataUtil.isEmpty(json.get("msg"))?json.getString("msg"):null);
		order.setCreateTime(new Date());
		order.setUserId(user.getId());
		order.setTradeNo(DateUtil.toString(new Date(), DatePattern.TIMESTAMP_WITH_MILLISECOND)+DataUtil.createNums(3));
		//尝试获取桩信息
		ConnectorInfo connectorInfo=connectorInfoComponent.getConnectorInfoByConnectorId(connectorId);
		ChargingStation station=chargingStationComponent.getChargingStationByStationId(null!=connectorInfo?connectorInfo.getStationId():null);
		order.setProvider(analyzeProvider(connectorId));
		if(null!=station){
			order.setConnectorId(connectorInfo.getId());
			order.setStationId(station.getId());
			order.setProvider(Integer.valueOf(station.getProviderId()));
			order.setProviderName(station.getTitle());
		}
		//启动调度任务
		Map<String,Object> result=null;
		if(success){
			JobDataMap jobData=JobDataMapSupport.newJobDataMap(DataUtil.mapOf("orderId",String.valueOf(order.getId())));
			JobDetail job = JobBuilder.newJob(CheckChargingStateJob.class).withIdentity(Constants.STATE_JOB_PREFIX+order.getId()
				, Constants.STATE_GROUP_PREFIX+order.getId()).usingJobData(jobData).build();
			taskService.updateCron(job, Redis.use().get("charge_appending_cron"));
			result=DataUtil.mapOf("orderId",order.getId(),"timeout",Integer.valueOf(Redis.use().get("order_charge_timeout"))
					,"retry",Integer.valueOf(Redis.use().get("order_charge_retry")),"unit",Integer.valueOf(Redis.use().get("order_charge_unit"))
					,"interval",Integer.valueOf(Redis.use().get("order_charge_interval")),"provider",order.getProvider());
		}else{
			order.setState(OrderInfo.ORDER_STATE_ENUM.CANCEL.getState());
			log.info("启动充电失败,response="+response);
			Result.putValue(ResponseCode.CodeEnum.FAIL.getValue(),json.getString("msg"),Constants.EMPTY);
		}
		orderInfoMapper.insertSelective(order);
		return result;
	}
	
	//查询充电状态
	@Transactional
	public Map<String,Object> queryChargingState(Integer orderId,boolean auth){
		//检查订单是否可操作
		OrderInfo order=getOrderInfo(orderId, true);
		if(null==order){
			log.info("订单信息不存在,orderId="+orderId);
			Result.putValue(ResponseCode.CodeEnum.ORDER_NOT_EXISTS);
			return null;
		}
		//用户是否是在客户端操作
		UserInfo user=null;
		if(auth){
			user=(UserInfo)ThreadCache.getData(Constants.USER);
			if(!user.getId().equals(order.getUserId())){
				log.info("不能操作非本人订单,orderId="+order.getId()+",login.userId="+user.getId()+",order.userId="+order.getUserId());
				Result.putValue(ResponseCode.CodeEnum.ORDER_NOT_MINE);
				return null;
			}
		}else{
			user=userInfoComponent.getUserInfo(order.getUserId(), true);
		}
		OrderInfo info=getChargingState(order.getChargeId());
		Map<String,Object> result=null;
		if(null!=info){
			//充电已启动
			if(order.getState().equals(OrderInfo.ORDER_STATE_ENUM.PENDING.getState())){
				info.setState(OrderInfo.ORDER_STATE_ENUM.CHARGING.getState());
				order.setState(info.getState());
				//启动调度任务
				JobDataMap jobData=JobDataMapSupport.newJobDataMap(DataUtil.mapOf("orderId",String.valueOf(order.getId())));
				JobDetail job = JobBuilder.newJob(PullChargeBillJob.class).withIdentity(Constants.BILL_JOB_PREFIX+order.getId()
					, Constants.BILL_GROUP_PREFIX+order.getId()).usingJobData(jobData).build();
				taskService.updateCron(job, String.format(Redis.use().get("charge_bill_cron")
					, DateUtil.getSecond(DateUtils.addSeconds(new Date(),Constants.QUARTZ_JOB_DELAY))));
				//更新调度任务调用间隔
				taskService.updateCron(Constants.STATE_JOB_PREFIX+order.getId()
					, Constants.STATE_GROUP_PREFIX+order.getId(), String.format(Redis.use().get("charge_state_cron")
					,DateUtil.getSecond(DateUtils.addSeconds(new Date(),Constants.QUARTZ_JOB_DELAY*2))));
			}
			info.setId(order.getId());
			info.setProvider(null==info.getProvider()?order.getProvider():info.getProvider());
			info.setState(order.getState());
			if(order.getState().equals(OrderInfo.ORDER_STATE_ENUM.PENDING.getState())
					||order.getState().equals(OrderInfo.ORDER_STATE_ENUM.CHARGING.getState())){
				updateOrderInfo(info);
			}
			//检查账户余额是否充足
			result=DataUtil.objectToMap(info);
			JSONObject config=JSONObject.parseObject(Redis.use().get("auto_stop_config")).getJSONObject("auto_stop");
			if(config.getBooleanValue("switch")){
				if(user.getBalance().floatValue()-info.getTotalPrice()<=config.getFloatValue("app_stop_fee")){
					log.info("用户余额已不充足,弹窗提示,userId="+user.getId()+",orderId="+order.getId()
						+",balance="+user.getBalance()+",totalPrice="+info.getTotalPrice());
					result.putAll(DataUtil.mapOf("dialogTips",config.getJSONArray("tip"),"stopTimeout",config.getInteger("timeout")));
				}
				if(user.getBalance().floatValue()-info.getTotalPrice()<=config.getFloatValue("stop_fee")){
					log.info("用户余额已不充足,直接断电操作,userId="+user.getId()+",orderId="+order.getId()
						+",balance="+user.getBalance()+",totalPrice="+info.getTotalPrice());
					stopCharging(order.getId(),auth);
				}
			}
		}else{//保存失败原因
			info=new OrderInfo();
			info.setId(order.getId());
			info.setChargeState(Result.getThreadObject().getMsg());
			updateOrderInfo(info);
		}
		return result;
	}
	
	//结束充电
	@Transactional
	public void stopCharging(Integer orderId,boolean auth){
		UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
		//检查订单是否可操作
		OrderInfo order=getOrderInfo(orderId, true);
		if(null==order){
			log.info("订单信息不存在,orderId="+orderId);
			Result.putValue(ResponseCode.CodeEnum.ORDER_NOT_EXISTS);
			return;
		}
		if(auth&&!user.getId().equals(order.getUserId())){
			log.info("不能操作非本人订单,orderId="+order.getId()+",login.userId="+user.getId()+",order.userId="+order.getUserId());
			Result.putValue(ResponseCode.CodeEnum.ORDER_NOT_MINE);
			return;
		}
		if(order.getState().equals(OrderInfo.ORDER_STATE_ENUM.CHARGING.getState())){
			//发起结束充电请求
			String response=chargeClientBuilder.stopCharge(order.getChargeId());
			JSONObject json=DataUtil.isJSONObject(response)?JSONObject.parseObject(response):null;
			if(null==json){
				log.info("结束充电请求失败,response="+response);
				Result.putValue(ResponseCode.CodeEnum.FAIL);
				return;
			}
			if(DataUtil.isEmpty(json.get("success"))||!json.getBooleanValue("success")){
				log.info("结束充电失败,response="+response);
				Result.putValue(ResponseCode.CodeEnum.FAIL.getValue(),json.getString("msg"),Constants.EMPTY);
				return;
			}
			//更新订单状态
			updateOrderInfoState(order.getId(), null, OrderInfo.ORDER_STATE_ENUM.SETTLEMENT.getState());
		}else{
			log.info("订单在此状态下不支持结束充电,orderId="+order.getId()+",state="+order.getState());
		}
	}
	
	//获取账单信息
	public OrderInfo queryChargingBill(Integer orderId,boolean auth){
		//检查订单是否可操作
		OrderInfo order=getOrderInfo(orderId, false);
		if(null==order){
			log.info("订单信息不存在,orderId="+orderId);
			Result.putValue(ResponseCode.CodeEnum.ORDER_NOT_EXISTS);
			return null;
		}
		if(auth){
			UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
			if(!user.getId().equals(order.getUserId())){
				log.info("不能操作非本人订单,orderId="+order.getId()+",login.userId="+user.getId()+",order.userId="+order.getUserId());
				Result.putValue(ResponseCode.CodeEnum.ORDER_NOT_MINE);
				return null;
			}
		}
		List<Integer> states=Arrays.asList(OrderInfo.ORDER_STATE_ENUM.PENDING.getState()
				,OrderInfo.ORDER_STATE_ENUM.CANCEL.getState(),OrderInfo.ORDER_STATE_ENUM.REFUND.getState());
		if(states.contains(order.getState())){
			log.info("订单在此状态下不支持查询账单信息,orderId="+order.getId()+",state="+order.getState());
			Result.putValue(ResponseCode.CodeEnum.ORDER_STATE_INCORRECT);
			return null;
		}
		//账单已同步
		if(order.getState().equals(OrderInfo.ORDER_STATE_ENUM.NO_PAY.getState())
				||OrderInfo.END_CHARGING_STATES.contains(order.getState())){
			return order;
		}
		//账单未同步
		OrderInfo info=getChargingBill(order.getChargeId());
		if(null==info){
			return null;
		}
		//补全其他信息
		info.setEndCode(order.getEndCode());
		info.setProvider(order.getProvider());
		info.setStationId(order.getStationId());
		//将订单状态置为待支付
		states=Arrays.asList(OrderInfo.ORDER_STATE_ENUM.CHARGING.getState(),OrderInfo.ORDER_STATE_ENUM.SETTLEMENT.getState());
		if(states.contains(order.getState())&&info.getEndTime().compareTo(Constants.DEFAULT_DATE)>0){
			//检查是否已结束充电
			String response=chargeClientBuilder.queryChargeState(order.getChargeId());
			JSONObject json=DataUtil.isJSONObject(response)?JSONObject.parseObject(response):null;
			if(null!=json&&!DataUtil.isEmpty(json.get("success"))&&!json.getBooleanValue("success")){
				log.info("未获取到充电数据,充电结束,response="+response);
				info.setState(OrderInfo.ORDER_STATE_ENUM.NO_PAY.getState());
				info.setId(order.getId());
				updateOrderInfo(info);
				//取消调度任务
				taskService.deleteJob(Constants.STATE_JOB_PREFIX+order.getId(), Constants.STATE_GROUP_PREFIX+order.getId());
				taskService.deleteJob(Constants.BILL_JOB_PREFIX+order.getId(), Constants.BILL_GROUP_PREFIX+order.getId());
				return info;
			}
		}
		return null;
	}
	
	//获取我的充电订单列表
	public Map<String,Object> queryOrderList(Integer page,String state){
		UserInfo user=ThreadCache.getCurrentUserInfo();
		List<Integer> states=null;
		if(StringUtils.isNotEmpty(state)){
			states=Arrays.asList(state.split(",")).stream().map(o->Integer.valueOf(o)).collect(Collectors.toList());
		}else{
			states=OrderInfo.END_CHARGING_STATES;
		}
		Integer limit=JSONObject.parseObject(Redis.use().get("user_order_list_config")).getInteger("size");
		Integer offset=(page>0?page-1:0)*limit;
		List<Map<Object,Object>> result=queryOrders(user.getId(), states, offset, limit).stream().map(o->{
			ChargingStation station=chargingStationComponent.getChargingStation(o.getStationId());
			return DataUtil.mapOf("stationName",null!=station?station.getTitle():null,"startTime",o.getStartTime(),"endTime",o.getEndTime()
					,"totalPower",o.getTotalPower(),"totalPrice",o.getTotalPrice(),"totalTime",convertMinsToDesc(o.getTotalTime())
					,"id",o.getId(),"state",o.getState(),"payPrice",o.getPayPrice());
		}).collect(Collectors.toList());
		return DataUtil.mapOf("dataset",result);
	}
	
	//获取充电状态
	public OrderInfo getChargingState(String chargeId){
		if(StringUtils.isEmpty(chargeId)){
			log.info("必要参数未填写,chargeId="+chargeId);
			Result.putValue(ResponseCode.CodeEnum.REQUIRED_PARAM_NULL);
			return null;
		}
		String response=chargeClientBuilder.queryChargeState(chargeId);
		JSONObject json=DataUtil.isJSONObject(response)?JSONObject.parseObject(response):null;
		if(null==json||DataUtil.isEmpty(json.get("success"))){
			log.info("查询充电状态请求失败,response="+response);
			Result.putValue(ResponseCode.CodeEnum.FAIL);
			return null;
		}
		if(!json.getBooleanValue("success")){
			log.info("查询充电状态失败,response="+response);
			Result.putValue(ResponseCode.CodeEnum.CHARGE_SERVICE_STOPPED);
			return null;
		}
		json=json.getJSONObject("data");
		float serviceFee=Float.parseFloat(Redis.use().get("charging_service_fee"));
		ConnectorInfo connector=connectorInfoComponent.getConnectorInfoByConnectorId(json.getString("charge_pile_id"));
		ChargingStation station=chargingStationComponent.getChargingStationByStationId(json.getString("sta_id"));
		OrderInfo order=new OrderInfo();
		order.setChargeId(chargeId);
		order.setConnectorId(null!=connector?connector.getId():null);
		order.setStationId(null!=station?station.getId():null);
		order.setProvider(null!=station?Integer.parseInt(station.getProviderId()):null);
		order.setProviderName(null!=station?station.getProvider():null);
		order.setChargeState(json.getString("status"));
		order.setCurrent(null!=json.getFloat("galvanic")?json.getFloat("galvanic"):0);
		order.setTotalPowerPrice(null!=json.getFloat("electric_money")?json.getFloat("electric_money"):0);
		order.setTotalPower(null!=json.getFloat("charge_electric")?json.getFloat("charge_electric"):0);
		order.setTotalServiceFee(DataUtil.round(order.getTotalPower()*serviceFee, 2));
		order.setTotalPrice(DataUtil.round(order.getTotalPowerPrice()+order.getTotalServiceFee(), 2));
		order.setStartTime(new Date(json.getLong("start_time")*1000));
		order.setTotalTime(Long.valueOf((new Date().getTime()-order.getStartTime().getTime())/1000).intValue());
		order.setEndCode(json.getString("stop_code"));
		order.setSoc(json.getString("soc"));
		order.setPower(json.getFloat("voltage"));
		return order;
	}
	
	//获取账单信息
	public OrderInfo getChargingBill(String chargeId){
		if(StringUtils.isEmpty(chargeId)){
			log.info("必要参数未填写,chargeId="+chargeId);
			Result.putValue(ResponseCode.CodeEnum.REQUIRED_PARAM_NULL);
			return null;
		}
		String response=chargeClientBuilder.checkChargeOrders(chargeId);
		JSONObject json=DataUtil.isJSONObject(response)?JSONObject.parseObject(response):null;
		if(null==json){
			log.info("获取账单信息请求失败,response="+response);
			Result.putValue(ResponseCode.CodeEnum.FAIL);
			return null;
		}
		if(DataUtil.isEmpty(json.get("success"))||!json.getBooleanValue("success")){
			log.info("获取账单信息失败,response="+response);
			Result.putValue(ResponseCode.CodeEnum.FAIL.getValue(),json.getString("msg"),Constants.EMPTY);
			return null;
		}
		float serviceFee=Float.parseFloat(Redis.use().get("charging_service_fee"));
		json=json.getJSONObject("data");
		OrderInfo order=new OrderInfo();
		order.setChargeId(chargeId);
		order.setTotalPowerPrice(null!=json.getFloat("electric_money")?json.getFloat("electric_money"):0);
		order.setTotalPower(null!=json.getFloat("total_power")?json.getFloat("total_power"):0);
		order.setTotalServiceFee(DataUtil.round(order.getTotalPower()*serviceFee, 2));
		order.setTotalPrice(DataUtil.round(order.getTotalPowerPrice()+order.getTotalServiceFee(), 2));
		order.setStartTime(new Date(json.getLong("start_time")*1000));
		order.setEndTime(new Date(json.getLong("stop_time")*1000));
		order.setTotalTime(Long.valueOf((new Date().getTime()-order.getStartTime().getTime())/1000).intValue());
		return order;
	}
	
	//解析桩编号
	public String analyzeConnectorCode(String code){
		//特来电
		Pattern pattern=Pattern.compile("^hlht://(.*)\\..*");
		Matcher matcher=pattern.matcher(code);
		if(matcher.find()){
			return matcher.group(1).trim();
		}else{
			return code;
		}
	}
	
	//解析桩企
	public Integer analyzeProvider(String code){
		//特来电
		JSONArray array=JSONArray.parseArray(Redis.use().get("station_providers"));
		for(int i=0;i<array.size();i++){
			JSONObject json=array.getJSONObject(i);
			if(json.getInteger("length").equals(code.length())){
				return json.getInteger("provider");
			}
		}
		return null;
	}
	
	//将分钟转换成文字描述
	public static String convertMinsToDesc(int secs) {
		StringBuffer str = new StringBuffer();
		int days = secs / (24 * 60 * 60);
		str.append(days + "天");
		int hours = (secs % (24 * 60 * 60)) / (60 * 60);
		str.append(hours + "小时");
		int minutes= ((secs % (24 * 60 * 60)) % (60 * 60)) / 60;
		str.append(minutes + "分钟");
		secs = ((secs % (24 * 60 * 60)) % (60 * 60)) % 60;
		str.append(secs + "秒");
		return str.toString();
	}
	
	//删除充电超时订单
	@Transactional
	public void orderChargeTimeout(Integer orderId){
		OrderInfo order=getOrderInfo(orderId, false);
		List<OrderInfo> orders=new ArrayList<>();
		if(null!=order){
			if(!order.getState().equals(OrderInfo.ORDER_STATE_ENUM.PENDING.getState())){
				log.info("订单状态不正确,orderId="+order.getId()+",state="+order.getState());
				Result.putValue(ResponseCode.CodeEnum.ORDER_STATE_INCORRECT);
				return;
			}
			updateOrderInfoState(order.getId(), null, OrderInfo.ORDER_STATE_ENUM.CANCEL.getState());
			orders.add(order);
		}else{
			Integer timeout=Integer.valueOf(Redis.use().get("auto_cancel_timeout"));
			OrderInfo info=new OrderInfo();
			info.setState(OrderInfo.ORDER_STATE_ENUM.CANCEL.getState());
			QueryExample example=new QueryExample();
			example.and().andEqualTo("state", OrderInfo.ORDER_STATE_ENUM.PENDING.getState())
				.andLessThanOrEqualTo("create_time", DateUtils.addSeconds(new Date(), -timeout));
			orders.addAll(orderInfoMapper.selectByExample(example));
			orderInfoMapper.updateByExampleSelective(info, example);
		}
		//取消调度任务
		for(OrderInfo o:orders){
			taskService.deleteJob(Constants.STATE_JOB_PREFIX+o.getId(), Constants.STATE_GROUP_PREFIX+o.getId());
		}
	}
	
	//查询用户订单
	public List<OrderInfo> queryOrders(Integer userId,List<Integer> states,Integer offset,Integer limit){
		if(null!=userId&&userId>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("user_id", userId).andIn("state", states);
			example.setOrderByClause("create_time desc");
			example.setOffset(offset);
			example.setLimit(limit);
			return orderInfoMapper.selectByExample(example);
		}
		return null;
	}
	
	//查询订单
	public OrderInfo getOrderByBillId(String billId){
		if(StringUtils.isNotEmpty(billId)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("bill_id", billId);
			List<OrderInfo> result=orderInfoMapper.selectByExample(example);
			return result.size()>0?result.get(0):null;
		}
		return null;
	}
	
	//查询订单
	public List<OrderInfo> selectByExample(QueryExample example){
		return orderInfoMapper.selectByExample(example);
	}
	
	//查询订单
	public OrderInfo getOrderInfo(Integer orderId,boolean lock){
		if(null!=orderId&&orderId>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", orderId);
			example.setLock(lock);
			List<OrderInfo> result=orderInfoMapper.selectByExample(example);
			return result.size()>0?result.get(0):null;
		}
		return null;
	}
	
	//更新订单状态
	@Transactional
	public void updateOrderInfoState(Integer orderId,String endCode,Integer state){
		if(null!=orderId&&orderId>0){
			if(null!=endCode||null!=state){
				QueryExample example=new QueryExample();
				example.and().andEqualTo("id", orderId);
				OrderInfo update=new OrderInfo();
				update.setEndCode(endCode);
				update.setState(state);
				orderInfoMapper.updateByExampleSelective(update, example);
			}
		}
	}
	
	//更新订单
	@Transactional
	public void updateOrderInfo(OrderInfo order){
		if(null!=order.getId()&&order.getId()>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", order.getId());
			orderInfoMapper.updateByExampleSelective(order, example);
		}
	}
	
}

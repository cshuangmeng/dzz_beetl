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
	public Map<String,Object> pay(Integer orderId,Float price,Integer couponId){
		//锁定用户账户
		UserInfo user=ThreadCache.getCurrentUserInfo();
		Map<String,Object> param=ThreadCache.getHttpData();
		user=userInfoComponent.getUserInfo(user.getId(), true);
		//检查订单是否可操作
		OrderInfo order=getOrderInfo(orderId, true);
		if(null==order){
			log.info("订单信息不存在,orderId="+orderId);
			Result.putValue(ResponseCode.CodeEnum.ORDER_NOT_EXISTS);
			return null;
		}
		if(!user.getId().equals(order.getUserId())){
			log.info("不能操作非本人订单,orderId="+order.getId()+",login.userId="+user.getId()+",order.userId="+order.getUserId());
			Result.putValue(ResponseCode.CodeEnum.ORDER_NOT_MINE);
			return null;
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
			if(coupon.getCategory().equals(CouponInfo.COUPON_CATEGORY_ENUM.CHARGING.getCategory())){
				if(coupon.getReduceType().equals(CouponInfo.REDUCE_TYPE_ENUM.DISCOUNT.getType())){
					payPrice=order.getTotalPowerPrice()*coupon.getAmount();
				}else if(coupon.getReduceType().equals(CouponInfo.REDUCE_TYPE_ENUM.REDUCE.getType())){
					payPrice=order.getTotalPowerPrice()-coupon.getAmount();
				}
				payPrice=payPrice>0?payPrice:0;
				payPrice+=order.getTotalServiceFee();
			}else if(coupon.getCategory().equals(CouponInfo.COUPON_CATEGORY_ENUM.SERVICEFEE.getCategory())){
				if(coupon.getReduceType().equals(CouponInfo.REDUCE_TYPE_ENUM.DISCOUNT.getType())){
					payPrice=order.getTotalServiceFee()*coupon.getAmount();
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
					,"timeout",residualTime,"retry",Redis.use().get("order_charge_retry"),"unit",Redis.use().get("order_charge_unit"));
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
		if(!json.getString("code").equalsIgnoreCase("ok")){
			log.info("启动充电失败,response="+response);
			Result.putValue(ResponseCode.CodeEnum.FAIL.getValue(),json.getString("info"),Constants.EMPTY);
			return null;
		}
		//保存订单
		OrderInfo order=new OrderInfo();
		order.setConnectorCode(code);
		order.setChargeId(json.getJSONObject("data").getString("chargeId"));
		order.setCreateTime(new Date());
		order.setUserId(user.getId());
		order.setTradeNo(DateUtil.toString(new Date(), DatePattern.TIMESTAMP_WITH_MILLISECOND)+DataUtil.createNums(3));
		//尝试获取桩信息
		ConnectorInfo connectorInfo=connectorInfoComponent.getConnectorInfoByConnectorId(connectorId);
		ChargingStation station=chargingStationComponent.getChargingStationByStationId(null!=connectorInfo?connectorInfo.getStationId():null);
		if(null!=station){
			order.setConnectorId(connectorInfo.getId());
			order.setStationId(station.getId());
		}
		orderInfoMapper.insertSelective(order);
		//启动调度任务
		JobDataMap jobData=JobDataMapSupport.newJobDataMap(DataUtil.mapOf("orderId",String.valueOf(order.getId())));
		JobDetail job = JobBuilder.newJob(CheckChargingStateJob.class).withIdentity(Constants.QUARTZ_JOB_PREFIX+order.getId()
			, Constants.QUARTZ_GROUP_PREFIX+order.getId()).usingJobData(jobData).build();
		taskService.updateCron(job, String.format(Redis.use().get("charge_state_cron"), DateUtil.getSecond(order.getCreateTime())));
		return DataUtil.mapOf("orderId",order.getId(),"timeout",Integer.valueOf(Redis.use().get("order_charge_timeout"))
				,"retry",Redis.use().get("order_charge_retry"),"unit",Redis.use().get("order_charge_unit"));
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
			info.setState(order.getState());
			//充电已启动
			if(order.getState().equals(OrderInfo.ORDER_STATE_ENUM.PENDING.getState())){
				if(info.getChargeState().equalsIgnoreCase(OrderInfo.CHARGE_STATE_ENUM.CHARGE_START.getState())){
					info.setState(OrderInfo.ORDER_STATE_ENUM.CHARGING.getState());
					order.setState(info.getState());
				}
			}
			//补充运营商信息
			ChargingStation station=chargingStationComponent.getChargingStation(order.getStationId());
			if(null!=station){
				info.setProviderName(station.getProvider());
			}
			//国家电网的不收取服务费
			if((null!=station&&station.getProviderId().equals(Constants.GJDW_PROVIDER_ID))||StringUtils.isNotEmpty(info.getEndCode())){
				info.setTotalServiceFee(0F);
				info.setTotalPowerPrice(info.getTotalMoney());
				info.setTotalPrice(info.getTotalPowerPrice());
			}
			info.setId(order.getId());
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
			if(!json.getString("code").equalsIgnoreCase("ok")){
				log.info("结束充电失败,response="+response);
				Result.putValue(ResponseCode.CodeEnum.FAIL.getValue(),json.getString("info"),Constants.EMPTY);
				return;
			}
			updateOrderInfoState(order.getId(), null, OrderInfo.ORDER_STATE_ENUM.SETTLEMENT.getState());
		}else{
			log.info("订单在此状态下不支持结束充电,orderId="+order.getId()+",state="+order.getState());
		}
	}
	
	//获取账单信息
	public OrderInfo queryChargingBill(Integer orderId){
		UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
		//检查订单是否可操作
		OrderInfo order=getOrderInfo(orderId, false);
		if(null==order){
			log.info("订单信息不存在,orderId="+orderId);
			Result.putValue(ResponseCode.CodeEnum.ORDER_NOT_EXISTS);
			return null;
		}
		if(!user.getId().equals(order.getUserId())){
			log.info("不能操作非本人订单,orderId="+order.getId()+",login.userId="+user.getId()+",order.userId="+order.getUserId());
			Result.putValue(ResponseCode.CodeEnum.ORDER_NOT_MINE);
			return null;
		}
		List<Integer> states=Arrays.asList(OrderInfo.ORDER_STATE_ENUM.PENDING.getState(),OrderInfo.ORDER_STATE_ENUM.CHARGING.getState()
				,OrderInfo.ORDER_STATE_ENUM.CANCEL.getState(),OrderInfo.ORDER_STATE_ENUM.REFUND.getState());
		if(states.contains(order.getState())){
			log.info("订单在此状态下不支持查询账单信息,orderId="+order.getId()+",state="+order.getState());
			Result.putValue(ResponseCode.CodeEnum.ORDER_STATE_INCORRECT);
			return null;
		}
		//账单已同步
		if(order.getState().equals(OrderInfo.ORDER_STATE_ENUM.NO_PAY.getState())
				||OrderInfo.END_CHARGING__STATES.contains(order.getState())){
			return order;
		}else{//账单未同步
			OrderInfo info=getChargingBill(order.getBillId());
			if(null!=info){
				info.setEndCode(order.getEndCode());
				info.setProvider(order.getProvider());
			}
			return info;
		}
	}
	
	//同步账单信息
	@Transactional
	public boolean syncChargingBill(){
		String text=JSONObject.toJSONString(ThreadCache.getData(Constants.HTTP_PARAM));
		JSONObject json=JSONObject.parseObject(text);
		OrderInfo order=getOrderByBillId(json.getString("bill_id"));
		if(null==order){
			log.info("订单信息不存在,billId="+json.getString("bill_id"));
			return false;
		}
		float serviceFee=Float.parseFloat(Redis.use().get("charging_service_fee"));
		OrderInfo info=new OrderInfo();
		ConnectorInfo connector=connectorInfoComponent.getConnectorInfoByConnectorId(json.getString("ConnectorID"));
		info.setBillId(json.getString("bill_id"));
		info.setConnectorId(null!=connector?connector.getId():null);
		info.setStartTime(new Date(json.getLongValue("StartTime")*1000));
		info.setEndTime(new Date(json.getLongValue("EndTime")*1000));
		info.setTotalPower(json.getFloat("TotalPower"));
		info.setTotalPowerPrice(json.getFloat("TotalElecMoney"));
		info.setTotalServiceFee(DataUtil.round(info.getTotalPower()*serviceFee, 2));
		info.setTotalPrice(DataUtil.round(info.getTotalPowerPrice()+info.getTotalServiceFee(), 2));
		//设置充电状态
		OrderInfo cs=getChargingState(order.getChargeId());
		info.setChargeState(cs.getChargeState());
		info.setEndCode(cs.getEndCode());
		//补全其他信息
		ChargingStation station=chargingStationComponent.getChargingStationByStationId(null!=connector?connector.getStationId():null);
		info.setStationId(null!=station?station.getId():null);
		//将订单状态置为待支付
		List<Integer> states=Arrays.asList(OrderInfo.ORDER_STATE_ENUM.CHARGING.getState(),OrderInfo.ORDER_STATE_ENUM.SETTLEMENT.getState());
		if(states.contains(order.getState())){
			info.setState(OrderInfo.ORDER_STATE_ENUM.NO_PAY.getState());
			//取消调度任务
			taskService.deleteJob(Constants.QUARTZ_JOB_PREFIX+order.getId(), Constants.QUARTZ_GROUP_PREFIX+order.getId());
		}
		info.setId(order.getId());
		updateOrderInfo(info);
		return true;
	}
	
	//获取我的充电订单列表
	public Map<String,Object> queryOrderList(Integer page,String state){
		UserInfo user=ThreadCache.getCurrentUserInfo();
		List<Integer> states=null;
		if(StringUtils.isNotEmpty(state)){
			states=Arrays.asList(state.split(",")).stream().map(o->Integer.valueOf(o)).collect(Collectors.toList());
		}else{
			states=OrderInfo.END_CHARGING__STATES;
		}
		Integer limit=JSONObject.parseObject(Redis.use().get("user_order_list_config")).getInteger("size");
		Integer offset=(page>0?page-1:0)*limit;
		List<Map<Object,Object>> result=queryOrders(user.getId(), states, offset, limit).stream().map(o->{
			ChargingStation station=chargingStationComponent.getChargingStation(o.getStationId());
			return DataUtil.mapOf("stationName",null!=station?station.getTitle():null,"startTime",o.getStartTime(),"endTime",o.getEndTime()
					,"totalPower",o.getTotalPower(),"totalPrice",o.getTotalPrice(),"totalTime",o.getTotalTime()
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
		if(null==json){
			log.info("查询充电状态请求失败,response="+response);
			Result.putValue(ResponseCode.CodeEnum.FAIL);
			return null;
		}
		if(!json.getString("code").equalsIgnoreCase("ok")){
			log.info("查询充电状态失败,response="+response);
			Result.putValue(ResponseCode.CodeEnum.FAIL.getValue(),json.getString("info"),Constants.EMPTY);
			return null;
		}
		json=json.getJSONObject("data");
		float serviceFee=Float.parseFloat(Redis.use().get("charging_service_fee"));
		ConnectorInfo connector=connectorInfoComponent.getConnectorInfoByConnectorId(json.getString("ConnectorID"));
		OrderInfo order=new OrderInfo();
		order.setChargeId(chargeId);
		order.setConnectorId(null!=connector?connector.getId():null);
		order.setChargeState(json.getString("StartChargeSeqStat"));
		order.setCurrent(null!=json.getFloat("powerCurrent")?json.getFloat("powerCurrent"):0);
		order.setTotalPowerPrice((null!=json.getFloat("ElecMoney")?json.getFloat("ElecMoney"):0)/100);
		order.setTotalPower(null!=json.getFloat("TotalPower")?json.getFloat("TotalPower"):0);
		order.setTotalServiceFee(DataUtil.round(order.getTotalPower()*serviceFee, 2));
		order.setTotalPrice(DataUtil.round(order.getTotalPowerPrice()+order.getTotalServiceFee(), 2));
		order.setTotalMoney(null!=json.getFloat("TotalMoney")?json.getFloat("TotalMoney"):0);
		order.setTotalTime(json.getInteger("totalTime"));
		order.setEndCode(json.getString("code"));
		order.setSoc(json.getString("Soc"));
		order.setPower(json.getFloat("power"));
		order.setProvider(json.getInteger("type"));
		order.setBillId(json.getString("bill_id"));
		return order;
	}
	
	//获取账单信息
	public OrderInfo getChargingBill(String billId){
		if(StringUtils.isEmpty(billId)){
			log.info("必要参数未填写,billId="+billId);
			Result.putValue(ResponseCode.CodeEnum.REQUIRED_PARAM_NULL);
			return null;
		}
		String response=chargeClientBuilder.checkChargeOrders(billId);
		JSONObject json=DataUtil.isJSONObject(response)?JSONObject.parseObject(response):null;
		if(null==json){
			log.info("获取账单信息请求失败,response="+response);
			Result.putValue(ResponseCode.CodeEnum.FAIL);
			return null;
		}
		if(!json.getString("code").equalsIgnoreCase("ok")){
			log.info("获取账单信息失败,response="+response);
			Result.putValue(ResponseCode.CodeEnum.FAIL.getValue(),json.getString("info"),Constants.EMPTY);
			return null;
		}
		float serviceFee=Float.parseFloat(Redis.use().get("charging_service_fee"));
		json=json.getJSONObject("data");
		ConnectorInfo connector=connectorInfoComponent.getConnectorInfoByConnectorId(json.getString("ConnectorID"));
		OrderInfo order=new OrderInfo();
		order.setBillId(json.getString("bill_id"));
		order.setConnectorId(null!=connector?connector.getId():null);
		order.setStartTime(new Date(json.getLongValue("StartTime")*1000));
		order.setEndTime(new Date(json.getLongValue("EndTime")*1000));
		order.setTotalPower(json.getFloat("TotalPower"));
		order.setTotalPowerPrice(json.getFloat("TotalElecMoney"));
		order.setTotalServiceFee(DataUtil.round(order.getTotalPower()*serviceFee, 2));
		order.setTotalPrice(DataUtil.round(order.getTotalPowerPrice()+order.getTotalServiceFee(), 2));
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
			taskService.deleteJob(Constants.QUARTZ_JOB_PREFIX+o.getId(), Constants.QUARTZ_GROUP_PREFIX+o.getId());
		}
	}
	
	//创建AccessToken供第三发应用调用
	public Map<String,Object> queryToken(){
		String token=DataUtil.buildUUID().toUpperCase();
		return DataUtil.mapOf("Ret",0,"Msg","","Data",DataUtil.mapOf("AccessToken",token,"TokenAvailableTime",7200,"SuccStat",0));
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
	public void updateOrderInfoState(Integer orderId,String chargeState,Integer state){
		if(null!=orderId&&orderId>0){
			if(null!=chargeState||null!=state){
				QueryExample example=new QueryExample();
				example.and().andEqualTo("id", orderId);
				OrderInfo update=new OrderInfo();
				update.setChargeState(chargeState);
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

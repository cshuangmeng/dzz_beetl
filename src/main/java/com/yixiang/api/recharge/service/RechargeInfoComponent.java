package com.yixiang.api.recharge.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
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
import com.yixiang.api.order.pojo.CouponInfo;
import com.yixiang.api.order.pojo.TradeHistory;
import com.yixiang.api.order.service.CouponInfoComponent;
import com.yixiang.api.order.service.TradeHistoryComponent;
import com.yixiang.api.recharge.mapper.RechargeInfoMapper;
import com.yixiang.api.recharge.pojo.RechargeInfo;
import com.yixiang.api.recharge.pojo.RechargeTemplate;
import com.yixiang.api.refund.pojo.RefundSummary;
import com.yixiang.api.refund.service.RefundInfoComponent;
import com.yixiang.api.refund.service.RefundSummaryComponent;
import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.user.service.UserInfoComponent;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.PayClientBuilder;
import com.yixiang.api.util.ResponseCode;
import com.yixiang.api.util.Result;
import com.yixiang.api.util.ThreadCache;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class RechargeInfoComponent {

	@Autowired
	private RechargeInfoMapper rechargeInfoMapper;
	@Autowired
	private RechargeTemplateComponent rechargeTemplateComponent;
	@Autowired
	private PayClientBuilder payClientBuilder;
	@Autowired
	private UserInfoComponent userInfoComponent;
	@Autowired
	private TradeHistoryComponent tradeHistoryComponent;
	@Autowired
	private RefundSummaryComponent refundSummaryComponent;
	@Autowired
	private RefundInfoComponent refundInfoComponent;
	@Autowired
	private CouponInfoComponent couponInfoComponent;
	
	Logger log=LoggerFactory.getLogger(getClass());
	
	//生成充值请求
	@Transactional
	public Map<String,Object> buildRechargeRequest(Integer templateId,Integer payWay){
		UserInfo user=(UserInfo)ThreadCache.getData(Constants.USER);
		//检查充值模板是否可用
		RechargeTemplate template=rechargeTemplateComponent.getRechargeTemplate(templateId);
		if(null==template){
			log.info("充值模板不存在,templateId="+templateId);
			Result.putValue(ResponseCode.CodeEnum.TEMPLATE_NOT_EXISTS);
			return null;
		}
		if(!template.getState().equals(Constants.YES)){
			log.info("充值模板不可用,templateId="+templateId+",template.state="+template.getState());
			Result.putValue(ResponseCode.CodeEnum.TEMPLATE_NOT_ENABLE);
			return null;
		}
		//保存充值请求
		RechargeInfo info=new RechargeInfo();
		info.setBonus(template.getBonus());
		info.setCreateTime(new Date());
		info.setPayWay(payWay);
		info.setPrice(template.getPrice());
		info.setTemplateId(template.getId());
		info.setTradeNo(DateUtil.toString(new Date(), DatePattern.TIMESTAMP_WITH_MILLISECOND)+DataUtil.createNums(3));
		info.setUserId(user.getId());
		rechargeInfoMapper.insertSelective(info);
		//组装支付信息
		Integer orderType=RefundSummary.ORDER_TYPE_ENUM.RECHARGE.getType();
		JSONObject json=JSONArray.parseArray(Redis.use().get("pay_type_config")).getJSONObject(orderType-1);
		Map<String,Object> payInfo=payClientBuilder.prepay(info.getPayWay(), info.getTradeNo(), info.getPrice()
				, json.getString("title"), json.getString("body"), String.valueOf(orderType)
				, ThreadCache.getData(Constants.IP).toString(), null);
		return DataUtil.mapOf("payInfo",payInfo);
	}
	
	//充值成功
	@Transactional
	public boolean paySuccessCallback(String tradeNo,String outTradeNo,Float price){
		//检查充值请求是否可支付
		RechargeInfo info=getRechargeInfoByTradeNo(tradeNo);
		if(null==info){
			log.info("充值请求不存在,tradeNo="+tradeNo+",outTradeNo="+outTradeNo);
			return false;
		}
		if(!info.getState().equals(RechargeInfo.STATE_TYPE_ENUM.NOPAY.getState())){
			log.info("充值请求状态不正确,tradeNo="+tradeNo+",outTradeNo="+outTradeNo+",state="+info.getState());
			return false;
		}
		if(!price.equals(info.getPrice())){
			log.info("支付金额不正确,price="+price+",recharge.price="+info.getPrice());
			return false;
		}
		//充值到用户账户
		BigDecimal interval=new BigDecimal(info.getPrice()+info.getBonus()).setScale(2, BigDecimal.ROUND_HALF_UP);
		userInfoComponent.addBalance(info.getUserId(), interval);
		//记录流水
		if(info.getPrice()>0){
			tradeHistoryComponent.saveTradeHistory(info.getUserId(), info.getId(), TradeHistory.TRADE_TYPE_ENUM.RECHARGE.getType()
					, info.getPrice(), TradeHistory.TRADE_STATE_ENUM.YICHULI.getState(), null);
		}
		if(info.getBonus()>0){
			tradeHistoryComponent.saveTradeHistory(info.getUserId(), info.getId(), TradeHistory.TRADE_TYPE_ENUM.RECHARGE_GIVE.getType()
					, info.getBonus(), TradeHistory.TRADE_STATE_ENUM.YICHULI.getState(), null);
		}
		//记录退款汇总信息
		refundSummaryComponent.saveRefundSummary(info.getUserId(), info.getId(), RefundSummary.ORDER_TYPE_ENUM.RECHARGE.getType()
				, price, 0F);
		//更新充值请求状态
		updateRechargeInfoState(info.getId(), outTradeNo, RechargeInfo.STATE_TYPE_ENUM.PAYED.getState());
		return true;
	}
	
	//退款充值
	@Transactional
	public Map<String,Object> refundRecharge(String reason){
		//锁定用户信息
		UserInfo user=ThreadCache.getCurrentUserInfo();
		user=userInfoComponent.getUserInfo(user.getId(), true);
		//计算应退金额,积分墙兑换的余额不退
		float jfqPrice=couponInfoComponent.getUserCoupons(user.getId(), CouponInfo.COUPON_CATEGORY_ENUM.RECHARGE.getCategory())
				.stream().map(i->i.getAmount()).reduce((a,b)->a+b).get();
		float refundPrice=user.getBalance().subtract(new BigDecimal(jfqPrice)).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
		List<RechargeInfo> recharges=null;
		if(refundPrice>0){
			recharges=queryRechargeInfos(user.getId(), RechargeInfo.STATE_TYPE_ENUM.PAYED.getState());
			for(RechargeInfo o:recharges){
				refundPrice-=o.getBonus();
			}
		}
		refundPrice=new BigDecimal(refundPrice).setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
		log.info("退款用户:"+user.getId()+",应退金额为:"+user.getBalance()+",减完赠送金额后实退金额为:"+refundPrice);
		if(refundPrice<=0){
			log.info("已无可退金额,userId="+user.getId()+",refundPrice="+refundPrice);
			Result.putValue(ResponseCode.CodeEnum.BALANCE_NOT_ENOUGH);
			return null;
		}
		//保存退款记录
		Integer orderType=RefundSummary.ORDER_TYPE_ENUM.RECHARGE.getType();
		Integer tradeType=TradeHistory.TRADE_TYPE_ENUM.RECHARGE_REFUND.getType();
		float refundable=0;
		float third=0;
		TradeHistory history=null;
		for(RechargeInfo o:recharges){
			RefundSummary summary=refundSummaryComponent.getRefundSummary(o.getId(), orderType);
			if(null==summary){
				log.info("未找到退款汇总记录:RechargeInfo.id"+o.getId()+",已跳过");
				continue;
			}
			refundable=summary.getThird()-summary.getThirdRefund();
			if(refundable>0){
				if(refundable>=refundPrice){
					summary.setThirdRefund(summary.getThirdRefund()+refundPrice);
					third=refundPrice;
					refundPrice=0;
				}else{
					summary.setThirdRefund(summary.getThirdRefund()+refundable);
					third=refundable;
					refundPrice-=refundable;
				}
				//记录流水
				history=tradeHistoryComponent.saveTradeHistory(user.getId(), o.getId(), tradeType, -third, null, null);
				refundInfoComponent.saveRefundInfo(user.getId(), o.getId(), orderType, third, 0F, o.getOutTradeNo()
						, o.getPayWay(), reason, null, history.getId());
				refundSummaryComponent.updateRefundSummary(summary);
				updateRechargeInfoState(o.getId(), null, RechargeInfo.STATE_TYPE_ENUM.REFUNDED.getState());
			}
			if(refundPrice<=0){
				break;
			}
		}
		//清空账户余额
		user.setBalance(new BigDecimal(jfqPrice));
		userInfoComponent.updateUserInfo(user);
		JSONObject json=JSONObject.parseObject(Redis.use().get("refund_submit_remind"));
		return DataUtil.mapOf("desc",String.format(json.getString("remind")
				, DateUtil.toString(new Date(), json.getString("remind_date_pattern"))));
	}
	
	//下发退款说明相关文案
	public Map<String,Object> getRefundExplain(){
		JSONObject json=JSONObject.parseObject(Redis.use().get("refund_submit_remind"));
		return DataUtil.mapOf("top",json.getJSONArray("top"),"bottom",json.getJSONArray("bottom"));
	} 
	
	//获取充值请求
	public RechargeInfo getRechargeInfoByTradeNo(String tradeNo){
		if(StringUtils.isNotEmpty(tradeNo)){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("trade_no", tradeNo);
			List<RechargeInfo> result=rechargeInfoMapper.selectByExample(example);
			return result.size()>0?result.get(0):null;
		}
		return null;
	}
	
	//获取充值请求
	public List<RechargeInfo> queryRechargeInfos(Integer userId,Integer state){
		if(null!=userId&&userId>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("user_id", userId).andEqualTo("state", state);
			return rechargeInfoMapper.selectByExample(example);
		}
		return null;
	}
	
	//更新充值请求状态
	@Transactional
	public void updateRechargeInfoState(Integer id,String outTradeNo,Integer state){
		if(null!=id&&id>0){
			if(null!=outTradeNo||null!=state){
				QueryExample example=new QueryExample();
				example.and().andEqualTo("id", id);
				RechargeInfo update=new RechargeInfo();
				update.setOutTradeNo(outTradeNo);
				update.setState(state);
				if(update.getState().equals(RechargeInfo.STATE_TYPE_ENUM.PAYED.getState())){//设置支付时间
					update.setPayTime(new Date());
				}else if(update.getState().equals(RechargeInfo.STATE_TYPE_ENUM.REFUNDED.getState())){//设置退款时间
					update.setRefundTime(new Date());
				}
				rechargeInfoMapper.updateByExampleSelective(update, example);
			}
		}
	}
	
}

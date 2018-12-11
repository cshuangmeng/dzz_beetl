package com.yixiang.api.refund.service;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import com.feilong.core.DatePattern;
import com.feilong.core.date.DateUtil;
import com.yixiang.api.order.pojo.TradeHistory;
import com.yixiang.api.order.service.TradeHistoryComponent;
import com.yixiang.api.refund.mapper.RefundInfoMapper;
import com.yixiang.api.refund.pojo.RefundInfo;
import com.yixiang.api.refund.pojo.RefundSummary;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.PayClientBuilder;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class RefundInfoComponent {
	
	@Autowired
	private RefundInfoMapper refundInfoMapper;
	@Autowired
	private PayClientBuilder payClientBuilder;
	@Autowired
	private RefundSummaryComponent refundSummaryComponent;
	@Autowired
	private TradeHistoryComponent tradeHistoryComponent;
	
	Logger log=LoggerFactory.getLogger(getClass());
	
	//处理退款请求
	public void executeRefund(){
		List<RefundInfo> refunds=queryRefundInfos(RefundInfo.REFUND_STATE_ENUM.DAICHULI.getState());
		refunds.stream().forEach(o->{
			RefundSummary summary=refundSummaryComponent.getRefundSummary(o.getOrderId(), o.getOrderType());
			if(null!=summary){
				String response=payClientBuilder.refund(o.getPayWay(), o.getOutTradeNo(), summary.getThird()
						, o.getThirdPrice(), o.getRemark(), o.getTradeNo());
				if(response.equalsIgnoreCase(Constants.SUCCESS)){
					o.setState(RefundInfo.REFUND_STATE_ENUM.CHULIZHONG.getState());
					//更新流水记录状态
					TradeHistory history=tradeHistoryComponent.getTradeHistory(o.getTradeHistoryId());
					history.setState(TradeHistory.TRADE_STATE_ENUM.CHULIZHONG.getState());
					tradeHistoryComponent.updateTradeHistory(history);
				}
				o.setResponse(response);
				o.setUpdateTime(new Date());
				updateRefundInfo(o);
			}else{
				log.info("未找到退款汇总信息,Refund.id="+o.getId());
			}
		});
		log.info("本次处理退款请求"+refunds.size()+"条");
	}
	
	//检查退款进度
	public void checkRefund(){
		List<RefundInfo> refunds=queryRefundInfos(RefundInfo.REFUND_STATE_ENUM.CHULIZHONG.getState());
		refunds.stream().forEach(o->{
			JSONObject json=payClientBuilder.refundQuery(o.getPayWay(), o.getOutTradeNo(), o.getTradeNo());
			String state=Constants.FAIL;
			if(o.getPayWay().equals(Constants.WEIXINPAY)){
				state=json.getString("refund_status_0");
			}else if(o.getPayWay().equals(Constants.ALIPAY)){
				if(json.getString("out_request_no").equals(o.getTradeNo())){
					state=Constants.SUCCESS;
				}
			}
			//更新流水记录状态
			if(state.equalsIgnoreCase(Constants.SUCCESS)){
				TradeHistory history=tradeHistoryComponent.getTradeHistory(o.getTradeHistoryId());
				history.setState(TradeHistory.TRADE_STATE_ENUM.YICHULI.getState());
				tradeHistoryComponent.updateTradeHistory(history);
				o.setState(RefundInfo.REFUND_STATE_ENUM.YICHULI.getState());
				o.setUpdateTime(new Date());
				updateRefundInfo(o);
			}
		});
		log.info("本次检查退款请求"+refunds.size()+"条");
	}
	
	//保存退款信息
	@Transactional
	public void saveRefundInfo(Integer userId,Integer orderId,Integer orderType,Float third,Float balance
			,String outTradeNo,Integer payWay,String reason,Integer state,Integer tradeHistoryId){
		RefundInfo refund=new RefundInfo();
		refund.setBalancePrice(null!=balance?balance:0);
		refund.setCreateTime(new Date());
		refund.setOrderId(orderId);
		refund.setOrderType(orderType);
		refund.setOutTradeNo(outTradeNo);
		refund.setPayWay(payWay);
		refund.setReason(reason);
		refund.setState(state);
		refund.setThirdPrice(null!=third?third:0);
		refund.setTotalPrice(refund.getBalancePrice()+refund.getThirdPrice());
		refund.setTradeHistoryId(tradeHistoryId);
		refund.setTradeNo(DateUtil.toString(new Date(), DatePattern.TIMESTAMP_WITH_MILLISECOND)+DataUtil.createNums(3));
		refund.setUserId(userId);
		refundInfoMapper.insertSelective(refund);
	}
	
	//获取退款信息
	public List<RefundInfo> queryRefundInfos(Integer state){
		if(null!=state){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("state", state);
			return refundInfoMapper.selectByExample(example);
		}
		return null;
	}

	//获取退款信息
	public RefundInfo getRefundInfo(Integer id){
		if(null!=id&&id>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", id);
			List<RefundInfo> result=refundInfoMapper.selectByExample(example);
			return result.size()>0?result.get(0):null;
		}
		return null;
	}
	
	//更新退款信息
	@Transactional
	public void updateRefundInfo(RefundInfo info){
		if(null!=info.getId()&&info.getId()>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", info.getId());
			refundInfoMapper.updateByExampleSelective(info, example);
		}
	}
	
}

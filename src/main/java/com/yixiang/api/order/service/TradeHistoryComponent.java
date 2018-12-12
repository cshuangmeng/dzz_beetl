package com.yixiang.api.order.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import com.feilong.core.DatePattern;
import com.feilong.core.date.DateUtil;
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.order.mapper.TradeHistoryMapper;
import com.yixiang.api.order.pojo.TradeHistory;
import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.user.service.UserInfoComponent;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.ThreadCache;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class TradeHistoryComponent {
	
	@Autowired
	private TradeHistoryMapper tradeHistoryMapper;
	@Autowired
	private UserInfoComponent userInfoComponent;
	
	//消费明细
	private final static Integer CONSUME_TYPE = 1;
	//充值记录
	private final static Integer RECHARGE_TYPE = 2;
	//兑换记录
	private final static Integer REDEEM_TYPE = 3;
	
	//保存交易记录
	@Transactional
	public TradeHistory saveTradeHistory(Integer userId,Integer tradeId,Integer tradeType
			,Float amount,Integer state,String remark){
		UserInfo user=userInfoComponent.getUserInfo(userId, false);
		JSONObject json=JSONObject.parseObject(Redis.use().get("trade_type_config")).getJSONObject("trade_type_"+tradeType);
		TradeHistory trade=new TradeHistory();
		trade.setAmount(new BigDecimal(amount).setScale(2, BigDecimal.ROUND_HALF_UP));
		trade.setBalance(user.getBalance());
		trade.setCreateTime(new Date());
		trade.setRemark(remark);
		trade.setState(state);
		trade.setTitle(json.getString("title"));
		trade.setTradeId(tradeId);
		trade.setTradeNo(DateUtil.toString(new Date(), DatePattern.TIMESTAMP_WITH_MILLISECOND)+DataUtil.createNums(3));
		trade.setTradeType(tradeType);
		trade.setUserId(user.getId());
		tradeHistoryMapper.insertSelective(trade);
		return trade;
	}
	
	//获取交易记录
	public Map<String,Object> queryTradeHistoryList(Integer page, Integer type){
		UserInfo user=ThreadCache.getCurrentUserInfo();
		Integer limit=JSONObject.parseObject(Redis.use().get("user_trade_history_config")).getInteger("size");
		Integer offset=(page>0?page-1:0)*limit;
		List<Integer> tradeTypes=null;
		if(null!=type){
			if(type.equals(CONSUME_TYPE)){
				tradeTypes=Arrays.asList(TradeHistory.TRADE_TYPE_ENUM.CHARGE_PAY.getType()
						,TradeHistory.TRADE_TYPE_ENUM.CHARGE_REFUND.getType());
			}else if(type.equals(RECHARGE_TYPE)){
				tradeTypes=Arrays.asList(TradeHistory.TRADE_TYPE_ENUM.RECHARGE.getType()
						,TradeHistory.TRADE_TYPE_ENUM.RECHARGE_GIVE.getType(),TradeHistory.TRADE_TYPE_ENUM.RECHARGE_REFUND.getType());
			}else if(type.equals(REDEEM_TYPE)){
				tradeTypes=Arrays.asList(TradeHistory.TRADE_TYPE_ENUM.JFQ_RECHARGE.getType());
			}
		}
		List<Map<Object,Object>> result=queryTradeHistorys(user.getId(),tradeTypes,offset,limit).stream().map(o->{
			return DataUtil.mapOf("title",o.getTitle(),"createTime",o.getCreateTime(),"amount",o.getAmount());
		}).collect(Collectors.toList());
		return DataUtil.mapOf("dataset",result);
	}
	
	//获取交易记录信息
	public TradeHistory getTradeHistory(Integer id){
		if(null!=id&&id>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", id);
			List<TradeHistory> list=selectByExample(example);
			return list.size()>0?list.get(0):null;
		}
		return null;
	}
	
	//获取交易记录信息
	public List<TradeHistory> queryTradeHistorys(Integer userId,List<Integer> tradeTypes,Integer offset,Integer limit){
		if(null!=userId&&userId>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("user_id", userId).andIn("trade_type", tradeTypes)
				.andNotEqualTo("state", TradeHistory.TRADE_STATE_ENUM.YISHANCHU.getState());
			example.setOrderByClause("create_time desc");
			example.setOffset(offset);
			example.setLimit(limit);
			return selectByExample(example);
		}
		return null;
	}
	
	//更新交易记录信息
	public int updateTradeHistory(TradeHistory history){
		if(null!=history.getId()&&history.getId()>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", history.getId());
			return updateByExampleSelective(history, example);
		}
		return 0;
	}

	//获取结果集大小
	public long countByExample(QueryExample example) {
		return tradeHistoryMapper.countByExample(example);
	}

	//保存
	public int insertSelective(TradeHistory record) {
		return tradeHistoryMapper.insertSelective(record);
	}

	//获取结果集
	public List<TradeHistory> selectByExample(QueryExample example) {
		return tradeHistoryMapper.selectByExample(example);
	}

	//更新
	public int updateByExampleSelective(TradeHistory record, QueryExample example) {
		return tradeHistoryMapper.updateByExampleSelective(record, example);
	}

}

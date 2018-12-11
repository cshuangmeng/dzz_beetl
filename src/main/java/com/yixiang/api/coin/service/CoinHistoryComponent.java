package com.yixiang.api.coin.service;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.assertj.core.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.coin.mapper.CoinHistoryMapper;
import com.yixiang.api.coin.pojo.CoinHistory;
import com.yixiang.api.user.pojo.UserDevice;
import com.yixiang.api.user.pojo.UserInfo;
import com.yixiang.api.user.service.UserDeviceComponent;
import com.yixiang.api.user.service.UserInfoComponent;
import com.yixiang.api.util.PushUtil;
import com.yixiang.api.util.pojo.QueryExample;

@Service
public class CoinHistoryComponent {
	
	@Autowired
	private CoinHistoryMapper coinHistoryMapper;
	@Autowired
	private UserInfoComponent userInfoComponent;
	@Autowired
	private UserDeviceComponent userDeviceComponent;
	
	//下发会员积分
	@Transactional
	public boolean giveCoins(Integer userId,Integer balance,String service){
		String value=Redis.use().get("give_coin_config");
		if(StringUtils.isNotEmpty(value)){
			JSONObject json=JSONObject.parseObject(value);
			if(json.containsKey(service)){
				json=json.getJSONObject(service);
				//检查是否已达赠送上限
				QueryExample example=new QueryExample();
				Date endDate=DateUtil.tomorrow();
				endDate=DateUtils.truncate(endDate, Calendar.DAY_OF_MONTH);
				endDate=DateUtils.addMilliseconds(endDate, -1);
				Date startDate=DateUtil.now();
				startDate=DateUtils.truncate(startDate, Calendar.DAY_OF_MONTH);
				startDate=DateUtils.addDays(startDate, json.getIntValue("day")-1);
				example.and().andEqualTo("user_id", userId).andEqualTo("trade_type", json.getInteger("tradeType"))
					.andBetween("create_time", startDate, endDate).andIn("state", Arrays.asList(CoinHistory.HISTORY_STATE_ENUM.DAICHULI.getState()
					,CoinHistory.HISTORY_STATE_ENUM.CHULIZHONG.getState(),CoinHistory.HISTORY_STATE_ENUM.YICHULI.getState()));
				//未达到赠送上限,下发会员积分
				if(selectByExample(example).size()<json.getIntValue("max")&&json.getInteger("coin")>0){
					CoinHistory history=new CoinHistory();
					history.setAmount(json.getInteger("coin"));
					history.setBalance(balance+history.getAmount());
					history.setCreateTime(DateUtil.now());
					history.setState(CoinHistory.HISTORY_STATE_ENUM.YICHULI.getState());
					history.setTitle(json.getString("title"));
					history.setTradeType(json.getInteger("tradeType"));
					history.setUserId(userId);
					insertSelective(history);
					userInfoComponent.addCoins(history.getUserId(), history.getAmount());
					//发送push通知
					value=Redis.use().get("coin_changed_push");
					if(StringUtils.isNotEmpty(value)){
						json=JSONObject.parseObject(value);
						String alert=json.getJSONObject("notification").getJSONObject("android").getString("alert");
						json.getJSONObject("notification").getJSONObject("android").put("alert", String.format(alert, history.getAmount()));
						json.getJSONObject("notification").getJSONObject("ios").put("alert", String.format(alert, history.getAmount()));
						UserInfo user=userInfoComponent.getUserInfo(userId, false);
						UserDevice device=null!=user?userDeviceComponent.getUserDevice(user.getDeviceId()):null;
						if(null!=device&&StringUtils.isNotEmpty(device.getRegistrationId())){
							PushUtil.push(device.getRegistrationId(), json);
						}
					}
					return true;
				}
			}
		}
		return false;
	}
	
	//获取虚拟币交易记录信息
	public CoinHistory getCoinHistory(Integer id){
		if(null!=id&&id>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", id);
			List<CoinHistory> list=selectByExample(example);
			return list.size()>0?list.get(0):null;
		}
		return null;
	}
	
	//更新虚拟币交易记录信息
	public int updateCoinHistory(CoinHistory history){
		if(null!=history.getId()&&history.getId()>0){
			QueryExample example=new QueryExample();
			example.and().andEqualTo("id", history.getId());
			return updateByExampleSelective(history, example);
		}
		return 0;
	}

	//计算结果集大小
	public long countByExample(QueryExample example) {
		return coinHistoryMapper.countByExample(example);
	}

	//保存
	public int insertSelective(CoinHistory record) {
		return coinHistoryMapper.insertSelective(record);
	}

	//获取结果集
	public List<CoinHistory> selectByExample(QueryExample example) {
		return coinHistoryMapper.selectByExample(example);
	}

	//更新
	public int updateByExampleSelective(CoinHistory record, QueryExample example) {
		return coinHistoryMapper.updateByExampleSelective(record, example);
	}

}

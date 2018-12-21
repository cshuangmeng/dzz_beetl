package com.yixiang.api.quartz;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.jfinal.plugin.redis.Redis;
import com.yixiang.api.order.pojo.OrderInfo;
import com.yixiang.api.order.service.OrderInfoComponent;
import com.yixiang.api.util.pojo.QueryExample;

public class PayChargingOrderJob implements Job {
	
	@Autowired
	private OrderInfoComponent orderInfoComponent;
	
	Logger log=LoggerFactory.getLogger(getClass());
	
	public PayChargingOrderJob(){}

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		//获取逾期未支付的订单执行自动扣款操作
		String value=Redis.use().get("auto_pay_timeout");
		if(StringUtils.isEmpty(value)){
			log.info("自动扣款超时时限未配置,放弃处理");
			return;
		}
		int hour=Integer.parseInt(value);
		if(hour<=0){
			log.info("自动扣款超时时限配置不正确,放弃处理,auto_pay_timeout="+hour);
			return;
		}
		QueryExample example=new QueryExample();
		example.and().andEqualTo("state", OrderInfo.ORDER_STATE_ENUM.NO_PAY.getState())
			.andLessThanOrEqualTo("t.date_add(create_time,interval "+hour+" hour)", new Date());
		List<OrderInfo> orders=orderInfoComponent.selectByExample(example);
		log.info("查询到"+orders.size()+"条待处理订单");
		orders.stream().forEach(i->{
			log.info("开始执行自动扣款任务:orderId="+i.getId());
			orderInfoComponent.pay(i.getId(), null, null, false);
		});
	}

}

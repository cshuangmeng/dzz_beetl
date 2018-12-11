package com.yixiang.api.quartz;

import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
	
	@Autowired
	private SchedulerFactoryBean schedulerFactoryBean;

	//暂停任务
	public boolean pauseJob(String jobName,String groupName){
		try {
			Scheduler scheduler = schedulerFactoryBean.getScheduler();
			JobKey jobKey = JobKey.jobKey(jobName, groupName);
			scheduler.pauseJob(jobKey);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	//恢复任务
	public boolean resumeJob(String jobName,String groupName){
		try {
			Scheduler scheduler = schedulerFactoryBean.getScheduler();
			JobKey jobKey = JobKey.jobKey(jobName, groupName);
			scheduler.resumeJob(jobKey);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	//删除任务
	public boolean deleteJob(String jobName,String groupName){
		try {
			Scheduler scheduler = schedulerFactoryBean.getScheduler();
			JobKey jobKey = JobKey.jobKey(jobName, groupName);
			scheduler.deleteJob(jobKey);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	//立即运行任务
	public boolean triggerJob(String jobName,String groupName){
		try {
			Scheduler scheduler = schedulerFactoryBean.getScheduler();
			JobKey jobKey = JobKey.jobKey(jobName, groupName);
			scheduler.triggerJob(jobKey);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	//更新任务
	public boolean updateCron(String jobName, String groupName, String cronExpression){
		try {
			Scheduler scheduler = schedulerFactoryBean.getScheduler();
			TriggerKey triggerKey = TriggerKey.triggerKey(jobName, groupName);
			CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);
			//表达式调度构建器
			CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(new CronExpression(cronExpression));
			//按新的cronExpression表达式重新构建trigger
			trigger = trigger.getTriggerBuilder().withIdentity(triggerKey)
			.withSchedule(scheduleBuilder).build();
			//按新的trigger重新设置job执行
			scheduler.rescheduleJob(triggerKey, trigger);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	//创建任务
	public boolean updateCron(JobDetail job, String cronExpression){
		try {
			Scheduler scheduler = schedulerFactoryBean.getScheduler();
			TriggerKey triggerKey = TriggerKey.triggerKey(job.getKey().getName(), job.getKey().getGroup());
			Trigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey)
					.withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
					.build();
			scheduler.scheduleJob(job, trigger);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

}

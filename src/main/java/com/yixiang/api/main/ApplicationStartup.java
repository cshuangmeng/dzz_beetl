package com.yixiang.api.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import com.jfinal.plugin.redis.Redis;
import com.jfinal.plugin.redis.RedisPlugin;
import com.jfinal.weixin.sdk.api.ApiConfig;
import com.jfinal.weixin.sdk.api.ApiConfigKit;
import com.jfinal.weixin.sdk.cache.RedisAccessTokenCache;
import com.jfinal.wxaapp.WxaConfig;
import com.jfinal.wxaapp.WxaConfigKit;
import com.yixiang.api.util.PropertiesUtil;

@Component()
public class ApplicationStartup implements ApplicationListener<ContextRefreshedEvent> {
	
	Logger log=LoggerFactory.getLogger(getClass());

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		try {
			if(null==Redis.use()){
				//设置小程序账户
				WxaConfig wc=new WxaConfig();
				wc.setAppId(PropertiesUtil.getProperty("wx_mini_appid"));
				wc.setAppSecret(PropertiesUtil.getProperty("wx_mini_appsecret"));
				WxaConfigKit.setWxaConfig(wc);
				WxaConfigKit.setDevMode(Boolean.valueOf(PropertiesUtil.getProperty("devMode")));
				//设置APP开放平台账户
				ApiConfig ac=new ApiConfig();
				ac.setAppId(PropertiesUtil.getProperty("wx_app_appid"));
				ac.setAppSecret(PropertiesUtil.getProperty("wx_app_appsecret"));
				ApiConfigKit.putApiConfig(ac);
				//设置小程序账户
				ac=new ApiConfig();
				ac.setAppId(PropertiesUtil.getProperty("wx_mini_appid"));
				ac.setAppSecret(PropertiesUtil.getProperty("wx_mini_appsecret"));
				ApiConfigKit.putApiConfig(ac);
				ApiConfigKit.setDevMode(Boolean.valueOf(PropertiesUtil.getProperty("devMode")));
				//设置accessToken存储方式
				RedisPlugin rp=new RedisPlugin("mainRedis", PropertiesUtil.getProperty("redis.host")
						, Integer.valueOf(PropertiesUtil.getProperty("redis.port")), PropertiesUtil.getProperty("redis.pwd"));
				rp.start();
				ApiConfigKit.setAccessTokenCache(new RedisAccessTokenCache());
				log.info("Application inited!");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

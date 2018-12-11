package com.yixiang.api.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.feilong.core.lang.ThreadUtil;
import com.jfinal.weixin.sdk.kit.ParaMap;
import com.jfinal.weixin.sdk.utils.HttpUtils;
import com.yixiang.api.util.DataUtil;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class App1 {

	public static void main(String[] args) {
		String url="http://restapi.amap.com/v3/assistant/coordinate/convert";
		Map<String,String> queryParas=ParaMap.create("key", "bb1ab2aa10af0e61619d7c1a5a349e82")
				.put("locations", "116.346839,39.989076")
				.put("coordsys", "baidu")
				.getData();;
        //String json = HttpUtils.get(url, queryParas);
        //System.out.println(json);
		//System.out.println(UUID.randomUUID().toString().replace("-", "").length());
		//test1();
		//test2();
		//test3();
	}
	
	public static void test3(){
		String row=null;
		int index=0;
		try {
			BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream("/Users/huangmeng/Downloads/易享充电/zc3u.txt"),"UTF-8"));
			BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream("/Users/huangmeng/Downloads/易享充电/zc3u.sql")));
			JSONObject json=null;
			List<Map<String,Object>> elect=new ArrayList<Map<String,Object>>();
			List<Map<String,Object>> parking=new ArrayList<Map<String,Object>>();
			while((row=reader.readLine())!=null){
				JSONObject rowJson=JSONObject.parseObject(row);
				index++;
				if(rowJson.getInteger("ret")==0&&rowJson.containsKey("data")){
					json=rowJson.getJSONObject("data");
					//电费
					elect.clear();
					if(json.containsKey("elect_prices")&&DataUtil.isJSONArray(json.getString("elect_prices"))){
						json.getJSONArray("elect_prices").stream().forEach(e->{
							JSONObject item=JSONObject.parseObject(e.toString());
							if(item.containsKey("items")&&DataUtil.isJSONArray(item.getString("items"))&&item.getJSONArray("items").size()>0){
								JSONArray array=item.getJSONArray("items");
								for(int i=0;i<array.size();i++){
									JSONObject obj=array.getJSONObject(i);
									elect.add(DataUtil.mapOf("during",obj.get("duration"),"price",obj.get("price"),"is_high",item.get("is_high")));
								}
							}else{
								elect.add(DataUtil.mapOf("during",item.get("section"),"price",item.get("price")
										,"is_high",item.get("is_high")));
							}
						});
					}
					//停车费
					parking.clear();
					if(!DataUtil.isEmpty(json.get("park_expense"))){
						String [] datas=json.getString("park_expense").split(";");
						for(int i=0;i<datas.length;i++){
							if(datas[i].indexOf(",")>0){
								parking.add(DataUtil.mapOf("price",datas[i].split(",")[1].trim()
										,"during",datas[i].split(",")[0].trim(),"is_underground",json.getInteger("is_ground")-1));
							}else{
								parking.add(DataUtil.mapOf("price",datas[i].trim()
										,"during","全天","is_underground",json.getInteger("is_ground")-1));
							}
						}
					}
					//详情图片
					String detailImgs="";
					if(json.containsKey("pics")&&DataUtil.isJSONArray(json.getString("pics"))&&json.getJSONArray("pics").size()>0){
						detailImgs=json.getJSONArray("pics").stream().map(p->{
							String name=p.toString().substring(p.toString().lastIndexOf("/")+1);
							name=name.substring(0,name.indexOf("@"))+".jpg";
							return name;
						}).reduce((a,b)->a+","+b).get();
					}
					//运营商图标
					String providerIcon="";
					if(!DataUtil.isEmpty(json.get("carrier_icon"))){
						providerIcon=json.getString("carrier_icon").substring(json.getString("carrier_icon").lastIndexOf("/")+1)+".jpg";
					}
					long fastNum=0;
					long slowNum=0;
					if(json.containsKey("piles")&&DataUtil.isJSONArray(json.getString("piles"))){
						fastNum=json.getJSONArray("piles").stream().filter(p->JSONObject.parseObject(p.toString()).getInteger("inter_type")==2).count();
						slowNum=json.getJSONArray("piles").stream().filter(p->JSONObject.parseObject(p.toString()).getInteger("inter_type")==1).count();
					}
					String sql="insert into charging_station(uuid,source,telephone,provider_icon,provider_id,provider,pay_way,pay_tip,electricity_price"
							+ ",fast_num,slow_num,is_underground,is_standard,is_private,lat,lng,title,address,open_time,parking_price"
							+ ",detail_imgs,service_fee,remark,create_time)"
							+ "values"
							+ "('"+DataUtil.buildUUID()+"',1,'"+json.getString("carr_phone").trim()+"','"+providerIcon+"','"+json.getString("carrier_id").trim()+"'"
							+ ",'"+json.getString("carrier_name").trim()+"','"+json.getString("pay_model").trim()+"'"
							+ ",'"+json.getString("pay_model_desc").trim()+"','"+JSONArray.toJSONString(elect)+"',"+fastNum+","+slowNum
							+","+(json.getInteger("is_ground")-1)+","+json.getString("is_guobiao")+","+(json.getInteger("is_public")-1)
							+","+(json.getJSONObject("geo").containsKey("latitude")?json.getJSONObject("geo").getString("latitude"):0)
							+","+(json.getJSONObject("geo").containsKey("longitude")?json.getJSONObject("geo").getString("longitude"):0)
							+",'"+json.getString("title").trim()+"','"+json.getJSONObject("geo").getString("location").trim()
							+"','"+json.getString("open_time").trim()+"','"+JSONArray.toJSONString(parking)+"','"+detailImgs
							+"',"+json.getString("serv_price").trim()+",'"+json.getString("remark").trim()+"',now());";
					writer.write(sql);
					writer.flush();
					writer.newLine();
				}
			}
			writer.close();
			reader.close();
		} catch (Exception e) {
			System.out.println(index+":"+row);
			e.printStackTrace();
		}
	}
	
	public static void test2(){
		try {
			RequestBody body=new MultipartBody.Builder()
					.setType(MultipartBody.ALTERNATIVE)
					.addFormDataPart("pileType", "1,2,3")
					.addFormDataPart("start","0")
					.addFormDataPart("operatorIds","-1")
					.addFormDataPart("count","10")
					.addFormDataPart("longitude","116.37936846068227")
					.addFormDataPart("latitude","40.010286974865252")
					.addFormDataPart("isReservable","false")
					.addFormDataPart("hasOtherOperator","false")
					.addFormDataPart("hasPersonalPile","false")
					.addFormDataPart("distance","10007180")
					.addFormDataPart("cityId","110000")
					.addFormDataPart("onlyOpen","false")
					.build();
			Request request=new Request.Builder().url("http://yyc.renrenchongdian.com/charge/pile/newlist")
					.addHeader("appVersion", "4.4.0")
					.addHeader("clientId", "0")
					.addHeader("os", "ios")
					.addHeader("device", "iPhone10,2")
					.put(body)
			        .get()
			        .build();
			OkHttpClient client=new OkHttpClient();
			Response response=client.newCall(request).execute();
			System.out.println(JSONArray.toJSONString(response.headers()));
			System.out.println(response.body().string());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void test1() {
		try {
			RequestBody body=new MultipartBody.Builder()
					.setType(MultipartBody.ALTERNATIVE)
					//.addFormDataPart("uuid", "adb9c744e282402d892db8d8d6de9b48")
					//.addFormDataPart("content","《战神》今日进😄行版本更新，此前宣布的拍🥛照模式正式上线。作为一款奎爷与儿子😂在北欧的“旅游”大作，没有自拍怎么行？为了让玩家更好地记录沿途风景，官方也是拼了。")
					//.addFormDataPart("tags","说的好,非常棒,牛逼")
					.addFormDataPart("files","gamersky_03small_06_2018510103098E.jpg"
							,RequestBody.create(MediaType.parse("application/octet-stream")
							, new File("/Users/huangmeng/Downloads/杂图/gamersky_03small_06_2018510103098E.jpg")))
					.build();
			Request request=new Request.Builder().url("https://api.sayiyinxiang.com/api/article/info/upload")
					//.addHeader("phone", "18910701047")
					//.addHeader("system", "android_4.4.0")
					//.addHeader("imei", "868062022063060")
			        .post(body)
			        .build();
			OkHttpClient client=new OkHttpClient();
			Response response=client.newCall(request).execute();
			System.out.println(JSONArray.toJSONString(response.headers()));
			System.out.println(response.body().string());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}

package com.yixiang.api.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.feilong.core.CharsetType;
import com.feilong.core.DatePattern;
import com.feilong.core.Validator;
import com.feilong.core.net.ParamUtil;
import com.jfinal.weixin.sdk.kit.ParaMap;
import com.jfinal.weixin.sdk.utils.HttpUtils;
import com.yixiang.api.brand.mapper.BrandCarMapper;
import com.yixiang.api.brand.pojo.BrandInfo;
import com.yixiang.api.brand.service.BrandInfoComponent;
import com.yixiang.api.charging.pojo.ChargingStation;
import com.yixiang.api.charging.service.ChargingStationComponent;
import com.yixiang.api.main.Application;
import com.yixiang.api.util.ChargeClientBuilder;
import com.yixiang.api.util.Constants;
import com.yixiang.api.util.DataUtil;
import com.yixiang.api.util.mapper.AreaInfoMapper;
import com.yixiang.api.util.pojo.AreaInfo;
import com.yixiang.api.util.pojo.QueryExample;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = Application.class)
public class ChargerImporter {

	@Autowired
	private ChargingStationComponent chargingStationComponent;
	@Autowired
	private BrandInfoComponent brandInfoComponent;
	@Autowired
	private ChargeClientBuilder chargeClientBuilder;
	@Autowired
	private AreaInfoMapper areaInfoMapper;
	@Autowired
	private BrandCarMapper brandCarMapper;
	
	@Test
	public void test(){
		test6();
	}
	
	//更新品牌车型中的价格
	public void test8(){
		try {
			QueryExample example=new QueryExample();
			DecimalFormat df=new DecimalFormat("#.##");
			brandCarMapper.selectByExample(example).stream().forEach(o->{
				example.clear();
				example.and().andEqualTo("id", o.getId());
				String price=o.getPrice();
				float avg=0;
				if(price.split("\\-").length>1){
					avg=(Float.valueOf(price.split("\\-")[0])+Float.valueOf(price.split("\\-")[1]))/2;
				}
				if(avg>0){
					o.setPrice(df.format(avg));
					brandCarMapper.updateByExampleSelective(o, example);
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//更新行政区域关联
	public void test7(){
		try {
			QueryExample example=new QueryExample();
			example.and().andEqualTo("grade", 3);
			areaInfoMapper.selectByExample(example).stream().forEach(o->{
				example.clear();
				example.and().andEqualTo("area_code", o.getParentId()).andEqualTo("grade", 2);
				AreaInfo area=areaInfoMapper.selectByExample(example).get(0);
				example.clear();
				example.and().andEqualTo("id", o.getId());
				o.setParentId(area.getId());
				areaInfoMapper.updateByExampleSelective(o, example);
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//写入充电桩信息至文件,待后续入库操作
	public void test5(){
		try {
			BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream("/Users/huangmeng/Downloads/yixiang/jnc_data.txt"),Constants.UTF8));
			Integer pageSize=10;
			Integer pageNo=1;
			boolean run=true;
			do{
				String row=chargeClientBuilder.queryStationsInfo(pageNo++, pageSize);
				writer.write(row);
				writer.newLine();
				writer.flush();
				run=JSONObject.parseObject(row).getJSONArray("StationInfos").size()>0;
			}while(run);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//读取桩信息
	public void test9(){
		try {
			BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream("/Users/huangmeng/Downloads/yixiang/jnc_data.txt"),Constants.UTF8));
			BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream("/Users/huangmeng/Downloads/yixiang/jnc_zhuang.txt"),Constants.UTF8));
			String row=null;
			JSONObject json=null;
			while((row=reader.readLine())!=null){
				json=JSONObject.parseObject(row);
				System.out.println("pageNo="+json.getIntValue("PageNo")+",pageCount="+json.getIntValue("PageCount"));
				JSONArray array=json.getJSONArray("StationInfos");
				for(int i=0;i<array.size();i++){
					JSONObject station=array.getJSONObject(i);
					String data=chargeClientBuilder.queryStationSingleStatus(station.getString("StationID"));
					writer.write(data);
					writer.newLine();
					writer.flush();
				}
			}
			writer.close();
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//读取桩信息
	public void test10(){
		try {
			BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream("/Users/huangmeng/Downloads/yixiang/jnc_zhuang.txt"),Constants.UTF8));
			String row=null;
			JSONObject json=null;
			int i=0;
			while((row=reader.readLine())!=null){
				json=JSONObject.parseObject(row);
				JSONArray array=json.getJSONArray("StationInfos");
				i+=array.size();
			}
			reader.close();
			System.out.println("个数："+i);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//从文件读取充电桩信息,组装sql文件
	public void test6(){
		try {
			BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream("/Users/huangmeng/Downloads/yixiang/jnc_data.txt"),Constants.UTF8));
			BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream("/Users/huangmeng/Downloads/yixiang/jnc_data.sql"),Constants.UTF8));
			String row=null;
			JSONObject json=null;
			while((row=reader.readLine())!=null){
				json=JSONObject.parseObject(row);
				System.out.println("pageNo="+json.getIntValue("PageNo")+",pageCount="+json.getIntValue("PageCount"));
				JSONArray array=json.getJSONArray("StationInfos");
				for(int i=0;i<array.size();i++){
					JSONObject station=array.getJSONObject(i);
					//拼装充电站信息
					String sql="insert into tmp_charging_station(uuid,station_id,area_code,source,construction,support_order,match_cars"
							+ ",telephone,service_phone,provider_id,provider,pay_way,electricity_price,lat,lng,title,address"
							+ ",site_guide,open_time,parking_price,park_nums,park_info,detail_imgs,service_fee,remark,state,create_time)values("
							+ "'"+DataUtil.buildUUID()+"','"+empty(station.getString("StationID"))+"','"+empty(station.getString("AreaCode"))+"'"
							+ ",'"+number(station.getString("StationType"))+"','"+number(station.getString("Construction"))+"','"+number(station.getString("SupportOrder"))
							+"','"+empty(station.getString("MatchCars"))+"','"+empty(station.getString("StationTel"))+"','"+empty(station.getString("ServiceTel"))
							+"','"+empty(station.getString("OperatorID"))+"','"+empty(station.getString("OperatorName"))+"','"+empty(station.getString("Payment"))
							+"','"+empty(station.getString("ElectricityFee"))+"','"+latlng(station.getString("StationLat"))+"','"+latlng(station.getString("StationLng"))
							+"','"+empty(station.getString("StationName"))+"','"+empty(station.getString("Address"))+"','"+empty(station.getString("SiteGuide"))
							+"','"+empty(station.getString("BusineHours"))+"','"+empty(station.getString("ParkFee"))+"','"+number(station.getString("ParkNums"))
							+"','"+empty(station.getString("ParkInfo"))+"','"+(station.getJSONArray("Pictures").stream().reduce((a,b)->a+","+b).get())
							+"','"+empty(station.getString("ServiceFee"))+"','"+empty(station.getString("Remark"))+"','"+number(station.getString("StationStatus"))+"',now());";
					writer.write(sql);
					writer.newLine();
					//拼装充电设备信息
					JSONArray equipments=station.getJSONArray("EquipmentInfos");
					for(int j=0;j<equipments.size();j++){
						JSONObject equipment=equipments.getJSONObject(j);
						sql="insert into tmp_equipment_info(station_id,equipment_id,manufacture_id,manufacture_name,equipment_name,equipment_model"
								+ ",production_date,equipment_type,lat,lng,power)values("
								+ "'"+empty(station.getString("StationID"))+"','"+empty(equipment.getString("EquipmentID"))+"','"+empty(equipment.getString("ManufacturerID"))
								+"','"+empty(equipment.getString("ManufacturerName"))+"','"+empty(equipment.getString("EquipmentName"))
								+"','"+empty(equipment.getString("EquipmentModel"))+"','"+empty(equipment.getString("ProductionDate"))
								+"','"+number(equipment.getString("EquipmentType"))+"',"+latlng(equipment.getString("EquipmentLat"))
								+",'"+latlng(equipment.getString("EquipmentLng"))+"','"+number(equipment.getString("Power"))+"');";
						writer.write(sql);
						writer.newLine();
						//拼装设备接口信息
						JSONArray connectors=equipment.getJSONArray("ConnectorInfos");
						for(int k=0;k<connectors.size();k++){
							JSONObject connector=connectors.getJSONObject(k);
							sql="insert into tmp_connector_info(station_id,equipment_id,connector_id,connector_name,connector_type,voltage_upper,voltage_lower"
									+ ",power,current,park_no,national_standard)values("
									+ "'"+empty(station.getString("StationID"))+"','"+empty(equipment.getString("EquipmentID"))+"','"+empty(connector.getString("ConnectorID"))
									+"','"+empty(connector.getString("ConnectorName"))+"','"+empty(connector.getString("ConnectorType"))
									+"','"+number(connector.getString("VoltageUpperLimits"))+"','"+number(connector.getString("VoltageLowerLimits"))
									+"','"+number(connector.getString("Power"))+"','"+number(connector.getString("Current"))+"','"+empty(connector.getString("ParkNo"))
									+"','"+number(connector.getString("NationalStandard"))+"');";
							writer.write(sql);
							writer.newLine();
						}
					}
					writer.flush();
				}
			}
			writer.close();
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private String empty(String str){
		return StringUtils.isNotEmpty(str)?str:"";
	}
	
	private String number(String str){
		return StringUtils.isNotEmpty(str)?str:"0";
	}
	
	private String latlng(String str){
		return StringUtils.isNotEmpty(str)&&!str.equals("999.99999999")?str:"0";
	}
	
	public void test4(){
		try {
			QueryExample example=new QueryExample();
			List<BrandInfo> infos=brandInfoComponent.selectByExample(example);
			File dir=new File("/Users/huangmeng/Downloads/汽车品牌");
			for(File file:dir.listFiles()){
				String name=file.getName().substring(0, file.getName().indexOf("."));
				System.out.println(name);
				if(DataUtil.isNumber(name)){
					continue;
				}
				BrandInfo info=infos.stream().filter(b->b.getBrand().equals(name)).findFirst().get();
				file.renameTo(new File("/Users/huangmeng/Downloads/汽车品牌/"+info.getIcon()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void test3(){
		try {
			OkHttpClient client=new OkHttpClient();
			BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream("/Users/huangmeng/Downloads/易享充电/zc3u.txt")));
			Map<String,String> params=ParaMap.create("did", "6274D016-F81F-4956-B46D-4125AE42149A")
					.put("g_tk", "8dce931aa28547e0adfc3866f5243a53").put("v", "2.6.6.0").put("t", "1528276542").getData();
			for(int i=0;i<=200000;i++){
				params.put("id", String.valueOf(i));
				String url="https://appapi.zc3u.com/station/detail?"+ParamUtil.toNaturalOrderingQueryString(params);
				System.out.println(DateFormatUtils.format(new Date(), DatePattern.CHINESE_COMMON_DATE_AND_TIME)+" id="+i+",请求开始.....");
				Request request=new Request.Builder().url(url)
					.addHeader("Referer", "https://zhongchuangsanyou.com")
			        .get()
			        .build();
				Response response=client.newCall(request).execute();
				writer.write(JSONObject.parseObject(response.body().string()).toJSONString());
				writer.newLine();
				writer.flush();
				response.close();
			}
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void test1()throws Exception{
		//BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream("/Users/huangmeng/Downloads/error1.log",true)));
		String listUrl="https://www.evyou.cc/appserver/getChargingPileUpdateDataForApp.do";
		Map<String,String> listParams=ParaMap.create("dataVersion", "6").getData();
		String listJson=HttpUtils.get(listUrl, listParams);
		//String detailUrl="https://www.evyou.cc/appserver/getChargingStationInfoDetailForApp.do";
		JSONObject body=JSONObject.parseObject(JSONObject.parseObject(listJson).getString("body"));
		JSONArray list=body.getJSONArray("charging_station");
		System.out.println("数据大小："+list.size());
		/*for(int i=0;i>list.size();i++){
			JSONObject charger=list.getJSONObject(i);
			Map<String,String> detailParams=ParaMap.create("chargingStationCode", charger.getString("chargingStationCode")).getData();
			String detailJson=HttpUtils.get(detailUrl, detailParams);
			JSONObject detail=JSONObject.parseObject(detailJson).getJSONObject("body").getJSONObject("charging_station");
			writer.write(detail.toJSONString());
			writer.newLine();
			try {
				if(i%10==0){
					int millis=RandomUtils.nextInt(1, 5);
					System.out.println("线程即将休眠"+millis+"秒");
					Thread.sleep(millis*TimeInterval.MILLISECOND_PER_SECONDS);
				}
				if(i%500==0){
					System.out.println(DateUtil.toString(new Date(), DatePattern.COMMON_TIME)+" 已导入"+i+"条数据");
					writer.flush();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		writer.close();
		*/
	}
	
	public void test2()throws Exception{
		BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream("/Users/huangmeng/Downloads/error.log"), CharsetType.UTF8));
		String row=reader.readLine();
		while(Validator.isNotNullOrEmpty(row)){
			//saveToDb(JSONObject.parseObject(row));
			row=reader.readLine();
		}
		reader.close();
	}
	
	public void saveToDb(JSONObject detail){
		ChargingStation station=new ChargingStation();
		station.setTelephone(detail.getString("carrPhone"));
		station.setProvider(detail.getString("carrierName"));
		station.setUuid(DataUtil.buildUUID());
		station.setPayTip(detail.getString("createCardProcess"));
		station.setElectricityPrice(detail.getJSONArray("electPrice").toJSONString());
		station.setFastNum(detail.getInteger("fastNum"));
		station.setIsUnderground(detail.getInteger("isGround"));
		station.setIsPrivate(detail.getInteger("isPublic"));
		station.setLat(new BigDecimal(detail.getString("latitude")));
		station.setAddress(detail.getString("location"));
		station.setLng(new BigDecimal(detail.getString("longitude")));
		station.setOpenTime(detail.getString("openTime"));
		List<Map<Object,Object>> packing=Arrays.asList(detail.getString("parkExpense").split(";")).stream()
				.filter(item->Validator.isNotNullOrEmpty(item)).map(item->{
					if(!item.contains(",")){
						return DataUtil.mapOf("during", "全天", "price", item);
					}else{
						return DataUtil.mapOf("during", item.split(",")[0], "price", item.split(",")[1]);
					}
				}).collect(Collectors.toList());
		station.setParkingPrice(JSONArray.toJSONString(packing));
		station.setPayWay(detail.getString("payModelDesc"));
		station.setHeadImg(detail.getString("pics"));
		station.setRemark(detail.getString("remark"));
		station.setServiceFee(detail.getString("servPrice"));
		station.setSlowNum(detail.getInteger("slowNum"));
		station.setTitle(detail.getString("title"));
		chargingStationComponent.insertSelective(station);
	}
	
}

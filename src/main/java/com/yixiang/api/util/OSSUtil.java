package com.yixiang.api.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.jfinal.plugin.redis.Redis;

public class OSSUtil {

	private static Logger log = LoggerFactory.getLogger(OSSUtil.class);

	// 上传多媒体资源
	public static String uploadMedia(String oss, MultipartFile... files) {
		log.info("接收到文件个数:" + (null != files ? files.length : 0));
		StringBuilder names = new StringBuilder();
		// 多媒体资源
		if (null != files) {
			for (MultipartFile file : files) {
				if (null != file && !file.isEmpty()) {
					log.info("开始上传文件:" + file.getOriginalFilename() + ",大小:" + file.getSize());
					String saveName = new Date().getTime() + DataUtil.createNums(7);
					saveName += file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
					try {
						if (uploadFileToOSS(file.getInputStream(), saveName, oss)) {
							names.append(names.length() > 0 ? "," + saveName : saveName);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return names.toString();
	}

	// 上传图片
	public static String saveImgToOSS(String oss, String img, String suffix) {
		try {
			InputStream is = new URL(img).openStream();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			int b = 0;
			byte[] bytes = new byte[1024];
			while ((b = is.read(bytes)) != -1) {
				bos.write(bytes, 0, b);
			}
			bos.flush();
			ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
			String saveName = new Date().getTime() + DataUtil.createNums(7);
			if (StringUtils.isNotEmpty(suffix)) {
				saveName += "." + suffix;
			} else {
				saveName += img.substring(img.lastIndexOf("."));
			}
			uploadFileToOSS(bis, saveName, oss);
			bos.close();
			is.close();
			return saveName;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	// 上传本地文件至OSS存储
	public static boolean uploadFileToOSS(String file, String saveName, String oss) {
		try {
			JSONObject json = JSONObject.parseObject(Redis.use().get(oss));
			OSS client = new OSSClientBuilder().build(json.getString("endpoint"), json.getString("accesskeyid"),
					json.getString("secretaccesskey"));
			InputStream is = new FileInputStream(file);
			ObjectMetadata meta = new ObjectMetadata();
			meta.setContentLength(is.available());
			saveName = StringUtils.isNotEmpty(saveName) ? saveName : file.substring(file.lastIndexOf("/") + 1);
			if (DataUtil.isImg(saveName)) {
				client.putObject(json.getString("bucketname"), json.getString("imgDir") + "/" + saveName, is, meta);
			} else if (DataUtil.isVideo(saveName)) {
				client.putObject(json.getString("bucketname"), json.getString("videoDir") + "/" + saveName, is, meta);
			}
			is.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	// 上传本地文件至OSS存储
	public static boolean uploadFileToOSS(File file, String saveName, String oss) {
		try {
			JSONObject json = JSONObject.parseObject(Redis.use().get(oss));
			OSS client = new OSSClientBuilder().build(json.getString("endpoint"), json.getString("accesskeyid"),
					json.getString("secretaccesskey"));
			InputStream is = new FileInputStream(file);
			ObjectMetadata meta = new ObjectMetadata();
			meta.setContentLength(is.available());
			saveName = StringUtils.isNotEmpty(saveName) ? saveName
					: file.getName().substring(file.getName().lastIndexOf("/") + 1);
			if (DataUtil.isImg(saveName)) {
				client.putObject(json.getString("bucketname"), json.getString("imgDir") + "/" + saveName, is, meta);
			} else if (DataUtil.isVideo(saveName)) {
				client.putObject(json.getString("bucketname"), json.getString("videoDir") + "/" + saveName, is, meta);
			}
			is.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	// 上传本地文件至OSS存储
	public static boolean uploadFileToOSS(InputStream is, String saveName, String oss) {
		try {
			JSONObject json = JSONObject.parseObject(Redis.use().get(oss));
			OSS client = new OSSClientBuilder().build(json.getString("endpoint"), json.getString("accesskeyid"),
					json.getString("secretaccesskey"));
			ObjectMetadata meta = new ObjectMetadata();
			meta.setContentLength(is.available());
			if (DataUtil.isImg(saveName)) {
				client.putObject(json.getString("bucketname"), json.getString("imgDir") + "/" + saveName, is, meta);
			} else if (DataUtil.isVideo(saveName)) {
				client.putObject(json.getString("bucketname"), json.getString("videoDir") + "/" + saveName, is, meta);
			} else if (DataUtil.isAudio(saveName)) {
				client.putObject(json.getString("bucketname"), json.getString("audioDir") + "/" + saveName, is, meta);
			}
			is.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	//拼装全路径
	public static String joinOSSFileUrl(String file,JSONObject oss){
		if(StringUtils.isNotEmpty(file)&&null!=oss){
			if (DataUtil.isImg(file)) {
				String imgDomain=oss.getString("domain")+"/"+oss.get("imgDir");
				return imgDomain+"/"+file;
			} else if (DataUtil.isVideo(file)) {
				String imgDomain=oss.getString("domain")+"/"+oss.get("videoDir");
				return imgDomain+"/"+file;
			}
		}
		return file;
	}
	
	//拼装全路径
	public static List<String> joinOSSFileUrl(JSONObject oss,String... file){
		return Arrays.asList(file).stream().filter(i->StringUtils.isNotEmpty(i))
				.map(i->joinOSSFileUrl(i,oss)).collect(Collectors.toList());
	}

}

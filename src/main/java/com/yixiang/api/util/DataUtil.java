package com.yixiang.api.util;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

public class DataUtil {
	
	private static final String[] chars = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e",
			"f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
			"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U",
			"V", "W", "X", "Y", "Z" };

	private static final char UNDERLINE = '_';

	// 生成指定位数的随机数字
	public static String createNums(int count) {
		String code = "";
		Random ram = new Random();
		for (int i = 0; i < count; i++) {
			code += ram.nextInt(10);
		}
		return code;
	}

	// 拼装Map
	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> mapOf(Object... v) {
		Map<K, V> ret = new HashMap<K, V>();
		if (null == v) {
			return ret;
		}
		for (int i = 0; i < v.length; i++) {
			ret.put((K) v[i], (V) v[++i]);
		}
		return ret;
	}

	// 拼装Map
	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> setOf(Object... v) {
		Map<K, V> ret = new TreeMap<K, V>();
		if (null == v) {
			return ret;
		}
		for (int i = 0; i < v.length; i++) {
			ret.put((K) v[i], (V) v[++i]);
		}
		return ret;
	}

	// 删除多余Key
	public static <K, V> void removeKeys(Map<K, V> map, String[] keys) {
		for (String key : keys) {
			map.remove(key);
		}
	}

	// 生成指定位数的随机字母字符串
	public static String createLetters(int count) {
		String code = "";
		int index = 0;
		Random ram = new Random();
		for (int i = 0; i < count; i++) {
			index = ram.nextInt(chars.length);
			while (index < 10) {
				index = ram.nextInt(chars.length);
			}
			code += chars[index];
		}
		return code;
	}

	// 生成指定位数的随机字符串
	public static String createStrings(int count) {
		String code = "";
		Random ram = new Random();
		for (int i = 0; i < count; i++) {
			code += chars[ram.nextInt(chars.length)];
		}
		return code;
	}

	// 验证电话号码
	public static boolean isPhoneNum(String mdn) {
		String reg = "((\\d{11})|^((\\d{7,8})|(\\d{4}|\\d{3})-(\\d{7,8})|(\\d{4}|\\d{3})"
				+ "-(\\d{7,8})-(\\d{4}|\\d{3}|\\d{2}|\\d{1})|(\\d{7,8})-(\\d{4}|\\d{3}|\\d{2}|\\d{1}))$)";
		return Pattern.matches(reg, null != mdn ? mdn : "");
	}

	// MD5 32位和16位加密
	public static String md5(String str, int count) {
		String result = "";
		try {
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			md5.update(str.getBytes("UTF-8"));
			byte[] bytes = md5.digest();
			int b = 0;
			StringBuffer mdString = new StringBuffer();
			for (int j = 0; j < bytes.length; j++) {
				b = bytes[j];
				if (b < 0) {
					b += 256;
				}
				if (b < 16) {
					mdString.append("0");
				}
				mdString.append(Integer.toHexString(b));
			}
			if (count == 16) {
				result = mdString.toString().substring(8, 24);
			} else if (count == 32) {
				result = mdString.toString();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	// Map转换成Object
	public static Object mapToObject(Map<String, Object> map, Class<?> beanClass) throws Exception {
		Object obj = beanClass.newInstance();
		if (null != map) {
			BeanInfo beanInfo = Introspector.getBeanInfo(obj.getClass());
			PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
			for (PropertyDescriptor property : propertyDescriptors) {
				Method setter = property.getWriteMethod();
				if (setter != null) {
					setter.invoke(obj, map.get(property.getName()));
				}
			}
		}
		return obj;
	}

	// Object转换成Map
	public static Map<String, Object> objectToMap(Object obj) {
		try {
			Map<String, Object> map = new HashMap<String, Object>();
			if (null != obj) {
				BeanInfo beanInfo = Introspector.getBeanInfo(obj.getClass());
				PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
				for (PropertyDescriptor property : propertyDescriptors) {
					String key = property.getName();
					if (key.compareToIgnoreCase("class") == 0) {
						continue;
					}
					Method getter = property.getReadMethod();
					Object value = getter != null ? getter.invoke(obj) : null;
					map.put(key, value);
				}
			}
			return map;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// Object转换成Map
	public static Map<String, Object> objectToMap(Map<String, Object> map, Object obj) {
		try {
			if (null != obj) {
				BeanInfo beanInfo = Introspector.getBeanInfo(obj.getClass());
				PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
				for (PropertyDescriptor property : propertyDescriptors) {
					String key = property.getName();
					if (key.compareToIgnoreCase("class") == 0) {
						continue;
					}
					Method getter = property.getReadMethod();
					Object value = getter != null ? getter.invoke(obj) : null;
					map.put(key, value);
				}
			}
			return map;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// 保留小数位
	public static float round(double a, int n) {
		return new BigDecimal(a).setScale(n, BigDecimal.ROUND_HALF_UP).floatValue();
	}

	// 保留小数位
	public static float round(float a, int n) {
		return new BigDecimal(a).setScale(n, BigDecimal.ROUND_HALF_UP).floatValue();
	}

	// 获取指定范围内的数字
	public static int getRandomNum(int minVal, int maxVal, boolean isRate) {
		Random r = new Random();
		int num;
		while (true) {
			num = r.nextInt(maxVal + 1);
			if (num < minVal) {
				continue;
			}
			if (isRate && num % 10 == 0) {
				break;
			}
		}
		return num;
	}

	// 将对象存储在Map中返回
	public static HashMap<String, Object> saveObjectToMap(String key, Object value) {
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put(key, value);
		return map;
	}

	// 数字校验
	public static boolean isNumber(String str) {
		if (StringUtils.isNotEmpty(str)) {
			return Pattern.matches("\\d+", str);
		}
		return false;
	}

	// UUID生成
	public static String buildUUID() {
		return UUID.randomUUID().toString().replaceAll("-", "");
	}

	// 图片格式判断
	public static boolean isImg(String img) {
		return Pattern.matches("[^\\s]+\\.(jpg|gif|png|bmp)", img.toLowerCase());
	}

	// 视频格式判断
	public static boolean isVideo(String img) {
		return Pattern.matches("[^\\s]+\\.(mp4|avi)", img.toLowerCase());
	}

	// 音频格式判断
	public static boolean isAudio(String img) {
		return Pattern.matches("[^\\s]+\\.(mp3|wma|wav|flac|aac|m4a|ogg)", img.toLowerCase());
	}

	// 字符串是否未空
	public static boolean isEmpty(Object str) {
		return null == str || StringUtils.isEmpty(str.toString());
	}

	// JSONObject校验
	public static boolean isJSONObject(String str) {
		try {
			JSONObject.parseObject(str);
			return true;
		} catch (Exception e) {
		}
		return false;
	}

	// JSONArray校验
	public static boolean isJSONArray(String str) {
		try {
			JSONArray.parseArray(str);
			return true;
		} catch (Exception e) {
		}
		return false;
	}
	
	//过滤不可见字符
	public static String stripNonValidXMLCharacters(String input) {
		if (input == null || ("".equals(input)))
			return "";
		StringBuilder out = new StringBuilder();
		char current;
		for (int i = 0; i < input.length(); i++) {
			current = input.charAt(i);
			if ((current == 0x9) || (current == 0xA) || (current == 0xD)
					|| ((current >= 0x20) && (current <= 0xD7FF))
					|| ((current >= 0xE000) && (current <= 0xFFFD))
					|| ((current >= 0x10000) && (current <= 0x10FFFF)))
				out.append(current);
		}
		return out.toString();
	}

	// 获取客户端IP地址
	public static String getIpAddr(HttpServletRequest request) {
		String ip = request.getHeader("X-Forwarded-For");
		if (StringUtils.isNotEmpty(ip) && !"unKnown".equalsIgnoreCase(ip)) {
			// 多次反向代理后会有多个ip值，第一个ip才是真实ip
			int index = ip.indexOf(",");
			if (index != -1) {
				return ip.substring(0, index);
			} else {
				return ip;
			}
		}
		ip = request.getHeader("X-Real-IP");
		if (StringUtils.isNotEmpty(ip) && !"unKnown".equalsIgnoreCase(ip)) {
			return ip;
		}
		return request.getRemoteAddr().equals("0:0:0:0:0:0:0:1") ? "127.0.0.1" : request.getRemoteAddr();
	}
	
	//获取本机的网络IP
	private static String localIp;
    public static String getLocalNetWorkIp() {
        if (localIp != null) {
            return localIp;
        }
        try {
            Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip = null;
            while (netInterfaces.hasMoreElements()) {// 遍历所有的网卡
                NetworkInterface ni = (NetworkInterface) netInterfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual()) {// 如果是回环和虚拟网络地址的话继续
                    continue;
                }
                Enumeration<InetAddress> addresss = ni.getInetAddresses();
                while (addresss.hasMoreElements()) {
                    InetAddress address = addresss.nextElement();
                    if (address instanceof Inet4Address) {// 这里暂时只获取ipv4地址
                        ip = address;
                        break;
                    }
                }
                if (ip != null) {
                    break;
                }
            }
            if (ip != null) {
                localIp = ip.getHostAddress();
            } else {
                localIp = "127.0.0.1";
            }
        } catch (Exception e) {
            localIp = "127.0.0.1";
        }
        return localIp;
    }

	// 驼峰格式字符串转换为下划线格式字符串
	public static String camelToUnderline(String param) {
		if (param == null || "".equals(param.trim())) {
			return "";
		}
		int len = param.length();
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			char c = param.charAt(i);
			if (Character.isUpperCase(c)) {
				sb.append(UNDERLINE);
				sb.append(Character.toLowerCase(c));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	// 将汉字转换为全拼
	public static String getPingYin(String src) {
		char[] t1 = null;
		t1 = src.toCharArray();
		String[] t2 = new String[t1.length];
		HanyuPinyinOutputFormat t3 = new HanyuPinyinOutputFormat();
		t3.setCaseType(HanyuPinyinCaseType.LOWERCASE);
		t3.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
		t3.setVCharType(HanyuPinyinVCharType.WITH_V);
		String t4 = "";
		int t0 = t1.length;
		try {
			for (int i = 0; i < t0; i++) {
				// 判断是否为汉字字符
				if (java.lang.Character.toString(t1[i]).matches("[\\u4E00-\\u9FA5]+")) {
					t2 = PinyinHelper.toHanyuPinyinStringArray(t1[i], t3);
					t4 += t2[0];
				} else
					t4 += java.lang.Character.toString(t1[i]);
			}
			return t4;
		} catch (BadHanyuPinyinOutputFormatCombination e1) {
			e1.printStackTrace();
		}
		return t4;
	}

	// 返回中文的首字母
	public static String getPinYinHeadChar(String str) {
		String convert = "";
		for (int j = 0; j < str.length(); j++) {
			char word = str.charAt(j);
			String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(word);
			if (pinyinArray != null) {
				convert += pinyinArray[0].charAt(0);
			}
		}
		return convert;
	}

	// 下划线格式字符串转换为驼峰格式字符串
	public static String underlineToCamel(String param) {
		if (param == null || "".equals(param.trim())) {
			return "";
		}
		int len = param.length();
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			char c = param.charAt(i);
			if (c == UNDERLINE) {
				if (++i < len) {
					sb.append(Character.toUpperCase(param.charAt(i)));
				}
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	// 计算两个经纬度之间的距离
	public static double getDistance(double lat1, double lng1, double lat2, double lng2) {
		double radLat1 = lat1 * Math.PI / 180.0;
		double radLat2 = lat2 * Math.PI / 180.0;
		double a = radLat1 - radLat2;
		double b = (lng1 * Math.PI / 180.0) - (lng2 * Math.PI / 180.0);
		double s = 2 * Math.asin(Math.sqrt(
				Math.pow(Math.sin(a / 2), 2) + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
		s = s * 6371.393;
		s = Math.round(s * 1000);
		return s;
	}

	private final static long kilometre = 1000;// 公里

	// 计算两个经纬度之间的距离
	public static String getDistanceFormatText(double lat1, double lng1, double lat2, double lng2) {
		double distance = getDistance(lat1, lng1, lat2, lng2);
		DecimalFormat df = new DecimalFormat("#.##");
		if (distance >= kilometre) {
			return df.format(distance / kilometre) + "km";
		}
		return df.format(distance) + "m";
	}

	// 计算两个经纬度之间的距离
	public static String getDistanceFormatText(Double distance) {
		DecimalFormat df = new DecimalFormat("#.##");
		if (distance >= kilometre) {
			return df.format(distance / kilometre) + "km";
		}
		return df.format(distance) + "m";
	}

	private final static long minute = 60 * 1000;// 1分钟
	private final static long hour = 60 * minute;// 1小时
	private final static long day = 24 * hour;// 1天
	private final static long month = 31 * day;// 月
	private final static long year = 12 * month;// 年

	// 返回文字描述的日期
	public static String getTimeFormatText(Date date) {
		if (date == null) {
			return null;
		}
		long diff = new Date().getTime() - date.getTime();
		long r = 0;
		if (diff > year) {
			r = (diff / year);
			return r + "年前";
		}
		if (diff > month) {
			r = (diff / month);
			return r + "个月前";
		}
		if (diff > day) {
			r = (diff / day);
			return r + "天前";
		}
		if (diff > hour) {
			r = (diff / hour);
			return r + "个小时前";
		}
		if (diff > minute) {
			r = (diff / minute);
			return r + "分钟前";
		}
		return "刚刚";
	}

}

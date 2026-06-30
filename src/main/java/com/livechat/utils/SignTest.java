package com.livechat.utils;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 签名测试类
 * @author yangfei
 * @since 2023/08/14
 */
public final class SignTest {

	static String AND  = "&";

	public static void main(String[] args) {

		String nonce = String.valueOf(getRandomNonce());
		String timestampStr = String.valueOf(System.currentTimeMillis());
		String secureKey = "NTVmZDkwY2YwNWY3YWFhNGJhYzM2NjAyODQxMjI4NmEzZWM0MDc4Mw==";
		String postParam = "{\"pin\":\"91b9f6e2b27bc38d274e5c96273332f9\",\"processingFee\":\"0.0\",\"receiverAccount\":\"25996347472\",\"receiverBankCode\":\"490\",\"receiverBankName\":\"BANK YUDHA BHAKTI\",\"receiverName\":\"PUJANGGA KEMBARC\",\"settlementAccount\":\"7211368401\",\"transferAmount\":\"10.0\"}";
		Map<String, Object> postData = convertRequestParamsToMap(postParam);
		System.out.println(JSON.toJSONString(postData));
		String sign = getSign(postData, nonce, timestampStr, secureKey);

		System.out.println(sign + "|" + nonce + "|" + timestampStr);

	}

	/**
	 * 4位随机数nonce
	 */
	private static int getRandomNonce() {
		return (int) (1000 + Math.ceil(Math.random() * 8999));
	}

	private static Map<String, Object> convertRequestParamsToMap(String requestParams) {
		if (StringUtils.isEmpty(requestParams)) {
			return new HashMap(64);
		} else if (!requestParams.contains(AND)) {
			return (Map)JSONObject.parseObject(requestParams, Map.class, new Feature[0]);
		} else {
			Map<String, Object> dataMap = new HashMap(64);
			String[] params = requestParams.split("&");
			String[] results = null;
			String[] var4 = params;
			int var5 = params.length;

			for(int var6 = 0; var6 < var5; ++var6) {
				String param = var4[var6];
				results = param.split("=");
				dataMap.put(results[0], results[1]);
			}

			return dataMap;
		}
	}

	private static String getSign(Map<String, Object> postDataMap, String nonce, String timestamp, String secureKey) {
		return getSignatureStr(new HashMap<>(64), postDataMap, nonce, timestamp, secureKey);
	}

	private static String getSignatureStr(Map<String, Object> getDataMap, Map<String, Object> postDataMap, String nonce, String timestamp, String secureKey) {
		StringBuilder md5StrBuilder = new StringBuilder();
		if (getDataMap != null && getDataMap.size() > 0) {
			md5StrBuilder.append(convertParamTypeToString((Map)(new TreeMap(getDataMap)), new StringBuilder()));
			md5StrBuilder.append("&");
		}

		if (postDataMap != null && postDataMap.size() > 0) {
			md5StrBuilder.append(convertParamTypeToString((Map)(new TreeMap(postDataMap)), new StringBuilder()));
			md5StrBuilder.append("&");
		}

		md5StrBuilder.append(secureKey).append("&").append(nonce).append("&").append(timestamp);
		return DigestUtils.md5Hex(md5StrBuilder.toString()).substring(8, 24);
	}

	private static String convertParamTypeToString(Map<String, Object> dataMap, StringBuilder sb) {
		dataMap.forEach((key, value) -> {
			if (value.getClass().isArray()) {
				sb.append(key).append(":").append(StringUtils.arrayToDelimitedString((Object[])((Object[])value), ","));
			} else if (value instanceof Map) {
				sb.append(key).append(":").append(convertParamTypeToString((Map)(new TreeMap((Map)value)), new StringBuilder()));
			} else if (value instanceof JSONArray) {
				sb.append(key).append(":").append(convertParamTypeToString((JSONArray)value, new StringBuilder()));
			} else if (value instanceof JSONObject) {
				sb.append(key).append(":").append(convertParamTypeToString((JSONObject)value, new StringBuilder()));
			} else {
				sb.append(key).append(":").append(value);
			}

			sb.append("|");
		});
		String paramStr = sb.toString();
		return paramStr.substring(0, paramStr.length() - 1);
	}

	private static String convertParamTypeToString(JSONArray jsonArray, StringBuilder sb) {
		jsonArray.forEach((value) -> {
			if (value instanceof Map) {
				sb.append(convertParamTypeToString((Map)(new TreeMap((Map)value)), new StringBuilder()));
			} else if (value instanceof JSONObject) {
				sb.append(convertParamTypeToString((JSONObject)value, new StringBuilder()));
			} else if (value instanceof JSONArray) {
				sb.append(convertParamTypeToString((JSONArray)value, new StringBuilder()));
			} else {
				sb.append(value);
			}

			sb.append(",");
		});
		String paramStr = sb.toString();
		return paramStr.substring(0, paramStr.length() - 1);
	}

}

package com.livechat.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 签名验证工具类
 * @author yangfei
 * @since 2023/08/14
 */
public class ValidateLogUtils {

	private static final Logger log = LoggerFactory.getLogger(ValidateLogUtils.class);

	public static void logRequestSignData(ServerHttpRequest request, String requestBody) {

		HttpHeaders httpHeaders = request.getHeaders();
		String path = request.getURI().getPath();
		String appVersion = httpHeaders.getFirst("app-version");
		String deviceType = httpHeaders.getFirst("device-type");
		String sign = httpHeaders.getFirst("sign");

		log.error("请求签名不通过url {},服务器时间{},app-version {},device-type {},前端签名{}  前端请求参数{}：",path,System.currentTimeMillis(),appVersion,deviceType,sign, requestBody);

	}
}

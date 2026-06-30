package com.livechat.utils;

import com.alibaba.fastjson.JSONObject;
import com.livechat.constant.GatewayConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * http 工具类
 * @author yangfei
 * @since 2023/08/14
 */
public final class HttpUtils {

	private static final Logger log = LoggerFactory.getLogger(HttpUtils.class);


	public static JSONObject returnResult(String code, String msg) {
		JSONObject result = new JSONObject();
		result.put(GatewayConstant.SUCCESS, Boolean.FALSE);
		result.put(GatewayConstant.ERR_CODE, code);
		result.put(GatewayConstant.ERR_MSG, msg);
		return result;
	}

	/**
	 *
	 *	未登陆时返回给客户端的也是http code 为200，
	 *  返回信息的msg code 为401
	 *
	 * @param response
	 * @param result
	 * @return
	 */
	public static Mono<Void> responseUnLoginWith(ServerHttpResponse response, JSONObject result) {
		return genErrorResponse(response, HttpStatus.OK, result);
	}


	public static Mono<Void> genInternalServerError(ServerHttpResponse response, JSONObject jsonObject) {
		return genErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, jsonObject);
	}

	public static Mono<Void> genErrorResponse(ServerHttpResponse response, HttpStatus httpStatus, JSONObject jsonObject) {
		byte[] bytes = JSONObject.toJSONString(jsonObject).getBytes(StandardCharsets.UTF_8);
		DataBuffer buffer = response.bufferFactory().wrap(bytes);
		HttpHeaders header = response.getHeaders();
		header.add("Content-Type","application/json");
		response.setStatusCode(httpStatus);
		return response.writeWith(Mono.just(buffer));
	}
}

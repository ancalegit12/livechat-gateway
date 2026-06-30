package com.livechat.filter;


import com.alibaba.fastjson.JSON;
import com.livechat.constant.GatewayConstant;
import com.bnc.neith.basic.response.dto.ApiResponse;
import com.livechat.utils.HttpUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;


/**
 * App token filter
 * @author yangfei
 * @since 2023/08/14
 */
@Component
public class BncAppTokenGatewayFilterFactory extends AbstractGatewayFilterFactory<Config> implements Ordered {

	private static final Logger log = LoggerFactory.getLogger(BncAppTokenGatewayFilterFactory.class);

	@Resource
	HalfLoginErrorCodeConver halfLoginErrorCodeConver;

	@Resource
	LoadBalancerClient loadBalancerClient;

	private WebClient webClient;

	public BncAppTokenGatewayFilterFactory() {
		super(Config.class);
	}


	@PostConstruct
	public void init() {
		this.webClient = WebClient.builder()
				.baseUrl(GatewayConstant.BASE_URL)
				.filter(new LoadBalancerExchangeFilterFunction(loadBalancerClient))
				.build();
	}


	@Override
	public GatewayFilter apply(Config config) {
		return new DefaultFilter((exchange, chain) -> {
			String path = exchange.getRequest().getURI().getPath().toLowerCase();
			log.info(String.format("request URL ->  %s  ", path));

			if (path.indexOf(GatewayConstant.PUBLIC_PATH) > GatewayConstant.ZERO) {
				return chain.filter(exchange);
			} else if (path.indexOf(GatewayConstant.OPTION_PATH) > GatewayConstant.ZERO) {
				// 可选uid
				return setUidInHead(exchange, chain, Boolean.TRUE);
			} else {
				// 必须有uid
				return setUidInHead(exchange, chain, Boolean.FALSE);
			}
		}, config);
	}

	private boolean pathShouldCheck(Config config, String path) {
		return Objects.nonNull(config.getCheckUri()) && config.getCheckUri().contains(path);
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	private Mono<Void> setUidInHead(ServerWebExchange exchange, GatewayFilterChain chain, Boolean optional) {
		String path = exchange.getRequest().getURI().getPath().toLowerCase();
		HttpHeaders headers = exchange.getRequest().getHeaders();
		List<String> headToken = headers.get(GatewayConstant.ACCESS_TOKEN);
		List<String> headSemiToken = headers.getOrDefault(GatewayConstant.SEMI_ACCESS_TOKEN, new ArrayList<>());
		String accessToken = StringUtils.EMPTY;
		String semiToken = headSemiToken.get(0);
		if (headToken != null && headToken.size() > GatewayConstant.ZERO) {
			accessToken = headToken.get(GatewayConstant.ZERO);
		}
		// 请求头里没有token视为未登陆
		if (org.apache.commons.lang.StringUtils.isBlank(accessToken) && optional) {
			return chain.filter(exchange);
		}
		log.info("livechat setUidInHead uri={}, semiToken={}, check={}", path, semiToken, isHalfLoginPath(path));
		// 半登录态校验
		if(isHalfLoginPath(path) && StringUtils.isNotBlank(semiToken)){
			return mobileClientSemiLoginResponse(exchange,chain,optional);
		}

		if(path.indexOf(GatewayConstant.MOBILE_URL_PATH) == GatewayConstant.ZERO ||
				path.indexOf(GatewayConstant.PUSH_URL_PATH) == GatewayConstant.ZERO ||
				path.indexOf(GatewayConstant.MIDRISK_URL_PATH) == GatewayConstant.ZERO){
			return mobileClientResponse(exchange,chain,optional);
		}
		return chain.filter(exchange);
	}

	private boolean isHalfLoginPath(String path) {
		return path.indexOf(GatewayConstant.COMMON_HALF_LOGIN_PATH) >=0;
	}

	private Mono<Void> mobileClientResponse(ServerWebExchange exchange, GatewayFilterChain chain, Boolean optional ) {

		return webClient
				.post()
				.uri(GatewayConstant.CHECK_TOKEN_URL)
				.contentType(MediaType.APPLICATION_JSON_UTF8)
				.headers(httpHeaders -> {
					exchange.getRequest().getHeaders().forEach((key, value) -> {
						// 坑转发请求是注意设置content-length
						if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(key)) {
							httpHeaders.remove(HttpHeaders.CONTENT_LENGTH);
						} else {
							httpHeaders.addAll(key, value);
						}
					});
				})
				.retrieve()
				.bodyToMono(ApiResponse.class)
				.flatMap(result -> {
					if (result != null && result.isSuccess()) {
						Map sessionResp = (Map) result.getData();
						log.info("session: " + JSON.toJSONString(sessionResp));

						ServerHttpRequest request = exchange.getRequest();
						ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
						builder.headers((httpHeaders) -> {
							httpHeaders.remove(GatewayConstant.UID);
							httpHeaders.remove(GatewayConstant.X_USER_ID);
							httpHeaders.add(GatewayConstant.UID, String.valueOf(sessionResp.get(GatewayConstant.UID)));
							httpHeaders.add(GatewayConstant.X_USER_ID, String.valueOf(sessionResp.get(GatewayConstant.UID)));
							httpHeaders.add(GatewayConstant.ECIF_ID, sessionResp.get(GatewayConstant.ECIF_ID).toString());
							httpHeaders.add(GatewayConstant.CREDIT_ECIF_ID, Optional.ofNullable(sessionResp.get(GatewayConstant.CREDIT_ECIF_ID)).orElse(StringUtils.EMPTY).toString());
						});
						return chain.filter(exchange.mutate().request(builder.build()).build());
					} else {
						if (!optional) {
							// 提示未登陆
							return HttpUtils.responseUnLoginWith(exchange.getResponse(), HttpUtils.returnResult(result.getErrCode(), result.getErrMsg()));
						} else {
							return chain.filter(exchange);
						}
					}
				})
				.onErrorResume(throwable -> {
					log.error("gateway error! ", throwable);
					return HttpUtils.genInternalServerError(exchange.getResponse(), HttpUtils.returnResult(GatewayConstant.SERVER_ERROR, GatewayConstant.SERVER_ERROR_MSG));
				});
	}


	private Mono<Void> mobileClientSemiLoginResponse(ServerWebExchange exchange, GatewayFilterChain chain, Boolean optional ) {

		return webClient
				.post()
				.uri(GatewayConstant.CHECK_HALF_LOGIN_TOKEN_URL)
				.contentType(MediaType.APPLICATION_JSON_UTF8)
				.headers(httpHeaders -> {
					exchange.getRequest().getHeaders().forEach((key, value) -> {
						// 坑转发请求是注意设置content-length
						if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(key)) {
							httpHeaders.remove(HttpHeaders.CONTENT_LENGTH);
						} else {
							httpHeaders.addAll(key, value);
						}
					});
				})
				.retrieve()
				.bodyToMono(ApiResponse.class)
				.flatMap(result -> {
					if (result != null && result.isSuccess()) {
						Map sessionResp = (Map) result.getData();
						log.info("session: " + JSON.toJSONString(sessionResp));

						ServerHttpRequest request = exchange.getRequest();
						ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
						builder.headers((httpHeaders) -> {
							httpHeaders.remove(GatewayConstant.UID);
							httpHeaders.remove(GatewayConstant.X_USER_ID);
							httpHeaders.add(GatewayConstant.UID, String.valueOf(sessionResp.get(GatewayConstant.UID)));
							httpHeaders.add(GatewayConstant.X_USER_ID, String.valueOf(sessionResp.get(GatewayConstant.UID)));
							httpHeaders.add(GatewayConstant.ECIF_ID, sessionResp.get(GatewayConstant.ECIF_ID).toString());
							httpHeaders.add(GatewayConstant.CREDIT_ECIF_ID, Optional.ofNullable(sessionResp.get(GatewayConstant.CREDIT_ECIF_ID)).orElse(StringUtils.EMPTY).toString());
						});
						return chain.filter(exchange.mutate().request(builder.build()).build());
					} else {
						if (!optional) {

							halfLoginErrorCodeConver.convertErrorCode(exchange,result);
							// 提示未登陆
							return HttpUtils.responseUnLoginWith(exchange.getResponse(), HttpUtils.returnResult(result.getErrCode(), result.getErrMsg()));
						} else {
							return chain.filter(exchange);
						}
					}
				})
				.onErrorResume(throwable -> {
					log.error("gateway error! ", throwable);
					return HttpUtils.genInternalServerError(exchange.getResponse(), HttpUtils.returnResult(GatewayConstant.SERVER_ERROR, GatewayConstant.SERVER_ERROR_MSG));
				});
	}
}


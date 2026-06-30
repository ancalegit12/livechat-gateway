package com.livechat.filter;

import com.alibaba.fastjson.JSON;
import com.bnc.neith.basic.response.dto.ApiResponse;
import com.livechat.constant.GatewayConstant;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class OpenApiPortalTokenGatewayFilterFactory extends AbstractGatewayFilterFactory<Config> implements Ordered {

	private static final Logger log = LoggerFactory.getLogger(OpenApiPortalTokenGatewayFilterFactory.class);

	@Resource
	LoadBalancerClient loadBalancerClient;

	private WebClient webClient;

	public OpenApiPortalTokenGatewayFilterFactory() {
		super(Config.class);
	}

	@PostConstruct
	public void init() {
		this.webClient = WebClient.builder()
				.baseUrl(GatewayConstant.OPENAPIPORTAL_URL)
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

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	private Mono<Void> setUidInHead(ServerWebExchange exchange, GatewayFilterChain chain, Boolean optional) {
		String path = exchange.getRequest().getURI().getPath().toLowerCase();
		HttpHeaders headers = exchange.getRequest().getHeaders();
		List<String> headToken = headers.get(GatewayConstant.ACCESS_TOKEN);
		String accessToken = StringUtils.EMPTY;
		if (headToken != null && headToken.size() > GatewayConstant.ZERO) {
			accessToken = headToken.get(GatewayConstant.ZERO);
		}
		// 请求头里没有token视为未登陆
		if (StringUtils.isBlank(accessToken) && optional) {
			return chain.filter(exchange);
		}
		log.info("openApiPortal setUidInHead uri={}", path);

		if(path.indexOf(GatewayConstant.OPENAPIPORTAL_BACKOFFICE_URL_PATH) == GatewayConstant.ZERO){
			return backofficePortalResponse(exchange,chain,optional,path);
		} else if(path.indexOf(GatewayConstant.OPENAPIPORTAL_USERPORTAL_URL_PATH) == GatewayConstant.ZERO){
			return userPortalResponse(exchange,chain,optional,path);
		}
		return chain.filter(exchange);
	}

	private Mono<Void> backofficePortalResponse(ServerWebExchange exchange, GatewayFilterChain chain, Boolean optional, String path) {

		return webClient
				.post()
				.uri(GatewayConstant.OPENAPIPORTAL_BACKOFFICE_CHECK_TOKEN_URL)
				.contentType(MediaType.APPLICATION_JSON_UTF8)
				.headers(httpHeaders -> {
					exchange.getRequest().getHeaders().forEach((key, value) -> {
						// 坑转发请求是注意设置content-length
						if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(key)) {
							httpHeaders.addAll(GatewayConstant.ACCESS_URL , Arrays.asList(path));
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
						ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
						builder.headers((httpHeaders) -> {
							httpHeaders.remove(GatewayConstant.UID);
							httpHeaders.add(GatewayConstant.UID, String.valueOf(sessionResp.get(GatewayConstant.ADMIN_ID)));
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

	private Mono<Void> userPortalResponse(ServerWebExchange exchange, GatewayFilterChain chain, Boolean optional, String path) {

		return webClient
				.post()
				.uri(GatewayConstant.OPENAPIPORTAL_USERPORTAL_CHECK_TOKEN_URL)
				.contentType(MediaType.APPLICATION_JSON_UTF8)
				.headers(httpHeaders -> {
					exchange.getRequest().getHeaders().forEach((key, value) -> {
						// 坑转发请求是注意设置content-length
						if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(key)) {
							httpHeaders.addAll(GatewayConstant.ACCESS_URL , Arrays.asList(path));
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
						ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
						builder.headers((httpHeaders) -> {
							httpHeaders.remove(GatewayConstant.UID);
							httpHeaders.add(GatewayConstant.UID, String.valueOf(sessionResp.get(GatewayConstant.PARTNER_ID)));
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
}


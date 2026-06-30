package com.livechat.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 默认的gateway filter 实现类
 * @author yangfei
 * @since 2023/08/14
 */
public class DefaultFilter implements GatewayFilter {

	private GatewayFilter gatewayFilter;

	private Config config;

	public DefaultFilter(GatewayFilter gatewayFilter ,Config config) {
		this.gatewayFilter = gatewayFilter;
		this.config = config;
	}


	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return this.gatewayFilter.filter(exchange, chain);
	}
}

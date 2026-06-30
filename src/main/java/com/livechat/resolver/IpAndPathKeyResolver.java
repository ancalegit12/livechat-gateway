package com.livechat.resolver;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.akulaku.platform.components.gateway.core.utils.HttpUtil;

@Component
public class IpAndPathKeyResolver implements KeyResolver {
	public IpAndPathKeyResolver() {
	}

	public Mono<String> resolve(ServerWebExchange exchange) {
		return Mono.justOrEmpty(HttpUtil.getRealIp(exchange) + exchange.getRequest().getPath());
	}
}


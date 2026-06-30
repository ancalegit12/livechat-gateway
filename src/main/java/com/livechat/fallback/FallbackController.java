package com.livechat.fallback;

import com.livechat.constant.GatewayStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import reactor.core.publisher.Mono;

import com.akulaku.platform.components.base.util.domain.ApiResult;

/**
 * 熔断控制类
 * @author yangfei
 * @since 2023/08/14
 */
@RestController
public class FallbackController {

	private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

	private static String SHORT_CIRCUIT_MESSAGE = "Hystrix circuit short-circuited and is OPEN";


	@RequestMapping("/fallback")
	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	public Mono fallback(ServerWebExchange exchange) {
		// 取出异常信息
		Exception exception = exchange.getAttribute(ServerWebExchangeUtils.HYSTRIX_EXECUTION_EXCEPTION_ATTR);
		// 获取上下文信息
		ServerWebExchange delegate = ((ServerWebExchangeDecorator) exchange).getDelegate();

		if (exception.getMessage().contains(SHORT_CIRCUIT_MESSAGE)) {
			log.error("触发熔断降级 fallback: {}, URL={}  {}",
					GatewayStatusCode.SYS_OUT_OF_SERVICE, delegate.getRequest().getURI(), SHORT_CIRCUIT_MESSAGE);
		} else {
			log.error("触发熔断降级 fallback: {}, URL={}",
					GatewayStatusCode.SYS_OUT_OF_SERVICE, delegate.getRequest().getURI(), exception);
		}

		ApiResult serviceUnavailable = ApiResult.errorResponse(GatewayStatusCode.SYS_OUT_OF_SERVICE, "Service Unavailable");
		return Mono.just(serviceUnavailable);
	}
}

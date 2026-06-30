package com.livechat.filter;


import com.livechat.service.BncSignatureAuthenticationService;
import com.livechat.utils.ValidateLogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.DefaultServerRequest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.akulaku.platform.components.base.util.domain.ApiResult;
import com.akulaku.platform.components.gateway.core.filter.config.SimpleFilterConfig;
import com.akulaku.platform.components.gateway.core.filter.gatewayfilterfactory.ApiAuthGatewayFilterFactory;
import com.akulaku.platform.components.gateway.core.filter.gatewayfilterfactory.DecoratedDefaultGatewayFilter;
import com.akulaku.platform.components.gateway.core.service.SignatureAuthenticationService;
import com.akulaku.platform.components.gateway.core.utils.HttpUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;


/**
 * BNC App 接口签名处理
 */
@Component
public class BYBApiAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<BYBApiAuthGatewayFilterFactory.Config> {
	private static final Logger log = LoggerFactory.getLogger(ApiAuthGatewayFilterFactory.class);
	private static final String KEY = "active";
	private final List<HttpMessageReader<?>> messageReaders;
	private BncSignatureAuthenticationService signatureAuthenticationService;

	public BYBApiAuthGatewayFilterFactory(BncSignatureAuthenticationService signatureAuthenticationService) {
		super(BYBApiAuthGatewayFilterFactory.Config.class);
		this.signatureAuthenticationService = signatureAuthenticationService;
		this.messageReaders = HandlerStrategies.withDefaults().messageReaders();
	}

	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("active");
	}

	public GatewayFilter apply(BYBApiAuthGatewayFilterFactory.Config config) {
		return new DecoratedDefaultGatewayFilter((exchange, chain) -> {

			if (!this.pathShouldCheck(config, exchange.getRequest().getURI().getPath())) {
				return chain.filter(exchange);
			} else {
				String contentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
				if (!Objects.equals(exchange.getRequest().getMethod(), HttpMethod.GET) && (null == contentType || !contentType.startsWith("multipart/form-data"))) {
					ServerRequest serverRequest = new DefaultServerRequest(exchange, this.messageReaders);
					Mono<String> modifiedBody = serverRequest.bodyToMono(String.class).flatMap((requestBody) -> {
						return this.signatureAuthenticationService.isValidRequestSignature(exchange.getRequest(), requestBody).flatMap((aBoolean) -> {
							if (!aBoolean) {
								ValidateLogUtils.logRequestSignData(exchange.getRequest(), requestBody);
							}
							return aBoolean ? Mono.just(requestBody) : HttpUtil.genErrorResponse(exchange.getResponse(), HttpStatus.FORBIDDEN, ApiResult.errorResponse("10910010010006", "invalid request")).map((aVoid) -> {
								return requestBody;
							});
						});
					});
					BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);
					HttpHeaders headers = new HttpHeaders();
					headers.putAll(exchange.getRequest().getHeaders());
					headers.remove("Content-Length");
					CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);
					return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
						ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
							public HttpHeaders getHeaders() {
								HttpHeaders httpHeaders = new HttpHeaders();
								httpHeaders.putAll(super.getHeaders());
								return httpHeaders;
							}

							public Flux<DataBuffer> getBody() {
								return outputMessage.getBody();
							}
						};
						return chain.filter(exchange.mutate().request(decorator).build());
					}));
				} else {
					return chain.filter(exchange);
				}
			}
		}, config);
	}

	private boolean pathShouldCheck(BYBApiAuthGatewayFilterFactory.Config config, String path) {
		return Objects.nonNull(config.getCheckUri()) && config.getCheckUri().contains(path);
	}

	public static class Config extends SimpleFilterConfig {
		private Set<String> checkUri;

		public Config() {
		}

		public Set<String> getCheckUri() {
			return this.checkUri;
		}

		public void setCheckUri(Set<String> checkUri) {
			this.checkUri = checkUri;
		}
	}
}


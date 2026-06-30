package com.livechat.filter;


import com.akulaku.platform.components.base.util.domain.ApiResult;
import com.akulaku.platform.components.gateway.core.constant.Constants;
import com.akulaku.platform.components.gateway.core.utils.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.bnc.neith.basic.response.dto.ApiResponse;
import com.livechat.config.BncSecurityConfig;
import com.livechat.config.SecurityBizConfig;
import com.livechat.constant.GatewayConstant;
import com.livechat.utils.ApiResultUtils;
import com.livechat.utils.HttpUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;


/**
 * App token filter
 * @author jiangyg
 * @since 2023/08/14
 */
@Component
public class BncSecurityGatewayFilterFactory extends AbstractGatewayFilterFactory<BncSecurityConfig> implements Ordered {

	private static final Logger log = LoggerFactory.getLogger(BncAppTokenGatewayFilterFactory.class);

	@Resource
	LoadBalancerClient loadBalancerClient;

	@Autowired
	private SecurityBizConfig securityBizConfig;

	private WebClient webClient;

	private final List<HttpMessageReader<?>> messageReaders;

	final String CONST_100 = "100";

	final int PATH_LENGTH = 2;

	final String SECURITY_PATH_SPECIAL_CHAR = "#";

	public BncSecurityGatewayFilterFactory() {
		super(BncSecurityConfig.class);
		this.messageReaders = HandlerStrategies.withDefaults().messageReaders();
	}


	@PostConstruct
	public void init() {
		this.webClient = WebClient.builder()
				.baseUrl(GatewayConstant.SECURITY_RISK_BASE_URL)
				.filter(new LoadBalancerExchangeFilterFunction(loadBalancerClient))
				.build();
	}


	@Override
	public GatewayFilter apply(BncSecurityConfig config) {
		return new DefaultFilter((exchange, chain) -> {

			if (CollectionUtils.isEmpty(config.getCheckUri())){
				return chain.filter(exchange);
			}
			String pathLowCase = exchange.getRequest().getURI().getPath().toLowerCase();
			Optional<String> checkUriOption = config.getCheckUri().stream().filter(isSecurityUri(pathLowCase)).findAny();
			if (checkUriOption.isPresent()){
				Pair<String, String> uriWithScene = this.getUriAndType(checkUriOption.get());
				String type = uriWithScene.getRight();
				log.info("security filter path need security check path={}, type={}", pathLowCase, type);
				return this.securityCheck(exchange, chain, type);
			}
			return chain.filter(exchange);
		}, config);
	}

	private Predicate<String> isSecurityUri(String uri){
		return (checkUri) -> {
			if (!checkUri.contains(SECURITY_PATH_SPECIAL_CHAR)) {
				return checkUri.equals(uri);
			} else {
				Pair<String, String> uriAndType = this.getUriAndType(checkUri);
				return uriAndType.getLeft().equals(uri);
			}
		};
	}


	private Mono<Void> securityCheck(ServerWebExchange exchange, GatewayFilterChain chain, String type) {
		String cacheBody = (String) exchange.getAttributes().get(Constants.CACHED_REQUEST_BODY_KEY);
		JSONObject cacheBodyJson = JSONObject.parseObject(cacheBody);

		// 装入type
		if (StringUtils.isNotBlank(type)) {
			cacheBodyJson.put("type", type);
			log.info("securityCheck do check param={}, decorateParam={}", cacheBody, cacheBodyJson.toJSONString());
		}

		BodyInserter<Mono<String>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(Mono.just(cacheBodyJson.toJSONString()), String.class);
		return webClient.post()
						.uri(GatewayConstant.SECURITY_RISK_CHECK_URL)
						.headers(headers -> {
							exchange.getRequest().getHeaders().forEach((key, value) -> {
								// 注意设置content-length
								if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(key)) {
									headers.remove(HttpHeaders.CONTENT_LENGTH);
								} else {
									headers.addAll(key, value);
								}
							});
							Route route = (Route) exchange.getAttributes().get(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
							headers.add(GatewayConstant.CALL_SYS, route.getId());
						})
						.body(bodyInserter)
						.retrieve()
						.bodyToMono(ApiResponse.class)
						.flatMap(result -> {
							log.info("securityCheck security check result={}", JSONObject.toJSONString(result));
							// 结果判断
							if (Objects.isNull(result)) {
								return HttpUtil.genErrorResponse(exchange.getResponse(), HttpStatus.OK, ApiResultUtils.getErrorResultByLanguage(this.getLanguageEnum(exchange), GatewayConstant.SECURITY_CHECK_ERROR_CODE));
							}

							if (!result.isSuccess()){
								return HttpUtil.genErrorResponse(exchange.getResponse(), HttpStatus.OK, ApiResult.errorResponse(result.getErrCode(), result.getErrMsg()));
							}

							Map securityCheckResp = (Map) result.getData();
							String status = String.valueOf(securityCheckResp.get(GatewayConstant.OPERATION_STATUS));
							if (!CONST_100.equals(status)) {
								return HttpUtil.genErrorResponse(exchange.getResponse(), HttpStatus.OK, ApiResultUtils.getErrorResultByLanguage(this.getLanguageEnum(exchange), GatewayConstant.SECURITY_CHECK_ERROR_CODE));
							}

							return chain.filter(exchange);
						})
						.onErrorResume(throwable -> {
							log.error("securityCheck security check error! ", throwable);
							return HttpUtils.genInternalServerError(exchange.getResponse(), HttpUtils.returnResult(GatewayConstant.SERVER_ERROR, GatewayConstant.SERVER_ERROR_MSG));
						});
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 1;
	}



	@NotNull
	private String getLanguageEnum(ServerWebExchange exchange) {
		HttpHeaders headers = exchange.getRequest().getHeaders();
		List<String> languages = headers.get(GatewayConstant.LANGUAGE_ID);
		if (!CollectionUtils.isEmpty(languages)){
			return languages.get(0);
		}
		return "en";
	}

	/**
	 * 获取请求路径和安全类型
	 * /user/private/login/password#101
	 * path：/user/private/login/password
	 * type：101
	 * @param pathWithScene
	 * @return
	 */
	private Pair<String, String> getUriAndType(String pathWithScene){
		String[] spliter = pathWithScene.split("#");
		String path = spliter[0];
		String type = StringUtils.EMPTY;
		if (spliter.length == PATH_LENGTH) {
			type = spliter[1];
		}

		return Pair.of(path, type);
	}
}
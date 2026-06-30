package com.livechat.filter;

import com.akulaku.platform.components.gateway.core.filter.config.SimpleFilterConfig;
import com.alibaba.fastjson.JSON;
import com.livechat.constant.GatewayConstant;
import com.bnc.neith.basic.response.dto.ApiResponse;
import com.livechat.utils.HttpUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * token 检查filter
 * @author yangfei
 * @since 2023/08/14
 */
@Component
public class LivechatTokenGatewayFilterFactory extends AbstractGatewayFilterFactory<Config> implements Ordered {

	private static final Logger log = LoggerFactory.getLogger(LivechatTokenGatewayFilterFactory.class);

	@Resource
	HalfLoginErrorCodeConver halfLoginErrorCodeConver;

	@Resource
	LoadBalancerClient loadBalancerClient;

	/**
	 * im提示升级的开关
	 */
	@Value("${im.update.switch:true}")
	private Boolean imUpdateSwitch;

	/**
	 * im提示升级的最低版本 ios
	 */
	@Value("${im.update.iosVersion:160}")
	private Integer iosVersion;

	private WebClient webClient;

	public LivechatTokenGatewayFilterFactory() {
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

			// 校验移动端的版本号，方法代码具有临时性，用完即撤
			if(imUpdateSwitch && !pathShouldCheck(config, path) && checkMobileVersion(exchange)) {
				return HttpUtils.responseUnLoginWith(exchange.getResponse(), HttpUtils.returnResult("30086", getToastMsg(exchange)));
			}
			if (path.indexOf(GatewayConstant.PUBLIC_PATH) > GatewayConstant.ZERO) {
				return chain.filter(exchange);
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
		List<String> headSemiToken = headers.getOrDefault(GatewayConstant.SEMI_ACCESS_TOKEN, new ArrayList<>());
		String accessToken = StringUtils.EMPTY;
		String semiToken = StringUtils.EMPTY;

		if (headToken != null && headToken.size() > GatewayConstant.ZERO) {
			accessToken = headToken.get(GatewayConstant.ZERO);
		}

		if (headSemiToken.size() > GatewayConstant.ZERO) {
			semiToken = headSemiToken.get(GatewayConstant.ZERO);
		}

		// 请求头里没有token视为未登陆
		if (StringUtils.isBlank(accessToken) && optional) {
			return chain.filter(exchange);
		}

		log.info("livechat setUidInHead uri={}, semiToken={}, check={}", path, semiToken, isHalfLoginPath(path));
		// 半登录态校验
		if(isHalfLoginPath(path) && StringUtils.isNotBlank(semiToken)){
			return mobileClientSemiLoginResponse(exchange,chain,optional);
		}
		return mobileClientResponse(exchange, chain, optional);

	}

	/**
	 * 校验移动端的版本号
	 * 此方法为临时过渡版本方案，等升级完成后，此方法将下线
	 * true 表示需要拦截，需要给出提示
	 * false 表示不需要拦截，直接通过
	 * @param exchange
	 * @return
	 */
	private Boolean checkMobileVersion(ServerWebExchange exchange) {
		String path = exchange.getRequest().getURI().getPath().toLowerCase();
		if(StringUtils.isNotBlank(path) && path.startsWith("/livechat/")) {
			// 仅校验IM的相关请求
			HttpHeaders headers = exchange.getRequest().getHeaders();

			// 获取头部信息
			List<String> headDeviceType = headers.get("device-type");
			List<String> headAppVersion = headers.get("app-version");
			String deviceType = StringUtils.EMPTY;
			Integer appVersion = 0;

			if (headDeviceType != null && headDeviceType.size() > GatewayConstant.ZERO) {
				deviceType = headDeviceType.get(GatewayConstant.ZERO);
			}

			if (headAppVersion != null && headAppVersion.size() > GatewayConstant.ZERO) {
				appVersion = Integer.parseInt(headAppVersion.get(GatewayConstant.ZERO));
			}
			log.info("livechat checkMobileVersion uri={}, deviceType={}, appVersion={}", path, deviceType, appVersion);

			// 比较版本，低于指定版本的给出提示
			if("ios".equalsIgnoreCase(deviceType) && appVersion < iosVersion) {
				return true;
			}

			// android端不在此处拦截处理，就不用比较版本了，并直接返回false
			if("android".equalsIgnoreCase(deviceType)) {
				return false;
			}
		}
		return false;
	}

	/**
	 * 多语言提示，目前处理两种语言
	 * @param exchange
	 * @return
	 */
	private String getToastMsg(ServerWebExchange exchange) {
		// 仅校验IM的相关请求
		HttpHeaders headers = exchange.getRequest().getHeaders();

		// 获取头部信息，默认使用英文
		List<String> headLanguageId = headers.get(GatewayConstant.LANGUAGE_ID);
		String languageId = "en";

		if (headLanguageId != null && headLanguageId.size() > GatewayConstant.ZERO) {
			languageId = headLanguageId.get(GatewayConstant.ZERO);
		}

		// android端不在此处拦截处理，就不用比较版本了，并直接返回false
		if("en".equalsIgnoreCase(languageId)) {
			return "Please upgrade the version before using";
		} else {
			return "Harap tingkatkan versi sebelum menggunakannya";
		}
	}

	private Mono<Void> mobileClientResponse(ServerWebExchange exchange, GatewayFilterChain chain, Boolean optional) {

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
					if (throwable != null) {
						throwable.printStackTrace();
					} else {
						log.info("throwable is null");
					}
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

	private boolean isHalfLoginPath(String path) {
		return  path.indexOf(GatewayConstant.COMMON_HALF_LOGIN_PATH) >=0;
	}

    /**
     * URL是否需要进行版本校验
     * 配置的URL是默认不需要进行版本校验的，直接通过
     * @param config
     * @param path
     * @return
     */
    private boolean pathShouldCheck(Config config, String path) {
        return Objects.nonNull(config.getCheckUri()) && config.getCheckUri().contains(path);
    }
}


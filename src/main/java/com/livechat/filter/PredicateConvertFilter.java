package com.livechat.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.actuate.GatewayControllerEndpoint;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 谓词转换便于服务迁移
 * @author yangfei
 * @since 2024/03/08
 */
@Component
public class PredicateConvertFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PredicateConvertFilter.class);

    /**
     * map 的格式
     * key 原url
     *  value 新url + ”,“ + 服务的id
     */
    @Value("#{${route.map:{}}}")
    Map<String, List<String>> routeMap;

    private volatile Map<String, Route> predicate;

    @Autowired
    RouteLocator routeDefinitionRouteLocator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();

        if (routeMap.containsKey(path)) {
            List<String> newUrl = routeMap.get(path);
            // 数组长度为2，第一个为新url，第二个为服务的id
            ServerWebExchangeUtils.addOriginalRequestUrl(exchange, request.getURI());
            ServerHttpRequest req = request.mutate()
                    .path(newUrl.get(0))
                    .build();

            exchange = exchange.mutate().request(req).build();
            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, req.getURI());
            Route route = getRoute(newUrl.get(1));
            // 重设route
            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
            return chain.filter(exchange);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 获取所有配置
     *      参考{@link GatewayControllerEndpoint}
     * @return
     */
    public Route getRoute(String id) {
        if (Objects.isNull(predicate)) {
            Flux<Route> routes = routeDefinitionRouteLocator.getRoutes();
            ArrayList<Route> definitions = new ArrayList<>();
            routes.subscribe(definitions::add);
            predicate = definitions.stream().collect(Collectors.toMap(Route::getId, Function.identity()));
        }
        return predicate.get(id);
    }
}

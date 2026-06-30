package com.livechat.filter;

import com.livechat.constant.GatewayConstant;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.DefaultServerRequest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.akulaku.platform.components.gateway.core.constant.Constants;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 将requestBody缓存至attribute
 * @author yangfei
 * @since 2023/08/14
 */
@Component
public class CacheBodyGatewayFilterFactory extends AbstractGatewayFilterFactory<Config> {

    private final List<HttpMessageReader<?>> messageReaders;

    private static final String ACTIVE_KEY = "active";


    @Override
    public List<String> shortcutFieldOrder() {
        return Collections.singletonList(ACTIVE_KEY);
    }

    public CacheBodyGatewayFilterFactory() {
        this(Config.class);
    }

    public CacheBodyGatewayFilterFactory(Class<Config> configClass) {
        super(configClass);
        this.messageReaders = HandlerStrategies.withDefaults().messageReaders();
    }


    @Override
    public GatewayFilter apply(Config config) {
        return new DefaultFilter((exchange, chain) -> {
            // GET请求直接跳过，文件表单类型multipart/form-data 的直接跳过。
            String contentType = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            boolean controllerMethodFlag = Objects.equals(exchange.getRequest().getMethod(), HttpMethod.GET) ||
                    (null != contentType && contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE));
            if (controllerMethodFlag) {
                return chain.filter(exchange);
            }

            ServerRequest serverRequest = new DefaultServerRequest(exchange, this.messageReaders);
            Mono<String> modifiedBody = serverRequest.bodyToMono(String.class).flatMap(requestBody -> {
                exchange.getAttributes().put(Constants.CACHED_REQUEST_BODY_KEY, requestBody);
                return Mono.just(requestBody);
            });

            BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(exchange.getRequest().getHeaders());
            headers.remove(HttpHeaders.CONTENT_LENGTH);

            CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);
            return bodyInserter.insert(outputMessage, new BodyInserterContext())
                .then(Mono.defer(() -> {
                    ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(
                        exchange.getRequest()) {
                        @Override
                        public HttpHeaders getHeaders() {
                            HttpHeaders httpHeaders = new HttpHeaders();
                            httpHeaders.putAll(super.getHeaders());
                            if(!CollectionUtils.isEmpty(headers.get(GatewayConstant.UID))) {
                                httpHeaders.put(GatewayConstant.X_USER_ID, headers.get(GatewayConstant.UID));
                            }
                            return httpHeaders;
                        }

                        @Override
                        public Flux<DataBuffer> getBody() {
                            return outputMessage.getBody();
                        }
                    };
                    return chain.filter(exchange.mutate().request(decorator).build());
                }));

        }, config);
    }
}

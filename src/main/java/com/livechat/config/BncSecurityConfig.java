package com.livechat.config;

import com.livechat.filter.Config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * bnc 安全过滤器配置
 * @author yangfei
 * @since 2023/08/14
 */
@ConfigurationProperties
@Component
@Getter
@Setter
public class BncSecurityConfig extends Config {
    private Set<String> checkUri;
}

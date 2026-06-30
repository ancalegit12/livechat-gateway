package com.livechat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 安全配置
 * @author jiangyg
 */
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityBizConfig {

    private Set<String> urls;

    public Set<String> getUrls() {
        return urls;
    }

    public void setUrls(Set<String> urls) {
        this.urls = urls;
    }
}

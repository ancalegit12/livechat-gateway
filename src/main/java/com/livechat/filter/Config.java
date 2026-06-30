package com.livechat.filter;

import java.util.Set;

/**
 * 签名接口配置类
 * @author yangfei
 * @since 2023/08/14
 */
public class Config {

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

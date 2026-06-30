package com.livechat.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.akulaku.platform.components.base.util.domain.ALResponse;

/**
 * slb 健康检查接口
 * @author yangfei
 * @since 2023/08/14
 */
@RestController
@Slf4j
public class HealthCheckController {
    /**
     * slb 网关http 检查心跳 url
     */
    private static final String HEALTH_CHECK = "/health/check";

    /**
     * slb 网关心跳 检查
     * @return
     */
    @ResponseBody
    @RequestMapping(value = {HEALTH_CHECK}, method = RequestMethod.GET)
    public ALResponse healthCheck(){
        return ALResponse.SUCCESS;
    }
}

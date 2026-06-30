package com.livechat.filter;


import com.bnc.neith.basic.response.dto.ApiResponse;
import com.livechat.constant.GatewayConstant;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Objects;

/**
 * 半登录态 token 无效errorCode 转换类
 *
 * @author yangfei
 * @since 2023/5/19
 */
@Component
public class HalfLoginErrorCodeConver {

    /**
     * 设备类型 android
     */
    final String ANDROID = "android";

    /**
     * 设备类型 ioS
      */
    final String IOS = "ios";

    /**
     * 老的半登录态token无效的errorCode
     */
    final String OLD_HALF_LOGIN_TOKEN_ERROR_CODE = "401";

    /**
     *  新登录态token无效的errorCode
      */
    final String NEW_HALF_LOGIN_TOKEN_ERROR_CODE = "407";


    /**
     * halfLogin errorCode android 需要转换的版本
     */
    @Value("${halfLogin.error.androidVersion:0}")
    private String androidVersion;

    /**
     * halfLogin errorCode ios 需要转换的版本
     */
    @Value("${halfLogin.error.iosVersion:0}")
    private String iosVersion;


    public void convertErrorCode(ServerWebExchange exchange, ApiResponse apiResponse) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        /**
         * 取版本
         */
        List<String> listVersion = headers.get("app-version");
        String appVersion = StringUtils.EMPTY;
        if (listVersion != null && listVersion.size() > GatewayConstant.ZERO) {
            appVersion = listVersion.get(GatewayConstant.ZERO);
        }

        /**
         * 取设备类型
         */
        List<String> listType = headers.get("device-type");
        String deviceType = StringUtils.EMPTY;
        if (listType != null && listType.size() > GatewayConstant.ZERO) {
            deviceType = listType.get(GatewayConstant.ZERO);
        }

        boolean needConvertFlag = Objects.nonNull(apiResponse) &&
                StringUtils.isNotBlank(apiResponse.getErrCode()) &&
                OLD_HALF_LOGIN_TOKEN_ERROR_CODE.equals(apiResponse.getErrCode()) &&
                ((ANDROID.equalsIgnoreCase(deviceType) && Integer.parseInt(appVersion) > Integer.parseInt(androidVersion))
                        || (IOS.equalsIgnoreCase(deviceType) && Integer.parseInt(appVersion) > Integer.parseInt(iosVersion)));
        if (needConvertFlag) {
            apiResponse.setErrCode(NEW_HALF_LOGIN_TOKEN_ERROR_CODE);
        }
    }

}

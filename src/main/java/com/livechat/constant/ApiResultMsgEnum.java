package com.livechat.constant;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * 安全校验错误枚举
 * @author yangfei
 * @since 2023/08/14
 */
@Getter
public enum ApiResultMsgEnum {
    // 安全校验不通过
    SECURITY_CHECK_ERROR("4600", "Verifikasi gagal, Silakan coba lagi, ya!", "Verification failed,Please try again!"),

    // 安全校验时operationId 不存在
    SECURITY_OPERATION_ID_NOT_EXIST("4602", "Verifikasi gagal, Silakan coba lagi, ya!", "Verification failed,Please try again!"),

    // 安全校验下游接口不可用
    SYS_UNAVAILABLE("1013", "Maaf, koneksi internet bermasalah. Coba Lagi, ya(1013)", "Sorry, request time out. Please try again(1013)"),
    ;

    private final String errorCode;

    private final String errorMsgIn;

    private final String errorMsgEn;

    ApiResultMsgEnum(String errorCode, String errorMsgIn, String errorMsgEn) {
        this.errorCode = errorCode;
        this.errorMsgIn = errorMsgIn;
        this.errorMsgEn = errorMsgEn;
    }

    public static String getMsg(String language, String errorCode){
        if (StringUtils.isBlank(errorCode)){
            return "";
        }

        ApiResultMsgEnum apiResultMsgEnum = null;
        for (ApiResultMsgEnum resultMsgEnum : ApiResultMsgEnum.values()){
            if (resultMsgEnum.getErrorCode().equals(errorCode)){
                apiResultMsgEnum = resultMsgEnum;
            }
        }

        if (Objects.isNull(apiResultMsgEnum)){
            return "";
        }

        if (StringUtils.isBlank(language)){
            return apiResultMsgEnum.getErrorMsgEn();
        }

        switch (language){
            case "en":
                return apiResultMsgEnum.getErrorMsgEn();
            case "in":
                return apiResultMsgEnum.getErrorMsgIn();
            default:
                return "";
        }
    }
}

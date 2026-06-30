package com.livechat.utils;

import com.akulaku.platform.components.base.util.domain.ApiResult;
import com.livechat.constant.ApiResultMsgEnum;

/**
 * 辅助工具类
 * @author jiangyg
 * @since 2023/08/15
 */
public class ApiResultUtils {

    public static ApiResult getErrorResultByLanguage(String language, String errorCode){
        return ApiResult.errorResponse(errorCode, ApiResultMsgEnum.getMsg(language, errorCode));
    }

}

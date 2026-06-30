
package com.livechat.service;

import com.akulaku.platform.components.gateway.core.config.ApiAuthConfig;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 签名服务类
 * @author yangfei
 * @since 2023/08/14
 */
@Service
public class BncSignatureAuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(BncSignatureAuthenticationService.class);
    private static final Gson GSON = new Gson();
    @Autowired
    private ApiAuthConfig apiAuthConfig;

    static final String ANDROID = "android";

    static final String IOS = "ios";

    static int SIGN_ARRAY_FIX_LENGTH = 3;

    static String AND  = "&";

    /**
     * 签名securityKey配置  <br/>
     *  key 分别为各端传入（ key 由后台知道） <br/>
     *  value 由后台分配 <br/>
     * example：
     *     api.auth.secure-key = {'web1617604391361617920':'1bc','ios13333':'asdfs','android122222':'qwwww'}
     */
    @Value("#{${api.auth.secure-key}}")
    private Map<String, String> signKeyMap = new HashMap<>();

    public BncSignatureAuthenticationService() {
    }

    public Mono<Boolean> isValidRequestSignature(ServerHttpRequest request, String bodyStr) {


        if (!this.apiAuthConfig.isEnableApiAuth()) {
            return Mono.just(true);
        } else {
            HttpHeaders headers = request.getHeaders();
            String appVersion = headers.getFirst("app-version");
            String deviceType = headers.getFirst("device-type");
            String signKey = headers.getFirst("signKey");
            if (!StringUtils.isEmpty(appVersion) && !StringUtils.isEmpty(deviceType)) {
                Long version = Long.valueOf(appVersion);
                Integer androidMinSignVersion = this.apiAuthConfig.getAndroidMinSignVersion();
                Integer iosMinSignVersion = this.apiAuthConfig.getIosMinSignVersion();
                boolean checkVersionFlag = ANDROID.equals(deviceType) && (androidMinSignVersion == null || version < (long)androidMinSignVersion)
                        || IOS.equals(deviceType) && (iosMinSignVersion == null || version < (long)iosMinSignVersion);

                if (checkVersionFlag) {
                    return Mono.just(true);
                } else {
                    String signParts = headers.getFirst("sign");
                    if (!StringUtils.isEmpty(appVersion) && !StringUtils.isEmpty(signParts)) {
                        String[] split = signParts.split("\\|");
                        if (split.length != SIGN_ARRAY_FIX_LENGTH) {
                            log.warn("请求签名参数不合法，vc={},signParts={}", appVersion, signParts);
                            return Mono.just(false);
                        } else {
                            String signature = split[0];
                            String nonce = split[1];
                            String timestampStr = split[2];
                            String secureKey = null;
                            // 取签名的盐值
                            if (org.apache.commons.lang3.StringUtils.isNotBlank(signKey)) {
                                secureKey = signKeyMap.get(signKey);
                            } else {
                                if (ANDROID.equals(deviceType)) {
                                    secureKey = (String) this.apiAuthConfig.getAndroidApiSecureKey().stream().filter((versionSecureKey) -> {
                                        return versionSecureKey.getMaxVersion() >= version && versionSecureKey.getMinVersion() <= version;
                                    }).map((versionSecureKey) -> {
                                        return versionSecureKey.getSecureKey();
                                    }).findFirst().orElse("");
                                } else if (IOS.equals(deviceType)) {
                                    secureKey = (String) this.apiAuthConfig.getIosApiSecureKey().stream().filter((versionSecureKey) -> {
                                        return versionSecureKey.getMaxVersion() >= version && versionSecureKey.getMinVersion() <= version;
                                    }).map((versionSecureKey) -> {
                                        return versionSecureKey.getSecureKey();
                                    }).findFirst().orElse("");
                                }
                            }
                            if (secureKey == null) {
                                log.warn("请求dt={},vc={}版本没有找到合适的SecureKey", deviceType, appVersion);
                                return Mono.just(false);
                            } else {
                                if (StringUtils.hasLength(timestampStr)) {
                                    try {
                                        long timestamp = Long.parseLong(timestampStr);
                                        long duration = System.currentTimeMillis() - timestamp;
                                        if (this.apiAuthConfig.getTimeoutDuration() < duration) {
                                            return Mono.just(false);
                                        }
                                    } catch (Exception var19) {
                                        log.warn("timestamp parsing error: {}", timestampStr);
                                        return Mono.just(false);
                                    }
                                }

                                Map<String, Object> getDataMap = new HashMap(24);
                                MultiValueMap<String, String> queryParams = request.getQueryParams();
                                if (queryParams != null && queryParams.size() > 0) {
                                    getDataMap.putAll(queryParams.toSingleValueMap());
                                }

                                Map<String, Object> postDataMap = new HashMap(24);
                                HttpMethod method = request.getMethod();
                                if (HttpMethod.POST == method) {
                                    postDataMap.putAll(convertRequestParamsToMapForPost(bodyStr));
                                } else {
                                    postDataMap.putAll(convertRequestParamsToMap(bodyStr));
                                }
                                return Mono.just(checkSignature(getDataMap, postDataMap, nonce, timestampStr, secureKey, signature));
                            }
                        }
                    } else {
                        log.warn("请求参数不合法，vc={},signParts={}", appVersion, signParts);
                        return Mono.just(false);
                    }
                }
            } else {
                return Mono.just(false);
            }
        }
    }

    private static Map<String, Object> convertRequestParamsToMapForPost(String requestParams) {
        if (StringUtils.isEmpty(requestParams)) {
            return new HashMap(24);
        } else {
            return (Map)JSONObject.parseObject(requestParams, Map.class, new Feature[0]);
        }
    }

    private static Map<String, Object> convertRequestParamsToMap(String requestParams) {
        if (StringUtils.isEmpty(requestParams)) {
            return new HashMap(24);
        } else if (!requestParams.contains(AND)) {
            return (Map)JSONObject.parseObject(requestParams, Map.class, new Feature[0]);
        } else {
            Map<String, Object> dataMap = new HashMap(24);
            String[] params = requestParams.split("&");
            String[] results = null;
            String[] var4 = params;
            int var5 = params.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                String param = var4[var6];
                results = param.split("=");
                dataMap.put(results[0], results[1]);
            }

            return dataMap;
        }
    }

    private static String convertParamTypeToString(Map<String, Object> dataMap, StringBuilder sb) {
        dataMap.forEach((key, value) -> {
            if (Objects.isNull(value)) {
                sb.append(org.apache.commons.lang3.StringUtils.EMPTY);
            } else {
                if (value.getClass().isArray()) {
                    sb.append(key).append(":").append(StringUtils.arrayToDelimitedString((Object[]) ((Object[]) value), ","));
                } else if (value instanceof Map) {
                    sb.append(key).append(":").append(convertParamTypeToString((Map) (new TreeMap((Map) value)), new StringBuilder()));
                } else if (value instanceof JSONArray) {
                    sb.append(key).append(":").append(convertParamTypeToString((JSONArray) value, new StringBuilder()));
                } else if (value instanceof JSONObject) {
                    sb.append(key).append(":").append(convertParamTypeToString((JSONObject) value, new StringBuilder()));
                } else {
                    sb.append(key).append(":").append(value);
                }
                sb.append("|");
            }

        });
        String paramStr = sb.toString();
        return paramStr.substring(0, paramStr.length() - 1);
    }

    private static String convertParamTypeToString(JSONObject data, StringBuilder sb) {
        data.forEach((key, value) -> {
            if (Objects.isNull(value)) {
                sb.append(org.apache.commons.lang3.StringUtils.EMPTY);
            } else {
                if (value instanceof JSONArray) {
                    sb.append(key).append(":").append(convertParamTypeToString((JSONArray) value, new StringBuilder()));
                } else if (value instanceof Map) {
                    sb.append(key).append(":").append(convertParamTypeToString((Map) (new TreeMap((Map) value)), new StringBuilder()));
                } else if (value instanceof JSONObject) {
                    sb.append(key).append(":").append(convertParamTypeToString((JSONObject) value, new StringBuilder()));
                } else {
                    sb.append(key).append(":").append(value);
                }

                sb.append("|");
            }
        });
        String paramStr = sb.toString();
        return paramStr.substring(0, paramStr.length() - 1);
    }

    private static String convertParamTypeToString(JSONArray jsonArray, StringBuilder sb) {
        jsonArray.forEach((value) -> {
            if (Objects.isNull(value)) {
                sb.append(org.apache.commons.lang3.StringUtils.EMPTY);
            } else {
                if (value instanceof Map) {
                    sb.append(convertParamTypeToString((Map) (new TreeMap((Map) value)), new StringBuilder()));
                } else if (value instanceof JSONObject) {
                    sb.append(convertParamTypeToString((JSONObject) value, new StringBuilder()));
                } else if (value instanceof JSONArray) {
                    sb.append(convertParamTypeToString((JSONArray) value, new StringBuilder()));
                } else {
                    sb.append(value);
                }

                sb.append(",");
            }
        });
        String paramStr = sb.toString();
        return paramStr.substring(0, paramStr.length() - 1);
    }

    private static boolean checkSignature(Map<String, Object> getDataMap, Map<String, Object> postDataMap, String nonce, String timestamp, String secureKey, String signature) {
        String generateSign = getSignatureStr(getDataMap, postDataMap, nonce, timestamp, secureKey);
        if (!generateSign.equals(signature)) {
            log.warn("请求签名不正确,req_sign={},gen_sign={}", signature, generateSign);
            return false;
        } else {
            return true;
        }
    }

    private static String getSignatureStr(Map<String, Object> getDataMap, Map<String, Object> postDataMap, String nonce, String timestamp, String secureKey) {
        StringBuilder md5StrBuilder = new StringBuilder();
        if (getDataMap != null && getDataMap.size() > 0) {
            md5StrBuilder.append(convertParamTypeToString((Map)(new TreeMap(getDataMap)), new StringBuilder()));
            md5StrBuilder.append("&");
        }

        if (postDataMap != null && postDataMap.size() > 0) {
            md5StrBuilder.append(convertParamTypeToString((Map)(new TreeMap(postDataMap)), new StringBuilder()));
            md5StrBuilder.append("&");
        }

        md5StrBuilder.append(secureKey).append("&").append(nonce).append("&").append(timestamp);
        return DigestUtils.md5Hex(md5StrBuilder.toString()).substring(8, 24);
    }
}

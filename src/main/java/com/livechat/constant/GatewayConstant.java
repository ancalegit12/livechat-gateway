package com.livechat.constant;


/**
 * 常量定义类
 * @author yangfei
 * @since 2023/08/14
 */
public interface GatewayConstant {

	
	String ACCESS_TOKEN = "accessToken";
	/**
	 * 半登录态 header 字段
	 */
	String SEMI_ACCESS_TOKEN = "semiAccessToken";

	String COMMON_HALF_LOGIN_PATH = "/half-login/";
	/**
	 * app 半登陆态检查
	 */
	String CHECK_HALF_LOGIN_TOKEN_URL = "/app/private/userService/checkHalfLogin";

	/**
	 *  response data node
	 */
	String SUCCESS = "success";
	
	String ERR_CODE = "errCode";

	String ERR_MSG = "errMsg";

	String PUBLIC_PATH ="/public/";

	String OPTION_PATH ="/option/";

	int ZERO = 0;


	String BASE_URL = "lb://mobile-biz";

	String DASHBOARD_URL = "lb://bnc-dashboard-biz";

	String OPENAPIPORTAL_URL = "lb://backend-partner-biz";

	 /**
	 * app 登陆态检查
	 */
	String CHECK_TOKEN_URL = "/app/private/userService/checkLogin";

	/**
	 * dashboard 登陆态检查
	 */
	String DASHBOARD_CHECK_TOKEN_URL = "/dashboard/private/dashboard/check/login";

	String OPENAPIPORTAL_BACKOFFICE_CHECK_TOKEN_URL = "/openapi-portal/backoffice/private/checkLogin";

	String OPENAPIPORTAL_USERPORTAL_CHECK_TOKEN_URL = "/openapi-portal/userportal/private/checkLogin";

	String UID = "uid";

	String STORE_ID = "storeId";

	String ADMIN_ID = "adminId";

	String PARTNER_ID = "partnerId";

	String ID = "id";

	String ECIF_ID = "ecifId";

	String SERVER_ERROR = "1013";

	String SERVER_ERROR_MSG = "server is busy";

	String ACCESS_URL = "accessUrl";

	String DASHBOARD_URL_PATH = "/dashboard/";

	String OPENAPIPORTAL_BACKOFFICE_URL_PATH = "/openapi-portal/backoffice/";

	String OPENAPIPORTAL_USERPORTAL_URL_PATH = "/openapi-portal/userportal/";
	
	String MOBILE_URL_PATH = "/app/";

	String PUSH_URL_PATH = "/capi/push";

	String MIDRISK_URL_PATH = "/midendrisk";

	String X_USER_ID = "X-user-id";

	/**
	 * 信用卡的用户id
	 */
	String CREDIT_ECIF_ID = "creditEcifId";

	/**
	 * 安全反查失败
	 */
	String SECURITY_CHECK_ERROR_CODE = "4600";

	String SECURITY_RISK_BASE_URL = "lb://risk-biz";
	String SECURITY_RISK_CHECK_URL = "/risk/public/security/check/security-biz-check";
	String OPERATION_STATUS = "operationStatus";
	String CALL_SYS = "call-sys";
	String LANGUAGE_ID = "languageId";

}

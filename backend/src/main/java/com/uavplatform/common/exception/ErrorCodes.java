package com.uavplatform.common.exception;

/**
 * 统一错误码。
 */
public final class ErrorCodes {

    public static final int OK = 0;
    public static final int BAD_REQUEST = 40000;
    public static final int UNAUTHORIZED = 40100;
    public static final int TOKEN_EXPIRED = 40101;
    public static final int FORBIDDEN = 40300;
    public static final int NOT_FOUND = 40400;
    public static final int CONFLICT = 40900;
    public static final int INTERNAL = 50000;

    public static final int AUTH_BAD_CREDENTIALS = 10001;
    public static final int AUTH_DISABLED = 10002;
    public static final int ACCOUNT_EXISTS = 10003;
    public static final int ROLE_NOT_FOUND = 10004;
    public static final int ROLE_LOCKED = 10005;
    public static final int SUPER_ADMIN_PROTECTED = 10006;
    public static final int CANNOT_DELETE_SELF = 10007;
    public static final int AUTH_PASSWORD_INCORRECT = 10008;
    public static final int EMAIL_INVALID = 10009;

    public static final int DEVICE_SN_EXISTS = 20001;
    public static final int BIND_CODE_INVALID = 20002;
    public static final int DEVICE_NOT_FOUND = 20003;

    public static final int ANNOTATION_NOT_FOUND = 30001;
    public static final int GEOMETRY_INVALID = 30002;
    public static final int FOLDER_LOCKED = 30003;
    public static final int NO_DEVICE_TO_SYNC = 30004;
    public static final int SYNC_RUNNING = 30005;
    public static final int SAFETY_AREA_NOT_FOUND = 30006;
    public static final int SAFETY_REMOTE_ERROR = 30007;
    public static final int WORK_AREA_NOT_FOUND = 30008;

    public static final int WAYLINE_NOT_FOUND = 40001;
    public static final int WAYLINE_NAME_INVALID = 40002;
    public static final int WAYLINE_PARSE_FAIL = 40003;
    public static final int WAYLINE_IN_USE = 40004;
    public static final int WAYLINE_TYPE_UNSUPPORTED = 40005;
    public static final int WAYLINE_FOLDER_NOT_FOUND = 40006;
    public static final int WAYLINE_FOLDER_NOT_EMPTY = 40007;

    public static final int MISSION_NOT_FOUND = 50001;
    public static final int WAYLINE_UNAVAILABLE = 50002;
    public static final int DEVICE_UNAVAILABLE = 50003;
    public static final int MISSION_STATUS_INVALID = 50004;
    public static final int MISSION_PARAM_MISSING = 50005;
    public static final int MULTIMODAL_UNAVAILABLE = 50006;
    public static final int MISSION_DELETE_FORBIDDEN = 50007;
    /** 司空飞行任务下发/查询失败 */
    public static final int MISSION_REMOTE_ERROR = 50008;

    public static final int MEDIA_NOT_FOUND = 60001;
    public static final int QUOTA_EXCEEDED = 60002;
    public static final int UPLOAD_CREDENTIAL_FAIL = 60003;
    public static final int RECONSTRUCT_STATUS_INVALID = 60004;
    public static final int FEATURE_DISABLED = 60005;
    /** FlightHub 原生 SDK 未就绪或调用失败（媒体库等依赖司空数据的接口） */
    public static final int FH2_SDK_UNAVAILABLE = 60006;

    public static final int TIME_RANGE_INVALID = 70001;

    public static final int ALGORITHM_NOT_FOUND = 80001;
    public static final int ALGORITHM_NOT_OPENED = 80002;

    private ErrorCodes() {
    }
}

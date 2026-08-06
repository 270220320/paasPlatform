package com.uavplatform.common.security;

import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;

/**
 * 权限校验工具。
 */
public final class PermissionChecker {

    private PermissionChecker() {
    }

    public static void require(String permissionCode) {
        LoginUser user = UserContext.get();
        if (user == null || user.getPermissions() == null || !user.getPermissions().contains(permissionCode)) {
            throw new BizException(ErrorCodes.FORBIDDEN, "无权限");
        }
    }

    public static void requireAny(String... permissionCodes) {
        if (permissionCodes == null || permissionCodes.length == 0) {
            throw new BizException(ErrorCodes.FORBIDDEN, "无权限");
        }
        for (String code : permissionCodes) {
            if (has(code)) {
                return;
            }
        }
        throw new BizException(ErrorCodes.FORBIDDEN, "无权限");
    }

    public static boolean has(String permissionCode) {
        LoginUser user = UserContext.get();
        return user != null && user.getPermissions() != null && user.getPermissions().contains(permissionCode);
    }

    public static boolean isSuperAdmin() {
        LoginUser user = UserContext.get();
        return user != null && "super_admin".equals(user.getRoleCode());
    }
}

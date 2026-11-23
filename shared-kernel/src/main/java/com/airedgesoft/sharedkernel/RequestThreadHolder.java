package com.airedgesoft.sharedkernel;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 请求线程持有者,用于用户相关信息传递（用户信息、语言、租户），其他无关变量禁止在此类中存储！！！
 *
 * @author jiangkun@airedgesoft.com
 */
public class RequestThreadHolder {

    private static final TransmittableThreadLocal<String> SESSION_DATA = new TransmittableThreadLocal<>();
    /**
     * 缓存working_set_id
     */
    private static final TransmittableThreadLocal<Long> WORKINGSET = new TransmittableThreadLocal<>();


    private RequestThreadHolder() {
    }

    public static void putSessionId(String sessionId) {
        SESSION_DATA.set(sessionId);
    }

    public static String getSessionId() {
        return SESSION_DATA.get();
    }

    public static void remove() {
        SESSION_DATA.remove();
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public static String getUserId() {
        return "419436436619264";
    }

    /**
     * 获取工作集合
     */
    public static Long getWorkingset() {
        return WORKINGSET.get();
    }

    /**
     * 设置工作集合
     */
    public static void setWorkingset(Long workingsetId) {
        WORKINGSET.set(workingsetId);
    }

    /**
     * 获取用户名称
     *
     * @return 用户ID
     */
    public static String getUserName() {
        return "管理员";
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public static Long getLongUserId() {
        return 419436436619264L;
    }


}

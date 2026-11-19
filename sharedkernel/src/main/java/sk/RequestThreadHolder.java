package sk;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.*;

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

    private static final TransmittableThreadLocal<Long> SNAPSHOT_ID = new TransmittableThreadLocal<>();

    private static final TransmittableThreadLocal<Long> WORKSPACE_TYPE = new TransmittableThreadLocal<>();
    /**
     * 缓存working_set_id
     */
    private static final TransmittableThreadLocal<Long> WORKSPACE = new TransmittableThreadLocal<>();

    /**
     * 有权限的实例id,防止多次处理 ，key档次查询入参md5,value 为符合条件的数据
     * <p>
     * 值必须是并发容器：{@link TransmittableThreadLocal} 默认按<b>引用</b>把父线程的值传给工作线程， 同一个请求的多个并行任务会共享同一个 Map 并发写入。
     * </p>
     */
    private static final TransmittableThreadLocal<Map<String, List<Long>>> DISCOVERY_IDS = new TransmittableThreadLocal<>();

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
     * 设置工作空间ID
     */
    public static void setWorkspace(Long workspaceId) {
        WORKSPACE.set(workspaceId);
    }

    /**
     * 获取工作空间ID
     */
    public static Long getWorkspace() {
        return WORKSPACE.get();
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
     * 设置快照ID
     *
     * @param snapshotId
     *            快照ID
     */
    public static void setSnapshotId(Long snapshotId) {
        SNAPSHOT_ID.set(snapshotId);
    }

    public static Long getSnapshotId() {
        return SNAPSHOT_ID.get();
    }

    public static void removeSnapshotId() {
        SNAPSHOT_ID.remove();
    }

    /**
     * 获取空间类型
     */
    public static void setWorkspaceType(Long type) {
        WORKSPACE_TYPE.set(type);
    }

    public static Long getWorkspaceType() {
        return WORKSPACE_TYPE.get();
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

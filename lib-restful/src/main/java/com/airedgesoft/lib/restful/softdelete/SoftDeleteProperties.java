package com.airedgesoft.lib.restful.softdelete;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 软删除配置（前缀 {@code airedge.restful.soft-delete}；v4-restful-api.md §5.1）。
 *
 * <p>
 * <b>列模型沿用 v3 既有表结构</b>（本次不改表结构）：{@code delete_flag int4}（{@code 0} = 存活、{@code 1} = 已删）+
 * {@code updated_time int8}（Unix Epoch 毫秒）。<b>无独立删除时刻列</b>——删除时刻由「已删行的 {@code updated_time}」推导； 软删 / 恢复均在同一语句刷新时间列（与 v3
 * {@code DataObject} 删除写口径一致）。
 *
 * <p>
 * {@code tables} = 具备上述两列的<b>表白名单</b>：读模型拦截器挂在共享 {@code MybatisPlusInterceptor} 链上会对链上全部 Mapper 生效， 未限定表会改写无软删列的表（如
 * legacy 表）→ {@code enabled=true} 时必填，缺失即启动失败。
 */
@ConfigurationProperties(prefix = "airedge.restful.soft-delete")
public class SoftDeleteProperties {

    /** 总开关（缺省 false；关闭时拦截器不注册）。 */
    private boolean enabled = false;

    /** 删除标志列（缺省 {@code delete_flag}，int4；v3 系统列口径）。 */
    private String flagColumn = "delete_flag";

    /** 「存活」标志值（缺省 0；查询过滤条件与恢复回写值）。 */
    private int aliveValue = 0;

    /** 「已删除」标志值（缺省 1；软删写入值）。 */
    private int deletedValue = 1;

    /** 时间列（缺省 {@code updated_time}，int8 毫秒；软删 / 恢复同语句刷新，已删行的该值即删除时刻）。 */
    private String timeColumn = "updated_time";

    /** 回收站放行参数名（缺省 {@code showDeleted}）。 */
    private String showDeletedParam = "showDeleted";

    /** 具备软删两列的表白名单（缺省空；{@code enabled=true} 时必填，拦截器只改写名单内主表的语句）。 */
    private List<String> tables = new ArrayList<>();

    /** 自动把拦截器挂进宿主 {@code MybatisPlusInterceptor} 链首（缺省 true；BC 自管次序时置 false）。 */
    private boolean autoRegister = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFlagColumn() {
        return flagColumn;
    }

    public void setFlagColumn(String flagColumn) {
        this.flagColumn = flagColumn;
    }

    public int getAliveValue() {
        return aliveValue;
    }

    public void setAliveValue(int aliveValue) {
        this.aliveValue = aliveValue;
    }

    public int getDeletedValue() {
        return deletedValue;
    }

    public void setDeletedValue(int deletedValue) {
        this.deletedValue = deletedValue;
    }

    public String getTimeColumn() {
        return timeColumn;
    }

    public void setTimeColumn(String timeColumn) {
        this.timeColumn = timeColumn;
    }

    public String getShowDeletedParam() {
        return showDeletedParam;
    }

    public void setShowDeletedParam(String showDeletedParam) {
        this.showDeletedParam = showDeletedParam;
    }

    public List<String> getTables() {
        return tables;
    }

    public void setTables(List<String> tables) {
        this.tables = tables;
    }

    public boolean isAutoRegister() {
        return autoRegister;
    }

    public void setAutoRegister(boolean autoRegister) {
        this.autoRegister = autoRegister;
    }

    /**
     * 装配期校验（{@code SoftDeleteAutoConfiguration} → {@code SoftDeleteContext.configure} 调用）：列名必须是蛇形标识符， 非法即启动失败——列名会被拼进
     * SQL（读侧改写器 / 仓储写语句声明），不能等到首次调用才暴露。
     */
    public void validate() {
        requireIdentifier(flagColumn, "flag-column");
        requireIdentifier(timeColumn, "time-column");
    }

    private static void requireIdentifier(String column, String key) {
        if (column == null || !column.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalStateException("airedge.restful.soft-delete." + key + " 非法（应为蛇形标识符）: " + column);
        }
    }
}

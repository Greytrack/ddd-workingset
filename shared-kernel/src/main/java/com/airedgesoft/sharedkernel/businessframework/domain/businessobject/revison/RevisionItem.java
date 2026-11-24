package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison;

import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.BusinessDataCarrier;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;

/**
 * 可版次化能力子项：一条版次记录。
 *
 * <p>
 * 版次记录<b>不是业务对象</b>，但同样是业务数据承载者——故继承 {@link BusinessDataCarrier}。
 *
 * <ul>
 * <li>{@link #getId()}：版次记录自身身份（版次号是状态，不是身份）</li>
 * <li>{@link #getRevision()}：版次值对象（版次号 + 签出签入状态）</li>
 * <li>{@link #setRevision(Revision)}：值对象不可变，状态推进即整体替换</li>
 * <li>{@link #setFieldValue(String, AttributeValue)}：唯一内容写入口（只写在本版次的业务数据上）</li>
 * </ul>
 */
public interface RevisionItem extends BusinessDataCarrier {

    /** 版次记录身份（裸 Long）。 */
    Long getId();

    /** 版次值对象。 */
    Revision getRevision();

    /** 写单个字段值（写在该版次的业务数据上，值已通过校验）。 */
    void setFieldValue(String column, AttributeValue value);

    /** 整体替换版次值对象。 */
    void setRevision(Revision revision);

    /** 版次号；便捷读取，等价于 {@code getRevision().revisionNumber().value()}。 */
    default int getRevisionNumber() {
        return getRevision().revisionNumber().value();
    }
}

package com.airedgesoft.sharedkernel.businessframework.domain.reference;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.RowMark;

import java.util.Objects;

/**
 * 值对象：引用版次记录（ReferenceRevisionRecord，引用稿 §五-2）。
 *
 * <p>
 * 版次 ＋ 三态标记（新增／不变／失效，引用只用 RowMark 三态、无 修改/替换——无 originRowId、无血缘）， 以版次为自然键、每版次至多一条（构造期 fail-fast；重复由聚合
 * {@code markUnchanged} 幂等挡住）。引用条数不随版次增长：签出只在聚合内追加一条记录，行本身不复制（口径 1）。
 *
 * <p>
 * 标记的写入只在聚合方法内发生（写路径原则）：本值对象只暴露读面与判定。
 *
 * @param revision
 *            引用方版次号（sourceRevision；≥1）
 * @param mark
 *            行标记（仅 ADDED / UNCHANGED / INVALIDATED 三态；其余拒绝）
 */
public record ReferenceRevisionRecord(int revision, RowMark mark) {

    public ReferenceRevisionRecord {
        if (revision < 1) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                    SharedKernelResultCode.REFERENCE_REVISION_INVALID, revision);
        }
        Objects.requireNonNull(mark, "mark");
        if (mark != RowMark.ADDED && mark != RowMark.UNCHANGED && mark != RowMark.INVALIDATED) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                    SharedKernelResultCode.REFERENCE_MARK_UNSUPPORTED, mark);
        }
    }

    /**
     * 本版次记录是否有效（非失效）——「整行删除」判据的原子判定（口径 3：记录集内不存在任何非失效记录才删行）。
     *
     * @return 有效=true
     */
    public boolean isEffective() {
        return mark != RowMark.INVALIDATED;
    }

    /**
     * 是否属于指定版次。
     *
     * @param revision
     *            版次号
     * @return 版次相同=true
     */
    public boolean belongsToRevision(int revision) {
        return this.revision == revision;
    }

    /**
     * 是否早于指定版次（「有历史记录」判据——决定换目标时留失效痕迹还是整行丢弃，口径 2）。
     *
     * @param revision
     *            版次号
     * @return 本记录版次更早=true
     */
    public boolean isBefore(int revision) {
        return this.revision < revision;
    }
}

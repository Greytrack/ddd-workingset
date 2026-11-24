package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.airedgesoft.lib.restful.id.ResourceIds;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * 版次记录读模型（跨对象通用，§12 只读子集合）：需求 / 物料 / 文档等**多版次业务对象**共用同一版次事实形状。
 *
 * <p>
 * 历史版次不可变、可长缓存；版次号是业务状态（非身份），与并发代数正交。
 *
 * @param name
 *            AIP-122 资源名（子资源含祖先段，如 {@code requirements/623809242866016/revisions/1}）；由调用方按所属对象拼好传入
 * @param revisionNumber
 *            业务版次号（R1..Rn）
 * @param checkedOut
 *            该版次是否签出态
 * @param checkoutActorId
 *            签出人 id
 * @param checkoutTime
 *            签出时刻（RFC 3339）
 * @param checkinActorId
 *            签入人 id
 * @param checkinTime
 *            签入时刻（RFC 3339）
 * @param latestCheckedIn
 *            是否最新签入版次
 * @param createTime
 *            版次创建时刻（RFC 3339，output-only）
 */
@Schema(description = "版次记录（跨对象通用只读子集合，§12）")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QRevision(@Schema(description = "AIP-122 资源名（含祖先段）") String name,
        @Schema(description = "业务版次号（R1..Rn）") Integer revisionNumber,
        @Schema(description = "是否签出态") Boolean checkedOut, @Schema(description = "签出人 id") String checkoutActorId,
        @Schema(description = "签出时刻（RFC 3339）") String checkoutTime,
        @Schema(description = "签入人 id") String checkinActorId,
        @Schema(description = "签入时刻（RFC 3339）") String checkinTime,
        @Schema(description = "是否最新签入版次") Boolean latestCheckedIn,
        @Schema(description = "版次创建时刻（RFC 3339）", accessMode = Schema.AccessMode.READ_ONLY) String createTime) {

    /** 版次子资源段（追加在所属对象资源名之后）：{@code {parent}/revisions/{n}}。 */
    private static final String REVISIONS_SEGMENT = "/revisions/";

    /**
     * 装配版次记录：资源名由所属对象资源名派生，时间契约形态（RFC 3339）与标识串在此统一产出。
     *
     * @param parentResourceName
     *            所属对象资源名（相对形态，如 {@code requirements/623809242866016}）；空 → 资源名空
     */
    public static QRevision of(String parentResourceName, int revisionNumber, boolean checkedOut, Long checkoutActorId,
            Long checkoutTime, Long checkinActorId, Long checkinTime, boolean latestCheckedIn, Long createTime) {
        String name = parentResourceName == null ? null : parentResourceName + REVISIONS_SEGMENT + revisionNumber;
        return new QRevision(name, revisionNumber, checkedOut, ResourceIds.format(checkoutActorId),
                rfc3339(checkoutTime), ResourceIds.format(checkinActorId), rfc3339(checkinTime), latestCheckedIn,
                rfc3339(createTime));
    }

    /** epoch millis → RFC 3339 时刻字符串；空值 → null。 */
    private static String rfc3339(Long epochMilli) {
        return epochMilli == null ? null : DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMilli));
    }
}

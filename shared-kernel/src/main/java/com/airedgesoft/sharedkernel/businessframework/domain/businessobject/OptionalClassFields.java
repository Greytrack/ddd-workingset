package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

import java.math.BigDecimal;

/**
 * 接口：需求版次的<b>类级可选字段</b>（旧栈动态分类现表预分配扩展列·01 段）。
 *
 * <p>
 * 与 {@link OptionalSubClassFields}（001 段＝子类级）成对：01 段（{@code date01…richtext05}）承载<b>类（Class）级</b>可选字段（75 列），
 * 两段列名互不冲突、同表共存；段位口径（01＝类级 / 001＝子类级）为用户 2026-10-13 拍板。
 *
 * <p>
 * <b>实现方式（用户拍板 2026-10-13）</b>：本类型是<b>能力接口</b>，由宿主版次 {@code RequirementRevision} 实现—— 可选字段值不另立 Holder
 * 对象，而是落在宿主的业务数据（BusinessData）里：可选列名即业务数据键， 默认访问器直读宿主业务数据（与 {@code RequirementRevision#getReleaseTime()} 同款「按列名
 * 类型化读」口径）， 写入经 {@code setFieldValue}（守卫＝版次签出态）。因此：
 *
 * <ul>
 * <li>可选字段天然随版次版本化——签出工作版次自复制业务数据时一并随行，无需另做 Holder 拷贝；</li>
 * <li>业务数据键＝可选列名（与 {@code FieldValue.dbName} 同值），供写仓储按列名落列。</li>
 * </ul>
 *
 * <p>
 * <b>类型口径</b>（领域侧，照旧栈列定义）：date / list / startdate / endstartdate（bigint）→ {@code Long}；numeric → {@code BigDecimal}；
 * 其余文本列 → {@code String}。HTTP 契约侧统一以 {@code String} 承载（见 application 层 {@code OptionalFields}）。
 *
 * <p>
 * <b>挂账</b>：可选字段的读取与写入映射由具体仓储接线，随动态属性 / 元数据轮落实。 本接口仅声明领域字段能力，不提供数据库列映射；接口存在不表示仓储已支持持久化这些字段。 将领域字段能力与具体存储表示分开，才能在仓储去掉专属
 * PO 时继续保留领域接口，不让领域依赖读取框架。
 */
public interface OptionalClassFields extends BusinessDataCarrier {

    /** 日期扩展字段01（date01）。 */
    String COLUMN_DATE_01 = "date01";

    /** 日期扩展字段02（date02）。 */
    String COLUMN_DATE_02 = "date02";

    /** 日期扩展字段03（date03）。 */
    String COLUMN_DATE_03 = "date03";

    /** 日期扩展字段04（date04）。 */
    String COLUMN_DATE_04 = "date04";

    /** 日期扩展字段05（date05）。 */
    String COLUMN_DATE_05 = "date05";

    /** 文本扩展字段01（text01）。 */
    String COLUMN_TEXT_01 = "text01";

    /** 文本扩展字段02（text02）。 */
    String COLUMN_TEXT_02 = "text02";

    /** 文本扩展字段03（text03）。 */
    String COLUMN_TEXT_03 = "text03";

    /** 文本扩展字段04（text04）。 */
    String COLUMN_TEXT_04 = "text04";

    /** 文本扩展字段05（text05）。 */
    String COLUMN_TEXT_05 = "text05";

    /** 文本扩展字段06（text06）。 */
    String COLUMN_TEXT_06 = "text06";

    /** 文本扩展字段07（text07）。 */
    String COLUMN_TEXT_07 = "text07";

    /** 文本扩展字段08（text08）。 */
    String COLUMN_TEXT_08 = "text08";

    /** 文本扩展字段09（text09）。 */
    String COLUMN_TEXT_09 = "text09";

    /** 文本扩展字段10（text10）。 */
    String COLUMN_TEXT_10 = "text10";

    /** 列表扩展字段01（list01）。 */
    String COLUMN_LIST_01 = "list01";

    /** 列表扩展字段02（list02）。 */
    String COLUMN_LIST_02 = "list02";

    /** 列表扩展字段03（list03）。 */
    String COLUMN_LIST_03 = "list03";

    /** 列表扩展字段04（list04）。 */
    String COLUMN_LIST_04 = "list04";

    /** 列表扩展字段05（list05）。 */
    String COLUMN_LIST_05 = "list05";

    /** 列表扩展字段06（list06）。 */
    String COLUMN_LIST_06 = "list06";

    /** 列表扩展字段07（list07）。 */
    String COLUMN_LIST_07 = "list07";

    /** 列表扩展字段08（list08）。 */
    String COLUMN_LIST_08 = "list08";

    /** 列表扩展字段09（list09）。 */
    String COLUMN_LIST_09 = "list09";

    /** 列表扩展字段10（list10）。 */
    String COLUMN_LIST_10 = "list10";

    /** 多选列表扩展字段01（multilist01）。 */
    String COLUMN_MULTILIST_01 = "multilist01";

    /** 多选列表扩展字段02（multilist02）。 */
    String COLUMN_MULTILIST_02 = "multilist02";

    /** 多选列表扩展字段03（multilist03）。 */
    String COLUMN_MULTILIST_03 = "multilist03";

    /** 多选列表扩展字段04（multilist04）。 */
    String COLUMN_MULTILIST_04 = "multilist04";

    /** 多选列表扩展字段05（multilist05）。 */
    String COLUMN_MULTILIST_05 = "multilist05";

    /** 多选列表扩展字段06（multilist06）。 */
    String COLUMN_MULTILIST_06 = "multilist06";

    /** 多选列表扩展字段07（multilist07）。 */
    String COLUMN_MULTILIST_07 = "multilist07";

    /** 多选列表扩展字段08（multilist08）。 */
    String COLUMN_MULTILIST_08 = "multilist08";

    /** 多选列表扩展字段09（multilist09）。 */
    String COLUMN_MULTILIST_09 = "multilist09";

    /** 多选列表扩展字段10（multilist10）。 */
    String COLUMN_MULTILIST_10 = "multilist10";

    /** 数值扩展字段01（numeric01）。 */
    String COLUMN_NUMERIC_01 = "numeric01";

    /** 数值扩展字段02（numeric02）。 */
    String COLUMN_NUMERIC_02 = "numeric02";

    /** 数值扩展字段03（numeric03）。 */
    String COLUMN_NUMERIC_03 = "numeric03";

    /** 数值扩展字段04（numeric04）。 */
    String COLUMN_NUMERIC_04 = "numeric04";

    /** 数值扩展字段05（numeric05）。 */
    String COLUMN_NUMERIC_05 = "numeric05";

    /** 数值扩展字段06（numeric06）。 */
    String COLUMN_NUMERIC_06 = "numeric06";

    /** 数值扩展字段07（numeric07）。 */
    String COLUMN_NUMERIC_07 = "numeric07";

    /** 数值扩展字段08（numeric08）。 */
    String COLUMN_NUMERIC_08 = "numeric08";

    /** 数值扩展字段09（numeric09）。 */
    String COLUMN_NUMERIC_09 = "numeric09";

    /** 数值扩展字段10（numeric10）。 */
    String COLUMN_NUMERIC_10 = "numeric10";

    /** 大文本扩展字段01（largetext01）。 */
    String COLUMN_LARGETEXT_01 = "largetext01";

    /** 大文本扩展字段02（largetext02）。 */
    String COLUMN_LARGETEXT_02 = "largetext02";

    /** 大文本扩展字段03（largetext03）。 */
    String COLUMN_LARGETEXT_03 = "largetext03";

    /** 大文本扩展字段04（largetext04）。 */
    String COLUMN_LARGETEXT_04 = "largetext04";

    /** 大文本扩展字段05（largetext05）。 */
    String COLUMN_LARGETEXT_05 = "largetext05";

    /** 复合文本扩展字段01（compositetext01）。 */
    String COLUMN_COMPOSITETEXT_01 = "compositetext01";

    /** 复合文本扩展字段02（compositetext02）。 */
    String COLUMN_COMPOSITETEXT_02 = "compositetext02";

    /** 复合文本扩展字段03（compositetext03）。 */
    String COLUMN_COMPOSITETEXT_03 = "compositetext03";

    /** 复合文本扩展字段04（compositetext04）。 */
    String COLUMN_COMPOSITETEXT_04 = "compositetext04";

    /** 复合文本扩展字段05（compositetext05）。 */
    String COLUMN_COMPOSITETEXT_05 = "compositetext05";

    /** 复合数值扩展字段01（compositenumber01）。 */
    String COLUMN_COMPOSITENUMBER_01 = "compositenumber01";

    /** 复合数值扩展字段02（compositenumber02）。 */
    String COLUMN_COMPOSITENUMBER_02 = "compositenumber02";

    /** 复合数值扩展字段03（compositenumber03）。 */
    String COLUMN_COMPOSITENUMBER_03 = "compositenumber03";

    /** 复合数值扩展字段04（compositenumber04）。 */
    String COLUMN_COMPOSITENUMBER_04 = "compositenumber04";

    /** 复合数值扩展字段05（compositenumber05）。 */
    String COLUMN_COMPOSITENUMBER_05 = "compositenumber05";

    /** 起始日期扩展字段01（startdate01）。 */
    String COLUMN_STARTDATE_01 = "startdate01";

    /** 起始日期扩展字段02（startdate02）。 */
    String COLUMN_STARTDATE_02 = "startdate02";

    /** 起始日期扩展字段03（startdate03）。 */
    String COLUMN_STARTDATE_03 = "startdate03";

    /** 起始日期扩展字段04（startdate04）。 */
    String COLUMN_STARTDATE_04 = "startdate04";

    /** 起始日期扩展字段05（startdate05）。 */
    String COLUMN_STARTDATE_05 = "startdate05";

    /** 结束日期扩展字段01（endstartdate01）。 */
    String COLUMN_ENDSTARTDATE_01 = "endstartdate01";

    /** 结束日期扩展字段02（endstartdate02）。 */
    String COLUMN_ENDSTARTDATE_02 = "endstartdate02";

    /** 结束日期扩展字段03（endstartdate03）。 */
    String COLUMN_ENDSTARTDATE_03 = "endstartdate03";

    /** 结束日期扩展字段04（endstartdate04）。 */
    String COLUMN_ENDSTARTDATE_04 = "endstartdate04";

    /** 结束日期扩展字段05（endstartdate05）。 */
    String COLUMN_ENDSTARTDATE_05 = "endstartdate05";

    /** 富文本扩展字段01（richtext01）。 */
    String COLUMN_RICHTEXT_01 = "richtext01";

    /** 富文本扩展字段02（richtext02）。 */
    String COLUMN_RICHTEXT_02 = "richtext02";

    /** 富文本扩展字段03（richtext03）。 */
    String COLUMN_RICHTEXT_03 = "richtext03";

    /** 富文本扩展字段04（richtext04）。 */
    String COLUMN_RICHTEXT_04 = "richtext04";

    /** 富文本扩展字段05（richtext05）。 */
    String COLUMN_RICHTEXT_05 = "richtext05";

    /** 读日期扩展字段01（date01）。 */
    default Long getDate01() {
        return getLongValue(COLUMN_DATE_01);
    }

    /** 写日期扩展字段01（date01）；守卫＝版次签出态。 */
    default void setDate01(Long date01) {
        setFieldValue(COLUMN_DATE_01, date01);
    }

    /** 读日期扩展字段02（date02）。 */
    default Long getDate02() {
        return getLongValue(COLUMN_DATE_02);
    }

    /** 写日期扩展字段02（date02）；守卫＝版次签出态。 */
    default void setDate02(Long date02) {
        setFieldValue(COLUMN_DATE_02, date02);
    }

    /** 读日期扩展字段03（date03）。 */
    default Long getDate03() {
        return getLongValue(COLUMN_DATE_03);
    }

    /** 写日期扩展字段03（date03）；守卫＝版次签出态。 */
    default void setDate03(Long date03) {
        setFieldValue(COLUMN_DATE_03, date03);
    }

    /** 读日期扩展字段04（date04）。 */
    default Long getDate04() {
        return getLongValue(COLUMN_DATE_04);
    }

    /** 写日期扩展字段04（date04）；守卫＝版次签出态。 */
    default void setDate04(Long date04) {
        setFieldValue(COLUMN_DATE_04, date04);
    }

    /** 读日期扩展字段05（date05）。 */
    default Long getDate05() {
        return getLongValue(COLUMN_DATE_05);
    }

    /** 写日期扩展字段05（date05）；守卫＝版次签出态。 */
    default void setDate05(Long date05) {
        setFieldValue(COLUMN_DATE_05, date05);
    }

    /** 读文本扩展字段01（text01）。 */
    default String getText01() {
        return getStringValue(COLUMN_TEXT_01);
    }

    /** 写文本扩展字段01（text01）；守卫＝版次签出态。 */
    default void setText01(String text01) {
        setFieldValue(COLUMN_TEXT_01, text01);
    }

    /** 读文本扩展字段02（text02）。 */
    default String getText02() {
        return getStringValue(COLUMN_TEXT_02);
    }

    /** 写文本扩展字段02（text02）；守卫＝版次签出态。 */
    default void setText02(String text02) {
        setFieldValue(COLUMN_TEXT_02, text02);
    }

    /** 读文本扩展字段03（text03）。 */
    default String getText03() {
        return getStringValue(COLUMN_TEXT_03);
    }

    /** 写文本扩展字段03（text03）；守卫＝版次签出态。 */
    default void setText03(String text03) {
        setFieldValue(COLUMN_TEXT_03, text03);
    }

    /** 读文本扩展字段04（text04）。 */
    default String getText04() {
        return getStringValue(COLUMN_TEXT_04);
    }

    /** 写文本扩展字段04（text04）；守卫＝版次签出态。 */
    default void setText04(String text04) {
        setFieldValue(COLUMN_TEXT_04, text04);
    }

    /** 读文本扩展字段05（text05）。 */
    default String getText05() {
        return getStringValue(COLUMN_TEXT_05);
    }

    /** 写文本扩展字段05（text05）；守卫＝版次签出态。 */
    default void setText05(String text05) {
        setFieldValue(COLUMN_TEXT_05, text05);
    }

    /** 读文本扩展字段06（text06）。 */
    default String getText06() {
        return getStringValue(COLUMN_TEXT_06);
    }

    /** 写文本扩展字段06（text06）；守卫＝版次签出态。 */
    default void setText06(String text06) {
        setFieldValue(COLUMN_TEXT_06, text06);
    }

    /** 读文本扩展字段07（text07）。 */
    default String getText07() {
        return getStringValue(COLUMN_TEXT_07);
    }

    /** 写文本扩展字段07（text07）；守卫＝版次签出态。 */
    default void setText07(String text07) {
        setFieldValue(COLUMN_TEXT_07, text07);
    }

    /** 读文本扩展字段08（text08）。 */
    default String getText08() {
        return getStringValue(COLUMN_TEXT_08);
    }

    /** 写文本扩展字段08（text08）；守卫＝版次签出态。 */
    default void setText08(String text08) {
        setFieldValue(COLUMN_TEXT_08, text08);
    }

    /** 读文本扩展字段09（text09）。 */
    default String getText09() {
        return getStringValue(COLUMN_TEXT_09);
    }

    /** 写文本扩展字段09（text09）；守卫＝版次签出态。 */
    default void setText09(String text09) {
        setFieldValue(COLUMN_TEXT_09, text09);
    }

    /** 读文本扩展字段10（text10）。 */
    default String getText10() {
        return getStringValue(COLUMN_TEXT_10);
    }

    /** 写文本扩展字段10（text10）；守卫＝版次签出态。 */
    default void setText10(String text10) {
        setFieldValue(COLUMN_TEXT_10, text10);
    }

    /** 读列表扩展字段01（list01）。 */
    default Long getList01() {
        return getLongValue(COLUMN_LIST_01);
    }

    /** 写列表扩展字段01（list01）；守卫＝版次签出态。 */
    default void setList01(Long list01) {
        setFieldValue(COLUMN_LIST_01, list01);
    }

    /** 读列表扩展字段02（list02）。 */
    default Long getList02() {
        return getLongValue(COLUMN_LIST_02);
    }

    /** 写列表扩展字段02（list02）；守卫＝版次签出态。 */
    default void setList02(Long list02) {
        setFieldValue(COLUMN_LIST_02, list02);
    }

    /** 读列表扩展字段03（list03）。 */
    default Long getList03() {
        return getLongValue(COLUMN_LIST_03);
    }

    /** 写列表扩展字段03（list03）；守卫＝版次签出态。 */
    default void setList03(Long list03) {
        setFieldValue(COLUMN_LIST_03, list03);
    }

    /** 读列表扩展字段04（list04）。 */
    default Long getList04() {
        return getLongValue(COLUMN_LIST_04);
    }

    /** 写列表扩展字段04（list04）；守卫＝版次签出态。 */
    default void setList04(Long list04) {
        setFieldValue(COLUMN_LIST_04, list04);
    }

    /** 读列表扩展字段05（list05）。 */
    default Long getList05() {
        return getLongValue(COLUMN_LIST_05);
    }

    /** 写列表扩展字段05（list05）；守卫＝版次签出态。 */
    default void setList05(Long list05) {
        setFieldValue(COLUMN_LIST_05, list05);
    }

    /** 读列表扩展字段06（list06）。 */
    default Long getList06() {
        return getLongValue(COLUMN_LIST_06);
    }

    /** 写列表扩展字段06（list06）；守卫＝版次签出态。 */
    default void setList06(Long list06) {
        setFieldValue(COLUMN_LIST_06, list06);
    }

    /** 读列表扩展字段07（list07）。 */
    default Long getList07() {
        return getLongValue(COLUMN_LIST_07);
    }

    /** 写列表扩展字段07（list07）；守卫＝版次签出态。 */
    default void setList07(Long list07) {
        setFieldValue(COLUMN_LIST_07, list07);
    }

    /** 读列表扩展字段08（list08）。 */
    default Long getList08() {
        return getLongValue(COLUMN_LIST_08);
    }

    /** 写列表扩展字段08（list08）；守卫＝版次签出态。 */
    default void setList08(Long list08) {
        setFieldValue(COLUMN_LIST_08, list08);
    }

    /** 读列表扩展字段09（list09）。 */
    default Long getList09() {
        return getLongValue(COLUMN_LIST_09);
    }

    /** 写列表扩展字段09（list09）；守卫＝版次签出态。 */
    default void setList09(Long list09) {
        setFieldValue(COLUMN_LIST_09, list09);
    }

    /** 读列表扩展字段10（list10）。 */
    default Long getList10() {
        return getLongValue(COLUMN_LIST_10);
    }

    /** 写列表扩展字段10（list10）；守卫＝版次签出态。 */
    default void setList10(Long list10) {
        setFieldValue(COLUMN_LIST_10, list10);
    }

    /** 读多选列表扩展字段01（multilist01）。 */
    default String getMultilist01() {
        return getStringValue(COLUMN_MULTILIST_01);
    }

    /** 写多选列表扩展字段01（multilist01）；守卫＝版次签出态。 */
    default void setMultilist01(String multilist01) {
        setFieldValue(COLUMN_MULTILIST_01, multilist01);
    }

    /** 读多选列表扩展字段02（multilist02）。 */
    default String getMultilist02() {
        return getStringValue(COLUMN_MULTILIST_02);
    }

    /** 写多选列表扩展字段02（multilist02）；守卫＝版次签出态。 */
    default void setMultilist02(String multilist02) {
        setFieldValue(COLUMN_MULTILIST_02, multilist02);
    }

    /** 读多选列表扩展字段03（multilist03）。 */
    default String getMultilist03() {
        return getStringValue(COLUMN_MULTILIST_03);
    }

    /** 写多选列表扩展字段03（multilist03）；守卫＝版次签出态。 */
    default void setMultilist03(String multilist03) {
        setFieldValue(COLUMN_MULTILIST_03, multilist03);
    }

    /** 读多选列表扩展字段04（multilist04）。 */
    default String getMultilist04() {
        return getStringValue(COLUMN_MULTILIST_04);
    }

    /** 写多选列表扩展字段04（multilist04）；守卫＝版次签出态。 */
    default void setMultilist04(String multilist04) {
        setFieldValue(COLUMN_MULTILIST_04, multilist04);
    }

    /** 读多选列表扩展字段05（multilist05）。 */
    default String getMultilist05() {
        return getStringValue(COLUMN_MULTILIST_05);
    }

    /** 写多选列表扩展字段05（multilist05）；守卫＝版次签出态。 */
    default void setMultilist05(String multilist05) {
        setFieldValue(COLUMN_MULTILIST_05, multilist05);
    }

    /** 读多选列表扩展字段06（multilist06）。 */
    default String getMultilist06() {
        return getStringValue(COLUMN_MULTILIST_06);
    }

    /** 写多选列表扩展字段06（multilist06）；守卫＝版次签出态。 */
    default void setMultilist06(String multilist06) {
        setFieldValue(COLUMN_MULTILIST_06, multilist06);
    }

    /** 读多选列表扩展字段07（multilist07）。 */
    default String getMultilist07() {
        return getStringValue(COLUMN_MULTILIST_07);
    }

    /** 写多选列表扩展字段07（multilist07）；守卫＝版次签出态。 */
    default void setMultilist07(String multilist07) {
        setFieldValue(COLUMN_MULTILIST_07, multilist07);
    }

    /** 读多选列表扩展字段08（multilist08）。 */
    default String getMultilist08() {
        return getStringValue(COLUMN_MULTILIST_08);
    }

    /** 写多选列表扩展字段08（multilist08）；守卫＝版次签出态。 */
    default void setMultilist08(String multilist08) {
        setFieldValue(COLUMN_MULTILIST_08, multilist08);
    }

    /** 读多选列表扩展字段09（multilist09）。 */
    default String getMultilist09() {
        return getStringValue(COLUMN_MULTILIST_09);
    }

    /** 写多选列表扩展字段09（multilist09）；守卫＝版次签出态。 */
    default void setMultilist09(String multilist09) {
        setFieldValue(COLUMN_MULTILIST_09, multilist09);
    }

    /** 读多选列表扩展字段10（multilist10）。 */
    default String getMultilist10() {
        return getStringValue(COLUMN_MULTILIST_10);
    }

    /** 写多选列表扩展字段10（multilist10）；守卫＝版次签出态。 */
    default void setMultilist10(String multilist10) {
        setFieldValue(COLUMN_MULTILIST_10, multilist10);
    }

    /** 读数值扩展字段01（numeric01）。 */
    default BigDecimal getNumeric01() {
        return getBigDecimalValue(COLUMN_NUMERIC_01);
    }

    /** 写数值扩展字段01（numeric01）；守卫＝版次签出态。 */
    default void setNumeric01(BigDecimal numeric01) {
        setFieldValue(COLUMN_NUMERIC_01, numeric01);
    }

    /** 读数值扩展字段02（numeric02）。 */
    default BigDecimal getNumeric02() {
        return getBigDecimalValue(COLUMN_NUMERIC_02);
    }

    /** 写数值扩展字段02（numeric02）；守卫＝版次签出态。 */
    default void setNumeric02(BigDecimal numeric02) {
        setFieldValue(COLUMN_NUMERIC_02, numeric02);
    }

    /** 读数值扩展字段03（numeric03）。 */
    default BigDecimal getNumeric03() {
        return getBigDecimalValue(COLUMN_NUMERIC_03);
    }

    /** 写数值扩展字段03（numeric03）；守卫＝版次签出态。 */
    default void setNumeric03(BigDecimal numeric03) {
        setFieldValue(COLUMN_NUMERIC_03, numeric03);
    }

    /** 读数值扩展字段04（numeric04）。 */
    default BigDecimal getNumeric04() {
        return getBigDecimalValue(COLUMN_NUMERIC_04);
    }

    /** 写数值扩展字段04（numeric04）；守卫＝版次签出态。 */
    default void setNumeric04(BigDecimal numeric04) {
        setFieldValue(COLUMN_NUMERIC_04, numeric04);
    }

    /** 读数值扩展字段05（numeric05）。 */
    default BigDecimal getNumeric05() {
        return getBigDecimalValue(COLUMN_NUMERIC_05);
    }

    /** 写数值扩展字段05（numeric05）；守卫＝版次签出态。 */
    default void setNumeric05(BigDecimal numeric05) {
        setFieldValue(COLUMN_NUMERIC_05, numeric05);
    }

    /** 读数值扩展字段06（numeric06）。 */
    default BigDecimal getNumeric06() {
        return getBigDecimalValue(COLUMN_NUMERIC_06);
    }

    /** 写数值扩展字段06（numeric06）；守卫＝版次签出态。 */
    default void setNumeric06(BigDecimal numeric06) {
        setFieldValue(COLUMN_NUMERIC_06, numeric06);
    }

    /** 读数值扩展字段07（numeric07）。 */
    default BigDecimal getNumeric07() {
        return getBigDecimalValue(COLUMN_NUMERIC_07);
    }

    /** 写数值扩展字段07（numeric07）；守卫＝版次签出态。 */
    default void setNumeric07(BigDecimal numeric07) {
        setFieldValue(COLUMN_NUMERIC_07, numeric07);
    }

    /** 读数值扩展字段08（numeric08）。 */
    default BigDecimal getNumeric08() {
        return getBigDecimalValue(COLUMN_NUMERIC_08);
    }

    /** 写数值扩展字段08（numeric08）；守卫＝版次签出态。 */
    default void setNumeric08(BigDecimal numeric08) {
        setFieldValue(COLUMN_NUMERIC_08, numeric08);
    }

    /** 读数值扩展字段09（numeric09）。 */
    default BigDecimal getNumeric09() {
        return getBigDecimalValue(COLUMN_NUMERIC_09);
    }

    /** 写数值扩展字段09（numeric09）；守卫＝版次签出态。 */
    default void setNumeric09(BigDecimal numeric09) {
        setFieldValue(COLUMN_NUMERIC_09, numeric09);
    }

    /** 读数值扩展字段10（numeric10）。 */
    default BigDecimal getNumeric10() {
        return getBigDecimalValue(COLUMN_NUMERIC_10);
    }

    /** 写数值扩展字段10（numeric10）；守卫＝版次签出态。 */
    default void setNumeric10(BigDecimal numeric10) {
        setFieldValue(COLUMN_NUMERIC_10, numeric10);
    }

    /** 读大文本扩展字段01（largetext01）。 */
    default String getLargetext01() {
        return getStringValue(COLUMN_LARGETEXT_01);
    }

    /** 写大文本扩展字段01（largetext01）；守卫＝版次签出态。 */
    default void setLargetext01(String largetext01) {
        setFieldValue(COLUMN_LARGETEXT_01, largetext01);
    }

    /** 读大文本扩展字段02（largetext02）。 */
    default String getLargetext02() {
        return getStringValue(COLUMN_LARGETEXT_02);
    }

    /** 写大文本扩展字段02（largetext02）；守卫＝版次签出态。 */
    default void setLargetext02(String largetext02) {
        setFieldValue(COLUMN_LARGETEXT_02, largetext02);
    }

    /** 读大文本扩展字段03（largetext03）。 */
    default String getLargetext03() {
        return getStringValue(COLUMN_LARGETEXT_03);
    }

    /** 写大文本扩展字段03（largetext03）；守卫＝版次签出态。 */
    default void setLargetext03(String largetext03) {
        setFieldValue(COLUMN_LARGETEXT_03, largetext03);
    }

    /** 读大文本扩展字段04（largetext04）。 */
    default String getLargetext04() {
        return getStringValue(COLUMN_LARGETEXT_04);
    }

    /** 写大文本扩展字段04（largetext04）；守卫＝版次签出态。 */
    default void setLargetext04(String largetext04) {
        setFieldValue(COLUMN_LARGETEXT_04, largetext04);
    }

    /** 读大文本扩展字段05（largetext05）。 */
    default String getLargetext05() {
        return getStringValue(COLUMN_LARGETEXT_05);
    }

    /** 写大文本扩展字段05（largetext05）；守卫＝版次签出态。 */
    default void setLargetext05(String largetext05) {
        setFieldValue(COLUMN_LARGETEXT_05, largetext05);
    }

    /** 读复合文本扩展字段01（compositetext01）。 */
    default String getCompositetext01() {
        return getStringValue(COLUMN_COMPOSITETEXT_01);
    }

    /** 写复合文本扩展字段01（compositetext01）；守卫＝版次签出态。 */
    default void setCompositetext01(String compositetext01) {
        setFieldValue(COLUMN_COMPOSITETEXT_01, compositetext01);
    }

    /** 读复合文本扩展字段02（compositetext02）。 */
    default String getCompositetext02() {
        return getStringValue(COLUMN_COMPOSITETEXT_02);
    }

    /** 写复合文本扩展字段02（compositetext02）；守卫＝版次签出态。 */
    default void setCompositetext02(String compositetext02) {
        setFieldValue(COLUMN_COMPOSITETEXT_02, compositetext02);
    }

    /** 读复合文本扩展字段03（compositetext03）。 */
    default String getCompositetext03() {
        return getStringValue(COLUMN_COMPOSITETEXT_03);
    }

    /** 写复合文本扩展字段03（compositetext03）；守卫＝版次签出态。 */
    default void setCompositetext03(String compositetext03) {
        setFieldValue(COLUMN_COMPOSITETEXT_03, compositetext03);
    }

    /** 读复合文本扩展字段04（compositetext04）。 */
    default String getCompositetext04() {
        return getStringValue(COLUMN_COMPOSITETEXT_04);
    }

    /** 写复合文本扩展字段04（compositetext04）；守卫＝版次签出态。 */
    default void setCompositetext04(String compositetext04) {
        setFieldValue(COLUMN_COMPOSITETEXT_04, compositetext04);
    }

    /** 读复合文本扩展字段05（compositetext05）。 */
    default String getCompositetext05() {
        return getStringValue(COLUMN_COMPOSITETEXT_05);
    }

    /** 写复合文本扩展字段05（compositetext05）；守卫＝版次签出态。 */
    default void setCompositetext05(String compositetext05) {
        setFieldValue(COLUMN_COMPOSITETEXT_05, compositetext05);
    }

    /** 读复合数值扩展字段01（compositenumber01）。 */
    default String getCompositenumber01() {
        return getStringValue(COLUMN_COMPOSITENUMBER_01);
    }

    /** 写复合数值扩展字段01（compositenumber01）；守卫＝版次签出态。 */
    default void setCompositenumber01(String compositenumber01) {
        setFieldValue(COLUMN_COMPOSITENUMBER_01, compositenumber01);
    }

    /** 读复合数值扩展字段02（compositenumber02）。 */
    default String getCompositenumber02() {
        return getStringValue(COLUMN_COMPOSITENUMBER_02);
    }

    /** 写复合数值扩展字段02（compositenumber02）；守卫＝版次签出态。 */
    default void setCompositenumber02(String compositenumber02) {
        setFieldValue(COLUMN_COMPOSITENUMBER_02, compositenumber02);
    }

    /** 读复合数值扩展字段03（compositenumber03）。 */
    default String getCompositenumber03() {
        return getStringValue(COLUMN_COMPOSITENUMBER_03);
    }

    /** 写复合数值扩展字段03（compositenumber03）；守卫＝版次签出态。 */
    default void setCompositenumber03(String compositenumber03) {
        setFieldValue(COLUMN_COMPOSITENUMBER_03, compositenumber03);
    }

    /** 读复合数值扩展字段04（compositenumber04）。 */
    default String getCompositenumber04() {
        return getStringValue(COLUMN_COMPOSITENUMBER_04);
    }

    /** 写复合数值扩展字段04（compositenumber04）；守卫＝版次签出态。 */
    default void setCompositenumber04(String compositenumber04) {
        setFieldValue(COLUMN_COMPOSITENUMBER_04, compositenumber04);
    }

    /** 读复合数值扩展字段05（compositenumber05）。 */
    default String getCompositenumber05() {
        return getStringValue(COLUMN_COMPOSITENUMBER_05);
    }

    /** 写复合数值扩展字段05（compositenumber05）；守卫＝版次签出态。 */
    default void setCompositenumber05(String compositenumber05) {
        setFieldValue(COLUMN_COMPOSITENUMBER_05, compositenumber05);
    }

    /** 读起始日期扩展字段01（startdate01）。 */
    default Long getStartdate01() {
        return getLongValue(COLUMN_STARTDATE_01);
    }

    /** 写起始日期扩展字段01（startdate01）；守卫＝版次签出态。 */
    default void setStartdate01(Long startdate01) {
        setFieldValue(COLUMN_STARTDATE_01, startdate01);
    }

    /** 读起始日期扩展字段02（startdate02）。 */
    default Long getStartdate02() {
        return getLongValue(COLUMN_STARTDATE_02);
    }

    /** 写起始日期扩展字段02（startdate02）；守卫＝版次签出态。 */
    default void setStartdate02(Long startdate02) {
        setFieldValue(COLUMN_STARTDATE_02, startdate02);
    }

    /** 读起始日期扩展字段03（startdate03）。 */
    default Long getStartdate03() {
        return getLongValue(COLUMN_STARTDATE_03);
    }

    /** 写起始日期扩展字段03（startdate03）；守卫＝版次签出态。 */
    default void setStartdate03(Long startdate03) {
        setFieldValue(COLUMN_STARTDATE_03, startdate03);
    }

    /** 读起始日期扩展字段04（startdate04）。 */
    default Long getStartdate04() {
        return getLongValue(COLUMN_STARTDATE_04);
    }

    /** 写起始日期扩展字段04（startdate04）；守卫＝版次签出态。 */
    default void setStartdate04(Long startdate04) {
        setFieldValue(COLUMN_STARTDATE_04, startdate04);
    }

    /** 读起始日期扩展字段05（startdate05）。 */
    default Long getStartdate05() {
        return getLongValue(COLUMN_STARTDATE_05);
    }

    /** 写起始日期扩展字段05（startdate05）；守卫＝版次签出态。 */
    default void setStartdate05(Long startdate05) {
        setFieldValue(COLUMN_STARTDATE_05, startdate05);
    }

    /** 读结束日期扩展字段01（endstartdate01）。 */
    default Long getEndstartdate01() {
        return getLongValue(COLUMN_ENDSTARTDATE_01);
    }

    /** 写结束日期扩展字段01（endstartdate01）；守卫＝版次签出态。 */
    default void setEndstartdate01(Long endstartdate01) {
        setFieldValue(COLUMN_ENDSTARTDATE_01, endstartdate01);
    }

    /** 读结束日期扩展字段02（endstartdate02）。 */
    default Long getEndstartdate02() {
        return getLongValue(COLUMN_ENDSTARTDATE_02);
    }

    /** 写结束日期扩展字段02（endstartdate02）；守卫＝版次签出态。 */
    default void setEndstartdate02(Long endstartdate02) {
        setFieldValue(COLUMN_ENDSTARTDATE_02, endstartdate02);
    }

    /** 读结束日期扩展字段03（endstartdate03）。 */
    default Long getEndstartdate03() {
        return getLongValue(COLUMN_ENDSTARTDATE_03);
    }

    /** 写结束日期扩展字段03（endstartdate03）；守卫＝版次签出态。 */
    default void setEndstartdate03(Long endstartdate03) {
        setFieldValue(COLUMN_ENDSTARTDATE_03, endstartdate03);
    }

    /** 读结束日期扩展字段04（endstartdate04）。 */
    default Long getEndstartdate04() {
        return getLongValue(COLUMN_ENDSTARTDATE_04);
    }

    /** 写结束日期扩展字段04（endstartdate04）；守卫＝版次签出态。 */
    default void setEndstartdate04(Long endstartdate04) {
        setFieldValue(COLUMN_ENDSTARTDATE_04, endstartdate04);
    }

    /** 读结束日期扩展字段05（endstartdate05）。 */
    default Long getEndstartdate05() {
        return getLongValue(COLUMN_ENDSTARTDATE_05);
    }

    /** 写结束日期扩展字段05（endstartdate05）；守卫＝版次签出态。 */
    default void setEndstartdate05(Long endstartdate05) {
        setFieldValue(COLUMN_ENDSTARTDATE_05, endstartdate05);
    }

    /** 读富文本扩展字段01（richtext01）。 */
    default String getRichtext01() {
        return getStringValue(COLUMN_RICHTEXT_01);
    }

    /** 写富文本扩展字段01（richtext01）；守卫＝版次签出态。 */
    default void setRichtext01(String richtext01) {
        setFieldValue(COLUMN_RICHTEXT_01, richtext01);
    }

    /** 读富文本扩展字段02（richtext02）。 */
    default String getRichtext02() {
        return getStringValue(COLUMN_RICHTEXT_02);
    }

    /** 写富文本扩展字段02（richtext02）；守卫＝版次签出态。 */
    default void setRichtext02(String richtext02) {
        setFieldValue(COLUMN_RICHTEXT_02, richtext02);
    }

    /** 读富文本扩展字段03（richtext03）。 */
    default String getRichtext03() {
        return getStringValue(COLUMN_RICHTEXT_03);
    }

    /** 写富文本扩展字段03（richtext03）；守卫＝版次签出态。 */
    default void setRichtext03(String richtext03) {
        setFieldValue(COLUMN_RICHTEXT_03, richtext03);
    }

    /** 读富文本扩展字段04（richtext04）。 */
    default String getRichtext04() {
        return getStringValue(COLUMN_RICHTEXT_04);
    }

    /** 写富文本扩展字段04（richtext04）；守卫＝版次签出态。 */
    default void setRichtext04(String richtext04) {
        setFieldValue(COLUMN_RICHTEXT_04, richtext04);
    }

    /** 读富文本扩展字段05（richtext05）。 */
    default String getRichtext05() {
        return getStringValue(COLUMN_RICHTEXT_05);
    }

    /** 写富文本扩展字段05（richtext05）；守卫＝版次签出态。 */
    default void setRichtext05(String richtext05) {
        setFieldValue(COLUMN_RICHTEXT_05, richtext05);
    }
}

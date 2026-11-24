package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

import java.math.BigDecimal;

/**
 * 接口：需求版次的<b>子类级可选字段</b>（旧栈动态分类现表预分配扩展列·001 段）。
 *
 * <p>
 * 与 {@link OptionalClassFields}（01 段＝类级）成对：001 段（{@code date001…richtext005}）承载<b>子类（SubClass）级</b>可选字段（75 列），
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
public interface OptionalSubClassFields extends BusinessDataCarrier {

    /** 日期可选字段001（date001）。 */
    String COLUMN_DATE_001 = "date001";

    /** 日期扩展字段002（date002）。 */
    String COLUMN_DATE_002 = "date002";

    /** 日期扩展字段003（date003）。 */
    String COLUMN_DATE_003 = "date003";

    /** 日期扩展字段004（date004）。 */
    String COLUMN_DATE_004 = "date004";

    /** 日期扩展字段005（date005）。 */
    String COLUMN_DATE_005 = "date005";

    /** 文本扩展字段001（text001）。 */
    String COLUMN_TEXT_001 = "text001";

    /** 文本扩展字段002（text002）。 */
    String COLUMN_TEXT_002 = "text002";

    /** 文本扩展字段003（text003）。 */
    String COLUMN_TEXT_003 = "text003";

    /** 文本扩展字段004（text004）。 */
    String COLUMN_TEXT_004 = "text004";

    /** 文本扩展字段005（text005）。 */
    String COLUMN_TEXT_005 = "text005";

    /** 文本扩展字段006（text006）。 */
    String COLUMN_TEXT_006 = "text006";

    /** 文本扩展字段007（text007）。 */
    String COLUMN_TEXT_007 = "text007";

    /** 文本扩展字段008（text008）。 */
    String COLUMN_TEXT_008 = "text008";

    /** 文本扩展字段009（text009）。 */
    String COLUMN_TEXT_009 = "text009";

    /** 文本扩展字段010（text010）。 */
    String COLUMN_TEXT_010 = "text010";

    /** 列表扩展字段001（list001）。 */
    String COLUMN_LIST_001 = "list001";

    /** 列表扩展字段002（list002）。 */
    String COLUMN_LIST_002 = "list002";

    /** 列表扩展字段003（list003）。 */
    String COLUMN_LIST_003 = "list003";

    /** 列表扩展字段004（list004）。 */
    String COLUMN_LIST_004 = "list004";

    /** 列表扩展字段005（list005）。 */
    String COLUMN_LIST_005 = "list005";

    /** 列表扩展字段006（list006）。 */
    String COLUMN_LIST_006 = "list006";

    /** 列表扩展字段007（list007）。 */
    String COLUMN_LIST_007 = "list007";

    /** 列表扩展字段008（list008）。 */
    String COLUMN_LIST_008 = "list008";

    /** 列表扩展字段009（list009）。 */
    String COLUMN_LIST_009 = "list009";

    /** 列表扩展字段010（list010）。 */
    String COLUMN_LIST_010 = "list010";

    /** 多选列表扩展字段001（multilist001）。 */
    String COLUMN_MULTILIST_001 = "multilist001";

    /** 多选列表扩展字段002（multilist002）。 */
    String COLUMN_MULTILIST_002 = "multilist002";

    /** 多选列表扩展字段003（multilist003）。 */
    String COLUMN_MULTILIST_003 = "multilist003";

    /** 多选列表扩展字段004（multilist004）。 */
    String COLUMN_MULTILIST_004 = "multilist004";

    /** 多选列表扩展字段005（multilist005）。 */
    String COLUMN_MULTILIST_005 = "multilist005";

    /** 多选列表扩展字段006（multilist006）。 */
    String COLUMN_MULTILIST_006 = "multilist006";

    /** 多选列表扩展字段007（multilist007）。 */
    String COLUMN_MULTILIST_007 = "multilist007";

    /** 多选列表扩展字段008（multilist008）。 */
    String COLUMN_MULTILIST_008 = "multilist008";

    /** 多选列表扩展字段009（multilist009）。 */
    String COLUMN_MULTILIST_009 = "multilist009";

    /** 多选列表扩展字段010（multilist010）。 */
    String COLUMN_MULTILIST_010 = "multilist010";

    /** 数值扩展字段001（numeric001）。 */
    String COLUMN_NUMERIC_001 = "numeric001";

    /** 数值扩展字段002（numeric002）。 */
    String COLUMN_NUMERIC_002 = "numeric002";

    /** 数值扩展字段003（numeric003）。 */
    String COLUMN_NUMERIC_003 = "numeric003";

    /** 数值扩展字段004（numeric004）。 */
    String COLUMN_NUMERIC_004 = "numeric004";

    /** 数值扩展字段005（numeric005）。 */
    String COLUMN_NUMERIC_005 = "numeric005";

    /** 数值扩展字段006（numeric006）。 */
    String COLUMN_NUMERIC_006 = "numeric006";

    /** 数值扩展字段007（numeric007）。 */
    String COLUMN_NUMERIC_007 = "numeric007";

    /** 数值扩展字段008（numeric008）。 */
    String COLUMN_NUMERIC_008 = "numeric008";

    /** 数值扩展字段009（numeric009）。 */
    String COLUMN_NUMERIC_009 = "numeric009";

    /** 数值扩展字段010（numeric010）。 */
    String COLUMN_NUMERIC_010 = "numeric010";

    /** 大文本扩展字段001（largetext001）。 */
    String COLUMN_LARGETEXT_001 = "largetext001";

    /** 大文本扩展字段002（largetext002）。 */
    String COLUMN_LARGETEXT_002 = "largetext002";

    /** 大文本扩展字段003（largetext003）。 */
    String COLUMN_LARGETEXT_003 = "largetext003";

    /** 大文本扩展字段004（largetext004）。 */
    String COLUMN_LARGETEXT_004 = "largetext004";

    /** 大文本扩展字段005（largetext005）。 */
    String COLUMN_LARGETEXT_005 = "largetext005";

    /** 复合文本扩展字段001（compositetext001）。 */
    String COLUMN_COMPOSITETEXT_001 = "compositetext001";

    /** 复合文本扩展字段002（compositetext002）。 */
    String COLUMN_COMPOSITETEXT_002 = "compositetext002";

    /** 复合文本扩展字段003（compositetext003）。 */
    String COLUMN_COMPOSITETEXT_003 = "compositetext003";

    /** 复合文本扩展字段004（compositetext004）。 */
    String COLUMN_COMPOSITETEXT_004 = "compositetext004";

    /** 复合文本扩展字段005（compositetext005）。 */
    String COLUMN_COMPOSITETEXT_005 = "compositetext005";

    /** 复合数值扩展字段001（compositenumber001）。 */
    String COLUMN_COMPOSITENUMBER_001 = "compositenumber001";

    /** 复合数值扩展字段002（compositenumber002）。 */
    String COLUMN_COMPOSITENUMBER_002 = "compositenumber002";

    /** 复合数值扩展字段003（compositenumber003）。 */
    String COLUMN_COMPOSITENUMBER_003 = "compositenumber003";

    /** 复合数值扩展字段004（compositenumber004）。 */
    String COLUMN_COMPOSITENUMBER_004 = "compositenumber004";

    /** 复合数值扩展字段005（compositenumber005）。 */
    String COLUMN_COMPOSITENUMBER_005 = "compositenumber005";

    /** 起始日期扩展字段001（startdate001）。 */
    String COLUMN_STARTDATE_001 = "startdate001";

    /** 起始日期扩展字段002（startdate002）。 */
    String COLUMN_STARTDATE_002 = "startdate002";

    /** 起始日期扩展字段003（startdate003）。 */
    String COLUMN_STARTDATE_003 = "startdate003";

    /** 起始日期扩展字段004（startdate004）。 */
    String COLUMN_STARTDATE_004 = "startdate004";

    /** 起始日期扩展字段005（startdate005）。 */
    String COLUMN_STARTDATE_005 = "startdate005";

    /** 结束日期扩展字段001（endstartdate001）。 */
    String COLUMN_ENDSTARTDATE_001 = "endstartdate001";

    /** 结束日期扩展字段002（endstartdate002）。 */
    String COLUMN_ENDSTARTDATE_002 = "endstartdate002";

    /** 结束日期扩展字段003（endstartdate003）。 */
    String COLUMN_ENDSTARTDATE_003 = "endstartdate003";

    /** 结束日期扩展字段004（endstartdate004）。 */
    String COLUMN_ENDSTARTDATE_004 = "endstartdate004";

    /** 结束日期扩展字段005（endstartdate005）。 */
    String COLUMN_ENDSTARTDATE_005 = "endstartdate005";

    /** 富文本扩展字段001（richtext001）。 */
    String COLUMN_RICHTEXT_001 = "richtext001";

    /** 富文本扩展字段002（richtext002）。 */
    String COLUMN_RICHTEXT_002 = "richtext002";

    /** 富文本扩展字段003（richtext003）。 */
    String COLUMN_RICHTEXT_003 = "richtext003";

    /** 富文本扩展字段004（richtext004）。 */
    String COLUMN_RICHTEXT_004 = "richtext004";

    /** 富文本扩展字段005（richtext005）。 */
    String COLUMN_RICHTEXT_005 = "richtext005";

    /** 读日期扩展字段001（date001）。 */
    default Long getDate001() {
        return getLongValue(COLUMN_DATE_001);
    }

    /** 写日期扩展字段001（date001）；守卫＝版次签出态。 */
    default void setDate001(Long date001) {
        setFieldValue(COLUMN_DATE_001, date001);
    }

    /** 读日期扩展字段002（date002）。 */
    default Long getDate002() {
        return getLongValue(COLUMN_DATE_002);
    }

    /** 写日期扩展字段002（date002）；守卫＝版次签出态。 */
    default void setDate002(Long date002) {
        setFieldValue(COLUMN_DATE_002, date002);
    }

    /** 读日期扩展字段003（date003）。 */
    default Long getDate003() {
        return getLongValue(COLUMN_DATE_003);
    }

    /** 写日期扩展字段003（date003）；守卫＝版次签出态。 */
    default void setDate003(Long date003) {
        setFieldValue(COLUMN_DATE_003, date003);
    }

    /** 读日期扩展字段004（date004）。 */
    default Long getDate004() {
        return getLongValue(COLUMN_DATE_004);
    }

    /** 写日期扩展字段004（date004）；守卫＝版次签出态。 */
    default void setDate004(Long date004) {
        setFieldValue(COLUMN_DATE_004, date004);
    }

    /** 读日期扩展字段005（date005）。 */
    default Long getDate005() {
        return getLongValue(COLUMN_DATE_005);
    }

    /** 写日期扩展字段005（date005）；守卫＝版次签出态。 */
    default void setDate005(Long date005) {
        setFieldValue(COLUMN_DATE_005, date005);
    }

    /** 读文本扩展字段001（text001）。 */
    default String getText001() {
        return getStringValue(COLUMN_TEXT_001);
    }

    /** 写文本扩展字段001（text001）；守卫＝版次签出态。 */
    default void setText001(String text001) {
        setFieldValue(COLUMN_TEXT_001, text001);
    }

    /** 读文本扩展字段002（text002）。 */
    default String getText002() {
        return getStringValue(COLUMN_TEXT_002);
    }

    /** 写文本扩展字段002（text002）；守卫＝版次签出态。 */
    default void setText002(String text002) {
        setFieldValue(COLUMN_TEXT_002, text002);
    }

    /** 读文本扩展字段003（text003）。 */
    default String getText003() {
        return getStringValue(COLUMN_TEXT_003);
    }

    /** 写文本扩展字段003（text003）；守卫＝版次签出态。 */
    default void setText003(String text003) {
        setFieldValue(COLUMN_TEXT_003, text003);
    }

    /** 读文本扩展字段004（text004）。 */
    default String getText004() {
        return getStringValue(COLUMN_TEXT_004);
    }

    /** 写文本扩展字段004（text004）；守卫＝版次签出态。 */
    default void setText004(String text004) {
        setFieldValue(COLUMN_TEXT_004, text004);
    }

    /** 读文本扩展字段005（text005）。 */
    default String getText005() {
        return getStringValue(COLUMN_TEXT_005);
    }

    /** 写文本扩展字段005（text005）；守卫＝版次签出态。 */
    default void setText005(String text005) {
        setFieldValue(COLUMN_TEXT_005, text005);
    }

    /** 读文本扩展字段006（text006）。 */
    default String getText006() {
        return getStringValue(COLUMN_TEXT_006);
    }

    /** 写文本扩展字段006（text006）；守卫＝版次签出态。 */
    default void setText006(String text006) {
        setFieldValue(COLUMN_TEXT_006, text006);
    }

    /** 读文本扩展字段007（text007）。 */
    default String getText007() {
        return getStringValue(COLUMN_TEXT_007);
    }

    /** 写文本扩展字段007（text007）；守卫＝版次签出态。 */
    default void setText007(String text007) {
        setFieldValue(COLUMN_TEXT_007, text007);
    }

    /** 读文本扩展字段008（text008）。 */
    default String getText008() {
        return getStringValue(COLUMN_TEXT_008);
    }

    /** 写文本扩展字段008（text008）；守卫＝版次签出态。 */
    default void setText008(String text008) {
        setFieldValue(COLUMN_TEXT_008, text008);
    }

    /** 读文本扩展字段009（text009）。 */
    default String getText009() {
        return getStringValue(COLUMN_TEXT_009);
    }

    /** 写文本扩展字段009（text009）；守卫＝版次签出态。 */
    default void setText009(String text009) {
        setFieldValue(COLUMN_TEXT_009, text009);
    }

    /** 读文本扩展字段010（text010）。 */
    default String getText010() {
        return getStringValue(COLUMN_TEXT_010);
    }

    /** 写文本扩展字段010（text010）；守卫＝版次签出态。 */
    default void setText010(String text010) {
        setFieldValue(COLUMN_TEXT_010, text010);
    }

    /** 读列表扩展字段001（list001）。 */
    default Long getList001() {
        return getLongValue(COLUMN_LIST_001);
    }

    /** 写列表扩展字段001（list001）；守卫＝版次签出态。 */
    default void setList001(Long list001) {
        setFieldValue(COLUMN_LIST_001, list001);
    }

    /** 读列表扩展字段002（list002）。 */
    default Long getList002() {
        return getLongValue(COLUMN_LIST_002);
    }

    /** 写列表扩展字段002（list002）；守卫＝版次签出态。 */
    default void setList002(Long list002) {
        setFieldValue(COLUMN_LIST_002, list002);
    }

    /** 读列表扩展字段003（list003）。 */
    default Long getList003() {
        return getLongValue(COLUMN_LIST_003);
    }

    /** 写列表扩展字段003（list003）；守卫＝版次签出态。 */
    default void setList003(Long list003) {
        setFieldValue(COLUMN_LIST_003, list003);
    }

    /** 读列表扩展字段004（list004）。 */
    default Long getList004() {
        return getLongValue(COLUMN_LIST_004);
    }

    /** 写列表扩展字段004（list004）；守卫＝版次签出态。 */
    default void setList004(Long list004) {
        setFieldValue(COLUMN_LIST_004, list004);
    }

    /** 读列表扩展字段005（list005）。 */
    default Long getList005() {
        return getLongValue(COLUMN_LIST_005);
    }

    /** 写列表扩展字段005（list005）；守卫＝版次签出态。 */
    default void setList005(Long list005) {
        setFieldValue(COLUMN_LIST_005, list005);
    }

    /** 读列表扩展字段006（list006）。 */
    default Long getList006() {
        return getLongValue(COLUMN_LIST_006);
    }

    /** 写列表扩展字段006（list006）；守卫＝版次签出态。 */
    default void setList006(Long list006) {
        setFieldValue(COLUMN_LIST_006, list006);
    }

    /** 读列表扩展字段007（list007）。 */
    default Long getList007() {
        return getLongValue(COLUMN_LIST_007);
    }

    /** 写列表扩展字段007（list007）；守卫＝版次签出态。 */
    default void setList007(Long list007) {
        setFieldValue(COLUMN_LIST_007, list007);
    }

    /** 读列表扩展字段008（list008）。 */
    default Long getList008() {
        return getLongValue(COLUMN_LIST_008);
    }

    /** 写列表扩展字段008（list008）；守卫＝版次签出态。 */
    default void setList008(Long list008) {
        setFieldValue(COLUMN_LIST_008, list008);
    }

    /** 读列表扩展字段009（list009）。 */
    default Long getList009() {
        return getLongValue(COLUMN_LIST_009);
    }

    /** 写列表扩展字段009（list009）；守卫＝版次签出态。 */
    default void setList009(Long list009) {
        setFieldValue(COLUMN_LIST_009, list009);
    }

    /** 读列表扩展字段010（list010）。 */
    default Long getList010() {
        return getLongValue(COLUMN_LIST_010);
    }

    /** 写列表扩展字段010（list010）；守卫＝版次签出态。 */
    default void setList010(Long list010) {
        setFieldValue(COLUMN_LIST_010, list010);
    }

    /** 读多选列表扩展字段001（multilist001）。 */
    default String getMultilist001() {
        return getStringValue(COLUMN_MULTILIST_001);
    }

    /** 写多选列表扩展字段001（multilist001）；守卫＝版次签出态。 */
    default void setMultilist001(String multilist001) {
        setFieldValue(COLUMN_MULTILIST_001, multilist001);
    }

    /** 读多选列表扩展字段002（multilist002）。 */
    default String getMultilist002() {
        return getStringValue(COLUMN_MULTILIST_002);
    }

    /** 写多选列表扩展字段002（multilist002）；守卫＝版次签出态。 */
    default void setMultilist002(String multilist002) {
        setFieldValue(COLUMN_MULTILIST_002, multilist002);
    }

    /** 读多选列表扩展字段003（multilist003）。 */
    default String getMultilist003() {
        return getStringValue(COLUMN_MULTILIST_003);
    }

    /** 写多选列表扩展字段003（multilist003）；守卫＝版次签出态。 */
    default void setMultilist003(String multilist003) {
        setFieldValue(COLUMN_MULTILIST_003, multilist003);
    }

    /** 读多选列表扩展字段004（multilist004）。 */
    default String getMultilist004() {
        return getStringValue(COLUMN_MULTILIST_004);
    }

    /** 写多选列表扩展字段004（multilist004）；守卫＝版次签出态。 */
    default void setMultilist004(String multilist004) {
        setFieldValue(COLUMN_MULTILIST_004, multilist004);
    }

    /** 读多选列表扩展字段005（multilist005）。 */
    default String getMultilist005() {
        return getStringValue(COLUMN_MULTILIST_005);
    }

    /** 写多选列表扩展字段005（multilist005）；守卫＝版次签出态。 */
    default void setMultilist005(String multilist005) {
        setFieldValue(COLUMN_MULTILIST_005, multilist005);
    }

    /** 读多选列表扩展字段006（multilist006）。 */
    default String getMultilist006() {
        return getStringValue(COLUMN_MULTILIST_006);
    }

    /** 写多选列表扩展字段006（multilist006）；守卫＝版次签出态。 */
    default void setMultilist006(String multilist006) {
        setFieldValue(COLUMN_MULTILIST_006, multilist006);
    }

    /** 读多选列表扩展字段007（multilist007）。 */
    default String getMultilist007() {
        return getStringValue(COLUMN_MULTILIST_007);
    }

    /** 写多选列表扩展字段007（multilist007）；守卫＝版次签出态。 */
    default void setMultilist007(String multilist007) {
        setFieldValue(COLUMN_MULTILIST_007, multilist007);
    }

    /** 读多选列表扩展字段008（multilist008）。 */
    default String getMultilist008() {
        return getStringValue(COLUMN_MULTILIST_008);
    }

    /** 写多选列表扩展字段008（multilist008）；守卫＝版次签出态。 */
    default void setMultilist008(String multilist008) {
        setFieldValue(COLUMN_MULTILIST_008, multilist008);
    }

    /** 读多选列表扩展字段009（multilist009）。 */
    default String getMultilist009() {
        return getStringValue(COLUMN_MULTILIST_009);
    }

    /** 写多选列表扩展字段009（multilist009）；守卫＝版次签出态。 */
    default void setMultilist009(String multilist009) {
        setFieldValue(COLUMN_MULTILIST_009, multilist009);
    }

    /** 读多选列表扩展字段010（multilist010）。 */
    default String getMultilist010() {
        return getStringValue(COLUMN_MULTILIST_010);
    }

    /** 写多选列表扩展字段010（multilist010）；守卫＝版次签出态。 */
    default void setMultilist010(String multilist010) {
        setFieldValue(COLUMN_MULTILIST_010, multilist010);
    }

    /** 读数值扩展字段001（numeric001）。 */
    default BigDecimal getNumeric001() {
        return getBigDecimalValue(COLUMN_NUMERIC_001);
    }

    /** 写数值扩展字段001（numeric001）；守卫＝版次签出态。 */
    default void setNumeric001(BigDecimal numeric001) {
        setFieldValue(COLUMN_NUMERIC_001, numeric001);
    }

    /** 读数值扩展字段002（numeric002）。 */
    default BigDecimal getNumeric002() {
        return getBigDecimalValue(COLUMN_NUMERIC_002);
    }

    /** 写数值扩展字段002（numeric002）；守卫＝版次签出态。 */
    default void setNumeric002(BigDecimal numeric002) {
        setFieldValue(COLUMN_NUMERIC_002, numeric002);
    }

    /** 读数值扩展字段003（numeric003）。 */
    default BigDecimal getNumeric003() {
        return getBigDecimalValue(COLUMN_NUMERIC_003);
    }

    /** 写数值扩展字段003（numeric003）；守卫＝版次签出态。 */
    default void setNumeric003(BigDecimal numeric003) {
        setFieldValue(COLUMN_NUMERIC_003, numeric003);
    }

    /** 读数值扩展字段004（numeric004）。 */
    default BigDecimal getNumeric004() {
        return getBigDecimalValue(COLUMN_NUMERIC_004);
    }

    /** 写数值扩展字段004（numeric004）；守卫＝版次签出态。 */
    default void setNumeric004(BigDecimal numeric004) {
        setFieldValue(COLUMN_NUMERIC_004, numeric004);
    }

    /** 读数值扩展字段005（numeric005）。 */
    default BigDecimal getNumeric005() {
        return getBigDecimalValue(COLUMN_NUMERIC_005);
    }

    /** 写数值扩展字段005（numeric005）；守卫＝版次签出态。 */
    default void setNumeric005(BigDecimal numeric005) {
        setFieldValue(COLUMN_NUMERIC_005, numeric005);
    }

    /** 读数值扩展字段006（numeric006）。 */
    default BigDecimal getNumeric006() {
        return getBigDecimalValue(COLUMN_NUMERIC_006);
    }

    /** 写数值扩展字段006（numeric006）；守卫＝版次签出态。 */
    default void setNumeric006(BigDecimal numeric006) {
        setFieldValue(COLUMN_NUMERIC_006, numeric006);
    }

    /** 读数值扩展字段007（numeric007）。 */
    default BigDecimal getNumeric007() {
        return getBigDecimalValue(COLUMN_NUMERIC_007);
    }

    /** 写数值扩展字段007（numeric007）；守卫＝版次签出态。 */
    default void setNumeric007(BigDecimal numeric007) {
        setFieldValue(COLUMN_NUMERIC_007, numeric007);
    }

    /** 读数值扩展字段008（numeric008）。 */
    default BigDecimal getNumeric008() {
        return getBigDecimalValue(COLUMN_NUMERIC_008);
    }

    /** 写数值扩展字段008（numeric008）；守卫＝版次签出态。 */
    default void setNumeric008(BigDecimal numeric008) {
        setFieldValue(COLUMN_NUMERIC_008, numeric008);
    }

    /** 读数值扩展字段009（numeric009）。 */
    default BigDecimal getNumeric009() {
        return getBigDecimalValue(COLUMN_NUMERIC_009);
    }

    /** 写数值扩展字段009（numeric009）；守卫＝版次签出态。 */
    default void setNumeric009(BigDecimal numeric009) {
        setFieldValue(COLUMN_NUMERIC_009, numeric009);
    }

    /** 读数值扩展字段010（numeric010）。 */
    default BigDecimal getNumeric010() {
        return getBigDecimalValue(COLUMN_NUMERIC_010);
    }

    /** 写数值扩展字段010（numeric010）；守卫＝版次签出态。 */
    default void setNumeric010(BigDecimal numeric010) {
        setFieldValue(COLUMN_NUMERIC_010, numeric010);
    }

    /** 读大文本扩展字段001（largetext001）。 */
    default String getLargetext001() {
        return getStringValue(COLUMN_LARGETEXT_001);
    }

    /** 写大文本扩展字段001（largetext001）；守卫＝版次签出态。 */
    default void setLargetext001(String largetext001) {
        setFieldValue(COLUMN_LARGETEXT_001, largetext001);
    }

    /** 读大文本扩展字段002（largetext002）。 */
    default String getLargetext002() {
        return getStringValue(COLUMN_LARGETEXT_002);
    }

    /** 写大文本扩展字段002（largetext002）；守卫＝版次签出态。 */
    default void setLargetext002(String largetext002) {
        setFieldValue(COLUMN_LARGETEXT_002, largetext002);
    }

    /** 读大文本扩展字段003（largetext003）。 */
    default String getLargetext003() {
        return getStringValue(COLUMN_LARGETEXT_003);
    }

    /** 写大文本扩展字段003（largetext003）；守卫＝版次签出态。 */
    default void setLargetext003(String largetext003) {
        setFieldValue(COLUMN_LARGETEXT_003, largetext003);
    }

    /** 读大文本扩展字段004（largetext004）。 */
    default String getLargetext004() {
        return getStringValue(COLUMN_LARGETEXT_004);
    }

    /** 写大文本扩展字段004（largetext004）；守卫＝版次签出态。 */
    default void setLargetext004(String largetext004) {
        setFieldValue(COLUMN_LARGETEXT_004, largetext004);
    }

    /** 读大文本扩展字段005（largetext005）。 */
    default String getLargetext005() {
        return getStringValue(COLUMN_LARGETEXT_005);
    }

    /** 写大文本扩展字段005（largetext005）；守卫＝版次签出态。 */
    default void setLargetext005(String largetext005) {
        setFieldValue(COLUMN_LARGETEXT_005, largetext005);
    }

    /** 读复合文本扩展字段001（compositetext001）。 */
    default String getCompositetext001() {
        return getStringValue(COLUMN_COMPOSITETEXT_001);
    }

    /** 写复合文本扩展字段001（compositetext001）；守卫＝版次签出态。 */
    default void setCompositetext001(String compositetext001) {
        setFieldValue(COLUMN_COMPOSITETEXT_001, compositetext001);
    }

    /** 读复合文本扩展字段002（compositetext002）。 */
    default String getCompositetext002() {
        return getStringValue(COLUMN_COMPOSITETEXT_002);
    }

    /** 写复合文本扩展字段002（compositetext002）；守卫＝版次签出态。 */
    default void setCompositetext002(String compositetext002) {
        setFieldValue(COLUMN_COMPOSITETEXT_002, compositetext002);
    }

    /** 读复合文本扩展字段003（compositetext003）。 */
    default String getCompositetext003() {
        return getStringValue(COLUMN_COMPOSITETEXT_003);
    }

    /** 写复合文本扩展字段003（compositetext003）；守卫＝版次签出态。 */
    default void setCompositetext003(String compositetext003) {
        setFieldValue(COLUMN_COMPOSITETEXT_003, compositetext003);
    }

    /** 读复合文本扩展字段004（compositetext004）。 */
    default String getCompositetext004() {
        return getStringValue(COLUMN_COMPOSITETEXT_004);
    }

    /** 写复合文本扩展字段004（compositetext004）；守卫＝版次签出态。 */
    default void setCompositetext004(String compositetext004) {
        setFieldValue(COLUMN_COMPOSITETEXT_004, compositetext004);
    }

    /** 读复合文本扩展字段005（compositetext005）。 */
    default String getCompositetext005() {
        return getStringValue(COLUMN_COMPOSITETEXT_005);
    }

    /** 写复合文本扩展字段005（compositetext005）；守卫＝版次签出态。 */
    default void setCompositetext005(String compositetext005) {
        setFieldValue(COLUMN_COMPOSITETEXT_005, compositetext005);
    }

    /** 读复合数值扩展字段001（compositenumber001）。 */
    default String getCompositenumber001() {
        return getStringValue(COLUMN_COMPOSITENUMBER_001);
    }

    /** 写复合数值扩展字段001（compositenumber001）；守卫＝版次签出态。 */
    default void setCompositenumber001(String compositenumber001) {
        setFieldValue(COLUMN_COMPOSITENUMBER_001, compositenumber001);
    }

    /** 读复合数值扩展字段002（compositenumber002）。 */
    default String getCompositenumber002() {
        return getStringValue(COLUMN_COMPOSITENUMBER_002);
    }

    /** 写复合数值扩展字段002（compositenumber002）；守卫＝版次签出态。 */
    default void setCompositenumber002(String compositenumber002) {
        setFieldValue(COLUMN_COMPOSITENUMBER_002, compositenumber002);
    }

    /** 读复合数值扩展字段003（compositenumber003）。 */
    default String getCompositenumber003() {
        return getStringValue(COLUMN_COMPOSITENUMBER_003);
    }

    /** 写复合数值扩展字段003（compositenumber003）；守卫＝版次签出态。 */
    default void setCompositenumber003(String compositenumber003) {
        setFieldValue(COLUMN_COMPOSITENUMBER_003, compositenumber003);
    }

    /** 读复合数值扩展字段004（compositenumber004）。 */
    default String getCompositenumber004() {
        return getStringValue(COLUMN_COMPOSITENUMBER_004);
    }

    /** 写复合数值扩展字段004（compositenumber004）；守卫＝版次签出态。 */
    default void setCompositenumber004(String compositenumber004) {
        setFieldValue(COLUMN_COMPOSITENUMBER_004, compositenumber004);
    }

    /** 读复合数值扩展字段005（compositenumber005）。 */
    default String getCompositenumber005() {
        return getStringValue(COLUMN_COMPOSITENUMBER_005);
    }

    /** 写复合数值扩展字段005（compositenumber005）；守卫＝版次签出态。 */
    default void setCompositenumber005(String compositenumber005) {
        setFieldValue(COLUMN_COMPOSITENUMBER_005, compositenumber005);
    }

    /** 读起始日期扩展字段001（startdate001）。 */
    default Long getStartdate001() {
        return getLongValue(COLUMN_STARTDATE_001);
    }

    /** 写起始日期扩展字段001（startdate001）；守卫＝版次签出态。 */
    default void setStartdate001(Long startdate001) {
        setFieldValue(COLUMN_STARTDATE_001, startdate001);
    }

    /** 读起始日期扩展字段002（startdate002）。 */
    default Long getStartdate002() {
        return getLongValue(COLUMN_STARTDATE_002);
    }

    /** 写起始日期扩展字段002（startdate002）；守卫＝版次签出态。 */
    default void setStartdate002(Long startdate002) {
        setFieldValue(COLUMN_STARTDATE_002, startdate002);
    }

    /** 读起始日期扩展字段003（startdate003）。 */
    default Long getStartdate003() {
        return getLongValue(COLUMN_STARTDATE_003);
    }

    /** 写起始日期扩展字段003（startdate003）；守卫＝版次签出态。 */
    default void setStartdate003(Long startdate003) {
        setFieldValue(COLUMN_STARTDATE_003, startdate003);
    }

    /** 读起始日期扩展字段004（startdate004）。 */
    default Long getStartdate004() {
        return getLongValue(COLUMN_STARTDATE_004);
    }

    /** 写起始日期扩展字段004（startdate004）；守卫＝版次签出态。 */
    default void setStartdate004(Long startdate004) {
        setFieldValue(COLUMN_STARTDATE_004, startdate004);
    }

    /** 读起始日期扩展字段005（startdate005）。 */
    default Long getStartdate005() {
        return getLongValue(COLUMN_STARTDATE_005);
    }

    /** 写起始日期扩展字段005（startdate005）；守卫＝版次签出态。 */
    default void setStartdate005(Long startdate005) {
        setFieldValue(COLUMN_STARTDATE_005, startdate005);
    }

    /** 读结束日期扩展字段001（endstartdate001）。 */
    default Long getEndstartdate001() {
        return getLongValue(COLUMN_ENDSTARTDATE_001);
    }

    /** 写结束日期扩展字段001（endstartdate001）；守卫＝版次签出态。 */
    default void setEndstartdate001(Long endstartdate001) {
        setFieldValue(COLUMN_ENDSTARTDATE_001, endstartdate001);
    }

    /** 读结束日期扩展字段002（endstartdate002）。 */
    default Long getEndstartdate002() {
        return getLongValue(COLUMN_ENDSTARTDATE_002);
    }

    /** 写结束日期扩展字段002（endstartdate002）；守卫＝版次签出态。 */
    default void setEndstartdate002(Long endstartdate002) {
        setFieldValue(COLUMN_ENDSTARTDATE_002, endstartdate002);
    }

    /** 读结束日期扩展字段003（endstartdate003）。 */
    default Long getEndstartdate003() {
        return getLongValue(COLUMN_ENDSTARTDATE_003);
    }

    /** 写结束日期扩展字段003（endstartdate003）；守卫＝版次签出态。 */
    default void setEndstartdate003(Long endstartdate003) {
        setFieldValue(COLUMN_ENDSTARTDATE_003, endstartdate003);
    }

    /** 读结束日期扩展字段004（endstartdate004）。 */
    default Long getEndstartdate004() {
        return getLongValue(COLUMN_ENDSTARTDATE_004);
    }

    /** 写结束日期扩展字段004（endstartdate004）；守卫＝版次签出态。 */
    default void setEndstartdate004(Long endstartdate004) {
        setFieldValue(COLUMN_ENDSTARTDATE_004, endstartdate004);
    }

    /** 读结束日期扩展字段005（endstartdate005）。 */
    default Long getEndstartdate005() {
        return getLongValue(COLUMN_ENDSTARTDATE_005);
    }

    /** 写结束日期扩展字段005（endstartdate005）；守卫＝版次签出态。 */
    default void setEndstartdate005(Long endstartdate005) {
        setFieldValue(COLUMN_ENDSTARTDATE_005, endstartdate005);
    }

    /** 读富文本扩展字段001（richtext001）。 */
    default String getRichtext001() {
        return getStringValue(COLUMN_RICHTEXT_001);
    }

    /** 写富文本扩展字段001（richtext001）；守卫＝版次签出态。 */
    default void setRichtext001(String richtext001) {
        setFieldValue(COLUMN_RICHTEXT_001, richtext001);
    }

    /** 读富文本扩展字段002（richtext002）。 */
    default String getRichtext002() {
        return getStringValue(COLUMN_RICHTEXT_002);
    }

    /** 写富文本扩展字段002（richtext002）；守卫＝版次签出态。 */
    default void setRichtext002(String richtext002) {
        setFieldValue(COLUMN_RICHTEXT_002, richtext002);
    }

    /** 读富文本扩展字段003（richtext003）。 */
    default String getRichtext003() {
        return getStringValue(COLUMN_RICHTEXT_003);
    }

    /** 写富文本扩展字段003（richtext003）；守卫＝版次签出态。 */
    default void setRichtext003(String richtext003) {
        setFieldValue(COLUMN_RICHTEXT_003, richtext003);
    }

    /** 读富文本扩展字段004（richtext004）。 */
    default String getRichtext004() {
        return getStringValue(COLUMN_RICHTEXT_004);
    }

    /** 写富文本扩展字段004（richtext004）；守卫＝版次签出态。 */
    default void setRichtext004(String richtext004) {
        setFieldValue(COLUMN_RICHTEXT_004, richtext004);
    }

    /** 读富文本扩展字段005（richtext005）。 */
    default String getRichtext005() {
        return getStringValue(COLUMN_RICHTEXT_005);
    }

    /** 写富文本扩展字段005（richtext005）；守卫＝版次签出态。 */
    default void setRichtext005(String richtext005) {
        setFieldValue(COLUMN_RICHTEXT_005, richtext005);
    }
}

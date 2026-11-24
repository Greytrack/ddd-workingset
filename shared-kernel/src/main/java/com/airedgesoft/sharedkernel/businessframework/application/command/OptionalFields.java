package com.airedgesoft.sharedkernel.businessframework.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 创建需求可选字段命令（全部 {@code String}）：承载类级（01 段）与子类级（001 段）两组可选扩展列。
 *
 * <p>
 * <b>唯一声明处（公共契约）</b>：150 个组件与 shared-kernel 能力接口 {@code OptionalClassFields}（75 列）/ {@code OptionalSubClassFields}（75
 * 列） 一一对应，列名即业务数据键；HTTP 侧（{@code CreateRequirementRequest#optionalFields}）与命令侧（{@code OptionalFields}） 共用本类型，不在适配层另立同构
 * Request，避免 150 列双份维护。
 *
 * <p>
 * <b>类型口径</b>：契约侧统一以 {@code String} 承载；日期 / 数值等类型转换与合法性校验由属性工厂按分类元数据完成。
 *
 * <p>
 * <b>空值口径</b>：未传或空白串＝该字段不参与创建（交给分类默认值与必填装配），不表达「置空」。
 */
@Schema(description = "需求可选字段（类级 01 段 + 子类级 001 段；全部为 String 承载）")
public record OptionalFields(@Schema(description = "日期可选字段01（date01）") String date01,
        @Schema(description = "日期可选字段02（date02）") String date02,
        @Schema(description = "日期可选字段03（date03）") String date03,
        @Schema(description = "日期可选字段04（date04）") String date04,
        @Schema(description = "日期可选字段05（date05）") String date05,
        @Schema(description = "日期可选字段001（date001）") String date001,
        @Schema(description = "日期可选字段002（date002）") String date002,
        @Schema(description = "日期可选字段003（date003）") String date003,
        @Schema(description = "日期可选字段004（date004）") String date004,
        @Schema(description = "日期可选字段005（date005）") String date005,
        @Schema(description = "文本可选字段01（text01）") String text01,
        @Schema(description = "文本可选字段02（text02）") String text02,
        @Schema(description = "文本可选字段03（text03）") String text03,
        @Schema(description = "文本可选字段04（text04）") String text04,
        @Schema(description = "文本可选字段05（text05）") String text05,
        @Schema(description = "文本可选字段06（text06）") String text06,
        @Schema(description = "文本可选字段07（text07）") String text07,
        @Schema(description = "文本可选字段08（text08）") String text08,
        @Schema(description = "文本可选字段09（text09）") String text09,
        @Schema(description = "文本可选字段10（text10）") String text10,
        @Schema(description = "文本可选字段001（text001）") String text001,
        @Schema(description = "文本可选字段002（text002）") String text002,
        @Schema(description = "文本可选字段003（text003）") String text003,
        @Schema(description = "文本可选字段004（text004）") String text004,
        @Schema(description = "文本可选字段005（text005）") String text005,
        @Schema(description = "文本可选字段006（text006）") String text006,
        @Schema(description = "文本可选字段007（text007）") String text007,
        @Schema(description = "文本可选字段008（text008）") String text008,
        @Schema(description = "文本可选字段009（text009）") String text009,
        @Schema(description = "文本可选字段010（text010）") String text010,
        @Schema(description = "列表可选字段01（list01）") String list01,
        @Schema(description = "列表可选字段02（list02）") String list02,
        @Schema(description = "列表可选字段03（list03）") String list03,
        @Schema(description = "列表可选字段04（list04）") String list04,
        @Schema(description = "列表可选字段05（list05）") String list05,
        @Schema(description = "列表可选字段06（list06）") String list06,
        @Schema(description = "列表可选字段07（list07）") String list07,
        @Schema(description = "列表可选字段08（list08）") String list08,
        @Schema(description = "列表可选字段09（list09）") String list09,
        @Schema(description = "列表可选字段10（list10）") String list10,
        @Schema(description = "列表可选字段001（list001）") String list001,
        @Schema(description = "列表可选字段002（list002）") String list002,
        @Schema(description = "列表可选字段003（list003）") String list003,
        @Schema(description = "列表可选字段004（list004）") String list004,
        @Schema(description = "列表可选字段005（list005）") String list005,
        @Schema(description = "列表可选字段006（list006）") String list006,
        @Schema(description = "列表可选字段007（list007）") String list007,
        @Schema(description = "列表可选字段008（list008）") String list008,
        @Schema(description = "列表可选字段009（list009）") String list009,
        @Schema(description = "列表可选字段010（list010）") String list010,
        @Schema(description = "多选列表可选字段01（multilist01）") String multilist01,
        @Schema(description = "多选列表可选字段02（multilist02）") String multilist02,
        @Schema(description = "多选列表可选字段03（multilist03）") String multilist03,
        @Schema(description = "多选列表可选字段04（multilist04）") String multilist04,
        @Schema(description = "多选列表可选字段05（multilist05）") String multilist05,
        @Schema(description = "多选列表可选字段06（multilist06）") String multilist06,
        @Schema(description = "多选列表可选字段07（multilist07）") String multilist07,
        @Schema(description = "多选列表可选字段08（multilist08）") String multilist08,
        @Schema(description = "多选列表可选字段09（multilist09）") String multilist09,
        @Schema(description = "多选列表可选字段10（multilist10）") String multilist10,
        @Schema(description = "多选列表可选字段001（multilist001）") String multilist001,
        @Schema(description = "多选列表可选字段002（multilist002）") String multilist002,
        @Schema(description = "多选列表可选字段003（multilist003）") String multilist003,
        @Schema(description = "多选列表可选字段004（multilist004）") String multilist004,
        @Schema(description = "多选列表可选字段005（multilist005）") String multilist005,
        @Schema(description = "多选列表可选字段006（multilist006）") String multilist006,
        @Schema(description = "多选列表可选字段007（multilist007）") String multilist007,
        @Schema(description = "多选列表可选字段008（multilist008）") String multilist008,
        @Schema(description = "多选列表可选字段009（multilist009）") String multilist009,
        @Schema(description = "多选列表可选字段010（multilist010）") String multilist010,
        @Schema(description = "数值可选字段01（numeric01）") String numeric01,
        @Schema(description = "数值可选字段02（numeric02）") String numeric02,
        @Schema(description = "数值可选字段03（numeric03）") String numeric03,
        @Schema(description = "数值可选字段04（numeric04）") String numeric04,
        @Schema(description = "数值可选字段05（numeric05）") String numeric05,
        @Schema(description = "数值可选字段06（numeric06）") String numeric06,
        @Schema(description = "数值可选字段07（numeric07）") String numeric07,
        @Schema(description = "数值可选字段08（numeric08）") String numeric08,
        @Schema(description = "数值可选字段09（numeric09）") String numeric09,
        @Schema(description = "数值可选字段10（numeric10）") String numeric10,
        @Schema(description = "数值可选字段001（numeric001）") String numeric001,
        @Schema(description = "数值可选字段002（numeric002）") String numeric002,
        @Schema(description = "数值可选字段003（numeric003）") String numeric003,
        @Schema(description = "数值可选字段004（numeric004）") String numeric004,
        @Schema(description = "数值可选字段005（numeric005）") String numeric005,
        @Schema(description = "数值可选字段006（numeric006）") String numeric006,
        @Schema(description = "数值可选字段007（numeric007）") String numeric007,
        @Schema(description = "数值可选字段008（numeric008）") String numeric008,
        @Schema(description = "数值可选字段009（numeric009）") String numeric009,
        @Schema(description = "数值可选字段010（numeric010）") String numeric010,
        @Schema(description = "大文本可选字段01（largetext01）") String largetext01,
        @Schema(description = "大文本可选字段02（largetext02）") String largetext02,
        @Schema(description = "大文本可选字段03（largetext03）") String largetext03,
        @Schema(description = "大文本可选字段04（largetext04）") String largetext04,
        @Schema(description = "大文本可选字段05（largetext05）") String largetext05,
        @Schema(description = "大文本可选字段001（largetext001）") String largetext001,
        @Schema(description = "大文本可选字段002（largetext002）") String largetext002,
        @Schema(description = "大文本可选字段003（largetext003）") String largetext003,
        @Schema(description = "大文本可选字段004（largetext004）") String largetext004,
        @Schema(description = "大文本可选字段005（largetext005）") String largetext005,
        @Schema(description = "复合文本可选字段01（compositetext01）") String compositetext01,
        @Schema(description = "复合文本可选字段02（compositetext02）") String compositetext02,
        @Schema(description = "复合文本可选字段03（compositetext03）") String compositetext03,
        @Schema(description = "复合文本可选字段04（compositetext04）") String compositetext04,
        @Schema(description = "复合文本可选字段05（compositetext05）") String compositetext05,
        @Schema(description = "复合文本可选字段001（compositetext001）") String compositetext001,
        @Schema(description = "复合文本可选字段002（compositetext002）") String compositetext002,
        @Schema(description = "复合文本可选字段003（compositetext003）") String compositetext003,
        @Schema(description = "复合文本可选字段004（compositetext004）") String compositetext004,
        @Schema(description = "复合文本可选字段005（compositetext005）") String compositetext005,
        @Schema(description = "复合数值可选字段01（compositenumber01）") String compositenumber01,
        @Schema(description = "复合数值可选字段02（compositenumber02）") String compositenumber02,
        @Schema(description = "复合数值可选字段03（compositenumber03）") String compositenumber03,
        @Schema(description = "复合数值可选字段04（compositenumber04）") String compositenumber04,
        @Schema(description = "复合数值可选字段05（compositenumber05）") String compositenumber05,
        @Schema(description = "复合数值可选字段001（compositenumber001）") String compositenumber001,
        @Schema(description = "复合数值可选字段002（compositenumber002）") String compositenumber002,
        @Schema(description = "复合数值可选字段003（compositenumber003）") String compositenumber003,
        @Schema(description = "复合数值可选字段004（compositenumber004）") String compositenumber004,
        @Schema(description = "复合数值可选字段005（compositenumber005）") String compositenumber005,
        @Schema(description = "起始日期可选字段01（startdate01）") String startdate01,
        @Schema(description = "起始日期可选字段02（startdate02）") String startdate02,
        @Schema(description = "起始日期可选字段03（startdate03）") String startdate03,
        @Schema(description = "起始日期可选字段04（startdate04）") String startdate04,
        @Schema(description = "起始日期可选字段05（startdate05）") String startdate05,
        @Schema(description = "起始日期可选字段001（startdate001）") String startdate001,
        @Schema(description = "起始日期可选字段002（startdate002）") String startdate002,
        @Schema(description = "起始日期可选字段003（startdate003）") String startdate003,
        @Schema(description = "起始日期可选字段004（startdate004）") String startdate004,
        @Schema(description = "起始日期可选字段005（startdate005）") String startdate005,
        @Schema(description = "结束日期可选字段01（endstartdate01）") String endstartdate01,
        @Schema(description = "结束日期可选字段02（endstartdate02）") String endstartdate02,
        @Schema(description = "结束日期可选字段03（endstartdate03）") String endstartdate03,
        @Schema(description = "结束日期可选字段04（endstartdate04）") String endstartdate04,
        @Schema(description = "结束日期可选字段05（endstartdate05）") String endstartdate05,
        @Schema(description = "结束日期可选字段001（endstartdate001）") String endstartdate001,
        @Schema(description = "结束日期可选字段002（endstartdate002）") String endstartdate002,
        @Schema(description = "结束日期可选字段003（endstartdate003）") String endstartdate003,
        @Schema(description = "结束日期可选字段004（endstartdate004）") String endstartdate004,
        @Schema(description = "结束日期可选字段005（endstartdate005）") String endstartdate005,
        @Schema(description = "富文本可选字段01（richtext01）") String richtext01,
        @Schema(description = "富文本可选字段02（richtext02）") String richtext02,
        @Schema(description = "富文本可选字段03（richtext03）") String richtext03,
        @Schema(description = "富文本可选字段04（richtext04）") String richtext04,
        @Schema(description = "富文本可选字段05（richtext05）") String richtext05,
        @Schema(description = "富文本可选字段001（richtext001）") String richtext001,
        @Schema(description = "富文本可选字段002（richtext002）") String richtext002,
        @Schema(description = "富文本可选字段003（richtext003）") String richtext003,
        @Schema(description = "富文本可选字段004（richtext004）") String richtext004,
        @Schema(description = "富文本可选字段005（richtext005）") String richtext005) {

    /** 记录组件（名即落库列名；只取一次，避免每次请求反射扫类）。 */
    private static final RecordComponent[] COMPONENTS = OptionalFields.class.getRecordComponents();

    /**
     * 转「落库列名 → 串值」映射：未传 / 空白值不参与创建（不表达「置空」）。
     *
     * @return 列名 → 串形态值（保持声明顺序）
     */
    public Map<String, String> toColumnValues() {
        Map<String, String> columns = new LinkedHashMap<>();
        for (RecordComponent component : COMPONENTS) {
            String value = accessorValue(component);
            if (value != null && !value.isBlank()) {
                columns.put(component.getName(), value);
            }
        }
        return Map.copyOf(columns);
    }

    /** 读单个组件值（组件名即列名，故映射无需手写）。 */
    private String accessorValue(RecordComponent component) {
        try {
            return (String) component.getAccessor().invoke(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取可选字段失败: " + component.getName(), e);
        }
    }
}

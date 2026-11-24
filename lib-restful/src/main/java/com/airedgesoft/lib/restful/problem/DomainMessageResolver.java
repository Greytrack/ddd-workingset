package com.airedgesoft.lib.restful.problem;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.exception.ResCode;

/**
 * 领域异常提示词（RFC 9457 {@code detail}）国际化解析器（v4-restful-api.md §8）。
 *
 * <p>
 * 宿主 / BC 注入同型 Bean（如对接 legacy {@code IAirI18n} / {@code MessageSource} 的实现）即自动接管 <b>detail 文案解析</b>： 按
 * {@code DomainException.getI18nKey()} + {@code getArgs()} 依当前 locale 取键并格式化；键为 {@code null} 或未命中 → 回退
 * {@code ex.getMessage()} 原文。缺省实现即身份解析（直接返回 message）。
 *
 * <p>
 * application 只携带 错误码（{@link com.airedgesoft.lib.exception.DomainResCode}）与参数，禁止自行解析文案。
 */
@FunctionalInterface
public interface DomainMessageResolver {

    /** 依 locale 解析 {@code ex} 的提示词 detail 文案。 */
    String resolve(DomainException ex);

    /**
     * 依 locale 解析单条字段级违规的 {@code description} 文案（problem+json 扩展成员 {@code fieldViolations} 渲染， 规范 §19.4）：缺省 = ResCode
     * 模板经 args String.format（与 {@link DomainException#getMessage()} 同语义）； 宿主 i18n 实现应覆写本方法按码值取键。
     */
    default String resolve(ResCode code, Object[] args) {
        try {
            if (args != null && args.length > 0) {
                return String.format(code.getDesc(), args);
            }
            return code.getDesc();
        } catch (Exception e) {
            return code.getDesc();
        }
    }
}

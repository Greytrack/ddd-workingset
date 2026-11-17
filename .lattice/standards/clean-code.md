---
mode: overlay
---

# Clean Code — AirEdge Overlay

> 与 Lattice clean-code atom 默认规则叠加。完整细则见 `.agent/rules/08-code-style.md`。

## Project-Specific Rules

- **Lombok 必选**：`@Data` `@RequiredArgsConstructor` `@Service` `@Slf4j`；构造器注入，禁止字段 `@Autowired`
- **行宽**：120 字符；缩进 4 空格；LF；K&R 大括号
- **import**：使用 import，禁止代码内全限定类名
- **日志**：`log.info("id: {}", id)`；禁止字符串拼接日志
- **函数规模**：Management 方法保持编排职责；复杂逻辑下沉 SDKImpl 或私有方法

## References

- `.agent/rules/08-code-style.md`
- `AGENTS.md` 第二节

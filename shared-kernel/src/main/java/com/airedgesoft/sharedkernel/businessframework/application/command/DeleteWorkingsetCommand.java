package com.airedgesoft.sharedkernel.businessframework.application.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 删除工作集命令（软删入回收站语义之外的强删除口径由应用服务裁决；默认工作集 / 存在子工作集或已挂分支 409）。
 */
@Schema(description = "删除工作集命令（204 幂等；幂等键必接）")
public record DeleteWorkingsetCommand() {
}

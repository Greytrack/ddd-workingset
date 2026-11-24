package com.airedgesoft.sharedkernel.businessframework.domain.history;

import java.util.List;
import java.util.Objects;

/** 一次已完成的业务操作在共享历史中的不可变记录。 */
public record OperationHistory(Origin origin, Subject subject, Actor actor, Review review) {

    /** 按动作校验记录，确保减签条目包含原节点和实际移除对象。 */
    public OperationHistory {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(actor, "actor");
        if ((origin.action() == Action.DELETE_APPROVER || origin.action() == Action.ADD_APPROVER
                || origin.action() == Action.ADD_EXCEPTION_APPROVER)
                && (review == null || review.approvers().isEmpty())) {
            throw new IllegalArgumentException("审批人变更历史需要原节点和实际变更的审批人");
        }
        if (origin.action() == Action.WITHDRAW && review == null) {
            throw new IllegalArgumentException("撤回历史需要原审批节点");
        }
    }

    /** 标识来源操作的独立消费记录，发生时刻为 Unix epoch 毫秒。 */
    public record Origin(String deliveryId, Action action, Long occurredAt) {
        /** 校验重投依赖的身份和业务时间。 */
        public Origin {
            if (deliveryId == null || deliveryId.isBlank()) {
                throw new IllegalArgumentException("历史来源不能为空");
            }
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /** 操作对象及其当时用于历史展示的编号。 */
    public record Subject(long id, long classificationId, String number) {}

    /** 操作时捕获的用户信息及可选的客户端信息。 */
    public record Actor(long id, String displayName, String loginName, String number, Client client) {}

    /** 请求中真正提供的客户端字段。 */
    public record Client(String ipAddress, String operatingSystem, String browser, String city) {}

    /** 审批动作特有的原节点与审批人快照。 */
    public record Review(Long nodeInstanceId, String nodeName, List<Approver> approvers) {
        /** 固定展示名称和实际移除顺序，避免下一轮流程或目录变更影响该条记录。 */
        public Review {
            Objects.requireNonNull(nodeName, "nodeName");
            approvers = List.copyOf(approvers);
        }
    }

    /** 保留个人和用户组的身份，即使目录已无名称。 */
    public record Approver(long id, ApproverKind kind, String displayName) {
        /** 固定审批人身份类型，避免个人与同标识的组混淆。 */
        public Approver {
            Objects.requireNonNull(kind, "kind");
        }
    }

    public enum ApproverKind {
        USER, USER_GROUP
    }

    public enum Action {
        DELETE_APPROVER, ADD_APPROVER, ADD_EXCEPTION_APPROVER, TRANSFER_SIGN, CHANGE_STATUS, REVIEW, WITHDRAW
    }
}

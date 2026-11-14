package sk.workingset.domain;

/** T2 只需要有效空间的身份；name 仅用于界面可读展示。 */
public record ProductSpace(String id, String name) {
    public ProductSpace(String id) {
        this(id, null);
    }

    public ProductSpace {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("productSpaceId must not be blank");
        }
    }
}

package sk.workingset.domain;

/** T2 只需要有效空间的身份。 */
public record ProductSpace(String id) {
    public ProductSpace {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("productSpaceId must not be blank");
        }
    }
}

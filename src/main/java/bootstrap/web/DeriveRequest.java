package bootstrap.web;

/** 非共享派生请求。 */
public record DeriveRequest(String sourceTrackerId,
                            String sourceLocalId,
                            int sourceRevision,
                            String commonId) {
}

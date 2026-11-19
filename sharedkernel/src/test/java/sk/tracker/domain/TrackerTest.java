package sk.tracker.domain;

import org.junit.jupiter.api.Test;
import sk.workingset.domain.Classification;
import sk.workingset.domain.ProductSpace;
import sk.workingset.domain.WorkingSetId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackerTest {
    @Test
    void descendantsKeepFamilyIdentityAndLinkToTheirImmediateSource() {
        Tracker root = Tracker.original(TrackerId.of(101), new ProductSpace("201"),
                new Classification("301", Classification.SharingMode.UNSHARED), null);
        Tracker child = Tracker.derived(TrackerId.of(102), root, WorkingSetId.of(2L));
        Tracker grandchild = Tracker.derived(TrackerId.of(103), child, WorkingSetId.of(3L));

        assertEquals(root.trackerId(), root.commonId());
        assertEquals(root.commonId(), child.commonId());
        assertEquals(root.commonId(), grandchild.commonId());
        assertEquals(child.trackerId(), grandchild.sourceId());
        assertEquals(TrackerId.of(103), grandchild.compatibility().trackerId());
        assertEquals(301L, grandchild.compatibility().originalClassificationId());
    }
}

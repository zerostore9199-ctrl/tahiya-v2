package om.mtc.attendance.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaceMatcherTest {

    private static float[] v(float... x) {
        return x;
    }

    @Test
    void cosineOfIdenticalAndOrthogonalVectors() {
        assertThat(FaceMatcher.cosine(v(1, 2, 3), v(1, 2, 3))).isCloseTo(1f, org.assertj.core.data.Offset.offset(1e-6f));
        assertThat(FaceMatcher.cosine(v(1, 0), v(0, 1))).isZero();
        assertThat(FaceMatcher.cosine(v(0, 0), v(0, 1))).isZero();
    }

    @Test
    void rejectsMismatchedDimensions() {
        assertThatThrownBy(() -> FaceMatcher.cosine(v(1, 0), v(1, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameSoldierIsNeverCountedTwiceInOnePhoto() {
        Map<Long, List<float[]>> c = new LinkedHashMap<>();
        c.put(1L, List.of(v(1, 0, 0)));
        List<FaceMatcher.Face> faces = List.of(
                new FaceMatcher.Face(v(0.95f, 0.05f, 0), 0.9f),
                new FaceMatcher.Face(v(1, 0, 0), 0.9f));
        var r = FaceMatcher.match(faces, c, 0.5, 0.5);
        assertThat(r).filteredOn(a -> a.outcome() == FaceMatcher.Outcome.MATCHED).hasSize(1);
        // The better of the two faces wins the soldier.
        assertThat(r.get(1).outcome()).isEqualTo(FaceMatcher.Outcome.MATCHED);
        assertThat(r.get(0).outcome()).isEqualTo(FaceMatcher.Outcome.UNKNOWN);
    }

    @Test
    void usesBestTemplateAndRespectsThresholdAndQuality() {
        Map<Long, List<float[]>> c = new LinkedHashMap<>();
        c.put(1L, List.of(v(0, 0, 1), v(1, 0, 0)));
        c.put(2L, List.of(v(0, 1, 0)));
        List<FaceMatcher.Face> faces = List.of(
                new FaceMatcher.Face(v(1, 0.1f, 0), 0.9f),      // → soldier 1 via 2nd template
                new FaceMatcher.Face(v(0.5f, 0.5f, 0.7f), 0.9f),  // below threshold → unknown
                new FaceMatcher.Face(v(0, 1, 0), 0.2f));         // unclear → low quality
        var r = FaceMatcher.match(faces, c, 0.8, 0.5);
        assertThat(r.get(0).soldierId()).isEqualTo(1L);
        assertThat(r.get(1).outcome()).isEqualTo(FaceMatcher.Outcome.UNKNOWN);
        assertThat(r.get(2).outcome()).isEqualTo(FaceMatcher.Outcome.LOW_QUALITY);
    }

    @Test
    void globalAssignmentResolvesConflicts() {
        // Face A is close to both soldiers, face B only to soldier 1.
        Map<Long, List<float[]>> c = new LinkedHashMap<>();
        c.put(1L, List.of(v(1, 0)));
        c.put(2L, List.of(v(0.6f, 0.8f)));
        List<FaceMatcher.Face> faces = List.of(
                new FaceMatcher.Face(v(0.8f, 0.6f), 0.9f),
                new FaceMatcher.Face(v(1, 0), 0.9f));
        var r = FaceMatcher.match(faces, c, 0.5, 0.5);
        assertThat(r.get(1).soldierId()).isEqualTo(1L);
        assertThat(r.get(0).soldierId()).isEqualTo(2L);
    }

    @Test
    void emptyInputs() {
        assertThat(FaceMatcher.match(List.of(), Map.of(), 0.5, 0.5)).isEmpty();
        var r = FaceMatcher.match(List.of(new FaceMatcher.Face(v(1, 0), 0.9f)), Map.of(), 0.5, 0.5);
        assertThat(r.get(0).outcome()).isEqualTo(FaceMatcher.Outcome.UNKNOWN);
    }
}

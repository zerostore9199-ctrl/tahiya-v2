package om.mtc.attendance.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure matching logic (no framework dependencies, unit-tested in isolation).
 *
 * <p>Each detected face is compared with every enrolled template of the platoon using
 * cosine similarity; a soldier's score is the best of his templates. Assignment is a
 * global greedy one-to-one matching: the highest-scoring (face, soldier) pairs are
 * taken first, so the same soldier can never be counted twice in one photo and two
 * faces can never claim the same soldier.</p>
 */
public final class FaceMatcher {

    public enum Outcome { MATCHED, UNKNOWN, LOW_QUALITY }

    public record Face(float[] embedding, float detScore) {
    }

    public record Assignment(int faceIndex, Outcome outcome, Long soldierId, float similarity) {
    }

    private FaceMatcher() {
    }

    public static float cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Embedding size mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0f;
        }
        return (float) (dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }

    /**
     * @param faces       faces detected in the photo
     * @param candidates  soldierId -> enrolled templates
     * @param threshold   minimum cosine similarity for a match
     * @param minDetScore faces detected with a lower score are ignored as unclear
     * @return one assignment per face, in the same order as {@code faces}
     */
    public static List<Assignment> match(List<Face> faces, Map<Long, List<float[]>> candidates,
                                         double threshold, double minDetScore) {
        Assignment[] result = new Assignment[faces.size()];
        record Pair(int face, Long soldier, float sim) {
        }
        List<Pair> pairs = new ArrayList<>();

        for (int i = 0; i < faces.size(); i++) {
            Face f = faces.get(i);
            if (f.detScore() < minDetScore) {
                result[i] = new Assignment(i, Outcome.LOW_QUALITY, null, 0f);
                continue;
            }
            for (Map.Entry<Long, List<float[]>> e : candidates.entrySet()) {
                float best = -1f;
                for (float[] t : e.getValue()) {
                    best = Math.max(best, cosine(f.embedding(), t));
                }
                if (best >= threshold) {
                    pairs.add(new Pair(i, e.getKey(), best));
                }
            }
        }

        pairs.sort(Comparator.comparingDouble((Pair p) -> p.sim()).reversed()
                .thenComparing(Pair::face).thenComparing(Pair::soldier));
        Set<Long> takenSoldiers = new HashSet<>();
        for (Pair p : pairs) {
            if (result[p.face()] == null && !takenSoldiers.contains(p.soldier())) {
                result[p.face()] = new Assignment(p.face(), Outcome.MATCHED, p.soldier(), p.sim());
                takenSoldiers.add(p.soldier());
            }
        }
        for (int i = 0; i < result.length; i++) {
            if (result[i] == null) {
                result[i] = new Assignment(i, Outcome.UNKNOWN, null, 0f);
            }
        }
        return Arrays.asList(result);
    }

    /** Best similarity between one embedding and a set of templates (or -1 if none). */
    public static float best(float[] embedding, List<float[]> templates) {
        float best = -1f;
        for (float[] t : templates) {
            best = Math.max(best, cosine(embedding, t));
        }
        return best;
    }
}

package om.mtc.attendance.repository;

import om.mtc.attendance.domain.FaceEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FaceEmbeddingRepository extends JpaRepository<FaceEmbedding, Long> {
    List<FaceEmbedding> findBySoldierIdOrderByCreatedAtAsc(Long soldierId);

    long countBySoldierId(Long soldierId);

    /** All templates of the active soldiers of one platoon — the recognition candidate pool. */
    @Query("""
            select f from FaceEmbedding f join fetch f.soldier s
            where s.platoon.id = :platoonId and s.active = true
            """)
    List<FaceEmbedding> findActiveByPlatoon(@Param("platoonId") Long platoonId);

    /** All templates of active soldiers in the unit (used for duplicate-identity checks at enrollment). */
    @Query("select f from FaceEmbedding f join fetch f.soldier s where s.active = true")
    List<FaceEmbedding> findAllActive();

    @Query("select f.soldier.id, count(f) from FaceEmbedding f where f.soldier.id in :ids group by f.soldier.id")
    List<Object[]> countBySoldierIds(@Param("ids") List<Long> ids);

    @Modifying
    @Query("delete from FaceEmbedding f where f.soldier.id = :soldierId")
    int deleteBySoldierId(@Param("soldierId") Long soldierId);
}

package om.mtc.attendance.repository;

import om.mtc.attendance.domain.Soldier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SoldierRepository extends JpaRepository<Soldier, Long> {
    boolean existsByMilitaryNumber(String militaryNumber);

    List<Soldier> findByPlatoon_IdAndActiveTrueOrderByFullNameAsc(Long platoonId);

    long countByPlatoon_IdAndActiveTrue(Long platoonId);

    @Query("select s.platoon.id, count(s) from Soldier s where s.active = true group by s.platoon.id")
    List<Object[]> countActiveByPlatoon();

    @Query(value = """
            select s from Soldier s join fetch s.platoon p
            where p.id in :platoonIds
              and (:active is null or s.active = :active)
              and (:q is null or lower(s.fullName) like :q or s.militaryNumber like :q)
            """, countQuery = """
            select count(s) from Soldier s
            where s.platoon.id in :platoonIds
              and (:active is null or s.active = :active)
              and (:q is null or lower(s.fullName) like :q or s.militaryNumber like :q)
            """)
    Page<Soldier> search(@Param("platoonIds") Collection<Long> platoonIds, @Param("active") Boolean active,
                         @Param("q") String q, Pageable pageable);
}

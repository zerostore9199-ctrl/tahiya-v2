package om.mtc.attendance.repository;

import om.mtc.attendance.domain.ProfilePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProfilePhotoRepository extends JpaRepository<ProfilePhoto, Long> {
    Optional<ProfilePhoto> findBySoldierId(Long soldierId);

    Optional<ProfilePhoto> findByUserId(Long userId);

    @Modifying
    @Query("delete from ProfilePhoto p where p.soldierId = :id")
    int deleteBySoldierId(@Param("id") Long id);

    @Modifying
    @Query("delete from ProfilePhoto p where p.userId = :id")
    int deleteByUserId(@Param("id") Long id);
}

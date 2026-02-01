package com.greenroots.repository;

import com.greenroots.entity.Plant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlantRepository extends JpaRepository<Plant, Long> {

    List<Plant> findByCategory(Plant.Category category);

    List<Plant> findByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Plant p WHERE p.id = :id")
    Optional<Plant> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT p FROM Plant p WHERE p.category = :category AND p.active = true")
    List<Plant> findActivePlantsByCategory(@Param("category") Plant.Category category);

    @Query("SELECT p FROM Plant p WHERE p.lightRequirement = :lightReq AND p.active = true")
    List<Plant> findByLightRequirement(@Param("lightReq") Plant.LightRequirement lightRequirement);
}

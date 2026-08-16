package com.migrationreport.repository;

import com.migrationreport.entity.MigrationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MigrationJobRepository extends JpaRepository<MigrationJob, Long> {
    
    List<MigrationJob> findByCategoryOrderByCreatedDateDesc(String category);

    Optional<MigrationJob> findByName(String name);

    boolean existsByNameIgnoreCase(String name);

    List<MigrationJob> findByStatus(String status);
}

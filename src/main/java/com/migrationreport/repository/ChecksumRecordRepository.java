package com.migrationreport.repository;

import com.migrationreport.entity.ChecksumRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChecksumRecordRepository extends JpaRepository<ChecksumRecord, String> {
}

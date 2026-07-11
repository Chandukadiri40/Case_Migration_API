package com.db_search.repository;

import com.db_search.entity.ChecksumRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChecksumRecordRepository extends JpaRepository<ChecksumRecord, String> {
}

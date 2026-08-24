-- ==============================================================================
-- PostgreSQL Indexing Script for Discovery Dashboard (1M+ Records Optimization)
-- ==============================================================================
-- This script creates highly optimized B-Tree and composite indexes on the 
-- DocVersion and Annotation tables to prevent sequential scans when the 
-- Discovery Dashboard performs GROUP BY and JOIN operations on 1M+ records.

-- ==============================================================================
-- 1. Indexes for docversion_source
-- ==============================================================================
CREATE INDEX IF NOT EXISTS idx_docversion_source_class_id ON docversion_source(object_class_id);
CREATE INDEX IF NOT EXISTS idx_docversion_source_created_date ON docversion_source(created_date);
CREATE INDEX IF NOT EXISTS idx_docversion_source_mime_type ON docversion_source(mime_type);
CREATE INDEX IF NOT EXISTS idx_docversion_source_content_size ON docversion_source(content_size);
CREATE INDEX IF NOT EXISTS idx_docversion_source_version_bucket ON docversion_source(major_version_number, minor_version_number);

-- ==============================================================================
-- 2. Indexes for docversion_staging
-- ==============================================================================
CREATE INDEX IF NOT EXISTS idx_docversion_staging_class_id ON docversion_staging(object_class_id);
CREATE INDEX IF NOT EXISTS idx_docversion_staging_created_date ON docversion_staging(created_date);
CREATE INDEX IF NOT EXISTS idx_docversion_staging_mime_type ON docversion_staging(mime_type);
CREATE INDEX IF NOT EXISTS idx_docversion_staging_content_size ON docversion_staging(content_size);
CREATE INDEX IF NOT EXISTS idx_docversion_staging_version_bucket ON docversion_staging(major_version_number, minor_version_number);

-- ==============================================================================
-- 3. Indexes for docversion_target
-- ==============================================================================
CREATE INDEX IF NOT EXISTS idx_docversion_target_class_id ON docversion_target(object_class_id);
CREATE INDEX IF NOT EXISTS idx_docversion_target_created_date ON docversion_target(created_date);
CREATE INDEX IF NOT EXISTS idx_docversion_target_mime_type ON docversion_target(mime_type);
CREATE INDEX IF NOT EXISTS idx_docversion_target_content_size ON docversion_target(content_size);
CREATE INDEX IF NOT EXISTS idx_docversion_target_version_bucket ON docversion_target(major_version_number, minor_version_number);

-- 6. Index for Fast Annotation Lookups (Annotation totals)
CREATE INDEX IF NOT EXISTS idx_annotation_annotated_id 
ON Annotation(annotated_id);

-- Note: Depending on your exact schema, make sure 'DocVersion' and 'Annotation'
-- match the exact casing of your tables (e.g., if lowercase, use 'docversion').

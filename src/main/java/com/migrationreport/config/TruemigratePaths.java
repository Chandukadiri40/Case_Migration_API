package com.migrationreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "truemigrate.paths")
public class TruemigratePaths {
    private String storageMountPath = "/home/skts/IS Migration";
    private String documentsPath = "/home/skts/IS Migration/IS Documents";
    private String caseMigrationDir = "/home/skts/IS Migration/Migration_Tools/CaseMigration";
    private String isMigrationDir = "/home/skts/IS Migration/Migration_Tools/TrueMigrator";
    private String caseImportJarPath = "/home/skts/IS Migration/Migration_Tools/CaseMigration/CaseImport/case-import-0.0.1.jar";
    private String filenetMigratorCmd = "dotnet TrueMigrator.dll";
    private String isExtractionScript = "python3 /opt/truemigrate/scripts/extract_is_docs.py";
    private String caseExtractionJarPath = "/home/skts/IS Migration/Migration_Tools/CaseMigration/CaseExtraction/case-extraction-0.0.1.jar";
    private String caseTransformationJarPath = "/home/skts/IS Migration/Migration_Tools/CaseMigration/CaseTransformation/case-transformation-0.0.1.jar";
    private String logDirectoryPath = "/var/log/truemigrate";
    
    // Offline configuration paths
    private String offlineIndexDbTable = "DOCTABA_STAGING_TABLE";
    private String offlineMkfExportPath = "/mnt/truemigrate/staging/mkf db";
    private String offlineMsarDatPath = "/mnt/truemigrate/staging/msar-dat";
    private String offlineFilePattern = "*.dat";
}

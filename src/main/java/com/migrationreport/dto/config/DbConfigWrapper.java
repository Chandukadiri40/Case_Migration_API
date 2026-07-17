package com.migrationreport.dto.config;

import java.util.List;
import java.util.Map;

public class DbConfigWrapper {
    
    private String activeDatabaseType;
    private List<Map<String, String>> databases;

    public String getActiveDatabaseType() {
        return activeDatabaseType;
    }

    public void setActiveDatabaseType(String activeDatabaseType) {
        this.activeDatabaseType = activeDatabaseType;
    }

    public List<Map<String, String>> getDatabases() {
        return databases;
    }

    public void setDatabases(List<Map<String, String>> databases) {
        this.databases = databases;
    }
}

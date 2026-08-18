package com.schwab.audit.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for database configuration and Flyway migrations.
 * 
 * Verifies that:
 * - Database is properly configured
 * - Flyway migrations have run successfully
 * - Required tables and indexes exist
 */
@SpringBootTest
@ActiveProfiles("test")
public class DatabaseConfigurationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testDatabaseConnectivity() {
        // Simple connectivity test
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertEquals(1, result);
    }

    @Test
    public void testUserTableExists() {
        // Verify users table was created by Flyway migration
        String query = "SELECT COUNT(*) FROM information_schema.tables " +
                      "WHERE upper(table_name) = 'USERS'";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class);
        
        assertEquals(1, count, "Users table should exist after Flyway migration");
    }

    @Test
    public void testAuditEventsTableExists() {
        // Verify audit_events table was created by Flyway migration
        String query = "SELECT COUNT(*) FROM information_schema.tables " +
                      "WHERE upper(table_name) = 'AUDIT_EVENTS'";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class);
        
        assertEquals(1, count, "Audit_events table should exist after Flyway migration");
    }

    @Test
    public void testAuditEventsTableColumns() {
        // Verify critical columns exist
        String query = "SELECT COUNT(*) FROM information_schema.columns " +
                      "WHERE upper(table_name) = 'AUDIT_EVENTS' AND upper(column_name) IN " +
                      "('ID', 'EVENT_TYPE', 'ACTOR_ID', 'RESOURCE_TYPE', 'RESOURCE_ID', " +
                      "'PAYLOAD', 'TIMESTAMP', 'CONTENT_HASH', 'PREVIOUS_HASH', 'CHAIN_POSITION')";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class);
        
        assertEquals(10, count, "All required audit_events columns should exist");
    }

    @Test
    public void testChainPositionIndexExists() {
        // Verify critical index for chain verification
        String query = "SELECT COUNT(*) FROM information_schema.indexes " +
                      "WHERE upper(table_name) = 'AUDIT_EVENTS' AND upper(index_name) = 'IDX_AUDIT_EVENTS_CHAIN_POSITION'";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class);
        
        assertTrue(count >= 1, "Chain position index should exist for performance");
    }

    @Test
    public void testActorIdIndexExists() {
        // Verify index for actor filtering
        String query = "SELECT COUNT(*) FROM information_schema.indexes " +
                      "WHERE upper(table_name) = 'AUDIT_EVENTS' AND upper(index_name) = 'IDX_AUDIT_EVENTS_ACTOR_ID'";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class);
        
        assertTrue(count >= 1, "Actor ID index should exist for query performance");
    }

    @Test
    public void testResourceTypeResourceIdIndexExists() {
        // Verify composite index for resource filtering
        String query = "SELECT COUNT(*) FROM information_schema.indexes " +
                      "WHERE upper(table_name) = 'AUDIT_EVENTS' AND upper(index_name) = 'IDX_AUDIT_EVENTS_RESOURCE_TYPE_ID'";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class);
        
        assertTrue(count >= 1, "Resource type/id composite index should exist");
    }

    @Test
    public void testContentHashUniqueConstraint() {
        // Verify content_hash has unique constraint
        String query = "SELECT COUNT(*) FROM information_schema.constraints " +
                      "WHERE upper(table_name) = 'AUDIT_EVENTS' AND constraint_type = 'UNIQUE' " +
                      "AND upper(constraint_name) LIKE '%CONTENT_HASH%'";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class);
        
        assertTrue(count >= 1, "Content hash should have unique constraint");
    }

    @Test
    public void testChainPositionUniqueConstraint() {
        // Verify chain_position has unique constraint
        String query = "SELECT COUNT(*) FROM information_schema.constraints " +
                      "WHERE upper(table_name) = 'AUDIT_EVENTS' AND constraint_type = 'UNIQUE' " +
                      "AND upper(constraint_name) LIKE '%CHAIN_POSITION%'";
        Integer count = jdbcTemplate.queryForObject(query, Integer.class);
        
        assertTrue(count >= 1, "Chain position should have unique constraint");
    }
}

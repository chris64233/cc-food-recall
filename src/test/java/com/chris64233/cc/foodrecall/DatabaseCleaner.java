package com.chris64233.cc.foodrecall;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 测试数据库清理：按外键依赖顺序清空所有表。
 */
@Component
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        jdbcTemplate.update("DELETE FROM report_items");
        jdbcTemplate.update("DELETE FROM downstream_reports");
        jdbcTemplate.update("DELETE FROM notification_items");
        jdbcTemplate.update("DELETE FROM recall_notifications");
        jdbcTemplate.update("DELETE FROM recall_closures");
        jdbcTemplate.update("DELETE FROM recall_impacts");
        jdbcTemplate.update("DELETE FROM lot_destinations");
        jdbcTemplate.update("DELETE FROM transformation_inputs");
        jdbcTemplate.update("DELETE FROM transformation_outputs");
        jdbcTemplate.update("DELETE FROM recall_events");
        jdbcTemplate.update("DELETE FROM transformations");
        jdbcTemplate.update("DELETE FROM downstream_holders");
        jdbcTemplate.update("DELETE FROM lots");
    }
}

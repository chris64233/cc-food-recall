package com.chris64233.cc.foodrecall;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

abstract class TestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM recall_impacts");
        jdbcTemplate.update("DELETE FROM transformation_inputs");
        jdbcTemplate.update("DELETE FROM transformation_outputs");
        jdbcTemplate.update("DELETE FROM recall_events");
        jdbcTemplate.update("DELETE FROM transformations");
        jdbcTemplate.update("DELETE FROM lots");
    }
}

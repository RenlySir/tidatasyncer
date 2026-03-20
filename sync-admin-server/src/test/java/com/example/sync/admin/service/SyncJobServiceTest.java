package com.example.sync.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sync.admin.dto.SyncJobDefinitionResponse;
import com.example.sync.admin.dto.SyncJobResponse;
import com.example.sync.admin.repository.SyncJobLogRepository;
import com.example.sync.admin.repository.SyncJobRepository;
import com.example.sync.admin.support.SyncJobFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({SyncJobService.class, SyncJobMapper.class, SyncJobServiceTest.TestConfig.class})
class SyncJobServiceTest {

    @Autowired
    private SyncJobService syncJobService;

    @Autowired
    private SyncJobRepository jobRepository;

    @Autowired
    private SyncJobLogRepository logRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    void createShouldPersistDefinitionAndExposeItThroughResponse() {
        SyncJobResponse response = syncJobService.create(SyncJobFixtures.upsertRequest());

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("mysql-to-tidb");
        assertThat(jobRepository.findAll()).hasSize(1);
        assertThat(logRepository.findAll()).isEmpty();
    }

    @Test
    void getDefinitionShouldRoundTripSerializedDefinition() {
        SyncJobResponse created = syncJobService.create(SyncJobFixtures.upsertRequest());

        SyncJobDefinitionResponse definitionResponse = syncJobService.getDefinition(created.id());

        assertThat(definitionResponse.jobId()).isEqualTo(created.id());
        assertThat(definitionResponse.jobName()).isEqualTo("mysql-to-tidb");
        assertThat(definitionResponse.definition().source().databaseType().name()).isEqualTo("MYSQL");
        assertThat(definitionResponse.definition().tableMappings()).singleElement()
                .extracting(mapping -> mapping.targetTable())
                .isEqualTo("orders");
    }
}

package com.edgareldy.springbatchtutorial.config;

import javax.sql.DataSource;
import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Pins the Spring Batch JobRepository and PlatformTransactionManager explicitly to
 * the same PostgreSQL DataSource that hosts the business schema, instead of relying
 * on spring-boot-starter-batch's implicit autoconfiguration, so both schemas
 * (business and BATCH_JOB_EXECUTION/BATCH_STEP_EXECUTION) always resolve to one
 * database.
 * <p>
 * Created by Edgar Muhamyangabo on 8/15/26
 * Author : Edgar Muhamyangabo
 * Date : 8/15/26
 * Project : spring-batch-tutorial
 */
@Configuration
public class BatchConfig extends JdbcDefaultBatchConfiguration {

    private final DataSource dataSource;

    public BatchConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected DataSource getDataSource() {
        return dataSource;
    }

    @Override
    protected PlatformTransactionManager getTransactionManager() {
        return new JdbcTransactionManager(dataSource);
    }
}

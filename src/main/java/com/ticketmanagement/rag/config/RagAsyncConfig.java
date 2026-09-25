package com.ticketmanagement.rag.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded executor for ticket re-ingestion (rag-ingestion.md §3), so a burst of ticket writes
 * cannot spawn unbounded threads (constitution Principle II) the way Spring's default
 * unbounded {@code SimpleAsyncTaskExecutor} would.
 */
@Configuration
@EnableAsync
public class RagAsyncConfig {

  public static final String INGESTION_EXECUTOR = "ragIngestionExecutor";

  @Bean(INGESTION_EXECUTOR)
  public Executor ragIngestionExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("rag-ingest-");
    executor.initialize();
    return executor;
  }
}

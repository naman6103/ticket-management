package com.ticketmanagement.rag.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import java.io.IOException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Extends the {@code ticket-knowledge} index mapping beyond the {@code embedding} field that
 * Spring AI's {@code ElasticsearchVectorStore} creates on its own. Ticket metadata (status,
 * priority, assignee, category, ticketId) is mapped as {@code keyword} — not left to
 * Elasticsearch's default dynamic string mapping (text + keyword multi-field) — per
 * architecture.md §6.1.
 */
@Configuration
public class TicketKnowledgeIndexInitializer {

  private static final String INDEX_NAME = "ticket-knowledge";

  /**
   * Runs once after the application context has fully started, by which point the {@code
   * vectorStore} bean (and therefore the index and its {@code embedding} field mapping) has
   * already been created — {@link ApplicationRunner} beans run strictly after context refresh
   * completes, so no explicit ordering dependency is needed.
   *
   * <p>Only registered when {@code spring.ai.vectorstore.elasticsearch.initialize-schema=true}
   * (enabled in the {@code docker} and {@code dev} profiles, where a real Elasticsearch is
   * expected) — matching the same condition Spring AI uses to decide whether to create the index
   * in the first place, so this runner never tries to reach an Elasticsearch that isn't there
   * (e.g. the {@code test} profile, per {@code rules/testing.md}).
   *
   * @param client Elasticsearch client used to extend the index mapping
   * @return the runner Spring Boot executes after context startup
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "spring.ai.vectorstore.elasticsearch",
      name = "initialize-schema",
      havingValue = "true")
  public ApplicationRunner ticketKnowledgeIndexMappingRunner(ElasticsearchClient client) {
    return (ApplicationArguments args) -> extendMappingIfIndexExists(client);
  }

  private void extendMappingIfIndexExists(ElasticsearchClient client) throws IOException {
    boolean indexExists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX_NAME))).value();
    if (!indexExists) {
      return;
    }
    client
        .indices()
        .putMapping(
            m ->
                m.index(INDEX_NAME)
                    .properties("content", p -> p.text(t -> t))
                    .properties(
                        "metadata",
                        p ->
                            p.object(
                                o ->
                                    o.properties("ticketId", pp -> pp.keyword(k -> k))
                                        .properties("status", pp -> pp.keyword(k -> k))
                                        .properties("priority", pp -> pp.keyword(k -> k))
                                        .properties("assignee", pp -> pp.keyword(k -> k))
                                        .properties("category", pp -> pp.keyword(k -> k))
                                        .properties("updatedAt", pp -> pp.date(d -> d)))));
  }
}

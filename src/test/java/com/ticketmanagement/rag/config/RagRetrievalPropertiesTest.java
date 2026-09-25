package com.ticketmanagement.rag.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RagRetrievalPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              org.springframework.boot.autoconfigure.AutoConfigurations.of(
                  PropertyPlaceholderAutoConfiguration.class))
          .withUserConfiguration(TestConfig.class);

  @Test
  void bindsDefaultsWhenNoPropertiesSet() {
    contextRunner.run(
        context -> {
          RagRetrievalProperties properties = context.getBean(RagRetrievalProperties.class);
          assertThat(properties.getTopK()).isEqualTo(5);
          assertThat(properties.getSimilarityThreshold()).isEqualTo(0.65);
        });
  }

  @Test
  void bindsOverriddenValuesFromConfiguration() {
    contextRunner
        .withPropertyValues("ai.rag.retrieval.top-k=10", "ai.rag.retrieval.similarity-threshold=0.8")
        .run(
            context -> {
              RagRetrievalProperties properties = context.getBean(RagRetrievalProperties.class);
              assertThat(properties.getTopK()).isEqualTo(10);
              assertThat(properties.getSimilarityThreshold()).isEqualTo(0.8);
            });
  }

  @EnableConfigurationProperties(RagRetrievalProperties.class)
  static class TestConfig {}
}

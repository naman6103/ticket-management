package com.ticketmanagement.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds retrieval tuning for the ticket knowledge Q&amp;A feature. Values MUST come from
 * configuration (never hardcoded in application logic) per constitution Principle V and
 * {@code rules/rag-vector-store.md}.
 */
@Component
@ConfigurationProperties(prefix = "ai.rag.retrieval")
public class RagRetrievalProperties {

  private int topK = 5;
  private double similarityThreshold = 0.65;

  public int getTopK() {
    return topK;
  }

  public void setTopK(int topK) {
    this.topK = topK;
  }

  public double getSimilarityThreshold() {
    return similarityThreshold;
  }

  public void setSimilarityThreshold(double similarityThreshold) {
    this.similarityThreshold = similarityThreshold;
  }
}

package com.ticketmanagement.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.pagination")
public class PaginationProperties {

  private int defaultSize = 20;
  private int maxSize = 100;

  public int getDefaultSize() {
    return defaultSize;
  }

  public void setDefaultSize(int defaultSize) {
    this.defaultSize = defaultSize;
  }

  public int getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }
}

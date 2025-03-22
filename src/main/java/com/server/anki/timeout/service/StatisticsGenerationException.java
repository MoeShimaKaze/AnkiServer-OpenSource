package com.server.anki.timeout.service;

public class StatisticsGenerationException extends RuntimeException {
  public StatisticsGenerationException(String message) {
    super(message);
  }

  public StatisticsGenerationException(String message, Throwable cause) {
    super(message, cause);
  }
}

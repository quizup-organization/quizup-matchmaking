package io.github.quizup.matchmaking.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active le scheduler Spring (balayeur de purge des défis terminaux).
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}

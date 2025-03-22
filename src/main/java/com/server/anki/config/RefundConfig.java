package com.server.anki.config;

import com.server.anki.wallet.RefundMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "refund")
public class RefundConfig {
    private RefundMode mode = RefundMode.DELIVERER_ONLY;
    private int platformPercentage = 0;

    // Getters and setters

}
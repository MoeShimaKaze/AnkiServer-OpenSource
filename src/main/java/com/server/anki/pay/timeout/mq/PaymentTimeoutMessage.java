package com.server.anki.pay.timeout.mq;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class PaymentTimeoutMessage {

    private String orderNumber;

    private LocalDateTime timeoutTime;

}

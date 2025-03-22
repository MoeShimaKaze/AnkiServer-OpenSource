package com.server.anki.timeout.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TimeoutEvent extends ApplicationEvent {

    private final String timeoutType;
    private final Long userId;

    public TimeoutEvent(Object source, String timeoutType, Long userId) {
        super(source);
        this.timeoutType = timeoutType;
        this.userId = userId;
    }

}
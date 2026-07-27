package com.example.knowyourcolleagues.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class TransactionRecordedEvent {

    private UUID eventId;
    private Long transactionId;
    private Instant occurredAt;
}

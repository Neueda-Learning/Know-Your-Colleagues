package com.example.knowyourcolleagues.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.knowyourcolleagues.bizexception.transaction.InvalidTransactionRequestException;
import com.example.knowyourcolleagues.bizexception.transaction.TransactionNotFoundException;
import com.example.knowyourcolleagues.bizexception.transaction.TransactionReferenceGenerationException;
import com.example.knowyourcolleagues.dto.CreateTransactionRequest;
import com.example.knowyourcolleagues.dto.TransactionPageResponse;
import com.example.knowyourcolleagues.dto.TransactionQueryRequest;
import com.example.knowyourcolleagues.dto.TransactionResponse;
import com.example.knowyourcolleagues.entity.Transaction;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import com.example.knowyourcolleagues.mapper.TransactionMapper;
import com.example.knowyourcolleagues.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import com.example.knowyourcolleagues.dto.TransactionRecordedEvent;

/**
 * 交易业务服务实现。
 */
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_REFERENCE_GENERATION_ATTEMPTS = 3;
    private static final int RANDOM_REFERENCE_LENGTH = 20;
    private static final DateTimeFormatter REFERENCE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final TransactionMapper transactionMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock = Clock.systemUTC();

    @Override
    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        validateCreateRequest(request);

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Transaction transaction = new Transaction();
        transaction.setAccountId(request.getAccountId().trim());
        transaction.setPayeeId(request.getPayeeId().trim());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency().trim().toUpperCase(Locale.ROOT));
        transaction.setTransactionType(request.getTransactionType());
        transaction.setStatus(request.getStatus() == null
                ? TransactionStatus.COMPLETED
                : request.getStatus());
        transaction.setDescription(trimToNull(request.getDescription()));
        transaction.setTransactionTime(request.getTransactionTime() == null
                ? now
                : request.getTransactionTime());
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);

        insertWithGeneratedReference(transaction, now);

        TransactionRecordedEvent event = new TransactionRecordedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTransactionId(transaction.getId());
        event.setOccurredAt(clock.instant());
        applicationEventPublisher.publishEvent(event);

        return toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionPageResponse getTransactions(TransactionQueryRequest query) {
        TransactionQueryRequest effectiveQuery = query == null
                ? new TransactionQueryRequest()
                : query;
        validateQuery(effectiveQuery);

        LambdaQueryWrapper<Transaction> wrapper =
                new LambdaQueryWrapper<Transaction>()
                        .eq(hasText(effectiveQuery.getAccountId()),
                                Transaction::getAccountId,
                                trimToNull(effectiveQuery.getAccountId()))
                        .eq(hasText(effectiveQuery.getPayeeId()),
                                Transaction::getPayeeId,
                                trimToNull(effectiveQuery.getPayeeId()))
                        .ge(effectiveQuery.getMinAmount() != null,
                                Transaction::getAmount,
                                effectiveQuery.getMinAmount())
                        .le(effectiveQuery.getMaxAmount() != null,
                                Transaction::getAmount,
                                effectiveQuery.getMaxAmount())
                        .ge(effectiveQuery.getTransactionTimeStart() != null,
                                Transaction::getTransactionTime,
                                effectiveQuery.getTransactionTimeStart())
                        .le(effectiveQuery.getTransactionTimeEnd() != null,
                                Transaction::getTransactionTime,
                                effectiveQuery.getTransactionTimeEnd())
                        .eq(effectiveQuery.getStatus() != null,
                                Transaction::getStatus,
                                effectiveQuery.getStatus())
                        .orderByDesc(Transaction::getTransactionTime)
                        .orderByDesc(Transaction::getId);

        Page<Transaction> transactionPage = transactionMapper.selectPage(
                new Page<>(effectiveQuery.getPage() + 1, effectiveQuery.getSize()),
                wrapper
        );

        TransactionPageResponse response = new TransactionPageResponse();
        response.setContent(transactionPage.getRecords().stream()
                .map(this::toResponse)
                .toList());
        response.setPage(effectiveQuery.getPage());
        response.setSize(effectiveQuery.getSize());
        response.setTotalElements(transactionPage.getTotal());
        response.setTotalPages(transactionPage.getPages());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long transactionId) {
        if (transactionId == null || transactionId <= 0) {
            throw new InvalidTransactionRequestException(
                    "transactionId must be positive"
            );
        }

        Transaction transaction = transactionMapper.selectById(transactionId);
        if (transaction == null) {
            throw new TransactionNotFoundException(
                    "Transaction not found: " + transactionId
            );
        }
        return toResponse(transaction);
    }

    private void validateCreateRequest(CreateTransactionRequest request) {
        if (request == null) {
            throw new InvalidTransactionRequestException(
                    "transaction request is required"
            );
        }
        if (!hasText(request.getAccountId())) {
            throw new InvalidTransactionRequestException("accountId is required");
        }
        if (!hasText(request.getPayeeId())) {
            throw new InvalidTransactionRequestException("payeeId is required");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new InvalidTransactionRequestException(
                    "amount must be greater than 0"
            );
        }
        if (!hasText(request.getCurrency())
                || !request.getCurrency().trim().matches("[A-Za-z]{3}")) {
            throw new InvalidTransactionRequestException(
                    "currency must be a three-letter code"
            );
        }
        if (request.getTransactionType() == null) {
            throw new InvalidTransactionRequestException(
                    "transactionType is required"
            );
        }
    }

    private void validateQuery(TransactionQueryRequest query) {
        if (query.getPage() < 0) {
            throw new InvalidTransactionRequestException(
                    "page must not be negative"
            );
        }
        if (query.getSize() <= 0 || query.getSize() > MAX_PAGE_SIZE) {
            throw new InvalidTransactionRequestException(
                    "size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
        if (query.getMinAmount() != null
                && query.getMinAmount().signum() < 0) {
            throw new InvalidTransactionRequestException(
                    "minAmount must not be negative"
            );
        }
        if (query.getMaxAmount() != null
                && query.getMaxAmount().signum() < 0) {
            throw new InvalidTransactionRequestException(
                    "maxAmount must not be negative"
            );
        }
        if (query.getMinAmount() != null
                && query.getMaxAmount() != null
                && query.getMinAmount().compareTo(query.getMaxAmount()) > 0) {
            throw new InvalidTransactionRequestException(
                    "minAmount must not be greater than maxAmount"
            );
        }
        if (query.getTransactionTimeStart() != null
                && query.getTransactionTimeEnd() != null
                && query.getTransactionTimeStart()
                        .isAfter(query.getTransactionTimeEnd())) {
            throw new InvalidTransactionRequestException(
                    "transactionTimeStart must not be after transactionTimeEnd"
            );
        }
    }

    private void insertWithGeneratedReference(
            Transaction transaction,
            LocalDateTime transactionCreationTime
    ) {
        DuplicateKeyException lastException = null;
        for (int attempt = 0;
             attempt < MAX_REFERENCE_GENERATION_ATTEMPTS;
             attempt++) {
            transaction.setId(null);
            transaction.setTransactionRef(
                    generateTransactionReference(transactionCreationTime)
            );
            try {
                transactionMapper.insert(transaction);
                return;
            } catch (DuplicateKeyException exception) {
                lastException = exception;
            }
        }

        throw new TransactionReferenceGenerationException(
                "Unable to generate a unique transaction reference after "
                        + MAX_REFERENCE_GENERATION_ATTEMPTS + " attempts",
                lastException
        );
    }

    private String generateTransactionReference(LocalDateTime creationTime) {
        String timePart = creationTime.format(REFERENCE_TIME_FORMATTER);
        String randomPart = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, RANDOM_REFERENCE_LENGTH)
                .toUpperCase(Locale.ROOT);
        return "TXN-" + timePart + "-" + randomPart;
    }

    private TransactionResponse toResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setTransactionRef(transaction.getTransactionRef());
        response.setAccountId(transaction.getAccountId());
        response.setPayeeId(transaction.getPayeeId());
        response.setAmount(transaction.getAmount());
        response.setCurrency(transaction.getCurrency());
        response.setTransactionType(transaction.getTransactionType());
        response.setStatus(transaction.getStatus());
        response.setDescription(transaction.getDescription());
        response.setTransactionTime(transaction.getTransactionTime());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setUpdatedAt(transaction.getUpdatedAt());
        return response;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}

package com.example.knowyourcolleagues.transaction.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.knowyourcolleagues.bizexception.transaction.InvalidTransactionRequestException;
import com.example.knowyourcolleagues.bizexception.transaction.TransactionNotFoundException;
import com.example.knowyourcolleagues.bizexception.transaction.TransactionReferenceGenerationException;
import com.example.knowyourcolleagues.dto.CreateTransactionRequest;
import com.example.knowyourcolleagues.dto.TransactionPageResponse;
import com.example.knowyourcolleagues.dto.TransactionQueryRequest;
import com.example.knowyourcolleagues.dto.TransactionRecordedEvent;
import com.example.knowyourcolleagues.dto.TransactionResponse;
import com.example.knowyourcolleagues.entity.Transaction;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import com.example.knowyourcolleagues.enums.TransactionType;
import com.example.knowyourcolleagues.mapper.TransactionMapper;
import com.example.knowyourcolleagues.service.impl.TransactionServiceImpl;
import com.example.knowyourcolleagues.websocket.RealtimeNotificationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.MockitoAnnotations;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionServiceImplTest {

    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private RealtimeNotificationPublisher notificationPublisher;

    private TransactionServiceImpl transactionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(
                        new MybatisConfiguration(),
                        "transaction-test"
                ),
                Transaction.class
        );
        transactionService = new TransactionServiceImpl(
                transactionMapper,
                applicationEventPublisher,
                notificationPublisher
        );
    }

    @Test
    void shouldCreateTransactionWithNormalizedDefaults() {
        when(transactionMapper.insert(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction transaction = invocation.getArgument(0);
                    transaction.setId(1001L);
                    return 1;
                });

        CreateTransactionRequest request = validCreateRequest();
        request.setCurrency("usd");
        request.setTransactionTime(null);

        TransactionResponse response =
                transactionService.createTransaction(request);

        ArgumentCaptor<Transaction> transactionCaptor =
                ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper).insert(transactionCaptor.capture());

        Transaction inserted = transactionCaptor.getValue();
        assertThat(inserted.getTransactionRef())
                .matches("TXN-\\d{17}-[A-F0-9]{20}");
        assertThat(inserted.getCurrency()).isEqualTo("USD");
        assertThat(inserted.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(inserted.getTransactionTime()).isNotNull();
        assertThat(inserted.getCreatedAt()).isNotNull();
        assertThat(response.getId()).isEqualTo(1001L);

        ArgumentCaptor<Object> eventCaptor =
                ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOf(TransactionRecordedEvent.class);

        TransactionRecordedEvent event =
                (TransactionRecordedEvent) eventCaptor.getValue();
        assertThat(event.getTransactionId()).isEqualTo(1001L);
        assertThat(event.getTransactions()).singleElement()
                .extracting(TransactionResponse::getId)
                .isEqualTo(1001L);
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    void shouldRetryWhenGeneratedTransactionReferenceCollides() {
        when(transactionMapper.insert(any(Transaction.class)))
                .thenThrow(new DuplicateKeyException("collision"))
                .thenThrow(new DuplicateKeyException("collision"))
                .thenAnswer(invocation -> {
                    Transaction transaction = invocation.getArgument(0);
                    transaction.setId(1001L);
                    return 1;
                });

        TransactionResponse response = transactionService.createTransaction(
                validCreateRequest()
        );

        verify(transactionMapper, times(3)).insert(any(Transaction.class));
        assertThat(response.getId()).isEqualTo(1001L);
        assertThat(response.getTransactionRef())
                .matches("TXN-\\d{17}-[A-F0-9]{20}");
    }

    @Test
    void shouldFailAfterThreeTransactionReferenceCollisions() {
        when(transactionMapper.insert(any(Transaction.class)))
                .thenThrow(new DuplicateKeyException("collision"));

        assertThatThrownBy(() -> transactionService.createTransaction(
                validCreateRequest()
        )).isInstanceOf(TransactionReferenceGenerationException.class)
                .hasMessageContaining("3 attempts");

        verify(transactionMapper, times(3)).insert(any(Transaction.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldQueryTransactionsWithFiltersAndDatabasePagination() {
        when(transactionMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    Page<Transaction> requestedPage = invocation.getArgument(0);
                    requestedPage.setRecords(List.of(transaction(1001L)));
                    requestedPage.setTotal(12L);
                    return requestedPage;
                });

        TransactionQueryRequest query = new TransactionQueryRequest();
        query.setAccountId("ACC-001");
        query.setPayeeId("PAYEE-001");
        query.setMinAmount(new BigDecimal("100.00"));
        query.setMaxAmount(new BigDecimal("20000.00"));
        query.setTransactionTimeStart(
                LocalDateTime.parse("2026-07-01T00:00:00")
        );
        query.setTransactionTimeEnd(
                LocalDateTime.parse("2026-07-31T23:59:59")
        );
        query.setStatus(TransactionStatus.NORMAL);
        query.setPage(2);
        query.setSize(5);

        TransactionPageResponse response =
                transactionService.getTransactions(query);

        ArgumentCaptor<Page<Transaction>> pageCaptor =
                ArgumentCaptor.forClass(Page.class);
        verify(transactionMapper).selectPage(
                pageCaptor.capture(),
                any(Wrapper.class)
        );

        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(3L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(5L);
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPage()).isEqualTo(2L);
        assertThat(response.getTotalElements()).isEqualTo(12L);
        assertThat(response.getTotalPages()).isEqualTo(3L);
    }

    @Test
    void shouldRejectReversedAmountRange() {
        TransactionQueryRequest query = new TransactionQueryRequest();
        query.setMinAmount(new BigDecimal("200.00"));
        query.setMaxAmount(new BigDecimal("100.00"));

        assertThatThrownBy(() -> transactionService.getTransactions(query))
                .isInstanceOf(InvalidTransactionRequestException.class)
                .hasMessageContaining("minAmount");

        verify(transactionMapper, never())
                .selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    void shouldThrowWhenTransactionDoesNotExist() {
        when(transactionMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> transactionService.getTransaction(999L))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void shouldUpdatePendingTransactionToEvaluationStatus() {
        when(transactionMapper.update(any(), any(Wrapper.class)))
                .thenReturn(1);
        Transaction updated = transaction(1001L);
        updated.setStatus(TransactionStatus.ABNORMAL);
        when(transactionMapper.selectById(1001L)).thenReturn(updated);

        transactionService.updateStatusAfterEvaluation(
                1001L,
                TransactionStatus.ABNORMAL
        );

        verify(transactionMapper).update(any(), any(Wrapper.class));
        verify(transactionMapper).selectById(1001L);
        verify(notificationPublisher)
                .publishTransactionStatusChanged(any(TransactionResponse.class));
    }

    @Test
    void shouldAcceptDuplicateEvaluationResultIdempotently() {
        when(transactionMapper.update(any(), any(Wrapper.class)))
                .thenReturn(0);
        Transaction existing = transaction(1001L);
        existing.setStatus(TransactionStatus.NORMAL);
        when(transactionMapper.selectById(1001L)).thenReturn(existing);

        transactionService.updateStatusAfterEvaluation(
                1001L,
                TransactionStatus.NORMAL
        );

        verify(transactionMapper).selectById(1001L);
        verify(notificationPublisher, never())
                .publishTransactionStatusChanged(any(TransactionResponse.class));
    }

    @Test
    void shouldRejectPendingAsFinalEvaluationStatus() {
        assertThatThrownBy(() ->
                transactionService.updateStatusAfterEvaluation(
                        1001L,
                        TransactionStatus.PENDING
                )
        ).isInstanceOf(InvalidTransactionRequestException.class)
                .hasMessageContaining("NORMAL or ABNORMAL");

        verify(transactionMapper, never()).update(any(), any(Wrapper.class));
    }

    private CreateTransactionRequest validCreateRequest() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAccountId("ACC-001");
        request.setPayeeId("PAYEE-001");
        request.setAmount(new BigDecimal("15000.00"));
        request.setCurrency("USD");
        request.setTransactionType(TransactionType.DEBIT);
        request.setDescription("Supplier payment");
        request.setTransactionTime(
                LocalDateTime.parse("2026-07-27T14:30:00")
        );
        return request;
    }

    private Transaction transaction(Long id) {
        LocalDateTime now = LocalDateTime.parse("2026-07-27T14:30:00");
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setTransactionRef(
                "TXN-20260727143000000-A1B2C3D4E5F60718293A"
        );
        transaction.setAccountId("ACC-001");
        transaction.setPayeeId("PAYEE-001");
        transaction.setAmount(new BigDecimal("15000.00"));
        transaction.setCurrency("USD");
        transaction.setTransactionType(TransactionType.DEBIT);
        transaction.setStatus(TransactionStatus.NORMAL);
        transaction.setDescription("Supplier payment");
        transaction.setTransactionTime(now);
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);
        return transaction;
    }
}

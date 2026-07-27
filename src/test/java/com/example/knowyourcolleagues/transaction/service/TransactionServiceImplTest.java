package com.example.knowyourcolleagues.transaction.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.knowyourcolleagues.bizexception.transaction.DuplicateTransactionReferenceException;
import com.example.knowyourcolleagues.bizexception.transaction.InvalidTransactionRequestException;
import com.example.knowyourcolleagues.bizexception.transaction.TransactionNotFoundException;
import com.example.knowyourcolleagues.dto.CreateTransactionRequest;
import com.example.knowyourcolleagues.dto.TransactionPageResponse;
import com.example.knowyourcolleagues.dto.TransactionQueryRequest;
import com.example.knowyourcolleagues.dto.TransactionResponse;
import com.example.knowyourcolleagues.entity.Transaction;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import com.example.knowyourcolleagues.enums.TransactionType;
import com.example.knowyourcolleagues.mapper.TransactionMapper;
import com.example.knowyourcolleagues.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionServiceImplTest {

    @Mock
    private TransactionMapper transactionMapper;

    private TransactionServiceImpl transactionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transactionService = new TransactionServiceImpl(transactionMapper);
    }

    @Test
    void shouldCreateTransactionWithNormalizedDefaults() {
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(transactionMapper.insert(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction transaction = invocation.getArgument(0);
                    transaction.setId(1001L);
                    return 1;
                });

        CreateTransactionRequest request = validCreateRequest();
        request.setCurrency("usd");
        request.setStatus(null);
        request.setTransactionTime(null);

        TransactionResponse response =
                transactionService.createTransaction(request);

        ArgumentCaptor<Transaction> transactionCaptor =
                ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper).insert(transactionCaptor.capture());

        Transaction inserted = transactionCaptor.getValue();
        assertThat(inserted.getCurrency()).isEqualTo("USD");
        assertThat(inserted.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(inserted.getTransactionTime()).isNotNull();
        assertThat(inserted.getCreatedAt()).isNotNull();
        assertThat(response.getId()).isEqualTo(1001L);
    }

    @Test
    void shouldRejectDuplicateTransactionReference() {
        when(transactionMapper.selectOne(any(Wrapper.class)))
                .thenReturn(transaction(1001L));

        assertThatThrownBy(() -> transactionService.createTransaction(
                validCreateRequest()
        )).isInstanceOf(DuplicateTransactionReferenceException.class)
                .hasMessageContaining("TXN-001");

        verify(transactionMapper, never()).insert(any(Transaction.class));
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
        query.setStatus(TransactionStatus.COMPLETED);
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

    private CreateTransactionRequest validCreateRequest() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setTransactionRef("TXN-001");
        request.setAccountId("ACC-001");
        request.setPayeeId("PAYEE-001");
        request.setAmount(new BigDecimal("15000.00"));
        request.setCurrency("USD");
        request.setTransactionType(TransactionType.DEBIT);
        request.setStatus(TransactionStatus.COMPLETED);
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
        transaction.setTransactionRef("TXN-001");
        transaction.setAccountId("ACC-001");
        transaction.setPayeeId("PAYEE-001");
        transaction.setAmount(new BigDecimal("15000.00"));
        transaction.setCurrency("USD");
        transaction.setTransactionType(TransactionType.DEBIT);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setDescription("Supplier payment");
        transaction.setTransactionTime(now);
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);
        return transaction;
    }
}

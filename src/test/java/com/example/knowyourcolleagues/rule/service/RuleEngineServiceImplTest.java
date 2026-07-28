package com.example.knowyourcolleagues.rule.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.example.knowyourcolleagues.dto.AlertResponse;
import com.example.knowyourcolleagues.dto.RuleEngineResult;
import com.example.knowyourcolleagues.dto.RuleEvaluationResult;
import com.example.knowyourcolleagues.entity.Rule;
import com.example.knowyourcolleagues.entity.Transaction;
import com.example.knowyourcolleagues.enums.RuleType;
import com.example.knowyourcolleagues.enums.Severity;
import com.example.knowyourcolleagues.enums.TransactionEvaluationStatus;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import com.example.knowyourcolleagues.mapper.RuleMapper;
import com.example.knowyourcolleagues.mapper.TransactionMapper;
import com.example.knowyourcolleagues.rule.RuleStrategyRegistry;
import com.example.knowyourcolleagues.service.AlertService;
import com.example.knowyourcolleagues.service.RuleEvaluationStrategy;
import com.example.knowyourcolleagues.service.impl.RuleEngineServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuleEngineServiceImplTest {

    @Mock
    private RuleMapper ruleMapper;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private RuleStrategyRegistry strategyRegistry;
    @Mock
    private AlertService alertService;
    @Mock
    private RuleEvaluationStrategy strategy;

    private RuleEngineServiceImpl ruleEngineService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(
                        new MybatisConfiguration(),
                        "rule-engine-test"
                ),
                Rule.class
        );
        ruleEngineService = new RuleEngineServiceImpl(
                ruleMapper,
                transactionMapper,
                strategyRegistry,
                alertService
        );
    }

    @Test
    void shouldReturnFlaggedSummaryWithCreatedAlertIds() {
        Transaction transaction = transaction(TransactionStatus.PENDING);
        Rule matchedRule = rule(5L, RuleType.AMOUNT_THRESHOLD);
        Rule clearRule = rule(8L, RuleType.NEW_PAYEE);
        when(transactionMapper.selectById(1001L)).thenReturn(transaction);
        when(ruleMapper.selectList(any())).thenReturn(
                List.of(matchedRule, clearRule)
        );
        when(strategyRegistry.get(any())).thenReturn(strategy);
        when(strategy.evaluate(matchedRule, transaction)).thenReturn(
                RuleEvaluationResult.matched(
                        "High amount",
                        "Threshold exceeded",
                        List.of(1001L)
                )
        );
        when(strategy.evaluate(clearRule, transaction)).thenReturn(
                RuleEvaluationResult.notMatched()
        );
        AlertResponse alert = new AlertResponse();
        alert.setId(20L);
        when(alertService.createAlert(any())).thenReturn(alert);

        Optional<RuleEngineResult> result =
                ruleEngineService.evaluateTransaction(1001L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getEvaluationStatus())
                .isEqualTo(TransactionEvaluationStatus.FLAGGED);
        assertThat(result.orElseThrow().getMatchedRuleIds())
                .containsExactly(5L);
        assertThat(result.orElseThrow().getAlertIds())
                .containsExactly(20L);
        verify(alertService).createAlert(any());
    }

    @Test
    void shouldReturnClearedSummaryWhenNoRuleMatches() {
        Transaction transaction = transaction(TransactionStatus.PENDING);
        when(transactionMapper.selectById(1001L)).thenReturn(transaction);
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        Optional<RuleEngineResult> result =
                ruleEngineService.evaluateTransaction(1001L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getEvaluationStatus())
                .isEqualTo(TransactionEvaluationStatus.CLEARED);
        assertThat(result.orElseThrow().getMatchedRuleIds()).isEmpty();
        assertThat(result.orElseThrow().getAlertIds()).isEmpty();
        verifyNoInteractions(alertService);
    }

    @Test
    void shouldSkipTransactionThatIsAlreadyEvaluated() {
        when(transactionMapper.selectById(1001L)).thenReturn(
                transaction(TransactionStatus.NORMAL)
        );

        Optional<RuleEngineResult> result =
                ruleEngineService.evaluateTransaction(1001L);

        assertThat(result).isEmpty();
        verify(ruleMapper, never()).selectList(any());
        verifyNoInteractions(alertService);
    }

    private Transaction transaction(TransactionStatus status) {
        Transaction transaction = new Transaction();
        transaction.setId(1001L);
        transaction.setAccountId("ACC-001");
        transaction.setStatus(status);
        return transaction;
    }

    private Rule rule(Long id, RuleType type) {
        Rule rule = new Rule();
        rule.setId(id);
        rule.setName("Rule " + id);
        rule.setType(type);
        rule.setSeverity(Severity.HIGH);
        rule.setEnabled(true);
        return rule;
    }
}

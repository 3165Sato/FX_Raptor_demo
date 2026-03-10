package com.example.fxraptor.service;

import com.example.fxraptor.domain.CoverExecutionLog;
import com.example.fxraptor.domain.CoverExecutionResult;
import com.example.fxraptor.domain.CoverMode;
import com.example.fxraptor.domain.CoverOrder;
import com.example.fxraptor.domain.CoverOrderStatus;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.order.model.CoverOrderCommand;
import com.example.fxraptor.order.service.CoverExecutionService;
import com.example.fxraptor.repository.CoverExecutionLogRepository;
import com.example.fxraptor.repository.CoverOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoverExecutionServiceTest {

    @Mock
    private CoverOrderRepository coverOrderRepository;

    @Mock
    private CoverExecutionLogRepository coverExecutionLogRepository;

    @Test
    void savesCoverOrderAndExecutionLog() {
        CoverExecutionService service = new CoverExecutionService(coverOrderRepository, coverExecutionLogRepository);
        CoverOrderCommand command = new CoverOrderCommand(
                11L,
                12L,
                "USD/JPY",
                OrderSide.SELL,
                new BigDecimal("1.50000000"),
                new BigDecimal("150.20000000"),
                CoverMode.FULL
        );

        when(coverOrderRepository.save(any(CoverOrder.class))).thenAnswer(invocation -> {
            CoverOrder order = invocation.getArgument(0);
            order.setCoverOrderId(99L);
            return order;
        });

        CoverOrder saved = service.execute(command);

        assertThat(saved.getCoverOrderId()).isEqualTo(99L);
        assertThat(saved.getStatus()).isEqualTo(CoverOrderStatus.FILLED);

        ArgumentCaptor<CoverExecutionLog> logCaptor = ArgumentCaptor.forClass(CoverExecutionLog.class);
        verify(coverExecutionLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getCoverOrderId()).isEqualTo(99L);
        assertThat(logCaptor.getValue().getExecutedPrice()).isEqualByComparingTo("150.20000000");
        assertThat(logCaptor.getValue().getExecutedQuantity()).isEqualByComparingTo("1.50000000");
        assertThat(logCaptor.getValue().getExecutionResult()).isEqualTo(CoverExecutionResult.SUCCESS);
    }
}

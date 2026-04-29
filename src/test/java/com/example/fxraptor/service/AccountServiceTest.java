package com.example.fxraptor.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.order.service.AccountService;
import com.example.fxraptor.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Test
    void savesUpdatedBalanceWhenRealizedPnlOccurs() {
        AccountService service = new AccountService(accountRepository);
        Account account = new Account();
        account.setUserId("user-1");
        account.setBalance(new BigDecimal("1000000.0000"));
        Position closedPosition = new Position();
        closedPosition.setSide(OrderSide.BUY);
        closedPosition.setAvgPrice(new BigDecimal("150.12000000"));

        when(accountRepository.findByUserId("user-1")).thenReturn(Optional.of(account));

        service.applyRealizedPnl(
                "user-1",
                closedPosition,
                new BigDecimal("150.50000000"),
                new BigDecimal("4000.00000000")
        );

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getBalance()).isEqualByComparingTo("1001520.00000000");
    }
}

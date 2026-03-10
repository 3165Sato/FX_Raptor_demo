package com.example.fxraptor.backoffice.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AccountQueryService {

    private final AccountRepository accountRepository;

    public AccountQueryService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Optional<Account> findById(Long accountId) {
        return accountRepository.findById(accountId);
    }
}

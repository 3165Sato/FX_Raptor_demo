package com.example.fxraptor.repository;

import com.example.fxraptor.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    /**
     * 実現損益反映や証拠金計算でユーザーの口座を引くための検索。
     */
    Optional<Account> findByUserId(String userId);
}

package com.paynest.users.repository;

import com.paynest.users.entity.AuthChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuthChallengeRepository extends JpaRepository<AuthChallenge, UUID> {

    List<AuthChallenge> findAllByAccountId(String accountId);
}


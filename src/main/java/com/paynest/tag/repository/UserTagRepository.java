package com.paynest.tag.repository;

import com.paynest.tag.entity.UserTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTagRepository extends JpaRepository<UserTag, Long> {

    Optional<UserTag> findByAccountIdAndTagId(String accountId, Long tagId);

    List<UserTag> findByAccountId(String accountId);

    List<UserTag> findByTagId(Long tagId);

    void deleteByTagId(Long tagId);
}

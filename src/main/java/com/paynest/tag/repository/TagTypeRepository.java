package com.paynest.tag.repository;

import com.paynest.tag.entity.TagType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TagTypeRepository extends JpaRepository<TagType, Long> {

    Optional<TagType> findByTypeCode(String typeCode);
}

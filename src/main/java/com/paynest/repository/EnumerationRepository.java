package com.paynest.repository;

import com.paynest.entity.Enumeration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnumerationRepository extends JpaRepository<Enumeration, Long> {

    @Query(value = "SHOW search_path", nativeQuery = true)
    String getSearchPath();

    List<Enumeration> findByEnumTypeAndIsActive(String type, boolean isActive);

    boolean existsByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue(
            String enumType,
            String enumCode
    );
}

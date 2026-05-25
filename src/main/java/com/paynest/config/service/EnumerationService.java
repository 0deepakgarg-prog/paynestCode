package com.paynest.config.service;

import com.paynest.config.dto.response.EnumerationResponse;
import com.paynest.config.repository.EnumerationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnumerationService {

    private final EnumerationRepository enumerationRepository;

    public List<EnumerationResponse> getActiveEnumerationsByType(String enumType) {
        return enumerationRepository
                .findByEnumTypeIgnoreCaseAndIsActiveTrueOrderByDisplayOrderAscEnumValueAsc(enumType)
                .stream()
                .map(EnumerationResponse::from)
                .toList();
    }
}

package com.paynest.payments.service;

import com.paynest.exception.ApplicationException;
import com.paynest.payments.entity.BillPaymentStatusRecord;
import com.paynest.payments.enums.BillPaymentStatus;
import com.paynest.payments.repository.BillPaymentStatusRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillPaymentStatusServiceTest {

    @Mock
    private BillPaymentStatusRepository billPaymentStatusRepository;

    @Test
    void getPendingRecord_shouldReturnRecordWhenStatusIsPending() {
        BillPaymentStatusService service = new BillPaymentStatusService(billPaymentStatusRepository);
        BillPaymentStatusRecord record = record(BillPaymentStatus.PENDING);
        when(billPaymentStatusRepository.findById("BP240401-123456-A0001"))
                .thenReturn(Optional.of(record));

        BillPaymentStatusRecord result = service.getPendingRecord("BP240401-123456-A0001");

        assertSame(record, result);
    }

    @Test
    void getPendingRecord_shouldRejectRecordWhenStatusIsSuccess() {
        BillPaymentStatusService service = new BillPaymentStatusService(billPaymentStatusRepository);
        when(billPaymentStatusRepository.findById("BP240401-123456-A0001"))
                .thenReturn(Optional.of(record(BillPaymentStatus.SUCCESS)));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.getPendingRecord("BP240401-123456-A0001")
        );

        assertEquals("BILL_PAYMENT_ALREADY_SETTLED", exception.getErrorCode());
    }

    @Test
    void getPendingRecord_shouldRejectRecordWhenStatusIsFailed() {
        BillPaymentStatusService service = new BillPaymentStatusService(billPaymentStatusRepository);
        when(billPaymentStatusRepository.findById("BP240401-123456-A0001"))
                .thenReturn(Optional.of(record(BillPaymentStatus.FAILED)));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.getPendingRecord("BP240401-123456-A0001")
        );

        assertEquals("BILL_PAYMENT_ALREADY_SETTLED", exception.getErrorCode());
    }

    @Test
    void markSuccess_shouldKeepSettledByNull() {
        BillPaymentStatusService service = new BillPaymentStatusService(billPaymentStatusRepository);
        BillPaymentStatusRecord record = record(BillPaymentStatus.PENDING);

        service.markSuccess(record, "ops-1", "provider confirmed", Map.of("providerRef", "ELEC-1"));

        assertEquals(BillPaymentStatus.SUCCESS, record.getStatus());
        assertNull(record.getSettledBy());
        assertNotNull(record.getSettledOn());
        assertEquals("provider confirmed", record.getComments());
        verify(billPaymentStatusRepository).save(record);
    }

    @Test
    void markFailed_shouldKeepSettledByNull() {
        BillPaymentStatusService service = new BillPaymentStatusService(billPaymentStatusRepository);
        BillPaymentStatusRecord record = record(BillPaymentStatus.PENDING);

        service.markFailed(
                record,
                "ops-1",
                "provider rejected",
                Map.of("providerRef", "ELEC-2"),
                "RB240401-123456-A0001"
        );

        assertEquals(BillPaymentStatus.FAILED, record.getStatus());
        assertNull(record.getSettledBy());
        assertNotNull(record.getSettledOn());
        assertEquals("RB240401-123456-A0001", record.getRollbackTransactionId());
        verify(billPaymentStatusRepository).save(record);
    }

    private BillPaymentStatusRecord record(BillPaymentStatus status) {
        BillPaymentStatusRecord record = new BillPaymentStatusRecord();
        record.setTransactionId("BP240401-123456-A0001");
        record.setStatus(status);
        return record;
    }
}

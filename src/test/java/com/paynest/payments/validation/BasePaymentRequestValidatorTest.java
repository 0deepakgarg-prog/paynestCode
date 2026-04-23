package com.paynest.payments.validation;

import com.paynest.config.PropertyReader;
import com.paynest.config.entity.SupportedLanguage;
import com.paynest.enums.AccountType;
import com.paynest.enums.RequestGateway;
import com.paynest.enums.WalletType;
import com.paynest.users.enums.AuthType;
import com.paynest.users.enums.IdentifierType;
import com.paynest.exception.ApplicationException;
import com.paynest.payments.dto.Authentication;
import com.paynest.payments.dto.Identifier;
import com.paynest.payments.dto.Party;
import com.paynest.payments.dto.TransactionInfo;
import com.paynest.payments.dto.U2UPaymentRequest;
import com.paynest.payments.enums.InitiatedBy;
import com.paynest.payments.validation.BasePaymentRequestValidator;
import com.paynest.config.repository.EnumerationRepository;
import com.paynest.config.repository.SupportedLanguageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasePaymentRequestValidatorTest {

    @Mock
    private PropertyReader propertyReader;

    @Mock
    private EnumerationRepository enumerationRepository;

    @Mock
    private SupportedLanguageRepository supportedLanguageRepository;

    @InjectMocks
    private BasePaymentRequestValidator validator;

    @Test
    void validate_shouldRequireRequestGateway() {
        when(propertyReader.getPropertyValue("operations.allowed")).thenReturn("U2U,MERCHANTPAY");
        U2UPaymentRequest request = validRequest();
        request.setRequestGateway(null);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> validator.validate(request)
        );

        assertEquals("REQUEST_GATEWAY_MISSING", exception.getErrorCode());
    }

    @Test
    void validate_shouldRequireWalletTypeForBothParties() {
        when(propertyReader.getPropertyValue("operations.allowed")).thenReturn("U2U,MERCHANTPAY");
        when(supportedLanguageRepository.findByLanguageCodeIgnoreCaseAndIsActiveTrue("en"))
                .thenReturn(Optional.of(language("en", true)));
        U2UPaymentRequest request = validRequest();
        request.getDebitor().setWalletType(null);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> validator.validate(request)
        );

        assertEquals("WALLET_TYPE_MISSING", exception.getErrorCode());
    }

    @Test
    void validate_shouldFallbackToDefaultLanguageWhenRequestedLanguageIsUnsupported() {
        when(propertyReader.getPropertyValue("operations.allowed")).thenReturn("U2U,MERCHANTPAY");
        when(supportedLanguageRepository.findByLanguageCodeIgnoreCaseAndIsActiveTrue("es"))
                .thenReturn(Optional.empty());
        when(supportedLanguageRepository.findFirstByIsDefaultTrueAndIsActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(Optional.of(language("en", true)));
        when(enumerationRepository.existsByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue("CURRENCY", "USD"))
                .thenReturn(true);
        U2UPaymentRequest request = validRequest();
        request.setPreferredLang("es");

        validator.validate(request);

        assertEquals("en", request.getPreferredLang());
    }

    @Test
    void validate_shouldUseDefaultLanguageWhenLanguageIsMissing() {
        when(propertyReader.getPropertyValue("operations.allowed")).thenReturn("U2U,MERCHANTPAY");
        when(supportedLanguageRepository.findFirstByIsDefaultTrueAndIsActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(Optional.of(language("en", true)));
        when(enumerationRepository.existsByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue("CURRENCY", "USD"))
                .thenReturn(true);
        U2UPaymentRequest request = validRequest();
        request.setPreferredLang(null);

        validator.validate(request);

        assertEquals("en", request.getPreferredLang());
    }

    @Test
    void validate_shouldFailWhenNoActiveDefaultLanguageExists() {
        when(propertyReader.getPropertyValue("operations.allowed")).thenReturn("U2U,MERCHANTPAY");
        when(supportedLanguageRepository.findByLanguageCodeIgnoreCaseAndIsActiveTrue("es"))
                .thenReturn(Optional.empty());
        when(supportedLanguageRepository.findFirstByIsDefaultTrueAndIsActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(Optional.empty());
        U2UPaymentRequest request = validRequest();
        request.setPreferredLang("es");

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> validator.validate(request)
        );

        assertEquals("DEFAULT_LANGUAGE_NOT_CONFIGURED", exception.getErrorCode());
    }

    @Test
    void validate_shouldRejectAmountsWithMoreThanTwoDecimals() {
        when(propertyReader.getPropertyValue("operations.allowed")).thenReturn("U2U,MERCHANTPAY");
        when(supportedLanguageRepository.findByLanguageCodeIgnoreCaseAndIsActiveTrue("en"))
                .thenReturn(Optional.of(language("en", true)));
        U2UPaymentRequest request = validRequest();
        request.getTransaction().setAmount(new BigDecimal("10.123"));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> validator.validate(request)
        );

        assertEquals("INVALID_AMOUNT_SCALE", exception.getErrorCode());
    }

    @Test
    void validate_shouldAcceptConfiguredCurrencyCodesLongerThanThreeCharacters() {
        when(propertyReader.getPropertyValue("operations.allowed")).thenReturn("U2U,MERCHANTPAY");
        when(supportedLanguageRepository.findByLanguageCodeIgnoreCaseAndIsActiveTrue("en"))
                .thenReturn(Optional.of(language("en", true)));
        when(enumerationRepository.existsByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue("CURRENCY", "EURO"))
                .thenReturn(true);
        U2UPaymentRequest request = validRequest();
        request.getTransaction().setCurrency("EURO");

        validator.validate(request);
    }

    @Test
    void validate_shouldRejectUnsupportedCurrencyCode() {
        when(propertyReader.getPropertyValue("operations.allowed")).thenReturn("U2U,MERCHANTPAY");
        when(supportedLanguageRepository.findByLanguageCodeIgnoreCaseAndIsActiveTrue("en"))
                .thenReturn(Optional.of(language("en", true)));
        when(enumerationRepository.existsByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue("CURRENCY", "ABCXYZ"))
                .thenReturn(false);
        U2UPaymentRequest request = validRequest();
        request.getTransaction().setCurrency("ABCXYZ");

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> validator.validate(request)
        );

        assertEquals("INVALID_CURRENCY", exception.getErrorCode());
    }

    @Test
    void validate_shouldRejectLongPaymentReference() {
        when(propertyReader.getPropertyValue("operations.allowed")).thenReturn("U2U,MERCHANTPAY");
        when(supportedLanguageRepository.findByLanguageCodeIgnoreCaseAndIsActiveTrue("en"))
                .thenReturn(Optional.of(language("en", true)));
        U2UPaymentRequest request = validRequest();
        request.setPaymentReference("x".repeat(101));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> validator.validate(request)
        );

        assertEquals("PAYMENT_REFERENCE_TOO_LONG", exception.getErrorCode());
    }

    @Test
    void validate_shouldRejectLongComments() {
        when(propertyReader.getPropertyValue("operations.allowed")).thenReturn("U2U,MERCHANTPAY");
        when(supportedLanguageRepository.findByLanguageCodeIgnoreCaseAndIsActiveTrue("en"))
                .thenReturn(Optional.of(language("en", true)));
        U2UPaymentRequest request = validRequest();
        request.setComments("x".repeat(301));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> validator.validate(request)
        );

        assertEquals("COMMENTS_TOO_LONG", exception.getErrorCode());
    }

    private U2UPaymentRequest validRequest() {
        U2UPaymentRequest request = new U2UPaymentRequest();
        request.setOperationType("U2U");
        request.setRequestGateway(RequestGateway.MOBILE);
        request.setPreferredLang("en");
        request.setInitiatedBy(InitiatedBy.DEBITOR);
        request.setDebitor(party(AccountType.SUBSCRIBER, WalletType.MAIN, IdentifierType.MOBILE, "9999999999"));
        request.setCreditor(party(AccountType.SUBSCRIBER, WalletType.MAIN, IdentifierType.MOBILE, "8888888888"));

        TransactionInfo transaction = new TransactionInfo();
        transaction.setAmount(new BigDecimal("10.50"));
        transaction.setCurrency("USD");
        request.setTransaction(transaction);
        return request;
    }

    private SupportedLanguage language(String languageCode, boolean isDefault) {
        SupportedLanguage supportedLanguage = new SupportedLanguage();
        supportedLanguage.setLanguageCode(languageCode);
        supportedLanguage.setIsDefault(isDefault);
        supportedLanguage.setIsActive(true);
        return supportedLanguage;
    }

    private Party party(AccountType accountType, WalletType walletType, IdentifierType identifierType, String identifierValue) {
        Party party = new Party();
        party.setAccountType(accountType);
        party.setWalletType(walletType);

        Identifier identifier = new Identifier();
        identifier.setType(identifierType);
        identifier.setValue(identifierValue);
        party.setIdentifier(identifier);

        Authentication authentication = new Authentication();
        authentication.setType(AuthType.PIN);
        authentication.setValue("1234");
        party.setAuthentication(authentication);
        return party;
    }
}

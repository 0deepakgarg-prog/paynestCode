$ErrorActionPreference = 'Stop'

$moves = @(
    @{ old = 'src/main/java/com/paynest/controller/AccountController.java'; new = 'src/main/java/com/paynest/users/controller/AccountController.java' },
    @{ old = 'src/main/java/com/paynest/controller/AuthController.java'; new = 'src/main/java/com/paynest/users/controller/AuthController.java' },
    @{ old = 'src/main/java/com/paynest/controller/WalletController.java'; new = 'src/main/java/com/paynest/users/controller/WalletController.java' },

    @{ old = 'src/main/java/com/paynest/dto/PermissionDto.java'; new = 'src/main/java/com/paynest/users/dto/PermissionDto.java' },
    @{ old = 'src/main/java/com/paynest/dto/RoleDto.java'; new = 'src/main/java/com/paynest/users/dto/RoleDto.java' },
    @{ old = 'src/main/java/com/paynest/dto/RolePermissionDto.java'; new = 'src/main/java/com/paynest/users/dto/RolePermissionDto.java' },
    @{ old = 'src/main/java/com/paynest/dto/UserRoleDto.java'; new = 'src/main/java/com/paynest/users/dto/UserRoleDto.java' },
    @{ old = 'src/main/java/com/paynest/dto/request/AddAccountKycRequest.java'; new = 'src/main/java/com/paynest/users/dto/request/AddAccountKycRequest.java' },
    @{ old = 'src/main/java/com/paynest/dto/request/AuthLoginRequest.java'; new = 'src/main/java/com/paynest/users/dto/request/AuthLoginRequest.java' },
    @{ old = 'src/main/java/com/paynest/dto/request/ChangePasswordRequest.java'; new = 'src/main/java/com/paynest/users/dto/request/ChangePasswordRequest.java' },
    @{ old = 'src/main/java/com/paynest/dto/request/ChangePinRequest.java'; new = 'src/main/java/com/paynest/users/dto/request/ChangePinRequest.java' },
    @{ old = 'src/main/java/com/paynest/dto/request/GetAccountDetailsRequest.java'; new = 'src/main/java/com/paynest/users/dto/request/GetAccountDetailsRequest.java' },
    @{ old = 'src/main/java/com/paynest/dto/request/RegisterUserRequest.java'; new = 'src/main/java/com/paynest/users/dto/request/RegisterUserRequest.java' },
    @{ old = 'src/main/java/com/paynest/dto/request/RegistrationRequest.java'; new = 'src/main/java/com/paynest/users/dto/request/RegistrationRequest.java' },
    @{ old = 'src/main/java/com/paynest/dto/request/RegistrationRequestWithOtp.java'; new = 'src/main/java/com/paynest/users/dto/request/RegistrationRequestWithOtp.java' },
    @{ old = 'src/main/java/com/paynest/dto/request/UpdateAccountRequest.java'; new = 'src/main/java/com/paynest/users/dto/request/UpdateAccountRequest.java' },
    @{ old = 'src/main/java/com/paynest/dto/response/AccountKycDetailsResponse.java'; new = 'src/main/java/com/paynest/users/dto/response/AccountKycDetailsResponse.java' },
    @{ old = 'src/main/java/com/paynest/dto/response/AccountWalletBalancesResponse.java'; new = 'src/main/java/com/paynest/users/dto/response/AccountWalletBalancesResponse.java' },
    @{ old = 'src/main/java/com/paynest/dto/response/AccountWalletsResponse.java'; new = 'src/main/java/com/paynest/users/dto/response/AccountWalletsResponse.java' },
    @{ old = 'src/main/java/com/paynest/dto/response/AuthLoginResponse.java'; new = 'src/main/java/com/paynest/users/dto/response/AuthLoginResponse.java' },
    @{ old = 'src/main/java/com/paynest/dto/response/BalanceResponse.java'; new = 'src/main/java/com/paynest/users/dto/response/BalanceResponse.java' },
    @{ old = 'src/main/java/com/paynest/dto/response/ChallengeTokenResponse.java'; new = 'src/main/java/com/paynest/users/dto/response/ChallengeTokenResponse.java' },
    @{ old = 'src/main/java/com/paynest/dto/response/RegistrationResponse.java'; new = 'src/main/java/com/paynest/users/dto/response/RegistrationResponse.java' },
    @{ old = 'src/main/java/com/paynest/dto/logging/ApiAuditLogEvent.java'; new = 'src/main/java/com/paynest/config/dto/logging/ApiAuditLogEvent.java' },

    @{ old = 'src/main/java/com/paynest/entity/Account.java'; new = 'src/main/java/com/paynest/users/entity/Account.java' },
    @{ old = 'src/main/java/com/paynest/entity/AccountAuth.java'; new = 'src/main/java/com/paynest/users/entity/AccountAuth.java' },
    @{ old = 'src/main/java/com/paynest/entity/AccountIdentifier.java'; new = 'src/main/java/com/paynest/users/entity/AccountIdentifier.java' },
    @{ old = 'src/main/java/com/paynest/entity/AuthChallenge.java'; new = 'src/main/java/com/paynest/users/entity/AuthChallenge.java' },
    @{ old = 'src/main/java/com/paynest/entity/KycDocument.java'; new = 'src/main/java/com/paynest/users/entity/KycDocument.java' },
    @{ old = 'src/main/java/com/paynest/entity/Otp.java'; new = 'src/main/java/com/paynest/users/entity/Otp.java' },
    @{ old = 'src/main/java/com/paynest/entity/Permission.java'; new = 'src/main/java/com/paynest/users/entity/Permission.java' },
    @{ old = 'src/main/java/com/paynest/entity/Role.java'; new = 'src/main/java/com/paynest/users/entity/Role.java' },
    @{ old = 'src/main/java/com/paynest/entity/RolePermission.java'; new = 'src/main/java/com/paynest/users/entity/RolePermission.java' },
    @{ old = 'src/main/java/com/paynest/entity/UserRole.java'; new = 'src/main/java/com/paynest/users/entity/UserRole.java' },
    @{ old = 'src/main/java/com/paynest/entity/VerificationStatus.java'; new = 'src/main/java/com/paynest/users/entity/VerificationStatus.java' },
    @{ old = 'src/main/java/com/paynest/entity/Wallet.java'; new = 'src/main/java/com/paynest/users/entity/Wallet.java' },
    @{ old = 'src/main/java/com/paynest/entity/WalletBalance.java'; new = 'src/main/java/com/paynest/users/entity/WalletBalance.java' },

    @{ old = 'src/main/java/com/paynest/entity/AuditApiLog.java'; new = 'src/main/java/com/paynest/config/entity/AuditApiLog.java' },
    @{ old = 'src/main/java/com/paynest/entity/Enumeration.java'; new = 'src/main/java/com/paynest/config/entity/Enumeration.java' },
    @{ old = 'src/main/java/com/paynest/entity/SupportedLanguage.java'; new = 'src/main/java/com/paynest/config/entity/SupportedLanguage.java' },
    @{ old = 'src/main/java/com/paynest/entity/TenantRegistry.java'; new = 'src/main/java/com/paynest/config/entity/TenantRegistry.java' },

    @{ old = 'src/main/java/com/paynest/entity/TransactionDetails.java'; new = 'src/main/java/com/paynest/payments/entity/TransactionDetails.java' },
    @{ old = 'src/main/java/com/paynest/entity/TransactionDetailsId.java'; new = 'src/main/java/com/paynest/payments/entity/TransactionDetailsId.java' },
    @{ old = 'src/main/java/com/paynest/entity/Transactions.java'; new = 'src/main/java/com/paynest/payments/entity/Transactions.java' },
    @{ old = 'src/main/java/com/paynest/entity/WalletLedger.java'; new = 'src/main/java/com/paynest/payments/entity/WalletLedger.java' },

    @{ old = 'src/main/java/com/paynest/enums/AccountType.java'; new = 'src/main/java/com/paynest/users/enums/AccountType.java' },
    @{ old = 'src/main/java/com/paynest/enums/AuthType.java'; new = 'src/main/java/com/paynest/users/enums/AuthType.java' },
    @{ old = 'src/main/java/com/paynest/enums/Gender.java'; new = 'src/main/java/com/paynest/users/enums/Gender.java' },
    @{ old = 'src/main/java/com/paynest/enums/IdentifierType.java'; new = 'src/main/java/com/paynest/users/enums/IdentifierType.java' },
    @{ old = 'src/main/java/com/paynest/enums/InitiatedBy.java'; new = 'src/main/java/com/paynest/payments/enums/InitiatedBy.java' },
    @{ old = 'src/main/java/com/paynest/enums/TransactionStatus.java'; new = 'src/main/java/com/paynest/payments/enums/TransactionStatus.java' },

    @{ old = 'src/main/java/com/paynest/repository/AccountAuthRepository.java'; new = 'src/main/java/com/paynest/users/repository/AccountAuthRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/AccountIdentifierRepository.java'; new = 'src/main/java/com/paynest/users/repository/AccountIdentifierRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/AccountRepository.java'; new = 'src/main/java/com/paynest/users/repository/AccountRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/AuthChallengeRepository.java'; new = 'src/main/java/com/paynest/users/repository/AuthChallengeRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/KycDocumentRepository.java'; new = 'src/main/java/com/paynest/users/repository/KycDocumentRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/OtpRepository.java'; new = 'src/main/java/com/paynest/users/repository/OtpRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/PermissionRepository.java'; new = 'src/main/java/com/paynest/users/repository/PermissionRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/RolePermissionRepository.java'; new = 'src/main/java/com/paynest/users/repository/RolePermissionRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/RoleRepository.java'; new = 'src/main/java/com/paynest/users/repository/RoleRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/UserRoleRepository.java'; new = 'src/main/java/com/paynest/users/repository/UserRoleRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/WalletBalanceRepository.java'; new = 'src/main/java/com/paynest/users/repository/WalletBalanceRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/WalletRepository.java'; new = 'src/main/java/com/paynest/users/repository/WalletRepository.java' },

    @{ old = 'src/main/java/com/paynest/repository/AuditApiLogRepository.java'; new = 'src/main/java/com/paynest/config/repository/AuditApiLogRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/EnumerationRepository.java'; new = 'src/main/java/com/paynest/config/repository/EnumerationRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/SupportedLanguageRepository.java'; new = 'src/main/java/com/paynest/config/repository/SupportedLanguageRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/TenantRegistryRepository.java'; new = 'src/main/java/com/paynest/config/repository/TenantRegistryRepository.java' },

    @{ old = 'src/main/java/com/paynest/repository/TransactionDetailsRepository.java'; new = 'src/main/java/com/paynest/payments/repository/TransactionDetailsRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/TransactionsRepository.java'; new = 'src/main/java/com/paynest/payments/repository/TransactionsRepository.java' },
    @{ old = 'src/main/java/com/paynest/repository/WalletLedgerRepository.java'; new = 'src/main/java/com/paynest/payments/repository/WalletLedgerRepository.java' },

    @{ old = 'src/main/java/com/paynest/security/JwtAuthenticationFilter.java'; new = 'src/main/java/com/paynest/config/security/JwtAuthenticationFilter.java' },
    @{ old = 'src/main/java/com/paynest/security/JwtService.java'; new = 'src/main/java/com/paynest/config/security/JwtService.java' },
    @{ old = 'src/main/java/com/paynest/security/JWTUtils.java'; new = 'src/main/java/com/paynest/config/security/JWTUtils.java' },
    @{ old = 'src/main/java/com/paynest/security/PaynestUserDetailsService.java'; new = 'src/main/java/com/paynest/config/security/PaynestUserDetailsService.java' },

    @{ old = 'src/main/java/com/paynest/service/AccountService.java'; new = 'src/main/java/com/paynest/users/service/AccountService.java' },
    @{ old = 'src/main/java/com/paynest/service/AuthService.java'; new = 'src/main/java/com/paynest/users/service/AuthService.java' },
    @{ old = 'src/main/java/com/paynest/service/PinService.java'; new = 'src/main/java/com/paynest/users/service/PinService.java' },
    @{ old = 'src/main/java/com/paynest/service/WalletCacheService.java'; new = 'src/main/java/com/paynest/users/service/WalletCacheService.java' },
    @{ old = 'src/main/java/com/paynest/service/WalletService.java'; new = 'src/main/java/com/paynest/users/service/WalletService.java' },

    @{ old = 'src/main/java/com/paynest/service/AsyncLogPublisher.java'; new = 'src/main/java/com/paynest/config/service/AsyncLogPublisher.java' },
    @{ old = 'src/main/java/com/paynest/service/TenantRegistryService.java'; new = 'src/main/java/com/paynest/config/service/TenantRegistryService.java' },

    @{ old = 'src/main/java/com/paynest/service/BalanceService.java'; new = 'src/main/java/com/paynest/payments/service/BalanceService.java' },
    @{ old = 'src/main/java/com/paynest/service/TransactionsService.java'; new = 'src/main/java/com/paynest/payments/service/TransactionsService.java' },

    @{ old = 'src/main/java/com/paynest/payment/controller/FinancialTransactionController.java'; new = 'src/main/java/com/paynest/payments/controller/FinancialTransactionController.java' },
    @{ old = 'src/main/java/com/paynest/payment/dto/Authentication.java'; new = 'src/main/java/com/paynest/payments/dto/Authentication.java' },
    @{ old = 'src/main/java/com/paynest/payment/dto/BasePaymentRequest.java'; new = 'src/main/java/com/paynest/payments/dto/BasePaymentRequest.java' },
    @{ old = 'src/main/java/com/paynest/payment/dto/BasePaymentResponse.java'; new = 'src/main/java/com/paynest/payments/dto/BasePaymentResponse.java' },
    @{ old = 'src/main/java/com/paynest/payment/dto/Identifier.java'; new = 'src/main/java/com/paynest/payments/dto/Identifier.java' },
    @{ old = 'src/main/java/com/paynest/payment/dto/Party.java'; new = 'src/main/java/com/paynest/payments/dto/Party.java' },
    @{ old = 'src/main/java/com/paynest/payment/dto/ResumeTransactionRequest.java'; new = 'src/main/java/com/paynest/payments/dto/ResumeTransactionRequest.java' },
    @{ old = 'src/main/java/com/paynest/payment/dto/StockApprovalRequest.java'; new = 'src/main/java/com/paynest/payments/dto/StockApprovalRequest.java' },
    @{ old = 'src/main/java/com/paynest/payment/dto/StockInitiateRequest.java'; new = 'src/main/java/com/paynest/payments/dto/StockInitiateRequest.java' },
    @{ old = 'src/main/java/com/paynest/payment/dto/StockReimbursementInitiateRequest.java'; new = 'src/main/java/com/paynest/payments/dto/StockReimbursementInitiateRequest.java' },
    @{ old = 'src/main/java/com/paynest/payment/dto/TransactionInfo.java'; new = 'src/main/java/com/paynest/payments/dto/TransactionInfo.java' },
    @{ old = 'src/main/java/com/paynest/payment/dto/U2UPaymentRequest.java'; new = 'src/main/java/com/paynest/payments/dto/U2UPaymentRequest.java' },
    @{ old = 'src/main/java/com/paynest/payment/service/StockService.java'; new = 'src/main/java/com/paynest/payments/service/StockService.java' },
    @{ old = 'src/main/java/com/paynest/payment/service/U2UPaymentService.java'; new = 'src/main/java/com/paynest/payments/service/U2UPaymentService.java' },
    @{ old = 'src/main/java/com/paynest/payment/validation/BasePaymentRequestValidator.java'; new = 'src/main/java/com/paynest/payments/validation/BasePaymentRequestValidator.java' },

    @{ old = 'src/main/java/com/paynest/tenant/SchemaRoutingDataSource.java'; new = 'src/main/java/com/paynest/config/tenant/SchemaRoutingDataSource.java' },
    @{ old = 'src/main/java/com/paynest/tenant/TenantContext.java'; new = 'src/main/java/com/paynest/config/tenant/TenantContext.java' },
    @{ old = 'src/main/java/com/paynest/tenant/TenantIdentifierResolver.java'; new = 'src/main/java/com/paynest/config/tenant/TenantIdentifierResolver.java' },
    @{ old = 'src/main/java/com/paynest/tenant/TraceContext.java'; new = 'src/main/java/com/paynest/config/tenant/TraceContext.java' },

    @{ old = 'src/test/java/com/paynest/service/AccountServiceTest.java'; new = 'src/test/java/com/paynest/users/service/AccountServiceTest.java' },
    @{ old = 'src/test/java/com/paynest/service/TenantRegistryServiceTest.java'; new = 'src/test/java/com/paynest/config/service/TenantRegistryServiceTest.java' },
    @{ old = 'src/test/java/com/paynest/e2e/AccountRegistrationE2ETest.java'; new = 'src/test/java/com/paynest/users/e2e/AccountRegistrationE2ETest.java' }
)

$classMap = @{}
foreach ($move in $moves) {
    $oldMatch = [regex]::Match($move.old, 'src/(main|test)/java/(.+)\.java$')
    $newMatch = [regex]::Match($move.new, 'src/(main|test)/java/(.+)\.java$')
    $classMap[($oldMatch.Groups[2].Value -replace '/', '.')] = ($newMatch.Groups[2].Value -replace '/', '.')

    $targetDir = Split-Path $move.new -Parent
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }
    if (Test-Path $move.old) {
        Move-Item -Path $move.old -Destination $move.new -Force
    }
}

$javaFiles = Get-ChildItem -Recurse -File src/main/java,src/test/java -Filter *.java
foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw
    $normalized = $file.FullName.Replace('\', '/')
    $packageMatch = [regex]::Match($normalized, '/src/(main|test)/java/(.+)/[^/]+\.java$')
    if ($packageMatch.Success) {
        $packageName = $packageMatch.Groups[2].Value -replace '/', '.'
        $content = [regex]::Replace($content, '(?m)^package\s+[^;]+;', "package $packageName;", 1)
    }

    foreach ($entry in $classMap.GetEnumerator()) {
        $content = $content.Replace($entry.Key, $entry.Value)
    }

    Set-Content -Path $file.FullName -Value $content
}

Get-ChildItem -Recurse -Directory src/main/java/com/paynest, src/test/java/com/paynest |
    Sort-Object FullName -Descending |
    ForEach-Object {
        if (-not (Get-ChildItem $_.FullName -Force | Select-Object -First 1)) {
            Remove-Item $_.FullName -Force
        }
    }

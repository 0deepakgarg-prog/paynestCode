INSERT INTO tenant_movii.enumerations (enum_type,
                                       enum_code,
                                       enum_value,
                                       description,
                                       display_order,
                                       is_active,
                                       is_system,
                                       created_at)
SELECT 'SYSTEM_CONFIG',
       'TESTING_MODE',
       'false',
       'Set to true to use fixed OTP, PIN, and password values for testing',
       0,
       TRUE,
       TRUE,
       CURRENT_TIMESTAMP WHERE NOT EXISTS (
    SELECT 1
    FROM tenant_movii.enumerations
    WHERE UPPER(enum_type) = 'SYSTEM_CONFIG'
      AND UPPER(enum_code) = 'TESTING_MODE'
);

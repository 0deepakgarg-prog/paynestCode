package com.paynest.validations;

public class RequestValidation {

    public static void validateMobileNumber(String mobileNumber) {
        if (mobileNumber == null || !mobileNumber.matches("\\d{10}")) {
            throw new IllegalArgumentException("Invalid mobile number. It must be a 10-digit number.");
        }
    }

     public static void validateOtp(String otp) {
        if (otp == null || !otp.matches("\\d{6}")) {
            throw new IllegalArgumentException("Invalid OTP. It must be a 6-digit number.");
        }
    }
}

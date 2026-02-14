package com.paynest.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "account")
@Data
public class Account {

    @Id
    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "gender")
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "preferred_lang")
    private String preferredLang;

    @Column(name = "nationality")
    private String nationality;

    @Column(name = "ssn")
    private String ssn;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "attr1")
    private String attr1;

    @Column(name = "attr2")
    private String attr2;

    @Column(name = "attr3")
    private String attr3;

    @Column(name = "attr4")
    private String attr4;

    @Column(name = "attr5")
    private String attr5;

    @Column(name = "attr6")
    private String attr6;

    @Column(name = "attr7")
    private String attr7;

    @Column(name = "attr8")
    private String attr8;

    @Column(name = "attr9")
    private String attr9;

    @Column(name = "attr10")
    private String attr10;

    @Column(name = "kyc_status")
    private String kycStatus;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

}

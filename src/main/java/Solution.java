package com.example.demo;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Solution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String regNo;
    private String questionType;
    @Column(length = 4000)
    private String finalQuery;
    private Instant createdAt = Instant.now();

    protected Solution() {}

    public Solution(String regNo, String questionType, String finalQuery) {
        this.regNo = regNo;
        this.questionType = questionType;
        this.finalQuery = finalQuery;
    }
}

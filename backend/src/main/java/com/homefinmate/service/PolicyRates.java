package com.homefinmate.service;

import java.math.BigDecimal;

// 정책대출 공식 기본금리표 (우대금리 전, 확인일 2026-10-26)
final class PolicyRates {

    // 표를 확인한 날짜, 출처
    static final String CHECKED_DATE = "2026-10-26";
    static final String FUND_RATE_SOURCE = "https://nhuf.molit.go.kr/init/include/NewBabyGetInform.jsp";
    static final String BOGEUMJARI_RATE_URL = "https://www.hf.go.kr/ko/sub01/sub01_01_04.do";

    // 기금 대출 만기 구간 (년), 보금자리론 만기 구간 (년)
    private static final int[] FUND_TERMS = { 10, 15, 20, 30 };
    private static final int[] BOGEUMJARI_TERMS = { 10, 15, 20, 30, 40, 50 };

    // 내집마련 디딤돌대출: 부부합산 연소득 상한(원)별 만기 10/15/20/30년 금리(%)
    private static final long[] DIDIMDOL_INCOME = { 20_000_000, 40_000_000, 70_000_000, 85_000_000 };
    private static final String[][] DIDIMDOL = {
            { "2.85", "2.95", "3.05", "3.10" },
            { "3.20", "3.30", "3.40", "3.45" },
            { "3.55", "3.65", "3.75", "3.80" },
            { "3.90", "4.00", "4.10", "4.15" },
    };

    // 신생아 특례 디딤돌대출: 부부합산 연소득 상한(원)별 만기 10/15/20/30년 금리(%), 1.3억 초과는 맞벌이만
    private static final long[] NEWBORN_INCOME = { 20_000_000, 40_000_000, 60_000_000, 85_000_000, 100_000_000,
            130_000_000, 150_000_000, 170_000_000, 200_000_000 };
    private static final String[][] NEWBORN = {
            { "1.80", "1.90", "2.00", "2.05" },
            { "2.15", "2.25", "2.35", "2.40" },
            { "2.40", "2.50", "2.60", "2.65" },
            { "2.65", "2.75", "2.85", "2.90" },
            { "2.90", "3.00", "3.10", "3.20" },
            { "3.20", "3.30", "3.40", "3.50" },
            { "3.50", "3.60", "3.70", "3.80" },
            { "3.85", "3.95", "4.05", "4.15" },
            { "4.20", "4.30", "4.40", "4.50" },
    };

    // 보금자리론(t-보금자리론) 만기 10/15/20/30/40/50년 금리(%), 2026-10-01 공시
    private static final String[] BOGEUMJARI = { "5.00", "5.10", "5.15", "5.20", "5.25", "5.30" };

    private PolicyRates() {
    }

    // 대출 기간에 해당하는 만기 구간 (년)
    static int termBucket(int[] terms, Integer termMonths) {
        int years = termMonths == null ? 30 : (termMonths + 11) / 12;
        for (int t : terms) {
            if (years <= t) {
                return t;
            }
        }
        return terms[terms.length - 1];
    }

    static int fundTerm(Integer termMonths) {
        return termBucket(FUND_TERMS, termMonths);
    }

    static int bogeumjariTerm(Integer termMonths) {
        return termBucket(BOGEUMJARI_TERMS, termMonths);
    }

    // 디딤돌 기본금리 (표 범위를 넘는 소득이면 null)
    static BigDecimal didimdol(BigDecimal annualIncome, Integer termMonths) {
        return lookup(DIDIMDOL_INCOME, DIDIMDOL, annualIncome, indexOf(FUND_TERMS, fundTerm(termMonths)));
    }

    // 신생아 특례 기본금리 (표 범위를 넘는 소득이면 null)
    static BigDecimal newborn(BigDecimal annualIncome, Integer termMonths) {
        return lookup(NEWBORN_INCOME, NEWBORN, annualIncome, indexOf(FUND_TERMS, fundTerm(termMonths)));
    }

    // 보금자리론 기본금리 (소득과 무관)
    static BigDecimal bogeumjari(Integer termMonths) {
        return new BigDecimal(BOGEUMJARI[indexOf(BOGEUMJARI_TERMS, bogeumjariTerm(termMonths))]);
    }

    private static BigDecimal lookup(long[] incomeCaps, String[][] table, BigDecimal annualIncome, int termIndex) {
        if (annualIncome == null) {
            return null;
        }
        for (int i = 0; i < incomeCaps.length; i++) {
            if (annualIncome.compareTo(BigDecimal.valueOf(incomeCaps[i])) <= 0) {
                return new BigDecimal(table[i][termIndex]);
            }
        }
        return null;
    }

    private static int indexOf(int[] terms, int term) {
        for (int i = 0; i < terms.length; i++) {
            if (terms[i] == term) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown term " + term);
    }
}

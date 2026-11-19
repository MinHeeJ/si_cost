package com.sicost.common.validation;

import com.sicost.common.api.ApiException;
import java.util.Locale;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InputValidator {
    private InputValidator() {}
    public static final String DATE_MESSAGE = "날짜 형식이 맞지 않습니다. YYYY-MM-DD로 적어 주세요.";
    public static final String MANAGEMENT_NUMBER_MESSAGE = "등록되지 않은 관리번호입니다. 개발자 풀에 먼저 등록해 주세요.";

    /** Collect independent checks without changing the rules or running any writes. */
    public static void all(Runnable... checks) {
        List<Map<String, String>> violations = new ArrayList<>();
        ApiException first = null;
        for (Runnable check : checks) {
            try {
                check.run();
            } catch (ApiException error) {
                if (first == null) first = error;
                violations.add(Map.of("field", error.field(), "message", error.getMessage()));
            }
        }
        if (first != null) {
            throw new ApiException(first.status(), first.code(), first.getMessage(),
                first.field(), Map.of("violations", violations));
        }
    }
    public static String text(String value, int max, String field) {
        if (value == null || value.isBlank()) throw new ApiException(400, "VALIDATION_ERROR",
            "developerId".equals(field) ? message(field) : label(field) + "을(를) 입력해 주세요.", field, null);
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > max) throw invalid(field);
        return trimmed;
    }
    public static String codeConversionMessage(String source, String target) {
        int sourceFinal = finalConsonant(source), targetFinal = finalConsonant(target);
        return "’" + source + "’" + (sourceFinal == 0 ? "를" : "을")
            + " ’" + target + "’" + (targetFinal == 0 || targetFinal == 8 ? "로" : "으로")
            + " 바꿔 읽었습니다";
    }
    private static int finalConsonant(String value) {
        char last = value.charAt(value.length() - 1);
        return last >= '가' && last <= '힣' ? (last - '가') % 28 : 0;
    }
    public static String optionalText(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        return text(value, max, field);
    }
    public static String groupId(String value) {
        String result = text(value, 30, "groupId").toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z][A-Z0-9_]{0,29}")) throw invalid("groupId");
        return result;
    }
    public static String code(String value) {
        String result = text(value, 20, "code").toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z0-9_]{1,20}")) throw invalid("code");
        return result;
    }
    public static String yesNo(String value, String field) {
        String result = text(value, 1, field);
        if (!result.equals("Y") && !result.equals("N")) throw invalid(field);
        return result;
    }
    public static void immutable(Object original, Object submitted, String field) {
        if (!Objects.equals(original, submitted)) throw invalid(field);
    }
    public static int page(int page) {
        if (page < 1) throw invalid("page");
        return page;
    }
    public static int size(Integer size) {
        if (size == null) return 50;
        if (size != 20 && size != 50 && size != 100) throw invalid("size");
        return size;
    }
    public static ApiException invalid(String field) {
        return new ApiException(400, "VALIDATION_ERROR", message(field), field, null);
    }
    public static String label(String field) {
        if(field==null)return "입력";
        String key=field.substring(field.lastIndexOf('.')+1);
        return switch(key) {
            case "name" -> "이름"; case "developerId" -> "개발자"; case "projectId" -> "프로젝트";
            case "customerId" -> "고객사"; case "partId" -> "영업 파트"; case "jobCode" -> "직무";
            case "gradeCode" -> "등급"; case "count" -> "인원"; case "months" -> "개월";
            case "startMonth" -> "시작월"; case "monthlyMm", "contractMm", "newContractMm" -> "월 MM";
            case "expectedRate" -> "예상 단가"; case "billingUnitPrice", "newUnitPrice" -> "청구 단가";
            case "startDate", "plannedStartDate" -> "시작일"; case "endDate", "plannedEndDate" -> "종료일";
            case "applyDate" -> "적용일"; case "applyMonth" -> "적용월"; case "newEndDate" -> "새 종료일";
            case "withdrawalDate" -> "철수일"; case "probability" -> "수주 확률"; case "amount" -> "금액";
            case "reasonCode", "reason" -> "사유"; case "memo" -> "메모"; case "needs" -> "필요 인력";
            default -> field != null && field.matches(".*[가-힣].*") ? field : "입력 항목";
        };
    }
    public static String message(String field) {
        if(field==null)return "입력 항목의 형식과 길이를 확인해 주세요.";
        return switch(field) {
            case "developerId" -> "개발자를 골라 주세요";
            case "billingUnitPrice", "newUnitPrice" -> "청구 단가를 입력해 주세요.";
            case "contractMm", "newContractMm", "monthlyMm" -> "0 초과 1.00 이하 숫자로 입력해 주세요.";
            case "probability" -> "0~100 사이 숫자로 입력해 주세요.";
            case "reasonCode" -> "사유를 골라 주세요.";
            case "startDate", "endDate", "applyDate", "newEndDate", "withdrawalDate", "plannedStartDate", "plannedEndDate", "hireDate", "retireDate", "availableDate" -> DATE_MESSAGE;
            default -> label(field)+" 칸의 형식과 길이를 확인해 주세요.";
        };
    }
}

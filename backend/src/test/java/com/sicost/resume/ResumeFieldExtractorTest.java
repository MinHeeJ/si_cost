package com.sicost.resume;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.resume.ResumeExtractionDtos.*;
import com.sicost.resume.ResumeExtractionDtos.Dictionary;

class ResumeFieldExtractorTest {
    final ResumeFieldExtractor extractor=new ResumeFieldExtractor();final LocalDate day=LocalDate.of(2026,11,16);
    Dictionary dictionary(){List<Code> codes=new ArrayList<>();int order=0;for(String[] pair:List.of(new String[]{"성명","NAME"},new String[]{"전화","TEL"},new String[]{"메일","MAIL"},new String[]{"거주지","ADDR"},new String[]{"기술","SKILL"},new String[]{"희망단가","WANT"},new String[]{"가능일","FROM"},new String[]{"소속","VNDR"}))codes.add(new Code("RSM_LABEL","L"+(++order),pair[0],pair[1],order));codes.add(new Code("JOB","BE","백엔드",null,1));codes.add(new Code("JOB","FE","프론트",null,2));codes.add(new Code("RSM_JOB_KW","J1","Java","BE",1));codes.add(new Code("RSM_JOB_KW","J2","JavaScript","FE",2));codes.add(new Code("SKILL","JAVA","Java",null,1));
        return new Dictionary(codes,List.of(new Area("41","SIDO",null,"경기","경기도",null,null),new Area("41135","SGG","41","경기","경기도","성남시 분당구",null),new Area("41135101","EMD","41135","경기","경기도","성남시 분당구","분당동")),List.of(new Vendor("V1","주식회사 시험업체")));}
    Extraction run(String text,String target){return extractor.extract(text,target,dictionary(),day);}
    @Test void explicitFieldsAndInferencesAreSeparateAndOnlyCandidateReceivesContactsMoneyDate(){var r=run("성 명： 김 하 늘\n전화: +82 10 1234 5678\n메일: TEST@EXAMPLE.COM\n기술\tJava/java, SQL / 새기술\n희망단가: 650만원\n가능일: 즉시\n거주지: 경기도 성남시 분당구 분당동 123번지 901호\n프리랜서 JavaScript JavaScript JavaScript JavaScript Java","CANDIDATE");assertThat(r.values()).containsEntry("name","김하늘").containsEntry("phone","010-1234-5678").containsEntry("email","test@example.com").containsEntry("desiredRate","6500000").containsEntry("availableDate",day.toString()).containsEntry("homeAreaCode","41135").containsEntry("jobCode","FE").containsEntry("subJobCode","BE").containsEntry("employeeType","FL");assertThat(r.values().get("skills")).isEqualTo(List.of("Java","SQL","새기술"));assertThat(r.fields().stream().filter(f->f.field().equals("homeAreaCode")).findFirst().orElseThrow().fragment()).doesNotContain("123","901");var dev=run("성명: 개발자\n전화:010-1234-5678\n메일:x@example.com\n희망단가:650만원\n가능일:즉시\n영업파트:1\n등급:특급\n동의:네","DEVELOPER");assertThat(dev.values()).containsOnlyKeys("name");}
    @Test void conflictingValuesAndNoLabelsDoNotInventNamesOrGrade(){var r=run("이름이란 무엇인가\n전화:010-1234-5678\n전화:010-2222-3333\n메일: a @example.com\n희망단가:연봉 6000만원\n가능일:2026-02-30\n경력 8년 2개월","CANDIDATE");assertThat(r.values()).doesNotContainKeys("name","phone","email","desiredRate","availableDate","gradeCode","consent");assertThat(r.careerReference()).isEqualTo("경력 8년 2개월");assertThat(r.fields()).anyMatch(f->!f.valid());}
    @Test void vendorAndFreelancerConflictNeedsManualDecision(){var r=run("소속: (주)시험업체\n프리랜서","CANDIDATE");assertThat(r.values()).doesNotContainKeys("employeeType","companyId");assertThat(r.warnings()).isNotEmpty();var os=run("소속: (주)시험업체","CANDIDATE");assertThat(os.values()).containsEntry("employeeType","OS").containsEntry("companyId","V1");}
    @Test void latinWordBoundariesAndOverlongNameAreNotSilentlyRepaired(){var r=run("성명: "+"가".repeat(51)+"\nJavaScript","CANDIDATE");assertThat(r.values()).doesNotContainKey("name").containsEntry("jobCode","FE").doesNotContainKey("subJobCode");}
    @Test void annualLabelNeverTurnsAnnualPayIntoMonthlyRate(){
        var d=dictionary();var codes=new ArrayList<>(d.codes());codes.add(new Code("RSM_LABEL","ANNUAL","희망연봉","WANT",99));
        var result=extractor.extract("희망연봉: 7800만원","CANDIDATE",new Dictionary(codes,d.areas(),d.vendors()),day);
        assertThat(result.values()).doesNotContainKey("desiredRate");
        assertThat(result.fields()).anyMatch(f->f.field().equals("desiredRate")&&!f.valid());
    }
    @Test void zeroNegativeAnnualRangeAndFractionalWonAreNotRates(){for(String amount:List.of("0원","-1원","600~700만원","연봉 6000만원","1.12345만원","650만원/년"))assertThat(run("희망단가: "+amount,"CANDIDATE").values()).doesNotContainKey("desiredRate");assertThat(run("희망단가: 650.5만원","CANDIDATE").values()).containsEntry("desiredRate","6505000");}
    @Test void adversarialUnlabelledContactsFinishWithinBudgetAndDoNotPoisonNextExtraction(){
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),()->{
            for(String text:List.of("a".repeat(200_000),"a.".repeat(99_999)+"@x", "@.".repeat(100_000),"a".repeat(199_980)+"@example.com"))
                assertThat(run(text,"CANDIDATE").values()).doesNotContainKey("email");
            assertThat(run("plain.person@example.com", "CANDIDATE").values()).containsEntry("email","plain.person@example.com");
        });
    }
    @Test void contactScanDoesNotTruncateLongAddressesOrAcceptAmbiguousOnes(){
        assertThat(run("x".repeat(100)+"@example.com", "CANDIDATE").values()).doesNotContainKey("email");
        assertThat(run("one@example.com two@example.com", "CANDIDATE").values()).doesNotContainKey("email");
        assertThat(run("(One.Name+tag@Example.com)", "CANDIDATE").values()).containsEntry("email","one.name+tag@example.com");
    }
    @Test void interruptedExtractionStopsBeforeKeepingDocumentWorkAlive(){
        Thread.currentThread().interrupt();
        try {assertThatThrownBy(()->run("person@example.com","CANDIDATE")).isInstanceOf(com.sicost.common.api.ApiException.class);}
        finally {Thread.interrupted();}
    }
    @Test void noTextIsARealZeroResult(){var r=run("", "CANDIDATE");assertThat(r.values()).isEmpty();assertThat(r.notice()).isEqualTo("가져올 칸을 찾지 못했습니다. 직접 적어 주세요.");}
}

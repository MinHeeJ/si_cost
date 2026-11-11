package com.sicost.common.validation;

import com.sicost.common.api.ApiException;
import com.sicost.common.persistence.DatabaseMonthCodec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PeopleInputPolicyTest {
    @Test void datesAndMonthsPreserveCalendarAndYearBoundaries() {
        assertThat(PeopleInputPolicy.date("0001-01-01", "date").toString()).isEqualTo("0001-01-01");
        assertThat(PeopleInputPolicy.date("9999-12-31", "date").toString()).isEqualTo("9999-12-31");
        assertThat(PeopleInputPolicy.date(" 2024-02-29 ", "date").getDayOfMonth()).isEqualTo(29);
        assertThat(DatabaseMonthCodec.toDatabase("0001-01")).isEqualTo("000101");
        assertThat(DatabaseMonthCodec.toApi("999912")).isEqualTo("9999-12");
    }
    @ParameterizedTest @ValueSource(strings={"0000-01-01","10000-01-01","2025-02-29","2026-04-31","2026-00-01","2026-1-01","2026-01-00","2026-01-01T00:00:00"})
    void invalidCalendarDatesAre400(String input) { assert400(() -> PeopleInputPolicy.date(input,"date")); }
    @Test void todayUsesSeoulEvenWhenClockIsUtc() {
        var clock=Clock.fixed(Instant.parse("2026-01-01T15:00:00Z"),ZoneOffset.UTC);
        assertThat(PeopleInputPolicy.today(clock).toString()).isEqualTo("2026-01-02");
    }
    @Test void amountsAreExactWonIntegersWithNullDistinctFromZero() {
        assertThat(PeopleInputPolicy.amount(null,"monthlyPay")).isNull();
        assertThat(PeopleInputPolicy.amount(" \n ","monthlyRate")).isNull();
        assertThat(PeopleInputPolicy.amount("0","monthlyRate").toPlainString()).isEqualTo("0");
        assertThat(PeopleInputPolicy.amount(" 999,999,999,999,999 원 ","monthlyRate").toPlainString()).isEqualTo("999999999999999");
        assertThat(PeopleInputPolicy.desiredRate("1원").toPlainString()).isEqualTo("1");
    }
    @ParameterizedTest @ValueSource(strings={"-1","+1","1.0","1e3","1000000000000000","원1","1원원","abc","NaN"})
    void invalidAmountsAre400WithoutRounding(String input) { assert400(() -> PeopleInputPolicy.amount(input,"monthlyRate")); }
    @Test void candidateRateRequiresAtLeastOneWon() {
        assert400(() -> PeopleInputPolicy.desiredRate("0"));
        assert400(() -> PeopleInputPolicy.desiredRate(null));
        assert400(() -> PeopleInputPolicy.desiredRate(" "));
    }
    @Test void skillsNormalizeCommaWhitespaceAndDuplicatesInFirstAppearanceOrderWithCasePreserved() {
        assertThat(PeopleInputPolicy.skills(List.of(" Java, SQL,Java, ","java","미등록 기술"),true)).containsExactly("Java","SQL","java","미등록 기술");
        assertThat(PeopleInputPolicy.skills(null,false)).isNull();
        assertThat(PeopleInputPolicy.skills(List.of(" , "),false)).isEmpty();
        assert400(() -> PeopleInputPolicy.skills(List.of(" , "),true));
        assert400(() -> PeopleInputPolicy.skills(null,true));
    }
    @Test void skillsAllowFiftyCodePointsAnd999DistinctItemsWithoutTruncating() {
        assertThat(PeopleInputPolicy.skills(List.of("가".repeat(50)),true)).containsExactly("가".repeat(50));
        assert400(() -> PeopleInputPolicy.skills(List.of("가".repeat(51)),true));
        assertThat(PeopleInputPolicy.skills(IntStream.range(0,999).mapToObj(i -> "기술"+i).toList(),true)).hasSize(999);
        assert400(() -> PeopleInputPolicy.skills(IntStream.range(0,1000).mapToObj(i -> "기술"+i).toList(),true));
        assert400(() -> PeopleInputPolicy.skills(java.util.Arrays.asList("Java",null),true));
    }
    @Test void stoppedCodeCanOnlyRemainUnchangedAndUnknownCodeIsNeverAccepted() {
        var stopped=new PeopleInputPolicy.CodeSelection("EXISTING","N","N");
        assertThat(PeopleInputPolicy.selectableCode(" EXISTING ","EXISTING",stopped,"jobCode")).isEqualTo("EXISTING");
        assert400(() -> PeopleInputPolicy.selectableCode("EXISTING",null,stopped,"jobCode"));
        assert400(() -> PeopleInputPolicy.selectableCode("UNKNOWN","UNKNOWN",null,"jobCode"));
        assert400(() -> PeopleInputPolicy.selectableCode("a".repeat(11),null,new PeopleInputPolicy.CodeSelection("a".repeat(11),"Y","N"),"jobCode"));
        assertThat(PeopleInputPolicy.selectableCode("ACTIVE",null,new PeopleInputPolicy.CodeSelection("ACTIVE","Y","N"),"jobCode")).isEqualTo("ACTIVE");
    }
    @Test void deletedCodeCannotBeNewlySelected() {
        assert400(() -> PeopleInputPolicy.selectableCode("OLD",null,new PeopleInputPolicy.CodeSelection("OLD","Y","Y"),"jobCode"));
    }
    @Test void serialAllocationStopsAt99999WithoutWrappingOrReordering() {
        assertThat(PeopleInputPolicy.nextSerial(0)).isEqualTo(1);
        assertThat(PeopleInputPolicy.nextSerial(99998)).isEqualTo(99999);
        assertThatThrownBy(() -> PeopleInputPolicy.nextSerial(99999)).isInstanceOfSatisfying(ApiException.class,e -> {assertThat(e.status()).isEqualTo(409);assertThat(e.code()).isEqualTo("SERIAL_LIMIT");});
    }
    void assert400(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.status()).isEqualTo(400));
    }
}

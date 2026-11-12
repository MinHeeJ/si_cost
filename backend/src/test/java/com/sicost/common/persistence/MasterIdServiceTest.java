package com.sicost.common.persistence;

import com.sicost.common.api.ApiException;
import java.math.BigInteger;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MasterIdServiceTest {
    final MasterIdMapper mapper=mock(MasterIdMapper.class);
    final MasterIdService service=new MasterIdService(mapper);
    @Test void formatsMinimumThreeDigitsWithoutTruncatingLargerIds() {
        when(mapper.nextClient()).thenReturn(BigInteger.ONE,new BigInteger("100"),new BigInteger("999999999999999999"));
        when(mapper.nextPart()).thenReturn(new BigInteger("121"),new BigInteger("999999999999999"));
        assertThat(service.nextClientId()).isEqualTo("C-001");
        assertThat(service.nextClientId()).isEqualTo("C-100");
        assertThat(service.nextClientId()).isEqualTo("C-999999999999999999");
        assertThat(service.nextPartId()).isEqualTo("PART-121");
        assertThat(service.nextPartId()).isEqualTo("PART-999999999999999");
    }
    @Test void peopleIdsPadOnlyMinimumDigitsAndPreserveTwentyCharacterMaximum() {
        when(mapper.nextDeveloper()).thenReturn(BigInteger.ONE,new BigInteger("9999999999999999"));
        when(mapper.nextCandidate()).thenReturn(BigInteger.ONE,new BigInteger("999999999999999"));
        when(mapper.nextManagementNo()).thenReturn(BigInteger.ONE,new BigInteger("999999999999999999"));
        assertThat(service.nextDeveloperId()).isEqualTo("DEV-000001");
        assertThat(service.nextDeveloperId()).isEqualTo("DEV-9999999999999999").hasSize(20);
        assertThat(service.nextCandidateId()).isEqualTo("CAND-000001");
        assertThat(service.nextCandidateId()).isEqualTo("CAND-999999999999999").hasSize(20);
        assertThat(service.nextManagementNo()).isEqualTo("K-0001");
        assertThat(service.nextManagementNo()).isEqualTo("K-999999999999999999").hasSize(20);
    }
    @Test void automaticManagementNumberSkipsExistingManualNumber() {
        when(mapper.nextManagementNo()).thenReturn(BigInteger.ONE,BigInteger.TWO);
        when(mapper.managementNoExists("K-0001")).thenReturn(true);
        assertThat(service.nextManagementNo()).isEqualTo("K-0002");
        verify(mapper,times(2)).nextManagementNo();
    }
    @Test void invalidOrOversizedSequenceValueIsNeverTruncated() {
        when(mapper.nextDeveloper()).thenReturn(new BigInteger("10000000000000000"));
        when(mapper.nextCandidate()).thenReturn(BigInteger.ZERO);
        when(mapper.nextManagementNo()).thenReturn(new BigInteger("1000000000000000000"));
        for (org.assertj.core.api.ThrowableAssert.ThrowingCallable action : new org.assertj.core.api.ThrowableAssert.ThrowingCallable[]{service::nextDeveloperId,service::nextCandidateId,service::nextManagementNo}) {
            assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("ID_EXHAUSTED"));
        }
    }
    @Test void peopleSequenceExhaustionIsSafe409() {
        when(mapper.nextDeveloper()).thenThrow(new DataAccessResourceFailureException("database detail",new SQLException("sequence detail","2200H")));
        assertThatThrownBy(service::nextDeveloperId).isInstanceOfSatisfying(ApiException.class,e -> {
            assertThat(e.status()).isEqualTo(409);assertThat(e.code()).isEqualTo("ID_EXHAUSTED");assertThat(e.getMessage()).doesNotContain("sequence detail");
        });
    }
    @Test void exhaustedSequenceIs409WithoutRetry() {
        when(mapper.nextClient()).thenThrow(new DataAccessResourceFailureException("database detail",new SQLException("sequence detail","2200H")));
        assertThatThrownBy(service::nextClientId).isInstanceOfSatisfying(ApiException.class,e -> {
            assertThat(e.status()).isEqualTo(409); assertThat(e.code()).isEqualTo("ID_EXHAUSTED");
            assertThat(e.getMessage()).doesNotContain("sequence detail");
        });
        verify(mapper).nextClient(); verifyNoMoreInteractions(mapper);
    }
    @Test void otherDatabaseFailureIsNotMislabeledAsExhaustion() {
        var failure=new DataAccessResourceFailureException("database unavailable");
        when(mapper.nextPart()).thenThrow(failure);
        assertThatThrownBy(service::nextPartId).isSameAs(failure);
    }
    @Test void assignmentIdsPadFourDigitsAndKeepExactEighteenDigitSuffix() {
        when(mapper.nextAssignment()).thenReturn(BigInteger.ONE,new BigInteger("10000"),new BigInteger("999999999999999999"));
        assertThat(service.nextAssignmentId()).isEqualTo("A-0001");
        assertThat(service.nextAssignmentId()).isEqualTo("A-10000");
        assertThat(service.nextAssignmentId()).isEqualTo("A-999999999999999999").hasSize(20);
        verify(mapper,times(3)).nextAssignment();verifyNoMoreInteractions(mapper);
    }
    @Test void assignmentAllocatorRejectsNonpositiveMissingAndOversizedValues() {
        when(mapper.nextAssignment()).thenReturn(null,BigInteger.ZERO,BigInteger.ONE.negate(),new BigInteger("1000000000000000000"));
        for (int i=0;i<4;i++) {
            assertThatThrownBy(service::nextAssignmentId).isInstanceOfSatisfying(ApiException.class,e -> {
                assertThat(e.status()).isEqualTo(409);assertThat(e.code()).isEqualTo("ID_EXHAUSTED");
            });
        }
    }
    @Test void assignmentSequenceExhaustionIsSafe409AndNeverRetried() {
        when(mapper.nextAssignment()).thenThrow(new DataAccessResourceFailureException("database detail",new SQLException("sequence detail","2200H")));
        assertThatThrownBy(service::nextAssignmentId).isInstanceOfSatisfying(ApiException.class,e -> {
            assertThat(e.status()).isEqualTo(409);assertThat(e.code()).isEqualTo("ID_EXHAUSTED");
            assertThat(e.getMessage()).doesNotContain("sequence detail");
        });
        verify(mapper).nextAssignment();verifyNoMoreInteractions(mapper);
    }
}

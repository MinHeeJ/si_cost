package com.sicost.basic147;

import com.sicost.authentication.AuthenticationIssuanceCoordinator;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthenticationIssuanceCoordinatorTest {
    @Test void sameAccountCannotCommitASecondIssueDuringTheFirstDelivery() throws Exception {
        var coordinator=new AuthenticationIssuanceCoordinator();
        var pool=Executors.newFixedThreadPool(2);
        var delivering=new CountDownLatch(1);var release=new CountDownLatch(1);
        var attempted=new CountDownLatch(1);var secondEntered=new CountDownLatch(1);
        var events=Collections.synchronizedList(new ArrayList<String>());
        try {
            var first=pool.submit(()->coordinator.run("synthetic",()->{
                events.add("commit1");delivering.countDown();await(release);events.add("delivery1");return true;
            }));
            assertThat(delivering.await(5,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(()->{attempted.countDown();return coordinator.run("synthetic",()->{
                secondEntered.countDown();events.add("commit2");events.add("delivery2");return true;
            });});
            assertThat(attempted.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(secondEntered.await(200,TimeUnit.MILLISECONDS)).isFalse();
            release.countDown();assertThat(first.get(5,TimeUnit.SECONDS)).isTrue();assertThat(second.get(5,TimeUnit.SECONDS)).isTrue();
            assertThat(events).containsExactly("commit1","delivery1","commit2","delivery2");
            assertEmpty(coordinator);
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void unrelatedAccountsAreNotSerializedBehindAnSmtpWait() throws Exception {
        var coordinator=new AuthenticationIssuanceCoordinator();var pool=Executors.newFixedThreadPool(2);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try {
            var first=pool.submit(()->coordinator.run("first",()->{entered.countDown();await(release);return true;}));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(pool.submit(()->coordinator.run("other",()->true)).get(5,TimeUnit.SECONDS)).isTrue();
            release.countDown();assertThat(first.get(5,TimeUnit.SECONDS)).isTrue();assertEmpty(coordinator);
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void failuresReleaseTheAccountAndRemoveItsEntry() throws Exception {
        var coordinator=new AuthenticationIssuanceCoordinator();
        assertThatThrownBy(()->coordinator.run("synthetic",()->{throw new IllegalStateException("synthetic failure");})).isInstanceOf(IllegalStateException.class);
        assertThat(coordinator.run("synthetic",()->"next explicit issuance")).isEqualTo("next explicit issuance");assertEmpty(coordinator);
    }
    private static void await(CountDownLatch latch) {
        try {if(!latch.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test synchronization timed out");}
        catch(InterruptedException exception) {Thread.currentThread().interrupt();throw new IllegalStateException("test interrupted");}
    }
    private static void assertEmpty(AuthenticationIssuanceCoordinator coordinator) throws Exception {
        var field=AuthenticationIssuanceCoordinator.class.getDeclaredField("accounts");field.setAccessible(true);
        assertThat((Map<?,?>)field.get(coordinator)).isEmpty();
    }
}

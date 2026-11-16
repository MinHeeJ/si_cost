package com.sicost.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.analysis.AnalysisQuestionInterpreter.Function.*;

/** Mock transport contract tests; these do NOT assert a live Ollama model is installed. */
class AnalysisModelAdapterTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void noneNeverSendsAndConfigurationIsServerOnly(){
        assertThat(new AnalysisModelAdapter("NONE","","",30,json).classify(COMPANY,true).mode()).isEqualTo("SERVER_RULE");
        for(int timeout:new int[]{0,121})assertThatThrownBy(()->new AnalysisModelAdapter("NONE","","",timeout,json)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AnalysisModelAdapter("REMOTE","","",30,json)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AnalysisModelAdapter("OLLAMA","http://user:password@localhost","test",30,json)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void onlyEnumAndOpaqueRefsLeaveTheProcessAndClearRulesSkipTheModel()throws Exception {
        var count=new AtomicInteger();var captured=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/chat",exchange->{count.incrementAndGet();captured.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] bytes=json.writeValueAsBytes(Map.of("message",Map.of("content","{\"functionKey\":\"COMPANY\",\"entityRef\":\"E0\",\"periodRef\":\"P0\"}")));exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try{
            var adapter=new AnalysisModelAdapter("OLLAMA","http://127.0.0.1:"+server.getAddress().getPort(),"test-model",2,json);
            assertThat(adapter.classify(COMPANY,false).mode()).isEqualTo("SERVER_RULE");assertThat(adapter.classify(UNKNOWN,true).mode()).isEqualTo("SERVER_RULE");assertThat(count).hasValue(0);
            assertThat(adapter.classify(COMPANY,true).mode()).isEqualTo("OLLAMA");assertThat(count).hasValue(1);
            var request=json.readTree(captured.get());assertThat(request.path("stream").asBoolean()).isFalse();assertThat(request.path("format").path("additionalProperties").asBoolean(true)).isFalse();assertThat(request.path("messages").get(1).path("content").asText()).isEqualTo("COMPANY E0 P0");assertThat(captured.get()).doesNotContain("question","salary","resume","2026","6500000","http://");
        }finally{server.stop(0);}
    }
    @Test void unknownRefsFunctionsAdditionalSqlAndInventedAmountsAreRejectedWithoutRetry()throws Exception {
        for(String content:new String[]{"{\"functionKey\":\"COMPANY\",\"entityRef\":\"OTHER\",\"periodRef\":\"P0\"}","{\"functionKey\":\"DROP\",\"entityRef\":\"E0\",\"periodRef\":\"P0\"}","{\"functionKey\":\"COMPANY\",\"entityRef\":\"E0\",\"periodRef\":\"P0\",\"sql\":\"DELETE FROM TB_HR_DEV\"}","{\"functionKey\":\"COMPANY\",\"entityRef\":\"E0\",\"periodRef\":\"P0\",\"explanation\":\"profit 999999\"}","x".repeat(70000)}) {
            var count=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/api/chat",exchange->{count.incrementAndGet();exchange.getRequestBody().readAllBytes();byte[] bytes=json.writeValueAsBytes(Map.of("message",Map.of("content",content)));exchange.sendResponseHeaders(200,bytes.length);try{exchange.getResponseBody().write(bytes);}finally{exchange.close();}});server.start();
            try{var result=new AnalysisModelAdapter("OLLAMA","http://127.0.0.1:"+server.getAddress().getPort(),"test",2,json).classify(COMPANY,true);assertThat(result.mode()).isEqualTo("SERVER_RULE");assertThat(result.notice()).contains("실패");assertThat(count).hasValue(1);}finally{server.stop(0);}
        }
    }
    @Test void responseBodyStallIsBoundedToo()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/chat",exchange->{exchange.getRequestBody().readAllBytes();exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write('{');exchange.getResponseBody().flush();try{Thread.sleep(2500);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{exchange.close();}});server.start();
        try{long start=System.nanoTime();assertThat(new AnalysisModelAdapter("OLLAMA","http://127.0.0.1:"+server.getAddress().getPort(),"test",1,json).classify(COMPANY,true).mode()).isEqualTo("SERVER_RULE");assertThat(System.nanoTime()-start).isLessThan(2_000_000_000L);}finally{server.stop(0);}
    }
}

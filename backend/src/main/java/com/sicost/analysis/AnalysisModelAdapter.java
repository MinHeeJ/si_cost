package com.sicost.analysis;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static com.sicost.analysis.AnalysisAssistantDtos.Interpreter;

/** The signature cannot accept a question, name, date, money, source row or resume. */
@Component
public class AnalysisModelAdapter {
    private final String provider,model;private final URI endpoint;private final int timeout;private final ObjectMapper json;
    public AnalysisModelAdapter(@Value("${AI_PROVIDER:NONE}") String provider,@Value("${AI_BASE_URL:}") String base,
        @Value("${AI_MODEL:}") String model,@Value("${AI_TIMEOUT_SECONDS:30}") int timeout,ObjectMapper json){
        if(!Set.of("NONE","OLLAMA").contains(provider)||timeout<1||timeout>120)throw new IllegalArgumentException("AI 설정을 확인해 주세요.");
        this.provider=provider;this.model=model;this.timeout=timeout;this.json=json;
        if(provider.equals("OLLAMA")){URI uri=URI.create(base);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||model.isBlank())throw new IllegalArgumentException("AI 서버 설정을 확인해 주세요.");endpoint=URI.create(base.replaceAll("/+$","")+"/api/chat");}else endpoint=null;
    }
    public Interpreter classify(AnalysisQuestionInterpreter.Function allowedFunction,boolean needsClassification) {
        if(provider.equals("NONE")||!needsClassification||allowedFunction==AnalysisQuestionInterpreter.Function.UNKNOWN)return rules(null);
        // Tokens come from the closed server enum. E0 and P0 reference locally retained arguments only.
        Map<String,Object> properties=Map.of("functionKey",Map.of("type","string","enum",List.of(allowedFunction.name())),"entityRef",Map.of("type","string","enum",List.of("E0")),"periodRef",Map.of("type","string","enum",List.of("P0")));
        Map<String,Object> schema=Map.of("type","object","properties",properties,"required",List.of("functionKey","entityRef","periodRef"),"additionalProperties",false);
        try {
            String body=json.writeValueAsString(Map.of("model",model,"stream",false,"format",schema,"messages",List.of(Map.of("role","system","content","Select only the supplied function and existing argument references. Return JSON only."),Map.of("role","user","content",allowedFunction.name()+" E0 P0"))));
            HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout)).followRedirects(HttpClient.Redirect.NEVER).build();
            HttpRequest request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(timeout)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var exchange=client.sendAsync(request,info->new LimitedBody());
            HttpResponse<byte[]> response;
            try{response=exchange.get(timeout,TimeUnit.SECONDS);}catch(Exception e){exchange.cancel(true);throw e;}
            byte[] bytes=response.body();if(response.statusCode()!=200)throw new IllegalArgumentException();
            JsonNode envelope=json.readTree(bytes),result=json.readTree(envelope.path("message").path("content").asText());
            if(!result.isObject()||result.size()!=3||!result.path("functionKey").asText().equals(allowedFunction.name())||!result.path("entityRef").asText().equals("E0")||!result.path("periodRef").asText().equals("P0"))throw new IllegalArgumentException();
            return new Interpreter("OLLAMA",model,"모델은 안전한 함수·참조 분류만 사용했습니다. 수치와 답변은 서버 공통 조회로 구성했습니다.");
        }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();return rules("모델 연결/반환 검증 실패. 명확한 로컬 해석으로 처리했습니다.");}
    }
    private static Interpreter rules(String reason){return new Interpreter("SERVER_RULE",null,"서버 규칙으로 해석·외부 모델 미사용"+(reason==null?"":" · "+reason));}
    /** Bound the response as it arrives, not after buffering an untrusted body. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> result=new CompletableFuture<>();final ByteArrayOutputStream bytes=new ByteArrayOutputStream();Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){if(bytes.size()+buffer.remaining()>65536){subscription.cancel();result.completeExceptionally(new IllegalArgumentException("모델 반환 한도 초과"));return;}byte[] b=new byte[buffer.remaining()];buffer.get(b);bytes.writeBytes(b);}
            subscription.request(1);
        }
        public void onError(Throwable error){result.completeExceptionally(error);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
}

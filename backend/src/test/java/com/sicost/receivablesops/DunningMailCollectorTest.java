package com.sicost.receivablesops;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;
import static org.assertj.core.api.Assertions.*;

class DunningMailCollectorTest {
    @ParameterizedTest @CsvSource({"accept,SENT","reject,FAILED","disconnect,UNKNOWN"})
    void loopbackCollectorVerifiesActualSmtpAcceptanceAndFailure(String mode,String expected) throws Exception {
        try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
            var collected=new CompletableFuture<String>();
            Thread collector=new Thread(()->{
                try(var socket=server.accept()) {
                    socket.setSoTimeout(10000);
                    var in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    var out=new PrintWriter(socket.getOutputStream(),true);out.print("220 local collector\r\n");out.flush();
                    var body=new StringBuilder();boolean data=false;
                    for(String line;(line=in.readLine())!=null;) {
                        if(data) {
                            if(line.equals(".")) {collected.complete(body.toString());if(mode.equals("disconnect"))return;out.print(mode.equals("reject")?"550 rejected\r\n":"250 accepted\r\n");out.flush();data=false;}
                            else body.append(line).append('\n');
                        } else if(line.startsWith("DATA")) {out.print("354 send data\r\n");out.flush();data=true;}
                        else if(line.startsWith("QUIT")) {out.print("221 bye\r\n");out.flush();return;}
                        else {out.print("250 OK\r\n");out.flush();}
                    }
                } catch(Exception error) {collected.completeExceptionally(error);}
            });collector.setDaemon(true);collector.start();
            var env=new MockEnvironment().withProperty("MAIL_ENABLED","true").withProperty("MAIL_HOST","127.0.0.1")
                .withProperty("MAIL_PORT",Integer.toString(server.getLocalPort())).withProperty("MAIL_FROM","sender@example.invalid")
                .withProperty("MAIL_STARTTLS","false").withProperty("APP_PUBLIC_URL","http://127.0.0.1:32652");
            var task=new TaskSource("id","event","B",1,1,false,"user","담당","receiver@example.invalid","MAIL","SENDING",null,"<fixed@sicost.invalid>",null,null,null,"시험 제목","<p>잔액: 111원</p>",LocalDateTime.now(),null,null);
            assertThat(new DunningDeliveryMail(env).send(task).state()).isEqualTo(expected);
            assertThat(collected.get(10,TimeUnit.SECONDS)).contains("Message-ID: <fixed@sicost.invalid>");
            collector.join(1000);
        }
    }
}

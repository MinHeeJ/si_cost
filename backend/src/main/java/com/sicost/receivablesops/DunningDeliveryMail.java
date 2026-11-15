package com.sicost.receivablesops;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.net.URI;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;

@Component
public class DunningDeliveryMail {
    public record MailResult(String state,String errorCode,String errorMessage) {}
    private final Environment settings;
    public DunningDeliveryMail(Environment settings) {this.settings=settings;}
    public String publicUrl() {
        try {
            String value=settings.getProperty("APP_PUBLIC_URL","");var uri=URI.create(value);
            return Set.of("http","https").contains(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null
                &&!value.contains("\r")&&!value.contains("\n")?value.replaceAll("/+$",""):null;
        } catch(RuntimeException e) {return null;}
    }
    public boolean configured() {
        try {
            int port=Integer.parseInt(settings.getProperty("MAIL_PORT","587"));
            String host=settings.getProperty("MAIL_HOST","");
            String username=settings.getProperty("MAIL_USERNAME",""),password=settings.getProperty("MAIL_PASSWORD","");
            return "true".equalsIgnoreCase(settings.getProperty("MAIL_ENABLED","false"))&&host.matches("[A-Za-z0-9._-]{1,253}")
                &&port>=1&&port<=65535&&address(settings.getProperty("MAIL_FROM",""))&&publicUrl()!=null&&username.isBlank()==password.isBlank();
        } catch(RuntimeException e) {return false;}
    }
    public static boolean address(String value) {
        if(value==null||value.length()>100||value.contains("\r")||value.contains("\n"))return false;
        try {var address=new InternetAddress(value,true);address.validate();return value.equals(address.getAddress())&&value.contains("@");}
        catch(AddressException e) {return false;}
    }
    public MailResult send(TaskSource task) {
        if(!configured())return new MailResult("FAILED","MAIL_NOT_CONFIGURED","메일 설정이 없어 시스템 알림만 처리했습니다.");
        if(!address(task.emailAddress()))return new MailResult("FAILED","RECIPIENT_EMAIL_INVALID","이메일 주소 형식을 확인해 주세요.");
        try {
            var sender=new JavaMailSenderImpl();sender.setHost(settings.getProperty("MAIL_HOST"));sender.setPort(Integer.parseInt(settings.getProperty("MAIL_PORT","587")));
            sender.setUsername(settings.getProperty("MAIL_USERNAME"));sender.setPassword(settings.getProperty("MAIL_PASSWORD"));
            Properties properties=sender.getJavaMailProperties();
            properties.setProperty("mail.smtp.auth",Boolean.toString(sender.getUsername()!=null&&!sender.getUsername().isBlank()));
            properties.setProperty("mail.smtp.starttls.enable",settings.getProperty("MAIL_STARTTLS","true"));
            properties.setProperty("mail.smtp.starttls.required",settings.getProperty("MAIL_STARTTLS","true"));
            properties.setProperty("mail.smtp.connectiontimeout","5000");properties.setProperty("mail.smtp.timeout","10000");properties.setProperty("mail.smtp.writetimeout","10000");
            MimeMessage message=new MimeMessage(sender.getSession()) {
                @Override protected void updateMessageID() throws MessagingException {setHeader("Message-ID",task.messageId());}
            };
            message.setFrom(new InternetAddress(settings.getProperty("MAIL_FROM")));
            message.setRecipient(Message.RecipientType.TO,new InternetAddress(task.emailAddress(),true));
            message.setSubject(task.subject(),"UTF-8");message.setText(task.body(),"UTF-8","html");
            sender.send(message);
            return new MailResult("SENT",null,null);
        } catch(MailSendException e) {
            // Only an explicit SMTP rejection proves non-acceptance; transport interruptions are uncertain.
            boolean rejected=Arrays.stream(e.getMessageExceptions()).anyMatch(DunningDeliveryMail::rejected);
            return rejected?new MailResult("FAILED","MAIL_REJECTED","메일 서버가 발송을 거절했습니다."):
                new MailResult("UNKNOWN","MAIL_ACCEPTANCE_UNKNOWN","메일 접수 여부를 확인해 주세요. 자동 재발송하지 않습니다.");
        } catch(Exception e) {
            return new MailResult("UNKNOWN","MAIL_ACCEPTANCE_UNKNOWN","메일 접수 여부를 확인해 주세요. 자동 재발송하지 않습니다.");
        }
    }
    private static boolean rejected(Throwable error) {
        if(error instanceof SendFailedException failed && failed.getValidSentAddresses()!=null&&failed.getValidSentAddresses().length>0)return false;
        if(error instanceof org.eclipse.angus.mail.smtp.SMTPAddressFailedException failed)return failed.getReturnCode()>=400;
        if(error instanceof org.eclipse.angus.mail.smtp.SMTPSendFailedException failed)return failed.getReturnCode()>=400;
        if(error instanceof MessagingException mail&&mail.getNextException()!=null)return rejected(mail.getNextException());
        return false;
    }
    public static String escape(String value) {return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    public String body(DeliveryPreview preview) {
        String link=publicUrl();
        return "<p>프로젝트: "+escape(preview.projectName())+"</p><p>청구: "+escape(preview.billId())+"</p><p>납기: "+preview.dueDate()
            +"</p><p>잔액: "+preview.unpaidAmount()+"원</p><p>단계: "+preview.currentStep()+"</p><p>기준시각: "+preview.observedAt()+"</p>"
            +(link==null?"<p>업무화면: UI-13</p>":"<a href=\""+escape(link+"/UI-13")+"\">청구·입금 업무화면</a>");
    }
}

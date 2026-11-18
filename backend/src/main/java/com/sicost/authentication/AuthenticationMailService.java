package com.sicost.authentication;

import com.sicost.common.api.ApiException;
import com.sicost.receivablesops.DunningDeliveryMail;
import jakarta.mail.MessagingException;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/** Authentication mail is synchronous and memory-only, never a delivery task or DB queue. */
@Service
public class AuthenticationMailService {
    private final Environment environment;
    public AuthenticationMailService(Environment environment) {this.environment=environment;}
    public void requireConfigured() {
        try {
            int port=Integer.parseInt(environment.getProperty("MAIL_PORT","587"));
            if(!environment.getProperty("MAIL_ENABLED",Boolean.class,false)
                    || !environment.getProperty("MAIL_HOST","").matches("[A-Za-z0-9._-]{1,253}")
                    || !DunningDeliveryMail.address(environment.getProperty("MAIL_FROM","")) || port<1 || port>65535
                    || environment.getProperty("MAIL_USERNAME","").isBlank()!=environment.getProperty("MAIL_PASSWORD","").isBlank())
                throw new IllegalArgumentException();
        } catch(RuntimeException e) {throw new ApiException(503,"MAIL_NOT_CONFIGURED","인증 메일 설정을 확인해 주세요. 관리자에게 문의해 주세요.");}
    }
    public String send(String address,String subject,String text) {
        requireConfigured();
        if(!DunningDeliveryMail.address(address))return "FAILED";
        var sender=new JavaMailSenderImpl();
        sender.setHost(environment.getProperty("MAIL_HOST"));sender.setPort(Integer.parseInt(environment.getProperty("MAIL_PORT","587")));
        sender.setUsername(environment.getProperty("MAIL_USERNAME"));sender.setPassword(environment.getProperty("MAIL_PASSWORD"));
        var properties=sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth",Boolean.toString(!environment.getProperty("MAIL_USERNAME","").isBlank()));
        properties.setProperty("mail.smtp.starttls.enable",environment.getProperty("MAIL_STARTTLS","true"));
        properties.setProperty("mail.smtp.starttls.required",environment.getProperty("MAIL_STARTTLS","true"));
        properties.setProperty("mail.smtp.connectiontimeout","5000");properties.setProperty("mail.smtp.timeout","10000");properties.setProperty("mail.smtp.writetimeout","10000");
        properties.setProperty("mail.debug","false");
        var message=new SimpleMailMessage();message.setFrom(environment.getProperty("MAIL_FROM"));message.setTo(address);message.setSubject(subject);message.setText(text);
        try {sender.send(message);return "ACCEPTED";}
        catch(MailSendException e) {return Arrays.stream(e.getMessageExceptions()).anyMatch(AuthenticationMailService::rejected)?"FAILED":"UNKNOWN";}
        catch(org.springframework.mail.MailAuthenticationException e) {return "FAILED";}
        catch(RuntimeException e) {return "UNKNOWN";}
        finally {message.setText("");}
    }
    private static boolean rejected(Throwable error) {
        if(error instanceof org.eclipse.angus.mail.smtp.SMTPAddressFailedException e)return e.getReturnCode()>=400;
        if(error instanceof org.eclipse.angus.mail.smtp.SMTPSendFailedException e)return e.getReturnCode()>=400;
        return error instanceof MessagingException e && e.getNextException()!=null && rejected(e.getNextException());
    }
}

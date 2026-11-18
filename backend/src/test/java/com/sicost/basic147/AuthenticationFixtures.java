package com.sicost.basic147;

import com.sicost.common.TimeConfiguration;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Additive synthetic fixtures for an explicitly opted-in disposable database. Never seeds production. */
public final class AuthenticationFixtures {
    public static final List<String> ROLES=List.of("ADMIN","EXEC","FIN","SALES_LDR","PM");
    public static final class Account {
        private final String id,password;
        private Account(String id,String password) {this.id=id;this.password=password;}
        public String id() {return id;}
        public String password() {return password;}
        @Override public String toString() {return "SyntheticAuthenticationAccount[redacted]";}
    }
    public static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;
        public MutableClock(Instant instant) {this(new AtomicReference<>(instant),TimeConfiguration.SEOUL);}
        private MutableClock(AtomicReference<Instant> instant,ZoneId zone) {this.instant=instant;this.zone=zone;}
        public void set(Instant value) {instant.set(Objects.requireNonNull(value));}
        public void advance(Duration value) {instant.updateAndGet(now->now.plus(value));}
        @Override public ZoneId getZone() {return zone;}
        @Override public Clock withZone(ZoneId value) {return new MutableClock(instant,value);}
        @Override public Instant instant() {return instant.get();}
    }
    private final JdbcTemplate sql;
    private final Clock clock;
    private final String prefix="fx147"+UUID.randomUUID().toString().replace("-","").substring(0,6);
    private final SecureRandom random=new SecureRandom();
    private final Map<String,String> baseline;
    private final Map<String,Long> sequences;
    public AuthenticationFixtures(JdbcTemplate sql,Clock clock) {
        if(!"true".equals(System.getenv("BASIC147_INTEGRATION")))throw new IllegalStateException("Disposable database opt-in is required.");
        this.sql=Objects.requireNonNull(sql);this.clock=Objects.requireNonNull(clock);this.baseline=fingerprint();this.sequences=sequences();
    }
    public Account account(Collection<String> roles,String status,Integer passwordAgeDays,boolean temporary) {
        if(!Set.of("USE","LOCK","STOP").contains(status))throw new IllegalArgumentException("Unknown synthetic status");
        String id=prefix+UUID.randomUUID().toString().replace("-","").substring(0,8);
        byte[] bytes=new byte[18];random.nextBytes(bytes);String password="Aa1!"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);Arrays.fill(bytes,(byte)0);
        LocalDateTime changed=passwordAgeDays==null?null:LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL).minusDays(passwordAgeDays);
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_chg_dttm,temp_pwd_yn,pwd_err_cnt,user_stat_cd,mfa_use_yn,mfa_err_cnt,reg_user_id,reg_dttm) VALUES (?,?,?,?,?,?,0,?,?,0,'SYSTEM',?)",id,"합성 인증 사용자",id+"@example.test",new BCryptPasswordEncoder(10).encode(password),changed,temporary?"Y":"N",status,roles.contains("ADMIN")?"Y":"N",LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL));
        for(String role:roles)sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id,reg_dttm) VALUES (?,?,'SYSTEM',?)",id,role,LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL));
        return new Account(id,password);
    }
    public Map<String,Account> fiveRoles() {Map<String,Account> result=new LinkedHashMap<>();for(String role:ROLES)result.put(role,account(List.of(role),"USE",1,false));return Map.copyOf(result);}
    public Map<String,Account> statesAndAgeBoundaries() {return Map.of("USE89",account(List.of("PM"),"USE",89,false),"USE90",account(List.of("PM"),"USE",90,false),"UNKNOWN_DATE",account(List.of("PM"),"USE",null,false),"TEMP",account(List.of("PM"),"USE",90,true),"LOCK",account(List.of("ADMIN"),"LOCK",1,false),"STOP",account(List.of("PM"),"STOP",1,false),"PM_FIN",account(List.of("PM","FIN"),"USE",1,false));}
    public String role(String scope,boolean sensitive,boolean active) {
        if(!Set.of("ALL","PART","PROJ").contains(scope))throw new IllegalArgumentException("Unknown synthetic scope");
        String id=prefix+UUID.randomUUID().toString().replace("-","").substring(0,5);
        sql.update("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,snstv_view_yn,sys_role_yn,sort_ord,use_yn,reg_user_id,reg_dttm) VALUES (?,?,?,?,'N',999,?,'SYSTEM',?)",id,"합성 추가 역할 "+id,scope,sensitive?"Y":"N",active?"Y":"N",LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL));return id;
    }
    public void scope(Account account,String type,String part,String project) {
        if(!account.id().startsWith(prefix) || !Set.of("PART","PROJ").contains(type))throw new IllegalArgumentException("Synthetic fixture ownership is required");
        sql.update("INSERT INTO tb_sy_user_scope(user_id,scope_sn,scope_type_cd,part_id,proj_id,reg_user_id,reg_dttm) SELECT ?,coalesce(max(scope_sn),0)+1,?,?,?,'SYSTEM',? FROM tb_sy_user_scope WHERE user_id=?",account.id(),type,part,project,LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL),account.id());
    }
    /** Digests remain in memory; no hashes, ciphertext, accounts or baseline values are printed. */
    public void assertExistingConfigurationPreserved() {
        if(!baseline.equals(fingerprint()))throw new AssertionError("A pre-existing configuration or encrypted-account row changed");
        var current=sequences();sequences.forEach((name,value)->{if(!current.containsKey(name) || current.get(name)<value)throw new AssertionError("A pre-existing sequence was reset");});
    }
    private Map<String,Long> sequences() {
        Map<String,Long> values=new TreeMap<>();
        sql.queryForList("SELECT sequencename,last_value FROM pg_sequences WHERE schemaname=current_schema()").forEach(row->{if(row.get("last_value") instanceof Number value)values.put(row.get("sequencename").toString(),value.longValue());});return Map.copyOf(values);
    }
    public static final class ReceivedMail {
        private final String body;
        private ReceivedMail(String body) {this.body=body;}
        public String body() {return body;}
        @Override public String toString() {return "SyntheticCollectedMail[redacted]";}
    }
    /** Reads the actual opt-in SMTP collector; never fabricates delivery or persists secrets. */
    public List<ReceivedMail> collectedMail(Account account) throws Exception {
        String endpoint=System.getenv("BASIC147_MAIL_COLLECTOR");if(endpoint==null)throw new IllegalStateException("SMTP collector is not configured");
        var response=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(endpoint+"/api/v2/messages")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new IllegalStateException("SMTP collector request failed");
        var result=new ArrayList<ReceivedMail>();var root=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
        for(var item:root.path("items"))if(item.path("Content").path("Headers").path("To").toString().contains(account.id()+"@example.test")) {
            String body=item.path("Content").path("Body").asText();
            String encoding=item.path("Content").path("Headers").path("Content-Transfer-Encoding").path(0).asText();
            if("base64".equalsIgnoreCase(encoding))body=new String(Base64.getMimeDecoder().decode(body),java.nio.charset.StandardCharsets.UTF_8);
            result.add(new ReceivedMail(body));
        }
        return List.copyOf(result);
    }
    private Map<String,String> fingerprint() {
        Map<String,String> result=new TreeMap<>();
        for(String table:List.of("tb_sy_user","tb_sy_role","tb_sy_user_role","tb_sy_role_menu_auth","tb_sy_user_scope","tb_sy_user_pref","tb_sy_menu","tb_sy_oper_std","tb_bs_clnt")) {
            String exclusion=switch(table) {case "tb_sy_user","tb_sy_user_role","tb_sy_user_scope","tb_sy_user_pref"->" WHERE user_id NOT LIKE ?";case "tb_sy_role","tb_sy_role_menu_auth"->" WHERE role_id NOT LIKE ?";default->"";};
            String query="SELECT md5(coalesce(string_agg(to_jsonb(t)::text,',' ORDER BY to_jsonb(t)::text),'')) FROM "+table+" t"+exclusion;
            result.put(table,exclusion.isEmpty()?sql.queryForObject(query,String.class):sql.queryForObject(query,String.class,prefix+"%"));
        }
        return Map.copyOf(result);
    }
}

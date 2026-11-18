"""Explicit disposable runtime API verification; secrets stay in process memory."""
import json, os, re, secrets, subprocess, urllib.request, urllib.error, http.cookiejar, email
BASE=os.environ.get('REQ18_BASE','http://127.0.0.1:32891')
RESULT=[]
def check(name,good,detail=''):
    RESULT.append({'test':name,'pass':bool(good),'detail':detail});print(('PASS ' if good else 'FAIL ')+name,flush=True)
class Client:
    def __init__(self):
        self.cookies=http.cookiejar.CookieJar();self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies));self.csrf={}
    def req(self,method,path,body=None,extra=None):
        headers={'Content-Type':'application/json',**self.csrf,**(extra or {})}
        request=urllib.request.Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,method=method,headers=headers)
        try:r=self.http.open(request,timeout=30)
        except urllib.error.HTTPError as e:r=e
        raw=r.read()
        try:value=json.loads(raw)
        except ValueError:value={}
        return r.status,value,r.headers
    def token(self):
        s,v,h=self.req('GET','/api/auth/csrf');d=v['data'];self.csrf={d['headerName']:d['token']}
    def login(self,id,pwd):self.token();return self.req('POST','/api/auth/login',{'userId':id,'password':pwd})
def mail(to,pattern):
    data=json.loads(subprocess.check_output(['docker','exec','codex-req18-mail','wget','-qO-','http://127.0.0.1:8025/api/v2/messages'],timeout=15))
    for item in data['items']:
        if any((x['Mailbox']+'@'+x['Domain'])==to for x in item['To']):
            message=email.message_from_string(item['Raw']['Data']); text=message.get_payload(decode=True).decode(message.get_content_charset() or 'utf-8');found=re.search(pattern,text)
            if found:return found.group(1)
    raise AssertionError('Expected actual SMTP message missing')

def main():
    admin_password='Codex1!'+secrets.token_hex(12)
    env=os.environ.copy();env['AUTH_INITIAL_PASSWORD']=admin_password
    command=['docker','run','--rm','--network','sicost-basic147-test','-v',os.getcwd()+'/backend/target/sicost.jar:/app/sicost.jar:ro','-e','DB_URL=jdbc:postgresql://sicost-basic147-postgres:5432/codex_req18','-e','DB_USER=postgres','-e','DB_PASSWORD=unused','-e','AUTH_INITIAL_PASSWORD','eclipse-temurin:17-jre','java','-Dloader.main=com.sicost.authentication.AuthenticationAdminCli','-cp','/app/sicost.jar','org.springframework.boot.loader.launch.PropertiesLauncher','reset','admin','codex-admin@example.test']
    run=subprocess.run(command,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=90)
    check('operator CLI initial reset',run.returncode==0)
    if run.returncode: return
    anon=Client();s,v,h=anon.req('GET','/api/auth/session');check('anonymous minimal session',s==200 and set(v['data'])=={'nextStep','serverTime'})
    s,v,h=anon.req('POST','/api/auth/login',{});check('missing CSRF rejected',s==403 and v.get('code')=='CSRF_INVALID')
    s,v,h=anon.req('GET','/api/current-user',extra={'X-User-ID':'SYSTEM','X-Role':'ADMIN','X-Request-Id':'forged'});check('identity spoof rejected and requestId generated',s==401 and h['X-Request-Id']!='forged')
    anon.token();s,v,h=anon.req('POST','/api/auth/login',{'userId':'codex-no-such-user','password':'Wrong1!'},extra={'X-Request-Id':'forged-audit-entry'})
    check('unknown account uniform failure',s==401 and v.get('code')=='LOGIN_FAILED',str(s))
    correlation=h['X-Request-Id']
    query="SELECT count(*) FROM tb_sy_accs_log WHERE user_id='ANONYMOUS' AND reg_user_id='ANONYMOUS' AND act_type_cd='LOGIN_FAIL' AND trgt_desc LIKE '%"+correlation+"%' AND trgt_desc NOT LIKE '%forged-audit-entry%'"
    recorded=subprocess.check_output(['docker','exec','sicost-basic147-postgres','psql','-U','postgres','-d','codex_req18','-At','-c',query],text=True).strip()
    check('audit requestId and anonymous actor from server',recorded=='1')
    admin=Client();s,v,h=admin.login('admin',admin_password);check('ADMIN temporary password requires MFA first',s==200 and v['data']['nextStep']=='MFA')
    s,v,h=admin.req('GET','/api/current-user');check('pending cannot read business',s==401 and v.get('code')=='AUTH_INCOMPLETE')
    admin.token()
    otp=mail('codex-admin@example.test',r'(?<!\d)(\d{6})(?!\d)')
    s,v,h=admin.req('POST','/api/auth/mfa/verify',{'code':otp});check('actual SMTP MFA then required password',s==200 and v['data']['nextStep']=='CHANGE_PASSWORD')
    admin.token();final_password='Final1!'+secrets.token_hex(12)
    s,v,h=admin.req('POST','/api/auth/password/required',{'newPassword':final_password,'confirmPassword':final_password});check('required password completes authentication',s==200 and v['data']['nextStep']=='AUTHENTICATED')
    admin.token();s,v,h=admin.req('DELETE','/api/assignments');check('unsupported DELETE yields 405 JSON',s==405 and v.get('code')=='METHOD_NOT_ALLOWED')
    for role in ['EXEC','FIN','SALES_LDR','PM']:
        id='cx'+role.lower().replace('_','')+secrets.token_hex(2);email=id+'@example.test'
        s,v,h=admin.req('POST','/api/business-users',{'userId':id,'name':'합성 '+role,'email':email,'roleIds':[role],'headOffice':False,'projectAssignments':[]})
        check('API seed '+role,s==201, str(s))
        if s!=201:continue
        pwd=mail(email,r'임시 비밀번호:\s*(\S+)')
        user=Client();s,v,h=user.login(id,pwd);check(role+' requires password change',s==200 and v['data']['nextStep']=='CHANGE_PASSWORD')
        new='User1!'+secrets.token_hex(12);user.token();s,v,h=user.req('POST','/api/auth/password/required',{'newPassword':new,'confirmPassword':new});check(role+' authentication completed',s==200)
        s,v,h=user.req('GET','/api/current-user');check(role+' actual own principal',s==200 and v['data']['userId']==id)
        cap_status,cap,_=user.req('GET','/api/current-user/capabilities');check(role+' capability endpoint',cap_status==200);allowed='INQ' in cap['data']['screens'].get('UI-01',[])
        s,v,h=user.req('GET','/api/projects');check(role+' scoped project count',(s==200 and v['data'].get('total',-1)>=0) if allowed else s==403,str(s))
        s,v,h=user.req('GET','/api/projects/DOES-NOT-EXIST');check(role+' outside object policy',s==(404 if allowed else 403),str(s))
        user.token();s,v,h=user.req('POST','/api/auth/logout',{});check(role+' logout',s==200)
        s,v,h=user.req('GET','/api/current-user');check(role+' old session denied',s==401)
    admin.token();admin.req('POST','/api/auth/logout',{})

try:main()
finally:
    with open('/tmp/req18-api-results.json','w') as out:json.dump(RESULT,out,ensure_ascii=False,indent=2)

if any(not result["pass"] for result in RESULT):
    raise SystemExit(1)

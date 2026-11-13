"""独立测试库验证创作者注册、岗位申请、项目归属和素材授权。"""
import base64
import json
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

root = Path(__file__).resolve().parents[1]
assert '/frameflow_test' in (root / '.env.test').read_text()
base = 'http://localhost:8082/api'
run = str(time.time_ns())
password = 'Register2026!'
tokens, users, results = {}, {}, []


def request(method, path, body=None, actor='admin', expected=200, raw=False, content_type=None):
    headers = {'Authorization': 'Bearer ' + tokens[actor]} if actor in tokens else {}
    if body is not None and not isinstance(body, bytes):
        body = json.dumps(body).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    if content_type:
        headers['Content-Type'] = content_type
    req = urllib.request.Request(base + path, data=body, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    payload = response.read()
    allowed = expected if isinstance(expected, tuple) else (expected,)
    assert response.status in allowed, f'{method} {path}: expected {expected}, got {response.status}: {payload.decode(errors="replace")}'
    if raw:
        return payload
    envelope = json.loads(payload)
    assert envelope['code'] == (0 if response.status == 200 else response.status), envelope
    return envelope['data'] if response.status == 200 else envelope['msg']


def passed(name):
    results.append(name)
    print('PASS ' + name, flush=True)


def register(actor):
    username = actor + '_' + run
    body = {'username':username,'displayName':'身份验收 '+actor,'password':password,'confirmPassword':password,'roles':['ADMIN'],'creatorId':1}
    request('POST', '/auth/register', body, actor='')
    login = request('POST', '/auth/login', {'username':username,'password':password}, actor='')
    tokens[actor], users[actor] = login['token'], login['user']
    assert login['user']['roles'] == ['CREATOR'] and login['user']['creatorId'] != 1
    return body


def apply(actor, role):
    return request('POST', '/role-applications', {'requestedRole':role,'reason':'申请承担对应岗位职责 '+run}, actor=actor)['id']


def approve(application):
    request('POST', f'/role-applications/{application}/approve', {'reviewNote':'确认岗位职责，批准申请'})


def project_body(name, members, owner=2):
    return {'name':name+' '+run,'clientName':'身份验证工作室','description':'项目范围验收','budget':1000,'startDate':'2026-01-01','dueDate':'2026-12-31','status':'PRODUCING','ownerId':owner,'creatorIds':members}


def work_body(project, asset):
    return {'projectId':project,'title':'授权作品 '+run,'description':'范围验收','status':'READY','assetIds':[asset]}


def upload(actor):
    png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z4k4AAAAASUVORK5CYII=')
    fields = {'name':'个人素材 '+actor+' '+run,'kind':'IMAGE','creatorId':users[actor]['creatorId'],'licenseType':'ORIGINAL','licenseStart':'2026-01-01','licenseEnd':'2026-12-31','allowedPlatforms':'ALL','tags':'身份验证','description':'本人上传'}
    boundary = 'RegistrationBoundary' + run
    chunks = [f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode() for key,value in fields.items()]
    chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="verification.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+png+b'\r\n')
    chunks.append(f'--{boundary}--\r\n'.encode())
    return request('POST','/assets',b''.join(chunks),actor=actor,content_type='multipart/form-data; boundary='+boundary)['id']


for actor in ['admin','producer','finance','reviewer']:
    login = request('POST','/auth/login',{'username':actor,'password':'FrameFlow2026!'},actor='')
    tokens[actor], users[actor] = login['token'], login['user']
body = register('owner')
register('member')
owner, member = users['owner'], users['member']
assert owner['creatorId'] != member['creatorId']
request('POST','/auth/register',body,actor='',expected=409)
request('POST','/auth/register',{**body,'username':'bad space'},actor='',expected=400)
request('POST','/auth/register',{**body,'username':'confirm_'+run,'confirmPassword':'different'},actor='',expected=400)
passed('注册独立创作者档案，强制默认CREATOR及输入校验')

for path in ['/projects','/works','/assets','/platform-accounts','/revenues','/settlements']:
    assert request('GET',path,actor='owner')['total'] == 0,path
request('GET','/users',actor='owner',expected=403)
request('GET','/role-applications?scope=ALL',actor='owner',expected=403)
request('POST','/projects',project_body('创作者不能立项',[owner['creatorId']]),actor='owner',expected=403)
passed('新创作者初始业务为空，仅开放本人范围及参与项目')

profile={'name':'验收创作者','handle':owner['username'],'email':'creator@example.com','phone':'','bio':'自行完善的创作者资料','status':'ACTIVE'}
request('PUT',f"/creators/{owner['creatorId']}",profile,actor='owner')
request('PUT',f"/creators/{member['creatorId']}",profile,actor='owner',expected=403)
account_body={'platformId':1,'creatorId':owner['creatorId'],'name':'本人发布账号','externalId':'OWN-'+run,'status':'ACTIVE'}
owner_account=request('POST','/platform-accounts',account_body,actor='owner')['id']
request('POST','/platform-accounts',{**account_body,'creatorId':member['creatorId'],'externalId':'DENIED-'+run},actor='owner',expected=403)
member_account=request('POST','/platform-accounts',{**account_body,'creatorId':member['creatorId'],'externalId':'MEMBER-'+run},actor='member')['id']
passed('创作者维护本人资料和平台账号，其他成员资料改写受限')

application=apply('owner','PRODUCER')
request('POST','/role-applications',{'requestedRole':'PRODUCER','reason':'重复申请'},actor='owner',expected=409)
request('POST','/role-applications',{'requestedRole':'CREATOR','reason':'重复基础身份'},actor='owner',expected=400)
assert request('GET','/auth/me',actor='owner')['roles']==['CREATOR']
assert request('GET','/role-applications',actor='member')['total']==0
mine=request('GET','/role-applications?status=PENDING',actor='owner')
assert mine['total']==1 and mine['items'][0]['id']==application
assert request('GET',f"/role-applications?scope=ALL&q={owner['username']}")['total']==1
request('POST',f'/role-applications/{application}/approve',{},actor='member',expected=403)
passed('岗位申请记录可查询，待审批不授予权限，重复申请与他人审批受限')

with ThreadPoolExecutor(max_workers=2) as pool:
    outcomes=list(pool.map(lambda _:request('POST',f'/role-applications/{application}/approve',{'reviewNote':'并发审批验证'},expected=(200,409)),range(2)))
assert sum(value is None or isinstance(value,dict) for value in outcomes)==1,outcomes
assert set(request('GET','/auth/me',actor='owner')['roles'])=={'PRODUCER','CREATOR'}
request('POST','/role-applications',{'requestedRole':'PRODUCER','reason':'已持有岗位'},actor='owner',expected=409)
rejected=apply('owner','FINANCE')
request('POST',f'/role-applications/{rejected}/reject',{'reviewNote':''},expected=400)
request('POST',f'/role-applications/{rejected}/reject',{'reviewNote':'当前职责为项目统筹'})
rejected_rows=request('GET','/role-applications?status=REJECTED',actor='owner')['items']
assert rejected_rows[0]['reviewNote']=='当前职责为项目统筹'
retry=apply('owner','FINANCE')
assert retry!=rejected
passed('审批一次生效并实时追加岗位，驳回意见保存且可重新申请')

project_a=request('POST','/projects',project_body('统筹负责项目',[member['creatorId']],owner=2),actor='owner')['id']
project_b=request('POST','/projects',project_body('作为创作者参与',[owner['creatorId'],member['creatorId']]),actor='producer')['id']
project_c=request('POST','/projects',project_body('无关项目',[member['creatorId']]),actor='producer')['id']
assert request('GET',f'/projects/{project_a}',actor='owner')['ownerId']==owner['id']
visible={row['id'] for row in request('GET','/projects?pageSize=100',actor='owner')['items']}
assert visible=={project_a,project_b},visible
request('GET',f'/projects/{project_c}',actor='owner',expected=403)
request('PUT',f'/projects/{project_b}',project_body('越权变更',[owner['creatorId']]),actor='owner',expected=403)
request('PUT',f'/projects/{project_a}',project_body('不能移交负责人',[member['creatorId']],owner=2),actor='owner',expected=403)
options=request('GET','/options',actor='owner')
assert {row['id'] for row in options['projects']}=={project_a,project_b}
assert request('GET','/dashboard',actor='owner')['metrics']['activeProjects']==2
for row in options['creators'] + request('GET','/creators?pageSize=100',actor='owner')['items']:
    if row['id'] != owner['creatorId']:
        assert set(row) <= {'id','name','handle','status'}, row
    else:
        assert row['email'] == profile['email']
passed('统筹负责项目与个人参与项目合并可见，项目成员及负责人修改限负责范围')

own_asset=upload('owner')
shared_asset=upload('member')
request('GET',f'/assets/{shared_asset}',actor='owner',expected=403)
request('GET',f'/assets/{shared_asset}/file',actor='owner',expected=403)
request('PUT',f'/assets/{shared_asset}/projects',{'projectIds':[project_a]},actor='member')
asset=request('GET',f'/assets/{shared_asset}',actor='owner')
assert project_a in asset['sharedProjectIds']
assert request('GET',f'/assets/{shared_asset}/file',actor='owner',raw=True)
request('PUT',f'/assets/{shared_asset}',{'name':'统筹不可改他人素材'},actor='owner',expected=403)
request('PUT',f'/assets/{shared_asset}/projects',{'projectIds':[project_b]},actor='owner',expected=403)
request('PUT',f'/assets/{own_asset}/projects',{'projectIds':[project_c]},actor='owner',expected=403)
passed('素材显式授权给项目，统筹可读取使用，归属及授权仍由素材所有者管理')

work_a=request('POST','/works',work_body(project_a,shared_asset),actor='owner')['id']
request('POST','/works',work_body(project_b,shared_asset),actor='owner',expected=403)
work_b=request('POST','/works',work_body(project_b,own_asset),actor='owner')['id']
assert project_b in request('GET',f'/assets/{own_asset}',actor='owner')['sharedProjectIds']
request('PUT',f'/assets/{shared_asset}/projects',{'projectIds':[]},actor='member',expected=409)
request('POST','/works',work_body(project_c,own_asset),actor='owner',expected=403)
passed('作品引用按目标项目授权，本人素材自动授权，已有引用保留授权关系')

publication=request('POST','/publications',{'workId':work_a,'platformAccountId':member_account,'externalId':'PUB-'+run,'publishedAt':'2026-11-13T12:00:00'},actor='owner')['id']
request('POST','/publications',{'workId':work_b,'platformAccountId':member_account,'externalId':'DENIED-'+run,'publishedAt':'2026-11-13T12:00:00'},actor='owner',expected=403)
cost_body={'projectId':project_a,'category':'拍摄','description':'负责项目成本','amount':1,'costDate':'2026-11-13'}
request('POST','/costs',cost_body,actor='owner')
request('POST','/costs',{**cost_body,'projectId':project_b},actor='owner',expected=403)
assert all(row['createdBy']==owner['id'] for row in request('GET','/costs',actor='owner')['items'])
assert all(row['creatorId']==owner['creatorId'] for row in request('GET','/balances',actor='owner'))
passed('统筹在负责项目发布及登记成本，创作者兼任统筹仍仅查看个人财务')

register('financeapp')
finance_application=apply('financeapp','FINANCE')
approve(finance_application)
assert set(request('GET','/auth/me',actor='financeapp')['roles'])=={'FINANCE','CREATOR'}
assert len(request('GET','/balances',actor='financeapp'))>1
request('GET',f'/projects/{project_c}',actor='financeapp')
request('POST','/works',work_body(project_c,shared_asset),actor='financeapp',expected=403)
request('PUT',f'/assets/{shared_asset}',{'name':'财务不可改他人素材'},actor='financeapp',expected=403)
request('POST','/costs',{**cost_body,'projectId':project_c},actor='financeapp')
request('GET','/users',actor='financeapp',expected=403)
passed('财务申请获批后全量财务可用，生产读取权限不扩大生产写入')

register('reviewapp')
approve(apply('reviewapp','REVIEWER'))
request('GET','/audit-logs',actor='reviewapp')
request('GET','/settlements',actor='reviewapp')
request('POST','/revenues',{'publicationId':publication,'sourceRef':'DENIED-'+run,'revenueDate':'2026-11-13','kind':'INCOME','grossAmount':10,'feeAmount':0},actor='reviewapp',expected=403)
request('POST','/payments',{'creatorId':member['creatorId'],'amount':1,'paidAt':'2026-11-13T12:00:00','reference':'DENIED-'+run},actor='reviewapp',expected=403)
passed('审核申请获批后可核查业务，收入录入与付款保持财务职责')

register('adminapp')
admin_request=apply('adminapp','ADMIN')
request('POST',f'/role-applications/{admin_request}/approve',{},actor='adminapp',expected=403)
approve(admin_request)
request('GET','/users',actor='adminapp')
self_request=apply('adminapp','FINANCE')
request('POST',f'/role-applications/{self_request}/approve',{},actor='adminapp',expected=(403,409))
request('POST',f'/role-applications/{self_request}/reject',{'reviewNote':'无需兼任'})
passed('管理员岗位由已有管理员批准，已是管理员也不能审批本人申请')

request('PUT',f"/users/{owner['id']}",{'displayName':owner['displayName'],'roles':[],'creatorId':None,'status':'ACTIVE'})
current=request('GET','/auth/me',actor='owner')
assert current['roles']==['CREATOR'] and current['creatorId']==owner['creatorId'],current
request('GET',f'/projects/{project_a}',actor='owner',expected=403)
assert {row['id'] for row in request('GET','/projects',actor='owner')['items']}=={project_b}
request('GET',f'/assets/{own_asset}',actor='owner')
request('POST','/projects',project_body('已撤回统筹',[owner['creatorId']]),actor='owner',expected=403)
passed('撤回附加岗位后保留创作者及资产，原统筹权限立即撤回')

request('PUT','/auth/password',{'oldPassword':password,'newPassword':'Updated2026!'},actor='owner')
login=request('POST','/auth/login',{'username':owner['username'],'password':'Updated2026!'},actor='')
tokens['owner']=login['token']
for actor in ['owner','member','financeapp','reviewapp','adminapp']:
    user=users[actor]
    request('PUT',f"/users/{user['id']}",{'displayName':user['displayName'],'roles':['CREATOR'],'creatorId':user['creatorId'],'status':'INACTIVE'})
request('GET','/auth/me',actor='owner',expected=401)
request('POST','/auth/login',{'username':owner['username'],'password':'Updated2026!'},actor='',expected=401)
passed('个人改密保持可用，停用账号使已有会话和新登录失效')

output=root/'docs/registration-verification.json'
output.write_text(json.dumps({'api':base,'database':'frameflow_test','run':run,'count':len(results),'passed':results},ensure_ascii=False,indent=2),encoding='utf-8')
print(f'Passed {len(results)} registration and role verification groups. Results: {output}')

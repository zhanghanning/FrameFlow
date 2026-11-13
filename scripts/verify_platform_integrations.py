"""在显式启用平台模块的独立测试服务上验证配置状态、内容权限与账号约束。"""
import json
import shlex
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

root=Path(__file__).resolve().parents[1]
settings={}
for line in (root/'.env.test').read_text().splitlines():
    if '=' in line and not line.startswith('#'):
        key,value=line.split('=',1)
        settings[key]=(shlex.split(value) or [''])[0]
assert '/frameflow_test' in settings['DB_URL']
base='http://localhost:8082/api'
run=str(time.time_ns())
tokens,users,results={},{},[]
password='PlatformTest2026!'


def api(method,path,body=None,actor='admin',expected=200):
    headers={'Authorization':'Bearer '+tokens[actor]} if actor in tokens else {}
    if body is not None:
        body=json.dumps(body).encode()
        headers['Content-Type']='application/json'
    try:
        response=urllib.request.urlopen(urllib.request.Request(base+path,data=body,headers=headers,method=method),timeout=30)
    except urllib.error.HTTPError as error:
        response=error
    payload=json.loads(response.read())
    allowed=expected if isinstance(expected,tuple) else (expected,)
    assert response.status in allowed,(method,path,response.status,payload)
    return payload['data'] if response.status==200 else payload


def sql(statement):
    with tempfile.NamedTemporaryFile(mode='w',prefix='platform-db-',suffix='.cnf',dir=root/'.runtime') as config:
        config.write('[client]\nuser='+settings['DB_USER']+'\npassword='+settings['DB_PASSWORD']+'\nhost=localhost\n')
        config.flush()
        return subprocess.check_output(['mysql','--defaults-extra-file='+config.name,'--default-character-set=utf8mb4','-N','frameflow_test','-e',statement],text=True).strip()


def passed(name):
    results.append(name)
    print('PASS '+name,flush=True)


for actor in ['admin','producer','finance','reviewer']:
    data=api('POST','/auth/login',{'username':actor,'password':'FrameFlow2026!'},actor='')
    tokens[actor],users[actor]=data['token'],data['user']
integration_status=api('GET','/platform-integrations',expected=(200,404))
if isinstance(integration_status,dict) and integration_status.get('code')==404:
    raise SystemExit('平台接入模块默认关闭。需要验证此可选模块时，请先以 PLATFORM_INTEGRATIONS_ENABLED=true bash scripts/test_backend.sh 启动独立测试服务。')

for actor in ['owner','other']:
    username='sync_'+actor+'_'+run
    api('POST','/auth/register',{'username':username,'displayName':'平台验收 '+actor,'password':password,'confirmPassword':password},actor='')
    data=api('POST','/auth/login',{'username':username,'password':password},actor='')
    tokens[actor],users[actor]=data['token'],data['user']

integrations=api('GET','/platform-integrations',actor='owner')
assert {x['code'] for x in integrations}=={'BILIBILI','DOUYIN','KUAISHOU','XIAOHONGSHU','YOUTUBE','INSTAGRAM'}
assert all(not x['canConnect'] for x in integrations)
assert {x['code']:x['status'] for x in integrations}['YOUTUBE']=='NOT_CONFIGURED'
assert {x['code']:x['status'] for x in integrations}['INSTAGRAM']=='NOT_CONFIGURED'
api('GET','/platform-integrations',actor='',expected=401)
before=sql('SELECT COUNT(*) FROM oauth_attempt')
for platform in integrations:
    api('POST','/platform-integrations/'+platform['code']+'/authorize',{},actor='owner',expected=(400,409,503))
assert sql('SELECT COUNT(*) FROM oauth_attempt')==before
passed('六平台显示真实待配置或待申请状态，未配置授权不产生OAuth请求')

options=api('GET','/options')
youtube=next(p['id'] for p in options['platforms'] if p['code']=='YOUTUBE')
body={'platformId':youtube,'creatorId':users['owner']['creatorId'],'name':'平台同步验收 · YouTube','externalId':'test-channel-'+run,'status':'ACTIVE'}
account=api('POST','/platform-accounts',body,actor='owner')['id']
connection=int(sql(f"INSERT INTO platform_connection(platform_account_id,authorized_by,status) VALUES({account},{users['owner']['id']},'CONNECTED'); SELECT LAST_INSERT_ID();"))
# 测试快照位于frameflow_test，业务库不建立模拟授权；封面来自本项目自制SVG。
for remote,title in [('aBcVideo','林间微光 · 平台同步验收'),('AbcVideo','城市清晨 · 大小写标识验收')]:
    sql(f"INSERT INTO platform_content(connection_id,external_id,title,cover_url,permalink,media_type,published_at,view_count,like_count,last_synced_at) VALUES({connection},'{remote}','{title}','/platform-test-cover.svg','https://www.youtube.com/','VIDEO','2026-11-13 10:00:00',1280,86,NOW())")
assert sql(f'SELECT COUNT(*) FROM platform_content WHERE connection_id={connection}')=='2'
passed('手工账号保留登记功能，远端内容ID区分大小写')

own=api('GET',f'/platform-content?connectionId={connection}',actor='owner')
assert own['total']==2 and len(own['items'])==2
assert all(row['platformCode']=='YOUTUBE' and row['accountName']==body['name'] for row in own['items'])
for actor in ['other','producer','finance','reviewer']:
    api('GET',f'/platform-content?connectionId={connection}',actor=actor,expected=403)
    assert api('GET','/platform-content',actor=actor)['total']==0
    assert not any(row['id']==connection for row in api('GET','/platform-connections',actor=actor))
    api('POST',f'/platform-connections/{connection}/sync',actor=actor,expected=403)
    api('DELETE',f'/platform-connections/{connection}',actor=actor,expected=403)
assert api('GET',f'/platform-content?connectionId={connection}')['total']==2
api('GET','/platform-content',actor='',expected=401)
passed('平台内容仅账号所有者与管理员可见，其他角色不可同步或断开')

page=api('GET',f'/platform-content?connectionId={connection}&platformCode=YOUTUBE&pageSize=1',actor='owner')
assert page['total']==2 and len(page['items'])==1
filtered=api('GET',f'/platform-content?connectionId={connection}&q='+urllib.parse.quote('林间微光'),actor='owner')
assert filtered['total']==1
assert api('GET',f'/platform-content?connectionId={connection}&platformCode=INSTAGRAM',actor='owner')['total']==0
summary=next(x for x in api('GET','/platform-connections',actor='owner') if x['id']==connection)
assert summary['contentCount']==2 and summary['canManage']
assert not ({'accessToken','refreshToken','accessTokenEncrypted','refreshTokenEncrypted','clientSecret'} & summary.keys())
passed('平台/账号/标题筛选与分页有效，连接摘要不返回令牌字段')

for change in [{'externalId':'changed-'+run},{'creatorId':users['other']['creatorId']},{'platformId':1}]:
    api('PUT',f'/platform-accounts/{account}',{**body,**change},expected=(400,409))
api('PUT',f'/platform-accounts/{account}',{**body,'name':'平台同步验收 · 改名'},actor='owner')
passed('关联账号固定平台主体与归属，允许本人修改显示名称')

api('DELETE',f'/platform-connections/{connection}',actor='owner')
summary=next(x for x in api('GET','/platform-connections',actor='owner') if x['id']==connection)
assert summary['status']=='DISCONNECTED'
assert api('GET',f'/platform-content?connectionId={connection}',actor='owner')['total']==2
assert sql(f'SELECT COUNT(*) FROM platform_account WHERE id={account}')=='1'
assert sql(f'SELECT COUNT(*) FROM platform_connection WHERE id={connection} AND access_token_encrypted IS NULL AND refresh_token_encrypted IS NULL')=='1'
api('POST',f'/platform-connections/{connection}/sync',actor='owner',expected=(400,409))
passed('解除连接清空本地授权并保留账号与作品，已断开账号停止同步')

output=root/'docs/platform-integration-verification.json'
output.write_text(json.dumps({'database':'frameflow_test','api':base,'count':len(results),'passed':results,'remoteOAuthVerified':False},ensure_ascii=False,indent=2))
(root/'.runtime/platform-ui-fixture.json').write_text(json.dumps({'username':users['owner']['username'],'password':password,'creatorId':users['owner']['creatorId'],'connectionId':connection,'accountId':account},ensure_ascii=False))
print(f'Passed {len(results)} platform verification groups. Results: {output}')

"""独立 frameflow_test 验证结算修改、重算、撤销、留档和再审批。"""
import base64
import json
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal
from pathlib import Path

root=Path(__file__).resolve().parents[1]
base='http://localhost:8082/api'
run=str(time.time_ns())
tokens={}
results=[]

def call(method,path,body=None,role='finance',expected=200):
    headers={'Content-Type':'application/json'}
    if role in tokens: headers['Authorization']='Bearer '+tokens[role]
    data=json.dumps(body).encode() if body is not None else None
    try: response=urllib.request.urlopen(urllib.request.Request(base+path,data=data,method=method,headers=headers),timeout=30)
    except urllib.error.HTTPError as error: response=error
    payload=json.loads(response.read())
    allowed=expected if isinstance(expected,tuple) else (expected,)
    assert response.status in allowed,(method,path,response.status,payload)
    return payload['data'] if response.status==200 else {'httpStatus':response.status,'message':payload['msg']}

def ok(name):
    results.append(name)
    print('PASS '+name,flush=True)

def amount(value): return Decimal(str(value))
def detail(sid,role='finance'): return call('GET',f'/settlements/{sid}',role=role)
def balance(): return next(row for row in call('GET','/balances') if row['creatorId']==creator)['balance']
def income(ref,value,day='2026-10-05',fee=0):
    rid=call('POST','/revenues',{'publicationId':publication,'sourceRef':ref+'-'+run,'revenueDate':day,'kind':'INCOME','grossAmount':value,'feeAmount':fee})['id']
    call('POST',f'/revenues/{rid}/confirm')
    return rid

def cost(ref,value,day='2026-10-05'):
    cid=call('POST','/costs',{'projectId':project,'category':'PRODUCTION','description':ref,'amount':value,'costDate':day})['id']
    call('POST',f'/costs/{cid}/approve',role='reviewer')
    return cid

for role in ['admin','finance','reviewer']:
    tokens[role]=call('POST','/auth/login',{'username':role,'password':'FrameFlow2026!'},role='')['token']
username='settlement_'+run
call('POST','/auth/register',{'username':username,'displayName':'结算流程验收','password':'Settlement2026!','confirmPassword':'Settlement2026!'},role='')
login=call('POST','/auth/login',{'username':username,'password':'Settlement2026!'},role='')
tokens['creator']=login['token']
creator=login['user']['creatorId']
options=call('GET','/options',role='admin')
project=call('POST','/projects',{'name':'结算修改验收 '+run,'clientName':'独立测试','description':'重算和撤销验证','budget':12000,'startDate':'2026-01-01','dueDate':'2026-12-31','status':'PRODUCING','ownerId':2,'creatorIds':[creator,2]},role='admin')['id']
account=call('POST','/platform-accounts',{'platformId':options['platforms'][0]['id'],'creatorId':creator,'name':'结算验收账号','externalId':'settlement-'+run,'status':'ACTIVE'},role='creator')['id']
png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z4k4AAAAASUVORK5CYII=')
boundary='SettlementBoundary'+run
parts=[]
for key,value in {'name':'结算验收素材','kind':'IMAGE','creatorId':creator,'licenseType':'ORIGINAL','licenseStart':'2026-01-01','allowedPlatforms':'ALL','tags':'验收','description':'独立库'}.items():
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="settlement.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+png+f'\r\n--{boundary}--\r\n'.encode())
req=urllib.request.Request(base+'/assets',data=b''.join(parts),method='POST',headers={'Authorization':'Bearer '+tokens['creator'],'Content-Type':'multipart/form-data; boundary='+boundary})
asset=json.loads(urllib.request.urlopen(req).read())['data']['id']
work=call('POST','/works',{'projectId':project,'title':'结算验收作品','description':'独立库','status':'READY','assetIds':[asset]},role='creator')['id']
publication=call('POST','/publications',{'workId':work,'platformAccountId':account,'externalId':'settlement-video-'+run,'publishedAt':'2026-01-01T10:00:00','url':'https://example.com/settlement-test'},role='creator')['id']
contracts=[]
for name,start,end in [('上半年','2026-01-01','2026-06-30'),('下半年','2026-07-01','2026-12-31')]:
    cid=call('POST','/contracts',{'projectId':project,'name':name+'验收合同 '+run,'startDate':start,'endDate':end,'studioRate':30,'participants':[{'creatorId':creator,'rate':60},{'creatorId':2,'rate':10}]})['id']
    call('POST',f'/contracts/{cid}/activate')
    contracts.append(cid)
params={'contractId':contracts[1],'periodStart':'2026-10-01','periodEnd':'2026-10-31'}
r1=income('原收入',10000,fee=1000)
c1=cost('原成本',2000)
sid=call('POST','/settlements',params)['id']
initial=detail(sid)
assert amount(initial['distributableAmount'])==7000
assert amount(balance())==0
call('POST',f'/settlements/{sid}/submit')
for method,tail,body in [('PUT','',{**params,'reason':'应先退回'}),('POST','/recalculate',{'reason':'应先退回'}),('POST','/cancel',{'reason':'应先退回'})]:
    call(method,f'/settlements/{sid}'+tail,body,expected=(400,409))
call('POST',f'/settlements/{sid}/reject',{'reason':'补齐月末收入与成本'},role='reviewer')
assert detail(sid)['status']=='DRAFT'
ok('提交后冻结；审核退回草稿后允许调整')

r2=income('补录收入',500,'2026-10-20')
c2=cost('补录成本',100,'2026-10-20')
call('POST',f'/settlements/{sid}/recalculate',{'reason':'纳入月末补录收支'})
updated=detail(sid)
assert updated['id']==sid and updated['settlementNo']==initial['settlementNo']
assert amount(updated['distributableAmount'])==7400
assert len(updated['revenues'])==2 and len(updated['costs'])==2
assert len(updated['revisions'])==1
assert updated['revisions'][0]['action']=='RECALCULATE'
assert amount(updated['revisions'][0]['snapshot']['distributableAmount'])==7000
assert len(updated['revisions'][0]['snapshot']['revenues'])==1
assert amount(balance())==0
ok('重算同一结算单，纳入新增收支并保存旧快照')

narrow={**params,'periodStart':'2026-10-15','reason':'调整为下半月'}
call('PUT',f'/settlements/{sid}',narrow)
assert amount(detail(sid)['distributableAmount'])==400
released=call('POST','/settlements',{**params,'periodEnd':'2026-10-14'})['id']
assert amount(detail(released)['distributableAmount'])==7000
call('POST',f'/settlements/{released}/cancel',{'reason':'合并回整月结算'})
r3=income('上半年收入',300,'2026-06-20')
call('PUT',f'/settlements/{sid}',{'contractId':contracts[0],'periodStart':'2026-06-01','periodEnd':'2026-06-30','reason':'核对合同切换'})
assert detail(sid)['contractId']==contracts[0] and amount(detail(sid)['distributableAmount'])==300
call('PUT',f'/settlements/{sid}',{**params,'reason':'恢复整月合同与期间'})
assert amount(detail(sid)['distributableAmount'])==7400
ok('修改期间及合同，旧来源可重新分配，单号保持')

before=detail(sid)
call('PUT',f'/settlements/{sid}',{**params,'periodStart':'2026-12-01','periodEnd':'2026-12-31','reason':'空期间验证'},expected=(400,409))
after=detail(sid)
for field in ['contractId','periodStart','periodEnd','totalIncome','totalCost','distributableAmount','revenues','costs','lines','revisions']:
    assert before[field]==after[field],field
call('POST','/settlements',params,expected=(400,409))
ok('空期间修改整体回滚，原快照和来源占用完整')

personal=detail(sid,'creator')
assert all(row['creatorId']==creator for row in personal['lines'])
assert all(row['creatorId']==creator for revision in personal['revisions'] for row in revision['snapshot']['lines'])
for method,tail,body in [('PUT','',{**params,'reason':'权限检查'}),('POST','/recalculate',{'reason':'权限检查'}),('POST','/cancel',{'reason':'权限检查'})]:
    call(method,f'/settlements/{sid}'+tail,body,role='creator',expected=403)
call('POST',f'/settlements/{sid}/cancel',{'reason':'撤销后重新核对'})
cancelled=detail(sid)
assert cancelled['status']=='CANCELLED' and cancelled['cancelReason']=='撤销后重新核对'
assert cancelled['revenues']==before['revenues'] and cancelled['costs']==before['costs']
assert cancelled['lines']==before['lines'] and amount(balance())==0
assert detail(sid,'creator')['status']=='CANCELLED'
call('POST',f'/settlements/{sid}/submit',expected=(400,409))
fresh=call('POST','/settlements',params)['id']
assert amount(detail(fresh)['distributableAmount'])==7400
ok('撤销保留依据与个人历史，释放来源且不产生应付')

call('POST',f'/settlements/{fresh}/submit')
call('POST',f'/settlements/{fresh}/approve',role='reviewer')
assert amount(balance())==4440
for method,tail,body in [('PUT','',{**params,'reason':'批准冻结'}),('POST','/recalculate',{'reason':'批准冻结'}),('POST','/cancel',{'reason':'批准冻结'})]:
    call(method,f'/settlements/{fresh}'+tail,body,expected=(400,409))
call('POST','/payments',{'creatorId':creator,'amount':3000,'paidAt':'2026-11-13T10:00:00','reference':'REVISION-PAY-'+run,'note':'独立测试付款'})
assert amount(balance())==1440
ok('重新结算审核后形成准确应付，已批准快照与付款保持一致')

income('并发收入',100.01,'2026-11-05')
race={'contractId':contracts[1],'periodStart':'2026-11-01','periodEnd':'2026-11-30'}
with ThreadPoolExecutor(max_workers=2) as pool:
    outcomes=list(pool.map(lambda _:call('POST','/settlements',race,expected=(200,400,409)),range(2)))
assert sum('id' in item for item in outcomes)==1,outcomes
race_id=next(item['id'] for item in outcomes if 'id' in item)
race_detail=detail(race_id)
assert amount(race_detail['distributableAmount'])==Decimal('100.01')
assert sum(amount(row['amount']) for row in race_detail['lines'])+amount(race_detail['studioAmount'])==Decimal('100.01')
ok('同合同并发生成只分配一次，分账尾差守恒')

refund=call('POST','/revenues',{'publicationId':publication,'sourceRef':'REFUND-'+run,'revenueDate':'2027-01-05','kind':'REFUND','grossAmount':100,'feeAmount':0,'originalRevenueId':r1,'note':'合同到期后退款'})['id']
call('POST',f'/revenues/{refund}/confirm')
refund_id=call('POST','/settlements',{'contractId':contracts[1],'periodStart':'2027-01-01','periodEnd':'2027-01-31'})['id']
call('POST',f'/settlements/{refund_id}/recalculate',{'reason':'核对跨期退款'})
assert amount(detail(refund_id)['distributableAmount'])==-100
call('POST',f'/settlements/{refund_id}/submit')
call('POST',f'/settlements/{refund_id}/approve',role='reviewer')
assert amount(balance())==1380
assert amount(detail(fresh)['distributableAmount'])==7400
ok('跨合同有效期退款可重算并抵扣余额，原批准单稳定')

(root/'docs/settlement-workflow-verification.json').write_text(json.dumps({'api':base,'count':len(results),'passed':results,'fixture':{'projectId':project,'draftId':race_id,'cancelledId':sid,'approvedId':fresh,'creatorId':creator}},ensure_ascii=False,indent=2)+'\n')
for role in tokens: call('POST','/auth/logout',role=role)
print(f'Passed {len(results)} settlement workflow groups.')

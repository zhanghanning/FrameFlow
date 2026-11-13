"""在独立 frameflow_test 数据库验证真实业务流程。先按 README 启动 8082 测试服务。"""
import base64
import json
import os
import time
import urllib.error
import urllib.request
from decimal import Decimal
from pathlib import Path

base = os.environ.get('TEST_API_URL', 'http://localhost:8082/api')
run = str(int(time.time()))
results = []
tokens = {}

def request(method, path, body=None, role='admin', expected=200, raw=False, content_type=None):
    headers = {}
    if role in tokens:
        headers['Authorization'] = 'Bearer ' + tokens[role]
    if body is not None and not isinstance(body, bytes):
        body = json.dumps(body).encode()
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
    assert response.status in allowed, f'{method} {path}: HTTP {response.status} {payload.decode(errors="replace")}'
    if raw:
        return payload
    data = json.loads(payload)
    if response.status == 200:
        assert data['code'] == 0, data
    return data.get('data')

def multipart(path, fields, filename, data, mime, role='admin'):
    boundary = 'FrameFlowBoundary' + run
    chunks = []
    for key, value in fields.items():
        chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
    chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n'.encode() + data + b'\r\n')
    chunks.append(f'--{boundary}--\r\n'.encode())
    return request('POST', path, b''.join(chunks), role=role, content_type='multipart/form-data; boundary=' + boundary)

def passed(name):
    results.append(name)
    print('PASS ' + name, flush=True)

for role in ['admin', 'producer', 'finance', 'reviewer', 'creator']:
    login = request('POST', '/auth/login', {'username': role, 'password': 'FrameFlow2026!'}, role='')
    tokens[role] = login['token']
    assert request('GET', '/auth/me', role=role)['username'] == role
request('GET', '/dashboard', role='', expected=401)
request('GET', '/users', role='creator', expected=403)
passed('登录、会话及角色访问')

for endpoint in ['creators','platform-accounts','projects','works','assets','publications','revenues','costs','contracts','settlements','payments','audit-logs','users']:
    data = request('GET', '/' + endpoint)
    assert isinstance(data['items'], list) and data['total'] >= 0, endpoint
admin_matches = request('GET','/users?q=admin&pageSize=100')
assert any(row['username'] == 'admin' for row in admin_matches['items'])
assert all('admin' in (row['username'] + row['displayName']).lower() for row in admin_matches['items'])
assert request('GET','/users?q=does-not-exist')['total'] == 0
assert len(request('GET','/users?page=1&pageSize=2')['items']) == 2
assert all(row['status'] == 'INACTIVE' for row in request('GET','/users?status=INACTIVE')['items'])
assert isinstance(request('GET','/balances'), list)
assert 'metrics' in request('GET','/dashboard')
options = request('GET','/options')
assert len(options['platformAccounts']) >= 1
platform_account = next(row for row in options['platformAccounts'] if row['creatorId'] == 1)
passed('管理页面列表与看板真实数据')

project = request('POST', '/projects', {'name':'全流程验收 ' + run,'clientName':'验收工作室','description':'独立测试库业务流程','budget':1000,'startDate':'2026-01-01','dueDate':'2026-12-31','status':'PRODUCING','ownerId':2,'creatorIds':[1,2]})['id']
contract = request('POST','/contracts',{'projectId':project,'name':'三方分账 '+run,'startDate':'2026-01-01','endDate':'2026-12-31','studioRate':33.3334,'participants':[{'creatorId':1,'rate':33.3333},{'creatorId':2,'rate':33.3333}]},role='finance')['id']
request('POST',f'/contracts/{contract}/activate',role='finance')
passed('项目协作与百分比分账协议')

png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z4k4AAAAASUVORK5CYII=')
asset = multipart('/assets', {'name':'授权样例 '+run,'kind':'IMAGE','creatorId':1,'licenseType':'ORIGINAL','licenseStart':'2026-01-01','licenseEnd':'2026-12-31','allowedPlatforms':'ALL','tags':'验收,原创','description':'上传与下载核对'},'verification.png',png,'image/png')['id']
assert request('GET', f'/assets/{asset}/file', raw=True) == png
request('PUT', f'/assets/{asset}/projects', {'projectIds':[project]})
work = request('POST','/works',{'projectId':project,'title':'验收作品 '+run,'description':'原创素材作品','status':'READY','assetIds':[asset]})['id']
publication = request('POST','/publications',{'workId':work,'platformAccountId':platform_account['id'],'externalId':'TEST-'+run,'publishedAt':'2026-10-01T10:00:00','url':'https://example.com/video/'+run})['id']
passed('素材真实上传下载、作品引用与发布')

expired = multipart('/assets', {'name':'已到期授权 '+run,'kind':'IMAGE','creatorId':1,'licenseType':'LICENSED','licenseStart':'2026-01-01','licenseEnd':'2026-02-01','allowedPlatforms':'ALL','tags':'验收','description':'验证授权日期'},'expired.png',png,'image/png')['id']
request('PUT', f'/assets/{expired}/projects', {'projectIds':[project]})
expired_work = request('POST','/works',{'projectId':project,'title':'过期授权作品 '+run,'description':'验收','status':'READY','assetIds':[expired]})['id']
request('POST','/publications',{'workId':expired_work,'platformAccountId':platform_account['id'],'externalId':'EXPIRED-'+run,'publishedAt':'2026-10-01T10:00:00','url':'https://example.com/expired'}, expected=(400,409))
passed('发布时素材授权日期检查')

income = request('POST','/revenues',{'publicationId':publication,'sourceRef':'INC-'+run,'revenueDate':'2026-10-02','kind':'INCOME','grossAmount':100.01,'feeAmount':0,'note':'精度验收'},role='finance')['id']
request('POST',f'/revenues/{income}/confirm',role='finance')
request('POST','/revenues',{'publicationId':publication,'sourceRef':'INC-'+run,'revenueDate':'2026-10-02','kind':'INCOME','grossAmount':100.01,'feeAmount':0,'note':'重复来源'},role='finance',expected=(400,409))
csv = ('publicationId,sourceRef,revenueDate,grossAmount,feeAmount,note\n'+f'{publication},CSV-{run},2026-10-03,0.03,0,CSV验收\n').encode('utf-8')
preview = multipart('/revenues/import/preview',{},'revenues.csv',csv,'text/csv',role='finance')
assert preview['validCount'] == 1
imported = request('POST','/revenues/import',{'rows':preview['rows']},role='finance')
assert imported['inserted'] == 1
again = request('POST','/revenues/import',{'rows':preview['rows']},role='finance')
assert again['inserted'] == 0 and again['duplicates'] == 1
passed('收入确认、CSV预览导入和来源去重')

cost = request('POST','/costs',{'projectId':project,'category':'PRODUCTION','description':'尾差验收','amount':0.01,'costDate':'2026-10-03'},role='finance')['id']
request('POST',f'/costs/{cost}/approve',role='reviewer')
settlement = request('POST','/settlements',{'contractId':contract,'periodStart':'2026-10-01','periodEnd':'2026-10-31'},role='admin')['id']
detail = request('GET',f'/settlements/{settlement}')
assert Decimal(str(detail['distributableAmount'])) == Decimal('100.00'), detail
assert sum(Decimal(str(line['amount'])) for line in detail['lines']) + Decimal(str(detail['studioAmount'])) == Decimal('100.00')
assert all(Decimal(str(line['amount'])) == Decimal('33.33') for line in detail['lines'])
request('POST','/settlements',{'contractId':contract,'periodStart':'2026-10-01','periodEnd':'2026-10-31'},expected=(400,409))
request('POST',f'/settlements/{settlement}/submit')
request('POST',f'/settlements/{settlement}/approve',expected=(400,403,409))
request('POST',f'/settlements/{settlement}/approve',role='reviewer')
assert request('GET',f'/settlements/{settlement}')['status'] == 'APPROVED'
passed('存储过程结算、金额尾差、唯一分配、提交审核分离')

balance_before = next(x for x in request('GET','/balances',role='finance') if x['creatorId'] == 1)
request('POST','/payments',{'creatorId':1,'amount':20,'paidAt':'2026-11-01T10:00:00','reference':'PAY-'+run,'note':'线下付款验收'},role='finance')
balance_after = next(x for x in request('GET','/balances',role='finance') if x['creatorId'] == 1)
assert Decimal(str(balance_before['balance'])) - Decimal(str(balance_after['balance'])) == Decimal('20')
request('POST','/payments',{'creatorId':1,'amount':10000000,'paidAt':'2026-11-01T10:00:00','reference':'OVER-'+run,'note':'超过余额'},role='finance',expected=(400,409))
passed('实际付款登记与应付余额')

refund = request('POST','/revenues',{'publicationId':publication,'sourceRef':'REF-'+run,'revenueDate':'2026-11-03','kind':'REFUND','grossAmount':10,'feeAmount':0,'originalRevenueId':income,'note':'次月退款'},role='finance')['id']
request('POST',f'/revenues/{refund}/confirm',role='finance')
refund_settlement = request('POST','/settlements',{'contractId':contract,'periodStart':'2026-11-01','periodEnd':'2026-11-30'},role='finance')['id']
refund_detail = request('GET',f'/settlements/{refund_settlement}')
assert Decimal(str(refund_detail['distributableAmount'])) == Decimal('-10')
request('POST',f'/settlements/{refund_settlement}/submit',role='finance')
request('POST',f'/settlements/{refund_settlement}/approve',role='reviewer')
latest = next(x for x in request('GET','/balances') if x['creatorId'] == 1)
assert Decimal(str(balance_after['balance'])) - Decimal(str(latest['balance'])) == Decimal('3.33')
assert request('GET',f'/settlements/{settlement}')['distributableAmount'] == detail['distributableAmount']
passed('退款沿用原协议、负向结算与历史金额稳定')

creator_balances = request('GET','/balances',role='creator')
assert all(x['creatorId'] == 1 for x in creator_balances)
request('POST','/payments',{'creatorId':1,'amount':1,'paidAt':'2026-11-01T10:00:00','reference':'DENIED-'+run},role='creator',expected=403)
passed('创作者数据范围和财务写权限')

request('PUT','/auth/password',{'oldPassword':'FrameFlow2026!','newPassword':'Changed2026!'},role='producer')
login = request('POST','/auth/login',{'username':'producer','password':'Changed2026!'},role='')
tokens['producer'] = login['token']
request('PUT','/auth/password',{'oldPassword':'Changed2026!','newPassword':'FrameFlow2026!'},role='producer')
request('POST','/auth/logout',role='creator')
request('GET','/auth/me',role='creator',expected=401)
passed('修改密码、重新登录与注销会话')

out = Path(__file__).resolve().parents[1] / 'docs/verification-results.json'
out.write_text(json.dumps({'api':base,'run':run,'passed':results,'count':len(results)},ensure_ascii=False,indent=2),encoding='utf-8')
print(f'Passed {len(results)} business verification groups. Results: {out}')

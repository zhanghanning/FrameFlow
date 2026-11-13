"""验证协作权限、历史映射稳定和分账表单。仅连接 .env.test 对应的 8082 测试服务。"""
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

root = Path(__file__).resolve().parents[1]
test_env = dict(line.split('=', 1) for line in (root / '.env.test').read_text().splitlines() if line and not line.startswith('#'))
assert test_env['SERVER_PORT'] == '8082' and '/frameflow_test' in test_env['DB_URL']
base = 'http://localhost:8082/api'
run = str(time.time_ns())
tokens = {}
results = []


def request(method, path, body=None, role='admin', expected=200, raw=False):
    headers = {'Authorization': 'Bearer ' + tokens[role]} if role in tokens else {}
    if body is not None:
        body = json.dumps(body).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    try:
        response = urllib.request.urlopen(urllib.request.Request(base + path, data=body, headers=headers, method=method), timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    payload = response.read()
    assert response.status == expected, f'{method} {path}: expected {expected}, got {response.status}: {payload.decode(errors="replace")}'
    if raw:
        return payload
    envelope = json.loads(payload)
    assert envelope['code'] == (0 if expected == 200 else expected), envelope
    return envelope['data'] if expected == 200 else envelope['msg']


def passed(name, evidence):
    results.append({'name': name, 'evidence': evidence})
    print('PASS ' + name, flush=True)


for role in ['admin', 'producer', 'finance']:
    tokens[role] = request('POST', '/auth/login', {'username': role, 'password': 'FrameFlow2026!'}, role='')['token']

accounts = request('GET', '/platform-accounts?pageSize=100')['items']
account = next(row for row in accounts if row['id'] == 1)
account_body = {key: account[key] for key in ['platformId', 'creatorId', 'name', 'externalId', 'status']}
account_body['externalId'] = 'collaboration-change-' + run
message = request('PUT', '/platform-accounts/1', account_body, expected=409)
assert next(row for row in request('GET', '/platform-accounts?pageSize=100')['items'] if row['id'] == 1)['externalId'] == account['externalId']
passed('已有发布的平台账号外部标识保持固定', {'status': 409, 'message': message})

project = request('GET', '/projects/1')
project_body = {key: project[key] for key in ['name', 'clientName', 'description', 'budget', 'startDate', 'dueDate', 'status', 'ownerId', 'creatorIds']}
assert 4 in project_body['creatorIds']
project_body['creatorIds'] = [creator_id for creator_id in project_body['creatorIds'] if creator_id != 4]
message = request('PUT', '/projects/1', project_body, role='producer', expected=409)
assert set(request('GET', '/projects/1')['creatorIds']) == set(project['creatorIds'])
passed('项目成员保留生效合同参与人', {'status': 409, 'message': message})

username = 'collab_xumo_' + run
user_id = request('POST', '/users', {'username': username, 'displayName': '协作验证 · 许墨', 'password': 'FrameFlow2026!', 'roles': ['CREATOR'], 'creatorId': 4})['id']
tokens['collaborator'] = request('POST', '/auth/login', {'username': username, 'password': 'FrameFlow2026!'}, role='')['token']
admin_settlement = request('GET', '/settlements/1')
creator_settlement = request('GET', '/settlements/1', role='collaborator')
assert len(admin_settlement['revenues']) == 2
assert {row['id'] for row in creator_settlement['revenues']} == {row['id'] for row in admin_settlement['revenues']}
assert len(creator_settlement['lines']) == 1 and creator_settlement['lines'][0]['creatorId'] == 4
assert creator_settlement['payments'] and all(row['creatorId'] == 4 for row in creator_settlement['payments'])
admin_file = request('GET', '/assets/1/file', raw=True)
creator_file = request('GET', '/assets/1/file', role='collaborator', raw=True)
assert b'<svg' in creator_file and creator_file == admin_file
original_name = request('GET', '/assets/1')['name']
request('PUT', '/assets/1', {'name': '不可修改的伙伴素材'}, role='collaborator', expected=403)
assert request('GET', '/assets/1')['name'] == original_name
request('GET', '/assets/3/file', role='collaborator', expected=403)
passed('共同结算依据可见且个人分账付款隔离；伙伴素材可读、修改受限', {'createdUserId': user_id, 'creatorId': 4, 'sourceRevenueCount': 2, 'ownLineCount': 1, 'sharedAssetBytes': len(creator_file), 'writeStatus': 403, 'unrelatedAssetStatus': 403})

finance_cost = request('POST', '/costs', {'projectId': 1, 'category': '协作验证', 'description': '财务录入，仅用于验证制作人员列表范围 ' + run, 'amount': 12.34, 'costDate': '2026-11-13'}, role='finance')['id']
producer_cost = request('POST', '/costs', {'projectId': 1, 'category': '协作验证', 'description': '制作统筹录入成本 ' + run, 'amount': 20.50, 'costDate': '2026-11-13'}, role='producer')['id']
producer_costs = request('GET', '/costs?pageSize=100', role='producer')['items']
assert producer_costs and all(row['createdBy'] == 2 for row in producer_costs)
assert producer_cost in {row['id'] for row in producer_costs} and finance_cost not in {row['id'] for row in producer_costs}
assert all(row['createdBy'] == 2 for row in request('GET', '/projects/1', role='producer')['costs'])
passed('制作统筹可录入成本，列表和项目详情仅显示本人录入', {'producerCostId': producer_cost, 'excludedFinanceCostId': finance_cost, 'visibleRows': len(producer_costs), 'actorId': 2})

contract_body = {'projectId': 1, 'name': '分账精度表单验证 ' + run, 'startDate': '2027-01-01', 'endDate': '2027-12-31', 'studioRate': 30.00001, 'participants': [{'creatorId': 1, 'rate': 70}]}
precision_message = request('POST', '/contracts', contract_body, role='finance', expected=400)
assert '四位' in precision_message
contract_body['studioRate'] = 100
contract_body['participants'] = [{'creatorId': 1, 'rate': 0}]
zero_message = request('POST', '/contracts', contract_body, role='finance', expected=400)
passed('分账比例精度及正数要求由表单校验明确返回400', {'studioRateStatus': 400, 'studioRateMessage': precision_message, 'participantRateStatus': 400, 'participantRateMessage': zero_message})

output = root / 'docs/collaboration-verification.json'
output.write_text(json.dumps({'api': base, 'database': 'frameflow_test', 'run': run, 'count': len(results), 'passed': results}, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Passed {len(results)} collaboration verification groups. Results: {output}')

"""独立测试库：真实视频封面、3秒预览、原文件与项目授权。"""
import json
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
assert '/frameflow_test' in (root/'.env.test').read_text()
base='http://localhost:8082/api'
run=str(time.time_ns())
fixtures=root/'.runtime/media-verification'
fixtures.mkdir(parents=True,exist_ok=True)
tokens,results={},[]


def request(method,path,body=None,actor='admin',expected=200,raw=False,content_type=None):
    headers={'Authorization':'Bearer '+tokens[actor]} if actor in tokens else {}
    if body is not None and not isinstance(body,bytes):
        body=json.dumps(body).encode()
        headers['Content-Type']='application/json'
    if content_type: headers['Content-Type']=content_type
    req=urllib.request.Request(base+path,data=body,headers=headers,method=method)
    try:
        response=urllib.request.urlopen(req,timeout=150)
    except urllib.error.HTTPError as error:
        response=error
    payload=response.read()
    assert response.status==expected,f'{method} {path}: {response.status} {payload.decode(errors="replace")[:700]}'
    if raw: return payload,response.headers
    envelope=json.loads(payload)
    return envelope['data'] if response.status==200 else envelope['msg']


def upload(path,kind,mime,expected=200):
    fields={'name':'媒体验收 '+path.stem+' '+run,'creatorId':1,'kind':kind,'licenseType':'ORIGINAL','licenseStart':'2026-01-01','allowedPlatforms':'ALL','tags':'媒体验收','description':'自动生成的预览验证素材'}
    boundary='MediaBoundary'+run
    chunks=[f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode() for key,value in fields.items()]
    chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\nContent-Type: {mime}\r\n\r\n'.encode()+path.read_bytes()+b'\r\n')
    chunks.append(f'--{boundary}--\r\n'.encode())
    return request('POST','/assets',b''.join(chunks),actor='creator',expected=expected,content_type='multipart/form-data; boundary='+boundary)


def passed(name):
    results.append(name)
    print('PASS '+name,flush=True)


def probe(path):
    return json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(path)],text=True))


for actor in ['admin','creator','producer']:
    tokens[actor]=request('POST','/auth/login',{'username':actor,'password':'FrameFlow2026!'},actor='')['token']
landscape=fixtures/'landscape.mp4'
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','testsrc2=size=960x540:rate=30:duration=6','-f','lavfi','-i','sine=frequency=440:duration=6','-c:v','libx264','-preset','ultrafast','-pix_fmt','yuv420p','-c:a','aac','-shortest','-movflags','+faststart',str(landscape)],check=True)
video=upload(landscape,'VIDEO','video/mp4')['id']
media=request('GET',f'/assets/{video}/media',actor='creator')
assert media['type']=='VIDEO' and 5.9<float(media['durationSeconds'])<6.1,media
assert media['posterUrl']==f'/assets/{video}/poster' and media['previewUrl']==f'/assets/{video}/preview'
poster,headers=request('GET',media['posterUrl'],actor='creator',raw=True)
assert poster[:3]==b'\xff\xd8\xff' and 'image/jpeg' in headers['Content-Type']
assert 'no-store' in headers.get('Cache-Control','')
preview,headers=request('GET',media['previewUrl'],actor='creator',raw=True)
clip=fixtures/'landscape-preview.mp4';clip.write_bytes(preview)
info=probe(clip)
assert abs(float(info['format']['duration'])-3)<0.08,info['format']['duration']
assert len(info['streams'])==1 and info['streams'][0]['codec_type']=='video'
assert max(info['streams'][0]['width'],info['streams'][0]['height'])<=640
assert info['streams'][0]['codec_name']=='h264'
assert len(preview)<landscape.stat().st_size
original,_=request('GET',f'/assets/{video}/file',actor='creator',raw=True)
assert original==landscape.read_bytes()
passed('真实视频生成JPEG封面和3秒静音H264预览，原文件内容保留')

for path in [f'/assets/{video}/media',media['posterUrl'],media['previewUrl']]:
    request('GET',path,actor='',expected=401)
    request('GET',path,actor='producer',expected=403)
request('PUT',f'/assets/{video}/projects',{'projectIds':[1]},actor='creator')
request('GET',f'/assets/{video}/media',actor='producer')
shared_preview,_=request('GET',media['previewUrl'],actor='producer',raw=True)
assert shared_preview==preview
request('PUT',f'/assets/{video}/projects',{'projectIds':[]},actor='creator')
for path in [f'/assets/{video}/media',media['posterUrl'],media['previewUrl']]:
    request('GET',path,actor='producer',expected=403)
passed('封面与预览沿用素材授权，已有缓存撤权后同会话立即受限')

short=fixtures/'portrait-short.mp4'
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','testsrc2=size=360x640:rate=30:duration=0.8','-c:v','libx264','-preset','ultrafast','-pix_fmt','yuv420p',str(short)],check=True)
short_id=upload(short,'VIDEO','video/mp4')['id']
short_media=request('GET',f'/assets/{short_id}/media',actor='creator')
short_bytes,_=request('GET',short_media['previewUrl'],actor='creator',raw=True)
short_clip=fixtures/'portrait-preview.mp4';short_clip.write_bytes(short_bytes)
short_info=probe(short_clip)
assert abs(float(short_info['format']['duration'])-3)<0.08
assert short_info['streams'][0]['height']>short_info['streams'][0]['width']
assert max(short_info['streams'][0]['width'],short_info['streams'][0]['height'])<=640
passed('不足3秒的视频循环补足，竖屏保持纵横比')

image=request('GET','/assets/1/media',actor='creator')
assert image['type']=='IMAGE' and image['posterUrl']=='/assets/1/file' and image.get('previewUrl') is None
request('GET','/assets/1/preview',actor='creator',expected=400)
passed('静态图片保留图片预览，不伪装为动态视频')

invalid=fixtures/'invalid.mp4';invalid.write_text('This is not a video.',encoding='utf-8')
before=request('GET','/assets',actor='creator')['total']
message=upload(invalid,'VIDEO','video/mp4',expected=400)
after=request('GET','/assets',actor='creator')['total']
assert before==after and message
passed('无法解码视频返回明确错误，失败上传事务不留下素材记录')

# 持有真实服务的生成锁，确认已有缓存仍能在锁释放前返回；不比较转码速度。
with tempfile.TemporaryDirectory(prefix='cache-concurrency-',dir=fixtures) as temporary:
    temporary=Path(temporary)
    libraries=temporary/'lib';libraries.mkdir()
    jars=list((root/'backend/target').glob('*.jar'))
    assert len(jars)==1,'请先运行 bash scripts/build.sh 生成后端应用包'
    with zipfile.ZipFile(jars[0]) as archive:
        for entry in archive.namelist():
            if entry.startswith('BOOT-INF/lib/') and entry.endswith('.jar'):
                (libraries/Path(entry).name).write_bytes(archive.read(entry))
    cached=temporary/'uploads/previews/1';cached.mkdir(parents=True)
    (cached/'metadata.json').write_text(json.dumps({'durationSeconds':media['durationSeconds']}),encoding='utf-8')
    (cached/'poster.jpg').write_bytes(poster)
    (cached/'preview.mp4').write_bytes(preview)
    source=temporary/'CachedMediaConcurrency.java'
    source.write_text("""package com.frameflow.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.concurrent.*;
public class CachedMediaConcurrency {
  public static void main(String[] args) throws Exception {
    var service = new AssetMediaService(null, new ObjectMapper(), args[0]);
    var lockHeld = new CountDownLatch(1);
    var releaseLock = new CountDownLatch(1);
    var readerStarted = new CountDownLatch(1);
    Thread generator = Thread.ofPlatform().start(() -> {
      synchronized (service) {
        lockHeld.countDown();
        try { releaseLock.await(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
      }
    });
    var executor = Executors.newSingleThreadExecutor();
    try {
      if (!lockHeld.await(10, TimeUnit.SECONDS)) throw new AssertionError("Generation lock was not acquired");
      Future<Double> read = executor.submit(() -> {
        readerStarted.countDown();
        return service.prepare(1, Path.of(args[1]));
      });
      if (!readerStarted.await(10, TimeUnit.SECONDS)) throw new AssertionError("Cache reader did not start");
      double duration;
      try { duration = read.get(10, TimeUnit.SECONDS); }
      catch (TimeoutException ex) { throw new AssertionError("Cached media blocked on the occupied generation lock", ex); }
      if (Math.abs(duration - Double.parseDouble(args[2])) > 0.001)
        throw new AssertionError("Cached metadata differs");
      if (releaseLock.getCount() != 1 || !generator.isAlive())
        throw new AssertionError("Generation lock was released before the cache read completed");
      System.out.println("CACHE_READ_COMPLETED_WHILE_GENERATION_LOCK_HELD");
    } finally {
      releaseLock.countDown();
      generator.join();
      executor.shutdownNow();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }
  }
}
""",encoding='utf-8')
    classpath=f"{root/'backend/target/classes'}:{libraries}/*"
    subprocess.run(['javac','-encoding','UTF-8','-cp',classpath,'-d',str(temporary),str(source)],check=True,timeout=30)
    result=subprocess.run(['java','-cp',f'{temporary}:{classpath}','com.frameflow.service.CachedMediaConcurrency',str(temporary/'uploads'),str(landscape),str(media['durationSeconds'])],check=True,capture_output=True,text=True,timeout=40)
    assert 'CACHE_READ_COMPLETED_WHILE_GENERATION_LOCK_HELD' in result.stdout,result.stdout+result.stderr
passed('并发持有视频生成锁时，已有素材缓存读取在锁释放前完成')

output=root/'docs/media-verification.json'
output.write_text(json.dumps({'api':base,'database':'frameflow_test','run':run,'count':len(results),'passed':results,'previewDuration':float(info['format']['duration']),'previewBytes':len(preview),'sourceBytes':landscape.stat().st_size,'cacheConcurrency':{'method':'当前后端AssetMediaService生成锁由独立线程持有，CountDownLatch控制顺序','result':'已有缓存读取在生成锁释放前完成'}},ensure_ascii=False,indent=2),encoding='utf-8')
print(f'Passed {len(results)} media verification groups. Results: {output}')

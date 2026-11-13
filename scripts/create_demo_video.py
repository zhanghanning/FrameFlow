"""生成自制的山野动态演示素材；不下载外部影像。"""
from pathlib import Path
import math
import subprocess
from PIL import Image, ImageDraw, ImageFont

root=Path(__file__).resolve().parents[1]
output=root/'.runtime/media-demo'
output.mkdir(parents=True,exist_ok=True)
video=output/'forest-light.mp4'
width,height,fps=960,540,30
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
title_font=ImageFont.truetype(font,48)
label_font=ImageFont.truetype(font,15)
small_font=ImageFont.truetype(font,13)
process=subprocess.Popen(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','rawvideo','-pix_fmt','rgb24','-s',f'{width}x{height}','-r',str(fps),'-i','-','-an','-c:v','libx264','-threads','2','-preset','veryfast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(video)],stdin=subprocess.PIPE)
for frame in range(fps*6):
    t=frame/fps
    image=Image.new('RGB',(width,height))
    draw=ImageDraw.Draw(image)
    for y in range(height):
        ratio=y/height
        draw.line((0,y,width,y),fill=(round(225-74*ratio),round(225-46*ratio),round(200-35*ratio)))
    sun_x=740+12*math.sin(t*.3)
    draw.ellipse((sun_x-48,66,sun_x+48,162),fill=(246,235,188))
    for layer,(base,amplitude,color,speed) in enumerate([(306,95,(132,155,137),5),(365,105,(84,117,102),10),(445,90,(44,82,69),18)]):
        points=[(x,base+amplitude*math.sin(x/180+layer*1.3+t*speed/180)+amplitude*.24*math.sin(x/71+t*.12)) for x in range(-10,width+11,6)]
        draw.polygon([(-10,height)]+points+[(width+10,height)],fill=color)
    draw.rectangle((40,34,139,58),outline=(50,79,65),width=1)
    draw.text((50,39),'FRAMEFLOW',font=small_font,fill=(32,65,51))
    draw.text((40,93),'FOREST LIGHT',font=title_font,fill=(27,60,49))
    draw.text((42,158),'A STUDY IN LIGHT / ORIGINAL MOTION',font=label_font,fill=(59,88,71))
    draw.line((42,477,918,477),fill=(132,161,140),width=1)
    draw.text((42,495),'DEMO FOOTAGE     /     06 SEC',font=small_font,fill=(221,230,211))
    draw.text((812,495),f'00:0{int(t)}:{frame%30:02d}',font=small_font,fill=(221,230,211))
    process.stdin.write(image.tobytes())
process.stdin.close()
assert process.wait()==0
print(video)

from pathlib import Path
import html
import shutil

root = Path(__file__).resolve().parents[1]
output = root / 'uploads/assets/demo'
output.mkdir(parents=True, exist_ok=True)
# 自制示例插画，供素材授权与下载流程演示。
scenes = [
    ('city-dawn','CITY / DAWN','城市醒来之前','#ecd6ba','#b76a48',0),
    ('mountain-trail','INTO THE WILD','山野来信','#d3ded0','#4d7761',1),
    ('coffee-window','SLOW MORNING','一杯咖啡的时间','#e9cc9d','#87572f',2),
    ('studio-portrait','LIGHT STUDY','人像光影练习','#e6d3d6','#ab6e84',3),
    ('neon-night','AFTER HOURS','夜色巡游','#c7c7e3','#686591',0),
    ('seaside-sunset','GOLDEN HOUR','沿海日落','#efcfaf','#c27c5b',1),
    ('product-skin','DAILY RITUAL','日常护理','#dfdfbd','#8f9565',2),
    ('autumn-leaves','AUTUMN NOTES','秋日采集','#e7bd9a','#a9643a',1),
    ('subway-motion','CITY RHYTHM','城市节奏','#c5d3d6','#597e85',0),
    ('ceramic-detail','HAND & EARTH','器物之间','#dfcebb','#9a795d',2),
    ('forest-light','FOREST LIGHT','森林里的光','#bed1bf','#517859',1),
    ('food-table','AT THE TABLE','风味记录','#e5c5b2','#ab6a4e',3),
]
for filename,en,cn,bg,fg,kind in scenes:
    shapes = ''
    if kind == 0:
        shapes = ''.join(f'<rect x="{x}" y="{y}" width="{w}" height="{600-y}" rx="8" fill="{fg}" opacity="{o}"/>' for x,y,w,o in [(60,240,120,.45),(205,170,85,.8),(315,290,110,.6),(455,205,140,.9),(620,140,95,.5),(735,270,130,.75)])
    elif kind == 1:
        shapes = f'<path d="M-30 580L235 185L480 580Z" fill="{fg}" opacity=".42"/><path d="M270 610L590 120L955 610Z" fill="{fg}" opacity=".85"/><path d="M-40 590Q430 260 930 580" stroke="{bg}" stroke-width="20" fill="none" opacity=".6"/>'
    elif kind == 2:
        shapes = f'<ellipse cx="460" cy="510" rx="265" ry="35" fill="{fg}" opacity=".2"/><rect x="340" y="185" width="180" height="300" rx="30" fill="{fg}"/><rect x="363" y="140" width="135" height="58" rx="12" fill="{fg}" opacity=".6"/><rect x="360" y="295" width="140" height="100" rx="2" fill="{bg}" opacity=".8"/><circle cx="640" cy="420" r="88" fill="{fg}" opacity=".35"/>'
    else:
        shapes = f'<circle cx="450" cy="265" r="100" fill="{fg}" opacity=".65"/><path d="M265 600V495Q265 360 450 360Q635 360 635 495V600" fill="{fg}"/><circle cx="720" cy="205" r="150" fill="{fg}" opacity=".12"/>'
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="900" height="600" viewBox="0 0 900 600"><rect width="900" height="600" fill="{bg}"/><circle cx="710" cy="125" r="68" fill="#fff4d9" opacity=".65"/>{shapes}<rect x="35" y="36" width="155" height="30" rx="15" fill="#fff" opacity=".65"/><text x="52" y="56" font-family="sans-serif" font-size="13" letter-spacing="3" fill="{fg}">FRAMEFLOW</text><text x="42" y="115" font-family="sans-serif" font-size="28" letter-spacing="5" fill="{fg}">{html.escape(en)}</text><text x="42" y="550" font-family="sans-serif" font-size="20" fill="#fff">{html.escape(cn)}</text></svg>'
    (output / f'{filename}.svg').write_text(svg, encoding='utf-8')
print(f'Created {len(scenes)} demo illustrations in {output}')

shutil.copytree(output, root / '.runtime/test-uploads/assets/demo', dirs_exist_ok=True)

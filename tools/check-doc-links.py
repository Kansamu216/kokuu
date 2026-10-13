import os
import re
import sys

DIST = os.path.join('docs', '.vuepress', 'dist')
BASE = '/voidkit/'

# 收集 dist 下所有已生成的路径（文件 + 目录）
existing = set()
for root, dirs, files in os.walk(DIST):
    rel = os.path.relpath(root, DIST).replace('\\', '/')
    if rel == '.':
        rel = ''
    for f in files:
        p = (rel + '/' + f).lstrip('/')
        existing.add('/' + p)
    # 目录本身也算存在（对应 index.html）
    if rel:
        existing.add('/' + rel + '/')

href_re = re.compile(r'href="(/voidkit/[^"#?]*)(#[^"]*)?"')
img_re = re.compile(r'src="(/voidkit/[^"#?]*)"')
problems = []

for root, dirs, files in os.walk(DIST):
    for f in files:
        if not f.endswith('.html'):
            continue
        path = os.path.join(root, f)
        with open(path, 'r', encoding='utf-8') as fh:
            html = fh.read()
        for m in list(href_re.finditer(html)) + list(img_re.finditer(html)):
            url = m.group(1)
            # url 形如 /voidkit/guide/ —— 去掉 base 前缀后与 dist 内的路径比较
            rel = url[len(BASE):].lstrip('/')
            cand = '/' + rel
            # 归一化
            if cand in existing:
                continue
            if (cand.rstrip('/') + '/index.html') in existing:
                continue
            nohtml = cand[:-5] if cand.endswith('.html') else cand
            if nohtml in existing or nohtml.rstrip('/') + '/index.html' in existing:
                continue
            problems.append((os.path.relpath(path, DIST), url))

if problems:
    print('发现可能的死链 %d 处：' % len(problems))
    for p, u in problems:
        print('  %-40s -> %s' % (p, u))
    sys.exit(1)
print('站内链接检查通过：无死链')

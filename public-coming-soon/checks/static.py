"""Small dependency-free checks for this static release artifact."""
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse
import struct
import xml.etree.ElementTree as ET

dist = Path(__file__).resolve().parents[1] / 'dist'

class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self.ids = set()
        self.headings = 0
        self.scripts = 0
    def handle_starttag(self, tag, pairs):
        attrs = dict(pairs)
        if 'id' in attrs:
            assert attrs['id'] not in self.ids
            self.ids.add(attrs['id'])
        if tag == 'h1': self.headings += 1
        if tag == 'script': self.scripts += 1
        for key in ('src', 'href'):
            if key in attrs: self.links.append(attrs[key])

page = Page()
page.feed((dist/'index.html').read_text())
assert page.headings == 1 and page.scripts == 0
for link in page.links:
    url = urlparse(link)
    if url.scheme:
        assert url.scheme == 'https'
        assert url.hostname in ('one3d.store', 'onecreations.in')
    elif link.startswith('#'):
        assert link[1:] in page.ids
    else:
        target = dist / (url.path.lstrip('/') or 'index.html')
        assert target.is_file(), f'Missing local asset: {link}'
for svg in (dist/'assets').glob('*.svg'): ET.parse(svg)
ET.parse(dist/'sitemap.xml')
png = (dist/'assets/social.png').read_bytes()
assert png[:8] == b'\x89PNG\r\n\x1a\n'
assert struct.unpack('>II', png[16:24]) == (1200,630)
files = list(p for p in dist.rglob('*') if p.is_file())
assert all(p.suffix not in ('.js','.map','.env') for p in files)
assert all('demo.one3d.store' not in p.read_text() for p in files if p.suffix != '.png')
def luminance(hex_value):
    rgb = [int(hex_value[i:i+2],16)/255 for i in (0,2,4)]
    linear = [v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4 for v in rgb]
    return sum(a*b for a,b in zip(linear, [.2126,.7152,.0722]))
contrast = (luminance('a8a8a8')+.05)/.05
assert contrast >= 4.5
print(f'PASS: {len(page.links)} links/assets; SVG/XML parse; 1200x630 PNG; no JS/demo references; muted text contrast {contrast:.2f}:1; {len(files)} publish files, {sum(p.stat().st_size for p in files):,} bytes.')

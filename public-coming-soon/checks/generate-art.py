"""Regenerate the original static layer illustration, favicon and social SVG."""
from pathlib import Path
import math
import xml.etree.ElementTree as ET

assets = Path(__file__).resolve().parents[1] / "dist" / "assets"
parts = []
for x, y in [(80, 88), (546, 88), (80, 550), (546, 550)]:
    parts.append(f'<path d="M{x-5} {y}h10M{x} {y-5}v10" stroke="#666"/>')
for row in range(8):
    for col in range(10):
        if (row * 17 + col * 13) % 11 in (0, 2, 5):
            parts.append(f'<rect x="{420+col*12}" y="{66+row*12}" width="3" height="3" fill="#333"/>')
for layer in range(33):
    t = layer / 32
    cy = 472 - layer * 9.7
    scale = 0.65 + 0.25 * math.sin(math.pi * t) + 0.1 * t
    points = []
    for step in range(145):
        angle = step / 144 * math.tau
        radius = 1 + 0.105 * math.cos(3 * angle + t * math.pi * 1.5)
        x = 315 + 208 * scale * radius * math.cos(angle) + 24 * math.sin(t * 3)
        y = cy + 89 * scale * radius * math.sin(angle)
        points.append(f'{x:.2f},{y:.2f}')
    shade = 105 + int(t * 128)
    parts.append(f'<polygon points="{" ".join(points)}" fill="#090909" stroke="rgb({shade},{shade},{shade})" stroke-width="1.15"/>')
parts.append('<path d="M525 217h24V499h-24M544 217h10M544 499h10" fill="none" stroke="#606060"/><path d="M98 547h395M98 542v10M493 542v10" stroke="#555"/>')
art = ''.join(parts)
(assets / 'layer-form.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" width="640" height="640" viewBox="0 0 640 640"><title>A form built layer by layer</title>' + art + '</svg>\n')

# Preserve the established mark geometry. The favicon uses its main silhouette.
ns = '{http://www.w3.org/2000/svg}'
root = ET.parse(assets / 'one3d-mark.svg').getroot()
shape = root.find(f'{ns}defs/{ns}path').attrib['d']
(assets / 'favicon.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" fill="#000"/><g transform="translate(4 17) scale(.035)"><path d="{shape}" fill="#fff" transform="translate(-120 -107)"/></g></svg>\n')
(assets / 'social.svg').write_text('''<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630"><rect width="1200" height="630" fill="#000"/><path d="M64 100H1136M64 566H1136" stroke="#333"/><text x="64" y="66" fill="#fff" font-family="Arial,Helvetica,sans-serif" font-size="28" font-weight="700">One3D<tspan fill="#a8a8a8" font-size="16" font-weight="400"> by OneCreations</tspan></text><text x="974" y="64" fill="#ccc" font-family="monospace" font-size="14" letter-spacing="2">COMING SOON</text><text x="64" y="234" fill="#fff" font-family="Arial,Helvetica,sans-serif" font-size="80" letter-spacing="-5">Ideas,</text><text x="64" y="322" fill="#fff" font-family="Arial,Helvetica,sans-serif" font-size="80" letter-spacing="-5">taking shape.</text><text x="64" y="400" fill="#a8a8a8" font-family="Arial,Helvetica,sans-serif" font-size="20">A future home for thoughtful 3D printing.</text><text x="64" y="505" fill="#a8a8a8" font-family="monospace" font-size="14">ONE3D.STORE</text><g transform="translate(715 92) scale(.67)">''' + art + '</g></svg>\n')

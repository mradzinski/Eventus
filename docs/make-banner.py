"""Draws the README's banner and the picture GitHub shows when the repository is shared.

Run it after changing the text, the colors or the logo:

    python3 docs/make-banner.py

It writes banner.png and social-preview.png next to this file. It needs rsvg-convert
(on a Mac: brew install librsvg). The social preview takes effect only once it is uploaded in the
repository's settings on GitHub.
"""
import subprocess
from pathlib import Path

SANS = "Helvetica Neue, Helvetica, Arial, sans-serif"
MONO = "Menlo, SF Mono, monospace"
ROWS = [("OnSaveClick", 110, True), ("OnQueryChange", 260, False), ("OnRefresh", 410, False)]


def logo(cx, cy, r, ring):
    """The site's logo (viewBox 120, disc radius 56) drawn with radius r at (cx, cy)."""
    s = r / 56
    return (f'<g transform="translate({cx - 60*s} {cy - 60*s}) scale({s})">'
            f'<circle cx="60" cy="60" r="53.5" fill="#0B2A3A" stroke="#5EEAD4" stroke-width="{ring}"/>'
            '<g fill="none" stroke-width="6" stroke-linecap="round">'
            '<path d="M30 60H88" stroke="#FFFFFF" stroke-opacity="0.35"/>'
            '<path d="M30 60C56 60 58 88 88 88" stroke="#FFFFFF" stroke-opacity="0.35"/>'
            '<path d="M30 60C56 60 58 32 88 32" stroke="#5EEAD4"/></g>'
            '<circle cx="30" cy="60" r="8" fill="#FFFFFF"/><circle cx="88" cy="32" r="8" fill="#5EEAD4"/>'
            '<circle cx="88" cy="60" r="6" fill="#FFFFFF" fill-opacity="0.45"/>'
            '<circle cx="88" cy="88" r="6" fill="#FFFFFF" fill-opacity="0.45"/></g>')

def illustration(x0, y0, s):
    o = [f'<g transform="translate({x0} {y0}) scale({s})">']
    lab = f'font-family="{SANS}" font-size="22" font-weight="700" letter-spacing="4" fill="#9FE8DC"'
    o.append(f'<text x="0" y="30" {lab}>EVENT</text>')
    o.append(f'<text x="640" y="30" {lab}>EVENT HANDLER</text>')
    o.append(f'<text x="470" y="356" text-anchor="middle" {lab}>ROUTER</text>')
    for name, y, active in ROWS:
        stroke = '#5EEAD4' if active else '#FFFFFF'
        op = '1' if active else '0.22'
        w = '5' if active else '3'
        o.append(f'<path d="M300 {y} C360 {y} 360 260 416 260" fill="none" stroke="{stroke}" stroke-opacity="{op}" stroke-width="{w}"/>')
        o.append(f'<path d="M524 260 C580 260 580 {y} 640 {y}" fill="none" stroke="{stroke}" stroke-opacity="{op}" stroke-width="{w}"/>')
    o.append(logo(470, 260, 56, 5))
    for name, y, active in ROWS:
        if active:
            fill, fo, st, so, tf = '#F6FFFD', '1', '#FFFFFF', '0', '#0B2A3A'
        else:
            fill, fo, st, so, tf = '#FFFFFF', '0.10', '#FFFFFF', '0.22', '#E3FBF7'
        txt = f'font-family="{MONO}" font-size="28" font-weight="{"700" if active else "400"}" fill="{tf}"'
        sh = ' filter="url(#shadow)"' if active else ''
        o.append(f'<rect x="0" y="{y-40}" width="300" height="80" rx="40" fill="{fill}" fill-opacity="{fo}" stroke="{st}" stroke-opacity="{so}" stroke-width="2"{sh}/>')
        o.append(f'<text x="150" y="{y+10}" text-anchor="middle" {txt}>{name}</text>')
        o.append(f'<rect x="640" y="{y-40}" width="440" height="80" rx="18" fill="{fill}" fill-opacity="{fo}" stroke="{st}" stroke-opacity="{so}" stroke-width="2"{sh}/>')
        o.append(f'<text x="672" y="{y+10}" {txt}>{name}Handler</text>')
    o.append('</g>')
    return "\n".join(o)

def chips(x, y, h, fs, pad, gap):
    o = []
    for name in ["Android", "iOS", "Desktop", "Web"]:
        w = len(name) * fs * 0.6 + 2 * pad
        o.append(f'<rect x="{x}" y="{y}" width="{w:.0f}" height="{h}" rx="{h/2}" fill="#FFFFFF" fill-opacity="0.14"/>')
        o.append(f'<text x="{x + w/2:.0f}" y="{y + h/2 + fs*0.35:.0f}" text-anchor="middle" font-family="{SANS}" font-size="{fs}" font-weight="700" fill="#FFFFFF">{name}</text>')
        x += w + gap
    return "\n".join(o)

def svg(W, H, radius, k, tx, top, ill):
    """k scales the text block; tx is its left edge; top is the y of the label pill."""
    gx, gy, gs = ill
    pw, ph = 510 * k, 66 * k
    title_y = top + ph + 205 * k
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<defs>
  <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#06131F"/><stop offset="0.55" stop-color="#0B3040"/><stop offset="1" stop-color="#0E6E6A"/>
  </linearGradient>
  <radialGradient id="glow" cx="{gx + 540*gs}" cy="{gy + 260*gs}" r="{620*gs}" gradientUnits="userSpaceOnUse">
    <stop offset="0" stop-color="#2DD4BF" stop-opacity="0.38"/><stop offset="1" stop-color="#2DD4BF" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="title" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#BDF5EA"/>
  </linearGradient>
  <filter id="shadow" x="-20%" y="-50%" width="140%" height="220%">
    <feDropShadow dx="0" dy="10" stdDeviation="14" flood-color="#02131A" flood-opacity="0.45"/>
  </filter>
  <clipPath id="clip"><rect width="{W}" height="{H}" rx="{radius}"/></clipPath>
</defs>
<g clip-path="url(#clip)">
  <rect width="{W}" height="{H}" fill="url(#bg)"/>
  <rect width="{W}" height="{H}" fill="url(#glow)"/>
  <rect x="{tx}" y="{top}" width="{pw}" height="{ph}" rx="{ph/2}" fill="#FFFFFF" fill-opacity="0.08" stroke="#FFFFFF" stroke-opacity="0.22" stroke-width="{2*k}"/>
  <text x="{tx + pw/2}" y="{top + ph/2 + 9.5*k}" text-anchor="middle" font-family="{SANS}" font-size="{27*k}" font-weight="700" letter-spacing="{5*k}" fill="#E3FBF7">KOTLIN MULTIPLATFORM</text>
  <text x="{tx - 8*k}" y="{title_y}" font-family="{SANS}" font-size="{232*k}" font-weight="700" letter-spacing="{-6*k}" fill="url(#title)">Eventus</text>
  <text font-family="{SANS}" font-size="{50*k}" fill="#A9E9DF">
    <tspan x="{tx}" y="{title_y + 100*k}">Routes each UI event of a screen</tspan>
    <tspan x="{tx}" y="{title_y + 164*k}">to its own event handler.</tspan>
  </text>
  {chips(tx, title_y + 212*k, 64*k, 30*k, 36*k, 20*k)}
  {illustration(gx, gy, gs)}
</g>
</svg>'''

here = Path(__file__).parent
pictures = {
    "banner": svg(2560, 840, 56, 1.0, 160, 132, (1330, 160, 1.0)),
    "social-preview": svg(1280, 640, 0, 0.5, 90, 160, (650, 185, 0.52)),
}

for name, drawing in pictures.items():
    subprocess.run(["rsvg-convert", "-o", str(here / f"{name}.png")], input=drawing.encode(), check=True)

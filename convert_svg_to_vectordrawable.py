#!/usr/bin/env python3
"""
Convert Flaticon pharmacy SVG to Android VectorDrawable.
- Input: mibotiquin.svg (288x288, transform="translate(0,288) scale(0.1,-0.1)")
- Output: ic_launcher_foreground.xml (108x108, white fill)
"""

import re
from svg.path import parse_path
from xml.etree import ElementTree as ET

SVG_PATH = "/media/anon/Datos/Codigo/MiBotiquin/mibotiquin.svg"
OUTPUT_PATH = "/media/anon/Datos/Codigo/MiBotiquin/app/src/main/res/drawable/ic_launcher_foreground.xml"

# SVG has: <g transform="translate(0,288) scale(0.1,-0.1)">
# This means: x' = x * 0.1, y' = 288 - y * 0.1
# Viewport 288x288 -> target 108x108
# So final: x_final = x * 0.1 * (108/288) = x * 0.0375
#           y_final = (288 - y * 0.1) * (108/288) = 108 - y * 0.0375

SCALE = 108 / 2880  # 0.0375 (combined: 0.1 * 108/288)
VIEWPORT = 108

def transform_point(x: float, y: float) -> tuple[float, float]:
    """Apply SVG group transform + viewport scaling."""
    return (x * SCALE, VIEWPORT - y * SCALE)

def transform_segment(seg) -> str:
    """Transform a single path segment and return SVG path command string."""
    # Get all relevant points for this segment type
    start = seg.start
    end = seg.end
    
    # Transform points
    sx, sy = transform_point(start.real, start.imag)
    ex, ey = transform_point(end.real, end.imag)
    
    if seg.__class__.__name__ == 'Line':
        return f"L {ex:.3f} {ey:.3f}"
    
    elif seg.__class__.__name__ == 'CubicBezier':
        c1x, c1y = transform_point(seg.control1.real, seg.control1.imag)
        c2x, c2y = transform_point(seg.control2.real, seg.control2.imag)
        return f"C {c1x:.3f} {c1y:.3f} {c2x:.3f} {c2y:.3f} {ex:.3f} {ey:.3f}"
    
    elif seg.__class__.__name__ == 'QuadraticBezier':
        cx, cy = transform_point(seg.control.real, seg.control.imag)
        return f"Q {cx:.3f} {cy:.3f} {ex:.3f} {ey:.3f}"
    
    elif seg.__class__.__name__ == 'Arc':
        # Arc: rx, ry, x_axis_rotation, large_arc_flag, sweep_flag, x, y
        rx, ry = seg.radius.real * SCALE, seg.radius.imag * SCALE
        rotation = seg.rotation
        large_arc = 1 if seg.large_arc else 0
        sweep = 1 if seg.sweep else 0
        return f"A {rx:.3f} {ry:.3f} {rotation:.3f} {large_arc} {sweep} {ex:.3f} {ey:.3f}"
    
    elif seg.__class__.__name__ == 'Close':
        return "Z"
    
    else:
        # Move, etc.
        return f"M {ex:.3f} {ey:.3f}"

def transform_path(d: str) -> str:
    """Transform entire SVG path data string."""
    path = parse_path(d)
    if not path:
        return ""
    
    result = []
    first = True
    
    for seg in path:
        if first:
            # First segment - use Move to start point
            sx, sy = transform_point(seg.start.real, seg.start.imag)
            result.append(f"M {sx:.3f} {sy:.3f}")
            first = False
        
        cmd = transform_segment(seg)
        result.append(cmd)
    
    return " ".join(result)

def main():
    # Parse SVG
    tree = ET.parse(SVG_PATH)
    root = tree.getroot()
    ns = {'svg': 'http://www.w3.org/2000/svg'}
    
    # Find all paths in the group
    paths_data = []
    for g in root.findall('.//svg:g', ns):
        for path_elem in g.findall('svg:path', ns):
            d = path_elem.get('d')
            if d:
                paths_data.append(d)
    
    print(f"Found {len(paths_data)} paths")
    
    # Transform each path
    transformed_paths = []
    for i, d in enumerate(paths_data):
        transformed = transform_path(d)
        if transformed:
            transformed_paths.append(transformed)
            print(f"Path {i+1}: {len(d)} chars -> {len(transformed)} chars")
    
    # Generate VectorDrawable XML
    xml_lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{VIEWPORT}dp"',
        f'    android:height="{VIEWPORT}dp"',
        f'    android:viewportWidth="{VIEWPORT}"',
        f'    android:viewportHeight="{VIEWPORT}">',
    ]
    
    for d in transformed_paths:
        xml_lines.append(f'    <path android:fillColor="#FFFFFF" android:pathData="{d}" />')
    
    xml_lines.append('</vector>')
    
    output_xml = "\n".join(xml_lines)
    
    with open(OUTPUT_PATH, 'w') as f:
        f.write(output_xml)
    
    print(f"\nGenerated: {OUTPUT_PATH}")
    print(f"Total paths: {len(transformed_paths)}")

if __name__ == '__main__':
    main()
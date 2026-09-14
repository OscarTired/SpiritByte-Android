"""Convert existing desktop assets; no generated/replacement artwork.
Requires fonttools and brotli. Run from the mobile project directory.
"""
from pathlib import Path
import xml.etree.ElementTree as ET
from fontTools.ttLib import TTFont

root = Path(__file__).resolve().parents[1]
desktop = root.parent / "SpireByte-V2" / "public"
fonts = root / "app/src/main/res/font"
fonts.mkdir(parents=True, exist_ok=True)
for variant in ("Square", "Grid", "Circle", "Triangle", "Line"):
    font = TTFont(desktop / "fonts" / f"GeistPixel-{variant}.woff2")
    font.flavor = None
    font.save(fonts / f"geist_pixel_{variant.lower()}.ttf")

android = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", android)
aapt = "http://schemas.android.com/aapt"
ET.register_namespace("aapt", aapt)
tools = "http://schemas.android.com/tools"
ET.register_namespace("tools", tools)
svg = ET.parse(desktop / "fox-byte.svg").getroot()
gradients = {e.attrib["id"]: e for e in svg.iter("{http://www.w3.org/2000/svg}linearGradient")}
vector = ET.Element("vector", {f"{{{android}}}{k}": v for k, v in {
    "width": "360dp", "height": "360dp", "viewportWidth": "1024", "viewportHeight": "1024"
}.items()})
# This is source artwork rasterized once off the UI thread, not a vector redrawn
# every animation frame. Preserve the desktop facets rather than simplifying them.
vector.set(f"{{{tools}}}ignore", "VectorRaster,VectorPath")
for path in svg.iter("{http://www.w3.org/2000/svg}path"):
    fill = path.attrib.get("fill", "#000000")
    converted = ET.SubElement(vector, "path", {f"{{{android}}}{k}": v for k, v in {
        "pathData": path.attrib["d"], "fillColor": fill if not fill.startswith("url(") else "#000000",
        "strokeColor": "#D9FFFFFF", "strokeWidth": "2.2", "strokeLineJoin": "round"
    }.items()})
    if fill.startswith("url("):
        gradient = gradients[fill[5:-1]]
        assert gradient.attrib["gradientUnits"] == "userSpaceOnUse"
        del converted.attrib[f"{{{android}}}fillColor"]
        attr = ET.SubElement(converted, f"{{{aapt}}}attr", {"name": "android:fillColor"})
        native = ET.SubElement(attr, "gradient", {f"{{{android}}}{k}": v for k, v in {
            "type": "linear", "startX": gradient.attrib["x1"], "startY": gradient.attrib["y1"],
            "endX": gradient.attrib["x2"], "endY": gradient.attrib["y2"]
        }.items()})
        for stop in gradient:
            ET.SubElement(native, "item", {f"{{{android}}}offset": stop.attrib["offset"],
                f"{{{android}}}color": stop.attrib["stop-color"]})
ET.indent(vector)
ET.ElementTree(vector).write(root / "app/src/main/res/drawable/fox_facets.xml", encoding="utf-8", xml_declaration=True)
print(f"Imported five Geist Pixel fonts and {len(vector)} original fox facets.")

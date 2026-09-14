// Reuse the exact Lucide nodes installed in the desktop project.
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const desktop = path.resolve(root, '../SpireByte-V2');
const icons = { folder:'folder', globe:'globe', mail:'mail', shield:'shield', user:'user', wallet:'wallet', briefcase:'briefcase',
  cloud:'cloud', gamepad:'gamepad-2', key:'key-square', heart:'heart', home:'house', music:'music', camera:'camera', code:'code',
  book:'book', cart:'shopping-cart', server:'server', phone:'smartphone', card:'credit-card', plane:'plane', gift:'gift' };
const escape = (s) => String(s).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;');
function pathData(tag, p) {
  if (tag === 'path') return p.d;
  if (tag === 'line') return `M${p.x1} ${p.y1}L${p.x2} ${p.y2}`;
  if (tag === 'polyline' || tag === 'polygon') {
    const coordinates = p.points.trim().split(/[\s,]+/).map(Number);
    if (coordinates.length % 2 || coordinates.some((n) => !Number.isFinite(n))) throw new Error('Invalid SVG points');
    return coordinates.reduce((d, n, i) => i % 2 === 0 ? d + (i ? 'L' : 'M') + n + ',' : d + n, '') + (tag === 'polygon' ? 'Z' : '');
  }
  if (tag === 'circle') { const x=+p.cx,y=+p.cy,r=+p.r; return `M${x-r},${y}a${r},${r} 0 1,0 ${r*2},0a${r},${r} 0 1,0 ${-r*2},0`; }
  if (tag === 'rect') {
    const x=+(p.x??0),y=+(p.y??0),w=+p.width,h=+p.height,r=+(p.rx??0);
    if (!r) return `M${x},${y}h${w}v${h}h${-w}Z`;
    return `M${x+r},${y}h${w-2*r}a${r},${r} 0 0 1 ${r},${r}v${h-2*r}a${r},${r} 0 0 1 ${-r},${r}h${-w+2*r}a${r},${r} 0 0 1 ${-r},${-r}v${-h+2*r}a${r},${r} 0 0 1 ${r},${-r}Z`;
  }
  throw new Error(`Unsupported SVG primitive ${tag}`);
}
for (const [id, name] of Object.entries(icons)) {
  const source=fs.readFileSync(path.join(desktop,`node_modules/lucide-react/dist/esm/icons/${name}.js`),'utf8');
  const match=source.match(/createLucideIcon\("[^"]+",\s*(\[[\s\S]*?\])\s*\);/);
  if (!match) throw new Error(`Missing icon data: ${name}`);
  const nodes=vm.runInNewContext(match[1], Object.create(null), {timeout:1000});
  const xml=`<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n` +
    nodes.map(([tag,p])=>`  <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="2" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="${escape(pathData(tag,p))}" />`).join('\n')+'\n</vector>\n';
  fs.writeFileSync(path.join(root,`app/src/main/res/drawable/folder_icon_${id}.xml`),xml);
}
fs.copyFileSync(path.join(desktop,'node_modules/lucide-react/LICENSE'), path.join(root,'app/src/main/assets/licenses/Lucide-LICENSE.txt'));
fs.mkdirSync(path.join(root,'app/src/main/res/drawable-nodpi'),{recursive:true});
fs.copyFileSync(path.join(desktop,'src-tauri/icons/icon.png'),path.join(root,'app/src/main/res/drawable-nodpi/desktop_logo.png'));
console.log('Copied desktop logo and 22 Lucide folder icons.');

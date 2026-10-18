"""Mechanical NeoForge 26.3 rewrites for Power Grid's Java sources (re-run after a rebase on upstream).

    python tools/port_26_3_mechanical.py src/main/java

1. Drops Fabric's @Environment(EnvType.X) annotations and their imports. NeoForge has no equivalent; client-only
   classes are simply never loaded on a dedicated server.
2. Rewrites references to classes 26.3 moved (com.mojang.blaze3d -> com.mojang.renderpearl, ...) using
   tools/class-moves-26.3.txt, which is copied from ZP5's Create Fly port (its tools/remap_26_3_imports.py builds it).
"""
import pathlib
import re
import sys

here = pathlib.Path(__file__).resolve().parent
mapping = dict(line.split() for line in (here / 'class-moves-26.3.txt').read_text(encoding='utf-8').splitlines() if line.strip())
pattern = re.compile(r'\b(' + '|'.join(re.escape(k) for k in sorted(mapping, key=len, reverse=True)) + r')\b(?![\w])')
slash = re.compile(r'L(' + '|'.join(re.escape(k.replace('.', '/')) for k in sorted(mapping, key=len, reverse=True)) + r')([;$])')
env_ann = re.compile(r'^[ \t]*@Environment\(\s*EnvType\.\w+\s*\)[ \t]*\r?\n', re.M)
env_inline = re.compile(r'@Environment\(\s*EnvType\.\w+\s*\)\s*')
env_imp = re.compile(r'^import net\.fabricmc\.api\.(EnvType|Environment);\r?\n', re.M)

changed = 0
for root in sys.argv[1:]:
    for f in pathlib.Path(root).rglob('*.java'):
        raw = f.read_bytes().decode('utf-8')
        out = env_ann.sub('', raw)
        out = env_inline.sub('', out)
        out = env_imp.sub('', out)
        out = pattern.sub(lambda m: mapping[m.group(1)], out)
        out = slash.sub(lambda m: 'L' + mapping[m.group(1).replace('/', '.')].replace('.', '/') + m.group(2), out)
        if out != raw:
            f.write_bytes(out.encode('utf-8'))
            changed += 1
print(f'{len(mapping)} moved classes, {changed} files rewritten')

from pathlib import Path
p = Path('fakewa/src/main/java/com/whatsapp/w4b/MainActivity.java')
s = p.read_text()
old = '''        chats.setSelected(true);\n'''
new = '''        // Start with ambiguous tab-selection metadata so DraftWA must explicitly\n        // normalize back to Chats before using the scrollable fallback.\n        chats.setSelected(false);\n'''
if old not in s:
    raise SystemExit('chats selected marker not found')
s = s.replace(old, new, 1)
old = '''            spacer.setText("Header " + i);\n            spacer.setVisibility(View.GONE);\n            root.addView(spacer);\n'''
new = '''            spacer.setText("H" + i);\n            spacer.setTextSize(1);\n            spacer.setHeight(1);\n            root.addView(spacer);\n'''
if old not in s:
    raise SystemExit('spacer marker not found')
s = s.replace(old, new, 1)
p.write_text(s)
print('0.8.4 regression fixture strengthened')

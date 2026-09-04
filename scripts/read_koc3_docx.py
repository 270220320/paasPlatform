# -*- coding: utf-8 -*-
"""读取 KOC3 测试报告 docx，输出全部段落（含样式名）和表格概要"""
import sys
from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph
from docx.oxml.ns import qn

sys.stdout.reconfigure(encoding='utf-8')

path = r"D:\KOC3\大疆上云 API 2 KOC 3 测试报告模板_20260826(1).docx"
doc = Document(path)

def iter_block_items(parent):
    parent_elm = parent.element.body
    for child in parent_elm.iterchildren():
        if child.tag == qn('w:p'):
            yield Paragraph(child, parent)
        elif child.tag == qn('w:tbl'):
            yield Table(child, parent)

idx = 0
for block in iter_block_items(doc):
    if isinstance(block, Paragraph):
        text = block.text.strip()
        if text:
            style = block.style.name if block.style else ''
            print(f"[P{idx}][{style}] {text}")
    else:
        rows = len(block.rows)
        cols = len(block.columns)
        first_cells = []
        try:
            for r in block.rows[:2]:
                first_cells.append(' | '.join(c.text.strip().replace('\n', ' ')[:30] for c in r.cells))
        except Exception as e:
            first_cells.append(f'<err {e}>')
        print(f"[T{idx}][TABLE {rows}x{cols}] {' // '.join(first_cells)}")
    idx += 1

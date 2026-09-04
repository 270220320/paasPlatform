# -*- coding: utf-8 -*-
"""生成汇报 PPT：使用 AI 走通无人机云端作业平台（UAVPlatform）全流程开发"""
from pptx import Presentation
from pptx.util import Inches as In, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.oxml.ns import qn

# ---------- 配色与字体 ----------
NAVY   = RGBColor.from_string('0F2B46')   # 深藏蓝
BLUE   = RGBColor.from_string('2E75B6')   # 主蓝
ACCENT = RGBColor.from_string('00B0F0')   # 亮蓝
LIGHT  = RGBColor.from_string('EAF2FA')   # 浅蓝底
LIGHT2 = RGBColor.from_string('F5F9FD')   # 更浅
GRAY   = RGBColor.from_string('5B6B7C')   # 灰
DARK   = RGBColor.from_string('22303E')   # 深灰字
LINEC  = RGBColor.from_string('C9D8E8')   # 分隔线
WHITE  = RGBColor.from_string('FFFFFF')
ORANGE = RGBColor.from_string('ED7D31')
GREEN  = RGBColor.from_string('2E9E6B')
FONT = '微软雅黑'

prs = Presentation()
prs.slide_width = In(13.333)
prs.slide_height = In(7.5)
BLANK = prs.slide_layouts[6]


def set_font(run, size=14, bold=False, color=DARK):
    run.font.name = FONT
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    rPr = run._r.get_or_add_rPr()
    ea = rPr.find(qn('a:ea'))
    if ea is None:
        ea = rPr.makeelement(qn('a:ea'), {})
        rPr.append(ea)
    ea.set('typeface', FONT)


def add_text(slide, x, y, w, h, paras, anchor=MSO_ANCHOR.TOP, wrap=True):
    tb = slide.shapes.add_textbox(In(x), In(y), In(w), In(h))
    tf = tb.text_frame
    tf.word_wrap = wrap
    tf.vertical_anchor = anchor
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    first = True
    for p in paras:
        para = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        para.alignment = p.get('align', PP_ALIGN.LEFT)
        para.space_before = Pt(p.get('sb', 0))
        para.space_after = Pt(p.get('sa', 0))
        if 'line' in p:
            para.line_spacing = p['line']
        for (t, s, b, c) in p['runs']:
            r = para.add_run()
            r.text = t
            set_font(r, size=s, bold=b, color=c)
    return tb


def add_shape(slide, kind, x, y, w, h, fill=None, line_color=None, line_w=0.75):
    sp = slide.shapes.add_shape(kind, In(x), In(y), In(w), In(h))
    if fill is None:
        sp.fill.background()
    else:
        sp.fill.solid()
        sp.fill.fore_color.rgb = fill
    if line_color is None:
        sp.line.fill.background()
    else:
        sp.line.color.rgb = line_color
        sp.line.width = Pt(line_w)
    sp.shadow.inherit = False
    return sp


def shape_text(sp, paras, anchor=MSO_ANCHOR.MIDDLE):
    tf = sp.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    tf.margin_left = tf.margin_right = In(0.08)
    tf.margin_top = tf.margin_bottom = In(0.03)
    first = True
    for p in paras:
        para = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        para.alignment = p.get('align', PP_ALIGN.CENTER)
        para.space_before = Pt(p.get('sb', 0))
        para.space_after = Pt(p.get('sa', 0))
        if 'line' in p:
            para.line_spacing = p['line']
        for (t, s, b, c) in p['runs']:
            r = para.add_run()
            r.text = t
            set_font(r, size=s, bold=b, color=c)
    return sp


def set_alpha(sp, pct):
    """pct: 0-100 透明度百分比"""
    sF = sp.fill._xPr.find(qn('a:solidFill'))
    clr = sF.find(qn('a:srgbClr'))
    a = clr.makeelement(qn('a:alpha'), {'val': str(int(pct * 1000))})
    clr.append(a)


PAGE_NO = [0]

def footer(slide):
    PAGE_NO[0] += 1
    add_text(slide, 0.55, 7.12, 6.0, 0.3,
             [{'runs': [('UAVPlatform · AI 驱动开发实践汇报', 9, False, GRAY)]}])
    add_text(slide, 12.3, 7.12, 0.5, 0.3,
             [{'runs': [(str(PAGE_NO[0]), 10, False, GRAY)], 'align': PP_ALIGN.RIGHT}])


def header(slide, part, title):
    add_shape(slide, MSO_SHAPE.RECTANGLE, 0.55, 0.44, 0.10, 0.52, fill=BLUE)
    add_text(slide, 0.82, 0.38, 8.6, 0.62, [{'runs': [(title, 24, True, NAVY)]}],
             anchor=MSO_ANCHOR.MIDDLE)
    add_text(slide, 9.2, 0.50, 3.58, 0.4,
             [{'runs': [(part, 11.5, False, GRAY)], 'align': PP_ALIGN.RIGHT}])
    add_shape(slide, MSO_SHAPE.RECTANGLE, 0.55, 1.12, 12.23, 0.018, fill=LINEC)
    footer(slide)


def badge(slide, x, y, d, num, fill=BLUE, color=WHITE, size=13):
    c = add_shape(slide, MSO_SHAPE.OVAL, x, y, d, d, fill=fill)
    shape_text(c, [{'runs': [(num, size, True, color)]}])
    return c


def table_style(tbl):
    tbl.first_row = False
    tbl.horz_banding = False


def cell_set(cell, paras, fill=None, anchor=MSO_ANCHOR.MIDDLE):
    if fill is not None:
        cell.fill.solid()
        cell.fill.fore_color.rgb = fill
    cell.vertical_anchor = anchor
    cell.margin_left = cell.margin_right = In(0.10)
    cell.margin_top = cell.margin_bottom = In(0.04)
    tf = cell.text_frame
    tf.word_wrap = True
    first = True
    for p in paras:
        para = tf.paragraphs[0] if first else tf.add_paragraph()
        first = False
        para.alignment = p.get('align', PP_ALIGN.LEFT)
        para.space_before = Pt(p.get('sb', 0))
        para.space_after = Pt(p.get('sa', 0))
        if 'line' in p:
            para.line_spacing = p['line']
        for (t, s, b, c) in p['runs']:
            r = para.add_run()
            r.text = t
            set_font(r, size=s, bold=b, color=c)


# ============================================================
# P1 封面
# ============================================================
s = prs.slides.add_slide(BLANK)
add_shape(s, MSO_SHAPE.RECTANGLE, 0, 0, 13.333, 7.5, fill=NAVY)
# 右侧装饰圆环与斜条
ring = add_shape(s, MSO_SHAPE.OVAL, 8.9, 1.6, 5.6, 5.6, fill=None, line_color=ACCENT, line_w=1.2)
ring2 = add_shape(s, MSO_SHAPE.OVAL, 10.1, 2.8, 3.2, 3.2, fill=None, line_color=BLUE, line_w=1.0)
dot = add_shape(s, MSO_SHAPE.OVAL, 11.55, 4.25, 0.3, 0.3, fill=ACCENT)
bar1 = add_shape(s, MSO_SHAPE.PARALLELOGRAM, 9.6, 0.9, 2.2, 0.16, fill=BLUE)
set_alpha(bar1, 55)
bar2 = add_shape(s, MSO_SHAPE.PARALLELOGRAM, 10.3, 1.35, 1.6, 0.12, fill=ACCENT)
set_alpha(bar2, 55)
add_shape(s, MSO_SHAPE.RECTANGLE, 0.9, 2.28, 0.62, 0.085, fill=ACCENT)
add_text(s, 0.9, 2.55, 11.5, 1.9, [
    {'runs': [('AI 驱动开发实践', 44, True, WHITE)], 'sa': 6},
    {'runs': [('用 AI 走通无人机云端作业平台全流程开发', 24, False, RGBColor.from_string('BFDC F2'.replace(' ', '')))],
     'sa': 0},
])
add_text(s, 0.9, 4.35, 11.0, 0.5, [
    {'runs': [('从一张需求截图，到可运行的 UAVPlatform · 司空 2 深度集成平台', 14, False, RGBColor.from_string('9FB8CE'))]},
])
add_shape(s, MSO_SHAPE.RECTANGLE, 0.9, 5.6, 3.3, 0.014, fill=RGBColor.from_string('3C5878'))
add_text(s, 0.9, 5.78, 11.0, 1.0, [
    {'runs': [('汇报人：____________', 13, False, RGBColor.from_string('9FB8CE'))], 'sa': 6},
    {'runs': [('2026 年 8 月', 13, False, RGBColor.from_string('9FB8CE'))]},
])

# ============================================================
# P2 目录
# ============================================================
s = prs.slides.add_slide(BLANK)
add_text(s, 0.9, 0.55, 6.0, 0.8, [{'runs': [('目录', 30, True, NAVY)]}])
add_shape(s, MSO_SHAPE.RECTANGLE, 0.92, 1.45, 0.8, 0.07, fill=ACCENT)
items = [
    ('01', 'AI 驱动开发的发展历程', '从代码补全到 Agent 全自动化的四个阶段'),
    ('02', '本平台的 AI 使用过程', '需求截图 → PRD → 原型 → 设计 → 编码 → 测试全链路'),
    ('03', '当前平台的功能', '单作业空间 · 12 大模块 · 与司空 2 深度集成'),
    ('04', 'AI 使用过程中的注意事项', '输入质量、规范前置、小步快跑、人在回路、人员能力新要求'),
    ('05', '未来的展望', '平台智能化 · 开发流程演进 · 组织能力沉淀'),
]
for i, (no, t, sub) in enumerate(items):
    y = 1.95 + i * 1.02
    card = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, 0.9, y, 11.5, 0.86, fill=LIGHT2, line_color=LINEC)
    card.adjustments[0] = 0.14
    add_text(s, 1.25, y + 0.13, 1.2, 0.6, [{'runs': [(no, 26, True, BLUE)]}])
    add_text(s, 2.5, y + 0.10, 9.6, 0.4, [{'runs': [(t, 17, True, NAVY)]}])
    add_text(s, 2.5, y + 0.47, 9.6, 0.32, [{'runs': [(sub, 11.5, False, GRAY)]}])

# ============================================================
# P3 发展历程 · 时间线
# ============================================================
s = prs.slides.add_slide(BLANK)
header(s, 'PART 01 · 发展历程', 'AI 驱动开发的发展历程：四个阶段')
add_shape(s, MSO_SHAPE.RECTANGLE, 1.0, 2.06, 11.4, 0.03, fill=LINEC)
stages = [
    ('~2021', '代码补全', 'TabNine / GitHub Copilot',
     ['行级、片段级自动补全', '人主导编写，AI 被动提示', '上下文：仅当前文件'], BLUE),
    ('2022-2023', '对话式生成', 'ChatGPT / Copilot Chat',
     ['问答生成函数、解释代码', '人负责搬运代码进工程', '上下文：单轮对话窗口'], BLUE),
    ('2023-2024', '工程化协作', 'Cursor / Qoder 等 AI IDE',
     ['按架构与规范生成整个文件', '跨文件理解、批量修改', '上下文：整个工程仓库'], ORANGE),
    ('2024 至今', 'Agent 全自动化', 'Claude Code / Devin / 自主 Agent',
     ['目标驱动：规划-编码-运行-修复闭环', '可调用终端、检索、浏览器等工具', '上下文：工程+知识库+规则'], GREEN),
]
for i, (yr, name, tool, pts, clr) in enumerate(stages):
    cx = 2.2 + i * 3.0
    add_text(s, cx - 1.35, 1.42, 2.7, 0.35,
             [{'runs': [(yr, 12.5, True, GRAY)], 'align': PP_ALIGN.CENTER}])
    add_shape(s, MSO_SHAPE.OVAL, cx - 0.14, 1.93, 0.28, 0.28, fill=clr)
    card = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, cx - 1.38, 2.5, 2.76, 3.75,
                     fill=WHITE, line_color=LINEC)
    card.adjustments[0] = 0.06
    add_shape(s, MSO_SHAPE.RECTANGLE, cx - 1.38, 2.5, 2.76, 0.09, fill=clr)
    add_text(s, cx - 1.18, 2.78, 2.4, 0.42, [{'runs': [(name, 16.5, True, NAVY)]}])
    add_text(s, cx - 1.18, 3.24, 2.4, 0.62, [{'runs': [(tool, 10.5, False, BLUE)], 'line': 1.1}])
    paras = []
    for p in pts:
        paras.append({'runs': [('· ', 11.5, True, clr), (p, 11, False, DARK)], 'sa': 7, 'line': 1.12})
    add_text(s, cx - 1.18, 3.95, 2.42, 2.2, paras)
band = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, 0.55, 6.5, 12.23, 0.52, fill=LIGHT)
band.adjustments[0] = 0.3
shape_text(band, [{'runs': [('趋势：', 12.5, True, NAVY),
                            ('从「补全一行代码」到「交付一个可运行系统」，人的角色由编写者转变为目标的制定者与验收者', 12.5, False, DARK)]}])

# ============================================================
# P4 发展历程 · 能力对比表
# ============================================================
s = prs.slides.add_slide(BLANK)
header(s, 'PART 01 · 发展历程', '四个阶段的能力对比')
rows, cols = 6, 5
gf = s.shapes.add_table(rows, cols, In(0.55), In(1.5), In(12.23), In(5.2))
tbl = gf.table
table_style(tbl)
widths = [1.9, 2.58, 2.58, 2.58, 2.59]
for i, w in enumerate(widths):
    tbl.columns[i].width = In(w)
tbl.rows[0].height = In(0.62)
for r in range(1, rows):
    tbl.rows[r].height = In(0.9)
head = ['对比维度', '阶段一 · 代码补全', '阶段二 · 对话式生成', '阶段三 · 工程化协作', '阶段四 · Agent 全自动化']
for c in range(cols):
    cell_set(tbl.cell(0, c), [{'runs': [(head[c], 12.5, True, WHITE)], 'align': PP_ALIGN.CENTER}],
             fill=NAVY)
data = [
    ('交互方式', '自动补全、被动提示', '自然语言问答', '指令式编辑（选中即改）', '目标委托、任务编排'),
    ('上下文范围', '当前文件', '对话窗口，需人工复制粘贴', '整个工程、跨多文件', '工程 + 工具 + 知识库 + 规则'),
    ('单次产出', '行、代码片段', '函数、代码块', '整个文件、功能模块', '可运行的功能闭环'),
    ('人的角色', '主编写者', '提问者 + 搬运工', '架构师 + 审查者', '目标制定者 + 验收者'),
    ('典型工具', 'TabNine、Copilot', 'ChatGPT、Copilot Chat', 'Cursor、Qoder AI IDE', 'Claude Code、Devin'),
]
for r, row in enumerate(data, start=1):
    cell_set(tbl.cell(r, 0), [{'runs': [(row[0], 12, True, BLUE)], 'align': PP_ALIGN.CENTER}], fill=LIGHT)
    for c in range(1, 5):
        bg = WHITE if r % 2 == 1 else LIGHT2
        cell_set(tbl.cell(r, c), [{'runs': [(row[c], 11.5, False, DARK)],
                                   'align': PP_ALIGN.CENTER, 'line': 1.1}], fill=bg)
add_text(s, 0.55, 6.85, 12.23, 0.4,
         [{'runs': [('本次汇报的实践基于阶段三、四：以 AI IDE + Agent 完成「文档 → 代码 → 测试」全流程', 12, True, ORANGE)],
           'align': PP_ALIGN.CENTER}])

# ============================================================
# P5 本平台 AI 使用过程 · 全流程流水线
# ============================================================
s = prs.slides.add_slide(BLANK)
header(s, 'PART 02 · AI 使用过程', '全流程流水线：每一步都有明确输入与产物')
flow1 = [
    ('1 需求输入', '司空 2 界面截图 + 场景说明'),
    ('2 PRD', '产品需求文档 V0.1 → V1.0'),
    ('3 交互原型', 'Element Plus 深色高保真原型'),
    ('4 概要设计', '模块划分 + 功能清单 U-ID'),
    ('5 详细设计', 'SDD 接口规约 + DDL / ER'),
]
flow2 = [
    ('6 编码实现', 'U-001 → U-091 前后端全量'),
    ('7 测试设计', '计划 / 方案 / 用例 / 冒烟'),
    ('8 联调排障', '代理 / WSL / 司空通道'),
    ('9 回写校准', 'PRD 与 SDD 升版 V2.0'),
]
for row_i, flow in enumerate([flow1, flow2]):
    y = 1.62 + row_i * 2.42
    n = len(flow)
    w = 2.62
    x0 = 0.55
    for i, (name, sub) in enumerate(flow):
        kind = MSO_SHAPE.PENTAGON if i == 0 else MSO_SHAPE.CHEVRON
        sp = add_shape(s, kind, x0 + i * (w + 0.06), y, w, 0.98,
                       fill=NAVY if row_i == 0 else BLUE)
        if i > 0:
            sp.adjustments[0] = 0.55
        shape_text(sp, [
            {'runs': [(name, 13.5, True, WHITE)], 'sa': 2},
            {'runs': [(sub, 9.5, False, RGBColor.from_string('CFE4F5'))], 'line': 1.0},
        ])
    if row_i == 0:
        ar = add_shape(s, MSO_SHAPE.DOWN_ARROW, 6.5, 2.68, 0.5, 0.5, fill=ORANGE)
note = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, 0.55, 6.35, 12.23, 0.68, fill=LIGHT)
note.adjustments[0] = 0.25
shape_text(note, [{'runs': [
    ('做法：', 12.5, True, NAVY),
    ('文档先行（SDD 先定接口契约再写实现），小步快跑逐功能点推进，实现完成后由 AI 回写校准文档，保证文档与代码始终一致',
     12.5, False, DARK)]}])

# ============================================================
# P6 各环节的输入与产出
# ============================================================
s = prs.slides.add_slide(BLANK)
header(s, 'PART 02 · AI 使用过程', '各环节怎么用：给 AI 什么，AI 产出什么')
gf = s.shapes.add_table(7, 3, In(0.55), In(1.5), In(12.23), In(5.3))
tbl = gf.table
table_style(tbl)
for i, w in enumerate([1.85, 5.1, 5.28]):
    tbl.columns[i].width = In(w)
tbl.rows[0].height = In(0.55)
for r in range(1, 7):
    tbl.rows[r].height = In(0.79)
cell_set(tbl.cell(0, 0), [{'runs': [('环节', 12.5, True, WHITE)], 'align': PP_ALIGN.CENTER}], fill=NAVY)
cell_set(tbl.cell(0, 1), [{'runs': [('给 AI 的输入（关键约束）', 12.5, True, WHITE)], 'align': PP_ALIGN.CENTER}], fill=NAVY)
cell_set(tbl.cell(0, 2), [{'runs': [('AI 的产出', 12.5, True, WHITE)], 'align': PP_ALIGN.CENTER}], fill=NAVY)
rows6 = [
    ('需求梳理', '司空平台界面截图 + 单作业空间定位说明', '需求文档、PRD（V0.1 → V1.0 定稿）'),
    ('交互原型', '约束：Element Plus + 深色主题 + 单 HTML 文件', '可点开浏览的高保真原型页面'),
    ('系统设计', 'PRD + 模块划分（12 个分册）', '概要设计、详细设计 SDD、ER 图、DDL 全套'),
    ('编码实现', '技术规范：Vue3+Vite+TS+UnoCSS+ElementPlus / Spring Boot 3.3+MyBatis Plus，先做 U-001', 'U-001~U-091 前后端全量功能实现'),
    ('测试设计', '设计文档与接口契约', '测试计划、测试方案、测试用例、冒烟用例'),
    ('联调排障', '报错日志 + 现象描述', '根因定位建议、修复方案与验证脚本'),
]
for r, (a, b, c) in enumerate(rows6, start=1):
    bg = WHITE if r % 2 == 1 else LIGHT2
    cell_set(tbl.cell(r, 0), [{'runs': [(a, 12, True, BLUE)], 'align': PP_ALIGN.CENTER}], fill=LIGHT)
    cell_set(tbl.cell(r, 1), [{'runs': [(b, 11.5, False, DARK)], 'line': 1.1}], fill=bg)
    cell_set(tbl.cell(r, 2), [{'runs': [(c, 11.5, False, DARK)], 'line': 1.1}], fill=bg)

# ============================================================
# P7 关键实践与典型问题
# ============================================================
s = prs.slides.add_slide(BLANK)
header(s, 'PART 02 · AI 使用过程', '四个关键实践：真实过程回顾')
cards = [
    ('1', '需求即截图', '以司空 2 界面截图直接作为需求源，AI 识别界面布局与公开能力，梳理成结构化 PRD；产品裁剪原则（不做多项目）一并写入。', BLUE),
    ('2', '原型有约束', '明确「Element Plus + 深色主题 + 单 HTML 文件」三点约束，AI 一次产出可交互的高保真原型，直接用于评审与对齐。', BLUE),
    ('3', '编码前定规范', '技术栈一次说清：前端 Vue3+Vite+TS+UnoCSS+Element Plus，后端 MyBatis Plus；并约定从 U-001 开始逐个功能点推进，先跑通再铺开。', ORANGE),
    ('4', '问题与解法', '司空 FH2 SDK 的 jar 仅支持 Linux / macOS —— AI 给出方案对比后，安装 WSL 运行后端打通全链路；开发环节是主要耗时环节，其余环节 AI 直接通过。', GREEN),
]
for i, (no, t, body, clr) in enumerate(cards):
    x = 0.55 + (i % 2) * 6.25
    y = 1.52 + (i // 2) * 2.75
    card = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, x, y, 5.98, 2.5, fill=WHITE, line_color=LINEC)
    card.adjustments[0] = 0.05
    add_shape(s, MSO_SHAPE.RECTANGLE, x, y, 0.09, 2.5, fill=clr)
    badge(s, x + 0.32, y + 0.3, 0.46, no, fill=clr)
    add_text(s, x + 0.95, y + 0.3, 4.8, 0.5, [{'runs': [(t, 16.5, True, NAVY)]}],
             anchor=MSO_ANCHOR.MIDDLE)
    add_text(s, x + 0.34, y + 0.95, 5.35, 1.45, [{'runs': [(body, 11.5, False, DARK)], 'line': 1.22}])

# ============================================================
# P8 AI 产出全景（数据）
# ============================================================
s = prs.slides.add_slide(BLANK)
header(s, 'PART 02 · AI 使用过程', 'AI 产出全景：一组真实数据')
stats = [
    ('91', '个功能点全量推进', 'U-001 ~ U-091，覆盖 P0 / P1 / P2'),
    ('11', '个后端业务模块', 'iam / device / map / safety / wayline / mission / media / analytics / fh2 …'),
    ('40+', '篇文档产物', '需求、PRD、原型、概要设计、详设 12 分册、ER / DDL、测试文档'),
    ('650+', '行建库 DDL', '另含 10+ 个增量 SQL，随迭代平滑演进'),
    ('600+', '行测试用例', '测试计划 / 测试方案 / 测试用例 / 冒烟用例齐备'),
    ('12', '大功能模块落地', '看板 / 地图 / 航线 / 任务 / 媒体 / 设备 / 人员 / 安全区 / 三维重建 …'),
]
for i, (num, label, sub) in enumerate(stats):
    x = 0.55 + (i % 3) * 4.15
    y = 1.55 + (i // 3) * 2.68
    card = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, x, y, 3.94, 2.42, fill=LIGHT2, line_color=LINEC)
    card.adjustments[0] = 0.06
    add_text(s, x + 0.3, y + 0.22, 3.3, 0.85, [{'runs': [(num, 34, True, BLUE)]}])
    add_text(s, x + 0.3, y + 1.08, 3.3, 0.4, [{'runs': [(label, 14.5, True, NAVY)]}])
    add_text(s, x + 0.3, y + 1.5, 3.38, 0.8, [{'runs': [(sub, 10.5, False, GRAY)], 'line': 1.15}])

# ============================================================
# P9 平台定位与技术架构
# ============================================================
s = prs.slides.add_slide(BLANK)
header(s, 'PART 03 · 平台功能', '平台定位与技术架构')
layers = [
    ('用户层', 'Web 浏览器 · Vue3 单页应用（深色控制台风格）', 0.72, BLUE),
    ('前端应用层', 'Vue3 + Vite + TypeScript + Element Plus + UnoCSS\npaas.js 组件：航线编辑器 WaylineEditor / 设备管理 / 驾驶舱 / 飞行回放', 1.28, ACCENT),
    ('后端服务层', 'Spring Boot 3.3 + MyBatis Plus + JWT 鉴权 · 11 个业务模块\nFH2 Java SDK 适配：设备 / 航线镜像同步、任务下发、安全区推送', 1.28, BLUE),
    ('数据与底座', 'MySQL 8（20+ 业务表）\n私有化司空 2（PaaS API / MQTT / Terra 三维重建引擎）', 1.28, NAVY),
]
y = 1.52
for name, body, h, clr in layers:
    band = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, 0.55, y, 8.55, h, fill=WHITE, line_color=LINEC)
    band.adjustments[0] = 0.10
    tag = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, 0.75, y + 0.16, 1.5, h - 0.32, fill=clr)
    tag.adjustments[0] = 0.18
    shape_text(tag, [{'runs': [(name, 12.5, True, WHITE)]}])
    lines = body.split('\n')
    paras = [{'runs': [(lines[0], 12, True, DARK)], 'sa': 3}]
    for ln in lines[1:]:
        paras.append({'runs': [(ln, 11, False, GRAY)], 'line': 1.1})
    add_text(s, 2.5, y + 0.12, 6.45, h - 0.24, paras, anchor=MSO_ANCHOR.MIDDLE)
    y += h + 0.18
pos = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, 9.35, 1.52, 3.43, 4.9, fill=NAVY)
pos.adjustments[0] = 0.05
add_text(s, 9.65, 1.82, 2.85, 4.4, [
    {'runs': [('平台定位', 16, True, WHITE)], 'sa': 12},
    {'runs': [('本平台', 12, True, ACCENT), (' = 统一门户 + 业务编排 + 数据沉淀', 12, False, WHITE)], 'sa': 8, 'line': 1.2},
    {'runs': [('司空 2', 12, True, ACCENT), (' = 设备控制 · 航线引擎 · 直播回放 · 重建引擎', 12, False, WHITE)], 'sa': 14, 'line': 1.2},
    {'runs': [('单一作业空间心智：', 12, True, WHITE), ('不提供多项目创建与切换，所有能力均为平台级能力', 12, False, RGBColor.from_string('BFDCF2'))], 'line': 1.25},
])
add_text(s, 0.55, 6.6, 12.23, 0.4,
         [{'runs': [('整套架构按「规范前置 + 文档先行」方式由 AI 生成并走通，前后端与文档保持同一基线', 12, True, ORANGE)],
           'align': PP_ALIGN.CENTER}])

# ============================================================
# P10 功能地图（12 模块）
# ============================================================
s = prs.slides.add_slide(BLANK)
header(s, 'PART 03 · 平台功能', '功能地图：12 大模块')
mods = [
    ('数据看板', '核心指标 + 中央地图 + 在飞位置'),
    ('地图作业', '标注、作业区与协同'),
    ('空域安全', '限飞区 / 作业区推送司空'),
    ('航线库', '内嵌司空航线编辑器'),
    ('任务计划', '立即 / 定时 / 循环下发'),
    ('驾驶舱', '直播画面与实时遥测'),
    ('飞行回放', '历史航迹与视频重现'),
    ('媒体库', '照片 / 视频按批次管理'),
    ('三维重建', 'Terra 建模与模型库'),
    ('设备管理', '本地 + 司空镜像同步'),
    ('人员权限', '角色分级 + JWT 鉴权'),
    ('司空集成', '设备 / 航线 / 任务 / 安全区通道'),
]
for i, (name, sub) in enumerate(mods):
    x = 0.55 + (i % 4) * 3.115
    y = 1.55 + (i // 4) * 1.85
    card = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, x, y, 2.95, 1.6, fill=WHITE, line_color=LINEC)
    card.adjustments[0] = 0.08
    sq = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, x + 0.25, y + 0.26, 0.34, 0.34, fill=BLUE)
    sq.adjustments[0] = 0.25
    add_text(s, x + 0.72, y + 0.22, 2.1, 0.42, [{'runs': [(name, 14, True, NAVY)]}],
             anchor=MSO_ANCHOR.MIDDLE)
    add_text(s, x + 0.25, y + 0.78, 2.5, 0.7, [{'runs': [(sub, 10, False, GRAY)], 'line': 1.12}])

# ============================================================
# P11 核心业务流程
# ============================================================
s = prs.slides.add_slide(BLANK)
header(s, 'PART 03 · 平台功能', '核心业务流程：一条链路走完日常作业')
steps1 = ['登录平台', '人员 / 设备就绪', '标注作业区与限飞区', '航线库创建航线', '任务计划下发司空']
steps2 = ['机场自动执行', '直播 / 遥测观看', '媒体回传资源库', '可选：三维重建', '看板与成果沉淀']
for row_i, steps in enumerate([steps1, steps2]):
    y = 1.7 + row_i * 1.9
    n = len(steps)
    w = 2.62
    for i, name in enumerate(steps):
        kind = MSO_SHAPE.PENTAGON if i == 0 else MSO_SHAPE.CHEVRON
        sp = add_shape(s, kind, 0.55 + i * (w + 0.06), y, w, 0.95,
                       fill=NAVY if row_i == 0 else BLUE)
        if i > 0:
            sp.adjustments[0] = 0.55
        shape_text(sp, [{'runs': [(name, 12.5, True, WHITE)]}])
    if row_i == 0:
        add_shape(s, MSO_SHAPE.DOWN_ARROW, 6.55, 2.75, 0.5, 0.5, fill=ORANGE)
note = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, 0.55, 5.85, 12.23, 0.62, fill=LIGHT)
note.adjustments[0] = 0.28
shape_text(note, [{'runs': [
    ('验收口径：', 12.5, True, NAVY),
    ('管理员在单一作业空间完成「加人 → 绑设备 → 画区 → 建线 → 建计划 → 看成果」，关键链路在看板可追溯', 12.5, False, DARK)]}])
add_text(s, 0.55, 6.65, 12.23, 0.4,
         [{'runs': [('链路中的设备控制、航线编辑、直播回放均复用司空 2 能力，本平台专注门户、编排与数据沉淀', 11.5, False, GRAY)],
           'align': PP_ALIGN.CENTER}])

# ============================================================
# P12 注意事项（六宫格）
# ============================================================
s = prs.slides.add_slide(BLANK)
header(s, 'PART 04 · 注意事项', 'AI 使用过程中的六条注意事项')
cares = [
    ('1', '输入决定输出', '截图要完整清晰，目标与边界提前说清；模糊的需求只会得到模糊的实现。', BLUE),
    ('2', '规范前置', '技术栈、目录结构、命名与响应包约定先交给 AI，再开始编码，避免大范围返工。', BLUE),
    ('3', '小步快跑', '按功能点逐个走通（先 U-001）、每步可验证；一次性大生成容易失控且难排查。', ORANGE),
    ('4', '人在回路', '架构决策、验收标准、安全相关事项必须人拍板；AI 产出必须 Review 后再合入。', ORANGE),
    ('5', '环境问题人决策', 'SDK 平台兼容、网络与密钥等工程问题，AI 只能给方案，拍板与操作在人（如 WSL 方案）。', GREEN),
    ('6', '文档即基线', '以文档驱动开发，实现后回写校准（V1.0 → V2.0），防止文档与代码「两张皮」。', GREEN),
]
for i, (no, t, body, clr) in enumerate(cares):
    x = 0.55 + (i % 3) * 4.15
    y = 1.55 + (i // 3) * 2.68
    card = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, x, y, 3.94, 2.45, fill=WHITE, line_color=LINEC)
    card.adjustments[0] = 0.06
    badge(s, x + 0.28, y + 0.26, 0.44, no, fill=clr)
    add_text(s, x + 0.86, y + 0.26, 2.9, 0.44, [{'runs': [(t, 15, True, NAVY)]}],
             anchor=MSO_ANCHOR.MIDDLE)
    add_text(s, x + 0.3, y + 0.92, 3.4, 1.4, [{'runs': [(body, 11, False, DARK)], 'line': 1.22}])

# ============================================================
# P13 AI 研发对人员能力的要求
# ============================================================
s = prs.slides.add_slide(BLANK)
header(s, 'PART 04 · 注意事项', 'AI 研发对人员能力的要求')
band = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, 0.55, 1.42, 12.23, 0.9, fill=LIGHT)
band.adjustments[0] = 0.22
shape_text(band, [
    {'runs': [('角色转变：', 13, True, NAVY),
              ('以前约六成精力耗在「搬运代码」（表单、接口对接、状态管理）；现在人聚焦定目标、定规范、验收、决策，能力重心从编码实现转向以下五项',
               12.5, False, DARK)], 'line': 1.25},
])
abils = [
    ('1', '描述能力', '把需求、目标、边界与约束讲清楚；「输入决定输出」，会描述就等于会指挥 AI', BLUE),
    ('2', '产品思维', '判断做什么、不做什么（如「单一作业空间」裁剪）；需求优先级排序 P0/P1/P2；制定体验与验收口径', BLUE),
    ('3', '架构思维', '模块划分、接口契约、技术选型与部署方式决策；AI 能生成代码，但架构决策必须人来定', ORANGE),
    ('4', '验收与质量判断', 'Review AI 产出的能力，识别幻觉与隐藏缺陷，把好审核关卡', ORANGE),
    ('5', '持续学习与工具敏感度', 'AI 工具形态迭代快（补全 → IDE → Agent），保持学习心态，快速把新工具融入工作流', GREEN),
]
for i, (no, t, body, clr) in enumerate(abils):
    x = 0.55 + i * 2.4675
    card = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, x, 2.6, 2.36, 3.62, fill=WHITE, line_color=LINEC)
    card.adjustments[0] = 0.06
    add_shape(s, MSO_SHAPE.RECTANGLE, x, 2.6, 2.36, 0.09, fill=clr)
    badge(s, x + 0.24, 2.92, 0.42, no, fill=clr, size=12)
    add_text(s, x + 0.24, 3.52, 1.92, 0.85, [{'runs': [(t, 14, True, NAVY)], 'line': 1.12}])
    add_text(s, x + 0.24, 4.38, 1.92, 1.75, [{'runs': [(body, 10.5, False, DARK)], 'line': 1.28}])
add_text(s, 0.55, 6.55, 12.23, 0.4,
         [{'runs': [('AI 时代研发核心竞争力：会描述需求、会产品判断、会架构决策、会质量把关，并保持工具敏感度', 12.5, True, BLUE)],
           'align': PP_ALIGN.CENTER}])

# ============================================================
# P14 未来展望
# ============================================================
s = prs.slides.add_slide(BLANK)
header(s, 'PART 05 · 未来展望', '未来展望：三个方向')
future = [
    ('平台智能化', BLUE, [
        '智能搜索、变化检测、超清全景、多模态识别',
        '工作流编排：采集 → 重建 → 分析 → 报告',
        '与机场无人值守运维深度联动',
    ]),
    ('开发流程演进', ORANGE, [
        'Agent 常驻全流程：需求 → 设计 → 编码 → 测试 → 部署',
        'MCP 工具直达设备与航线，运维即对话',
        'AI 自动回归测试与缺陷修复（CI/CD 融合）',
    ]),
    ('组织与规范', GREEN, [
        '提示词与技术规范资产化，沉淀项目知识库',
        '人机分工再定义：人定目标与标准，AI 执行',
        '团队 AI 工程能力成为基础设施',
    ]),
]
for i, (t, clr, pts) in enumerate(future):
    x = 0.55 + i * 4.15
    card = add_shape(s, MSO_SHAPE.ROUNDED_RECTANGLE, x, 1.55, 3.94, 4.9, fill=WHITE, line_color=LINEC)
    card.adjustments[0] = 0.04
    add_shape(s, MSO_SHAPE.RECTANGLE, x, 1.55, 3.94, 0.14, fill=clr)
    add_text(s, x + 0.35, 1.95, 3.2, 0.5, [{'runs': [(t, 17, True, NAVY)]}])
    paras = []
    for p in pts:
        paras.append({'runs': [('· ', 12.5, True, clr), (p, 12, False, DARK)], 'sa': 14, 'line': 1.25})
    add_text(s, x + 0.35, 2.75, 3.28, 3.4, paras)
add_text(s, 0.55, 6.7, 12.23, 0.4,
         [{'runs': [('让 AI 从「开发工具」演进为「研发流程本身」', 13, True, BLUE)], 'align': PP_ALIGN.CENTER}])

# ============================================================
# P15 结尾
# ============================================================
s = prs.slides.add_slide(BLANK)
add_shape(s, MSO_SHAPE.RECTANGLE, 0, 0, 13.333, 7.5, fill=NAVY)
add_shape(s, MSO_SHAPE.RECTANGLE, 0.9, 1.05, 0.62, 0.07, fill=ACCENT)
add_text(s, 0.9, 1.3, 11.5, 0.7, [{'runs': [('总结', 30, True, WHITE)]}])
concl = [
    ('全流程已走通', '从一张需求截图到可运行平台，AI 覆盖需求、设计、编码、测试全链路，文档完整可追溯。'),
    ('人机各司其职', '人负责目标、规范与验收；AI 负责生成、执行与重复劳动，效率与质量同时提升。'),
    ('下一步', '沉淀规范与知识库，让 Agent 常驻研发流程，覆盖部署与运维环节。'),
]
for i, (t, body) in enumerate(concl):
    y = 2.35 + i * 1.18
    add_shape(s, MSO_SHAPE.RECTANGLE, 0.92, y + 0.08, 0.06, 0.78, fill=ACCENT)
    add_text(s, 1.2, y, 2.6, 0.5, [{'runs': [(t, 16, True, ACCENT)]}])
    add_text(s, 3.9, y, 8.6, 0.9, [{'runs': [(body, 13.5, False, WHITE)], 'line': 1.2}])
add_text(s, 0.9, 6.15, 11.5, 0.8,
         [{'runs': [('谢谢观看 · 欢迎交流', 26, True, WHITE)], 'align': PP_ALIGN.CENTER}])

out = r'd:\AiFlow\AI驱动开发实践汇报.pptx'
prs.save(out)
print('saved:', out, '| slides:', len(prs.slides.__iter__.__self__._sldIdLst))

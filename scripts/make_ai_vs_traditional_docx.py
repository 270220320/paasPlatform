# -*- coding: utf-8 -*-
"""生成《关于推行AI人机协同开发流程的建议》(对比版)：传统管控平台 vs AI驱动平台
字数要求 1500-2500（脚本末尾自动统计并打印全文字数）
"""
from docx import Document
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn

ALL_TEXT = []  # 用于字数统计


def set_font(run, name_cn="宋体", name_en="Times New Roman", size=12, bold=False):
    run.font.name = name_en
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name_cn)
    run.font.size = Pt(size)
    run.font.bold = bold


def add_title(doc, text):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(text)
    set_font(r, "黑体", "SimHei", 18, bold=True)
    p.paragraph_format.space_after = Pt(10)
    ALL_TEXT.append(text)


def add_subtitle(doc, text):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(text)
    set_font(r, "楷体", "KaiTi", 13)
    p.paragraph_format.space_after = Pt(16)
    ALL_TEXT.append(text)


def add_h1(doc, text):
    p = doc.add_paragraph()
    r = p.add_run(text)
    set_font(r, "黑体", "SimHei", 15, bold=True)
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(6)
    ALL_TEXT.append(text)


def add_para(doc, text, indent=True, bold_prefix=None):
    p = doc.add_paragraph()
    p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.MULTIPLE
    p.paragraph_format.line_spacing = 1.35
    if indent:
        p.paragraph_format.first_line_indent = Pt(24)
    if bold_prefix:
        r1 = p.add_run(bold_prefix)
        set_font(r1, "宋体", "Times New Roman", 12, bold=True)
        ALL_TEXT.append(bold_prefix)
    r = p.add_run(text)
    set_font(r, "宋体", "Times New Roman", 12)
    ALL_TEXT.append(text)


def add_table(doc, headers, rows, widths=None):
    t = doc.add_table(rows=1 + len(rows), cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    for j, h in enumerate(headers):
        cell = t.rows[0].cells[j]
        cell.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = cell.paragraphs[0].add_run(h)
        set_font(r, "黑体", "SimHei", 11, bold=True)
        ALL_TEXT.append(h)
    for i, row in enumerate(rows):
        for j, v in enumerate(row):
            cell = t.rows[i + 1].cells[j]
            cell.paragraphs[0].alignment = (
                WD_ALIGN_PARAGRAPH.CENTER if j > 0 else WD_ALIGN_PARAGRAPH.LEFT)
            r = cell.paragraphs[0].add_run(v)
            set_font(r, "宋体", "Times New Roman", 11)
            ALL_TEXT.append(v)
    if widths:
        for j, w in enumerate(widths):
            for row in t.rows:
                row.cells[j].width = Cm(w)
    doc.add_paragraph()
    return t


doc = Document()
for sec in doc.sections:
    sec.top_margin, sec.bottom_margin = Cm(2.54), Cm(2.54)
    sec.left_margin, sec.right_margin = Cm(3.0), Cm(3.0)

add_title(doc, "关于推行“AI人机协同”开发流程的建议")
add_subtitle(doc, "——基于无人机管控平台传统开发与AI驱动开发的实证对比")

# ============ 一、现状描述 ============
add_h1(doc, "一、现状描述")
add_para(doc, "部门无人机管控平台（universal 0.5）采用传统编码模式建设。git提交记录显示：项目自2025年2月"
              "启动，至2026年7月方基本成型，历时17个月、9名研发人员接力投入、累计代码提交1904次，"
              "最终交付约1200个代码文件、9.5万行代码。复盘整个过程，存在四类突出问题：")
add_para(doc, "1．人力投入大、建设周期长。", indent=False, bold_prefix="")
add_para(doc, "9.5万行代码折合人均月产出仅约600行，9人×17个月的人力投入使项目成本高企，"
              "需求变更响应迟缓，多个功能排期以季度计。", bold_prefix="")
add_para(doc, "2．样板代码挤占设计时间。", indent=False, bold_prefix="")
add_para(doc, "表单页面、接口对接、状态管理等重复性编码约占工作量六成，研发精力被“搬运代码”大量消耗，"
              "架构打磨与自测时间被压缩。", bold_prefix="")
add_para(doc, "3．排障依赖人工试错。", indent=False, bold_prefix="")
add_para(doc, "设备联调期间同类问题需多轮盲改验证，单个问题平均1～3天才能闭环，且高度依赖个别“熟手”。", bold_prefix="")
add_para(doc, "4．知识沉淀在个人头脑。", indent=False, bold_prefix="")
add_para(doc, "踩坑经验以口口相传为主，人员一旦流动即面临断档，同类问题反复重踩。", bold_prefix="")
add_para(doc, "作为对照，2026年7月底起我们以“AI编程助手为主、人工审核为辅”的新模式启动新一代无人机航测"
              "平台（UAVPlatform）：1名研发协同AI历时约4周，即完成后端231个Java文件（1.63万行）、前端60个"
              "文件（1.33万行）及44份设计与排障文档，覆盖架构设计、前后端编码、联调、排障全流程。git记录与"
              "代码统计表明，两种模式的研发效率差距达一个数量级，流程转型条件已经成熟。")

# ============ 二、建议内容 ============
add_h1(doc, "二、建议内容")
add_para(doc, "建议将“AI人机协同”升级为部门标准开发流程，分三步实施：")
add_para(doc, "第一步（第1～2周）工具落地。为研发人员统一配备具备语义检索、跨文件重构、Agent自主执行与"
              "验证能力的AI编程助手；每个在建项目建立AI工作区，导入代码仓库与接口文档；发布操作规程，"
              "明确三条红线——AI代码必须经编译、测试与人工评审后方可合入，AI产出的任务结论必须落库到本地"
              "工程文档，涉密代码严禁提交公有云模型。")
add_para(doc, "第二步（第3～6周）知识赋能。将SDK文档、错误码字典、87个历史诊断脚本与44份技术文档结构化"
              "导入AI知识库；固化项目级“AI记忆”，使编码规范、架构分层、UI规范自动对齐团队标准；将排障"
              "固化为“复现→定位→修复→验证”四阶段技能，由AI自动生成验证脚本，取代人工逐版试错。")
add_para(doc, "第三步（第7周起）流程再造与度量。设计环节由AI产出架构图、接口与库表草案供评审修订；"
              "样板代码100%交由AI生成，人员聚焦业务逻辑与边界条件；测试用例随代码变更自动生成并纳入合入"
              "门禁；文档随代码同步起草。建立需求交付周期、人均产出、缺陷密度、问题平均修复时长等指标看板，"
              "双周复盘迭代提示词与知识库，先在1～2个项目组试点一个月，形成量化对比案例后全面推广。")

# ============ 三、可行性分析 ============
add_h1(doc, "三、可行性分析")
add_para(doc, "技术上已充分实证：新一代平台的全流程均由AI协同完成，涵盖Spring Boot 3.3后端、Vue3前端、"
              "DJI SDK深度集成与三维重建任务链路等复杂场景；涉密场景可选用私有化开源模型完全离线运行，"
              "技术路线无障碍。")
add_para(doc, "投入成本很轻：按10人团队测算，AI助手订阅约1.8万元/年，培训与知识库整理约60人时由内部消化，"
              "首年合计直接投入约2～4万元，无需新增编制与硬件。主要风险及应对如下：")
add_table(doc,
          ["风险点", "应对思路"],
          [["代码泄密", "涉密项目强制使用私有化模型；外发内容先行脱敏；定期审计"],
           ["AI幻觉引入缺陷", "编译、测试、人工评审三重门禁；关键模块以人工为主"],
           ["过度依赖", "保留编码基本功训练；核心算法坚持人工主导设计"]],
          widths=[3.5, 11.5])
add_para(doc, "组织上不改变现有项目管理与交付流程，属工具与作业方式升级，仅需1名兼职负责人统筹推进，"
              "实施阻力小、整体风险可控。")

# ============ 四、预期效益 ============
add_h1(doc, "四、预期效益")
add_para(doc, "两个平台的实测关键指标对比如下：")
add_table(doc,
          ["对比维度", "传统模式（管控平台实测）", "AI协同模式（新平台实测）"],
          [["投入人力", "9名研发接力", "1名研发＋AI"],
           ["建设周期", "17个月（1904次提交）", "约4周"],
           ["人均月代码产出", "约600行", "约3万行（并同步44份文档）"],
           ["文档沉淀", "零散、滞后", "随代码同步生成"]],
          widths=[3.6, 5.7, 5.7])
add_para(doc, "经济效益：按10人团队、年人力成本约200万元测算，整体研发效率提升40%即相当于每年释放约"
              "80万元人力价值，扣除首年投入后投入产出比超过20:1；交付周期大幅缩短还可提升并行承接项目的"
              "能力，直接增加合同营收。")
add_para(doc, "效率与质量：样板代码工时趋近于零，测试覆盖左移至编码阶段，线上问题闭环由1～3天缩短至"
              "0.5天以内，交付物完整度与规范性同步提高。")
add_para(doc, "组织能力：历史踩坑经验沉淀为组织级知识库，新人上手周期由1～2个月缩短至1～2周，有效对冲"
              "人员流动风险；同时形成可对外展示的“AI驱动开发”方法论与实证案例，提升部门技术品牌影响力，"
              "为承接更高复杂度项目奠定基础。")

p = doc.add_paragraph()
p.paragraph_format.space_before = Pt(20)
r = p.add_run("建议人：×××")
set_font(r, "宋体", "Times New Roman", 12)
p = doc.add_paragraph()
r = p.add_run("2026年8月27日")
set_font(r, "宋体", "Times New Roman", 12)

out = r"d:\AiFlow\AI人机协同开发流程建议书（对比版）.docx"
doc.save(out)

# 字数统计（近似 Word 口径：中文字符与英文单词均计1）
import re
total = 0
for t in ALL_TEXT:
    cn = len(re.findall(r"[\u4e00-\u9fff\uff00-\uffef\u3000-\u303f]", t))
    en = len(re.findall(r"[A-Za-z0-9]+", t))
    total += cn + en
print("saved:", out)
print("word_count(approx):", total)

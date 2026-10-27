# -*- coding: utf-8 -*-
"""构建《微商系统课程设计报告（第三篇）》Word 文档（详细设计）。"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

BUILD = r"E:/JLU/oopwork/oop-work-WeChat-merchant-system/report3_build"
FIG = os.path.join(BUILD, "fig")
OUT = os.environ.get(
    "REPORT_OUT",
    r"E:/JLU/oopwork/oop-work-WeChat-merchant-system/微商系统课程设计-第三篇报告.docx")

BODY_FONT = "宋体"
HEAD_FONT = "黑体"
ASCII_FONT = "Times New Roman"
MONO_FONT = "Consolas"

FIGMAP = {
    "图 1": ("fig01_modules_c.png", 12.6),
    "图 2": ("fig02_seq_c.png", 16.5),
}

doc = Document()


def _set_run(run, font=BODY_FONT, size=12, bold=False, italic=False, color=None):
    run.font.name = ASCII_FONT
    run.font.size = Pt(size)
    run.bold = bold
    run.italic = italic
    if color:
        run.font.color.rgb = RGBColor(*color)
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.get_or_add_rFonts()
    rFonts.set(qn("w:eastAsia"), font)
    rFonts.set(qn("w:ascii"), ASCII_FONT if font != MONO_FONT else MONO_FONT)
    if font == MONO_FONT:
        rFonts.set(qn("w:ascii"), MONO_FONT)
        rFonts.set(qn("w:hAnsi"), MONO_FONT)
    return run


def _shade(cell, hexcolor):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hexcolor)
    tcPr.append(shd)


def setup_styles():
    st = doc.styles["Normal"]
    st.font.name = ASCII_FONT
    st.font.size = Pt(12)
    st.element.rPr.rFonts.set(qn("w:eastAsia"), BODY_FONT)
    st.paragraph_format.space_after = Pt(3)
    st.paragraph_format.line_spacing = 1.25

    for name, size in (("Heading 1", 15), ("Heading 2", 13), ("Heading 3", 12)):
        h = doc.styles[name]
        h.font.name = ASCII_FONT
        h.font.size = Pt(size)
        h.font.bold = True
        h.font.color.rgb = RGBColor(0, 0, 0)
        h.element.rPr.rFonts.set(qn("w:eastAsia"), HEAD_FONT)
        h.paragraph_format.space_before = Pt(10 if name != "Heading 1" else 6)
        h.paragraph_format.space_after = Pt(4)

    sec = doc.sections[0]
    sec.left_margin = Cm(2.0)
    sec.right_margin = Cm(2.0)
    sec.top_margin = Cm(1.8)
    sec.bottom_margin = Cm(1.8)


def h1(text):
    doc.add_heading(text, level=1)


def h2(text):
    doc.add_heading(text, level=2)


def h3(text):
    doc.add_heading(text, level=3)


def p(text, size=12, indent=True, align=None, bold=False, italic=False, font=BODY_FONT):
    par = doc.add_paragraph()
    if indent:
        par.paragraph_format.first_line_indent = Pt(size * 2)
    if align == "center":
        par.alignment = WD_ALIGN_PARAGRAPH.CENTER
        par.paragraph_format.first_line_indent = Pt(0)
    r = par.add_run()
    r.text = text
    _set_run(r, font=font, size=size, bold=bold, italic=italic)
    return par


def bullets(items, size=12):
    for it in items:
        par = doc.add_paragraph(style="List Bullet")
        par.paragraph_format.space_after = Pt(1)
        par.paragraph_format.line_spacing = 1.25
        r = par.add_run()
        r.text = it
        _set_run(r, size=size)


def numbered(items, size=12):
    for it in items:
        par = doc.add_paragraph(style="List Number")
        par.paragraph_format.space_after = Pt(1)
        par.paragraph_format.line_spacing = 1.25
        r = par.add_run()
        r.text = it
        _set_run(r, size=size)


def note(text):
    par = doc.add_paragraph()
    par.paragraph_format.left_indent = Cm(0.5)
    par.paragraph_format.space_before = Pt(2)
    par.paragraph_format.space_after = Pt(4)
    r = par.add_run()
    r.text = text
    _set_run(r, size=10, italic=True, color=(0x40, 0x40, 0x40))


def code(text):
    par = doc.add_paragraph()
    par.paragraph_format.left_indent = Cm(0.6)
    par.paragraph_format.space_before = Pt(2)
    par.paragraph_format.space_after = Pt(4)
    par.paragraph_format.line_spacing = 1.1
    r = par.add_run()
    r.text = text
    _set_run(r, font=MONO_FONT, size=9.5)
    pPr = par._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), "F4F5F7")
    pPr.append(shd)


def caption(text):
    par = doc.add_paragraph()
    par.alignment = WD_ALIGN_PARAGRAPH.CENTER
    par.paragraph_format.space_before = Pt(2)
    par.paragraph_format.space_after = Pt(6)
    r = par.add_run()
    r.text = text
    _set_run(r, size=10.5, bold=True)


def figure(key, cap):
    fname, width = FIGMAP[key]
    par = doc.add_paragraph()
    par.alignment = WD_ALIGN_PARAGRAPH.CENTER
    par.paragraph_format.space_before = Pt(4)
    par.paragraph_format.space_after = Pt(0)
    par.add_run().add_picture(os.path.join(FIG, fname), width=Cm(width))
    caption(cap)


def table(headers, rows, widths=None, size=9):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    for i, htext in enumerate(headers):
        cell = t.rows[0].cells[i]
        cell.text = ""
        r = cell.paragraphs[0].add_run()
        r.text = htext
        _set_run(r, size=size, bold=True)
        cell.paragraphs[0].paragraph_format.space_after = Pt(1)
        cell.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER
        _shade(cell, "E8EEF7")
    for row in rows:
        cells = t.add_row().cells
        for i, val in enumerate(row):
            cells[i].text = ""
            for j, line in enumerate(str(val).split("\n")):
                para = cells[i].paragraphs[0] if j == 0 else cells[i].add_paragraph()
                para.paragraph_format.space_after = Pt(0)
                para.paragraph_format.line_spacing = 1.12
                r = para.add_run()
                r.text = line
                _set_run(r, size=size)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    doc.add_paragraph().paragraph_format.space_after = Pt(0)
    return t


def toc():
    par = doc.add_paragraph()
    run = par.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = r'TOC \o "1-3" \h \z \u'
    sep = OxmlElement("w:fldChar")
    sep.set(qn("w:fldCharType"), "separate")
    txt = OxmlElement("w:t")
    txt.text = "【请在 Word / WPS 中按 F9 更新目录】"
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    for el in (begin, instr, sep, txt, end):
        run._r.append(el)


setup_styles()

# ================================================================ 封面
title = doc.add_paragraph()
title.alignment = WD_ALIGN_PARAGRAPH.CENTER
title.paragraph_format.space_after = Pt(4)
_set_run(title.add_run(), font=HEAD_FONT, size=22, bold=True).text = "微商系统课程设计报告"

sub = doc.add_paragraph()
sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
sub.paragraph_format.space_after = Pt(20)
_set_run(sub.add_run(), font=HEAD_FONT, size=13).text = \
    "第三篇　前后端详细设计 · 技术架构迭代说明 · 计划偏差 · 掌握与困难"

table(
    ["项目名称", "基于 C/S 架构的微商（社交电商）系统"],
    [
        ["系统类型", "PC 端 C/S 桌面应用，单店经营、多用户访问"],
        ["技术路线", "Java 服务端 + Java Swing 客户端，TCP/IP 自定义协议，SQLite 数据库"],
        ["开发方式", "独立完成"],
        ["文档版本", "v1.0"],
        ["完成日期", "2026 年 9 月 15 日"],
        ["姓名 / 学号", "王彦如 / 55250408"],
        ["班级 / 指导教师", "11 班 / 【待确认：指导教师】"],
    ],
    widths=[3.6, 12.6],
    size=10.5,
)

doc.add_page_break()

# ================================================================ 目录
h1("目录")
toc()

# ================================================================ 第一章
h1("一、前端和后端详细设计")

h2("1.1 模块划分及模块间关系")
p("客户端（前端）模块：")
table(
    ["模块", "职责"],
    [
        ["ui 视图层", "登录/注册、商品列表、商品详情、购物车、结算、交易结果、售后、设置、店主端界面"],
        ["controller 控制器层", "各界面的事件调度，不写业务规则"],
        ["service 客户端业务层", "购物车副本维护、金额本地预演算"],
        ["comm 通信层", "协议编解码、连接管理"],
        ["dto 传输对象", "请求与应答的数据载体"],
    ],
    widths=[4.0, 12.6],
)
p("服务端（后端）模块：")
table(
    ["模块", "职责"],
    [
        ["comm 通信层", "Socket 监听、线程池、协议编解码、功能码分发"],
        ["service 业务层", "账号、商品、购物车、订单、促销、售后、事件发布"],
        ["dao 持久层", "DAO 接口 + JDBC 实现"],
        ["model / dto", "领域对象与传输对象"],
        ["util 工具层", "配置加载、日志、连接池"],
    ],
    widths=[4.0, 12.6],
)
p("模块间关系如下：客户端自 ui 至 comm 单向依赖，服务端自 comm 至 dao 单向依赖，"
  "两端仅通过通信层的 TCP/IP 自定义协议交互；业务层与事件模块之间为发布—订阅关系。")
figure("图 1", "图 1　模块划分及模块间关系图")

h2("1.2 UI 流程设计")
p("客户端为单进程桌面程序，登录后按角色动态挂载菜单，顾客与店主看到不同界面。"
  "UI 流程由三部分构成：界面清单、界面间跳转关系、交互原则。客户端界面清单如下（店主端界面仅店主角色可见）：")
table(
    ["界面", "主要内容", "主要交互"],
    [
        ["登录 / 注册", "账号、密码输入框", "校验通过后按角色进入主界面"],
        ["商品列表", "商品表格、搜索框、分页控件", "搜索、翻页、点击进入详情"],
        ["商品详情", "完整描述、促销活动", "选择数量、加入购物车"],
        ["购物车", "条目列表、复选框、数量增减", "修改后实时刷新小计"],
        ["结算", "结算单、优惠明细逐行展示", "确认提交订单"],
        ["交易结果", "成功/失败提示、订单号、计算明细", "查看明细、发起售后"],
        ["售后申请", "订单选择、退货原因表单", "提交申请"],
        ["设置", "界面主题选择", "即时换肤"],
        ["店主端界面", "商品维护、促销配置、订单查看、售后审核", "新增、修改、上/下架、审核"],
    ],
    widths=[3.0, 8.2, 5.4],
)
p("界面跳转流程：")
numbered([
    "顾客主线：登录 → 商品列表（搜索/翻页）→ 商品详情 → 加入购物车 → 购物车（勾选）→ 结算 → 交易结果 → 售后申请。",
    "店主主线：登录（店主角色）→ 店主端界面 → 商品维护 / 促销配置 / 售后审核 / 订单查看。",
    "公共：任意主界面可进入「设置」切换主题，切换后即时生效。",
])
p("界面按登录返回的角色动态挂载菜单，顾客看不到店主界面；结算页展示的金额一律带「预估」字样，"
  "提交后以服务端返回的实际金额为准。")

h2("1.3 类的设计及说明")
p("类的设计遵循两条原则：一是「先定接口、再定实现」，上层通过接口调用下层，不直接依赖实现类；"
  "二是「依赖单向向下」，自 ui/comm 向 dao 逐层依赖，任何一层不得反向依赖上层。"
  "领域对象（model）与传输对象（dto）相互隔离，业务逻辑不直接操作传输对象。服务端类按层划分如下：")

p("（一）model 领域对象层", indent=False, bold=True)
table(["类", "职责", "关键字段"], [
    ["User", "用户实体", "id、username、passwordHash、salt、role"],
    ["Product", "商品实体", "id、name、price、stock、status"],
    ["CartItem", "购物车条目", "userId、productId、quantity"],
    ["Order", "订单实体", "orderNo、userId、payableAmount、status"],
    ["OrderItem", "订单明细", "productName、priceSnapshot、quantity、subtotal"],
    ["Promotion", "促销活动", "type、params、status"],
    ["Coupon", "优惠券模板", "type、threshold、amount、status"],
    ["AfterSale", "售后单", "orderId、reason、status"],
], widths=[2.6, 5.4, 8.6])

p("（二）service 业务层", indent=False, bold=True)
table(["类", "职责", "要点"], [
    ["UserService", "注册、登录", "密码哈希校验、角色返回"],
    ["ProductService", "商品查询与维护", "分页搜索、上下架"],
    ["CartService", "购物车增删改查", "重复加购合并数量"],
    ["OrderService", "结算与下单", "事务、条件更新扣库存"],
    ["PromotionService", "促销配置与计算链装配", "调用促销引擎"],
    ["AfterSaleService", "售后申请与审核", "状态流转、回补库存"],
], widths=[3.0, 5.6, 8.0])

p("（三）promotion 促销引擎（设计核心）", indent=False, bold=True)
table(["类", "职责", "要点"], [
    ["PriceCalculator（接口）", "价格计算抽象", "定义 calculate()"],
    ["DiscountStrategy / MarkdownStrategy / CouponStrategy", "各玩法实现", "分别实现打折、降价、用券"],
    ["PromotionDecorator（抽象）", "装饰器基类", "持有被包裹的 PriceCalculator"],
    ["FullReduceDecorator / CouponDecorator", "具体装饰器", "逐环变换价格"],
    ["PromotionFactory", "反射装配策略链", "按配置 createChain()"],
], widths=[4.6, 4.6, 7.4])
p("PriceCalculator 是统一接口，三个策略实现各自玩法；装饰器逐层包裹形成计算链；"
  "PromotionFactory 按配置反射创建并组装。业务层只调用一次 calculate，无需关心链上长度。")
p("三种机制分工明确：策略模式解决「玩法可替换」，每种促销是一个独立策略类，新增玩法只增类、不改旧码；"
  "装饰模式解决「玩法可叠加」，多个优惠按配置顺序逐层包裹，叠加顺序变化只影响链的装配；"
  "反射解决「玩法可扩展」，玩法标识与实现类名的映射写入配置，运行时动态实例化，"
  "做到加新玩法不改核心交易代码（开闭原则）。三者协同是本系统面向对象设计的核心。")

p("（四）dao 持久层、comm 通信层与基础设施", indent=False, bold=True)
table(["层", "主要类", "要点"], [
    ["dao 持久层", "UserDao、ProductDao、CartDao、OrderDao、PromotionDao、CouponDao、AfterSaleDao（接口）＋ jdbc 包下的 JdbcXxxDao 实现", "对外只暴露接口"],
    ["comm 通信层", "ProtocolCodec、RequestHandler、ConnectionManager、ClientConnection", "编解码、功能码分发、线程池、连接与心跳"],
    ["event 事件层", "EventBus、OrderCompletedEvent、PromotionStartedEvent", "发布—订阅、阻塞队列"],
    ["util 工具层", "Config、DBConnectionPool、Logger", "配置、连接池（单例）、日志"],
], widths=[2.6, 7.2, 6.8])

p("持久层对外只暴露 DAO 接口、实现类放在 jdbc 子包内，业务层依赖接口而非实现，"
  "因此将来把 JDBC 实现替换为 MyBatis 实现时业务层零改动——这正是「分层柔性」在代码层的落点。")
p("客户端主要类：")
table(["层", "主要类", "要点"], [
    ["ui 视图层", "LoginView、ProductListView、ProductDetailView、CartView、CheckoutView、ResultView、AfterSaleView、SettingView、OwnerView", "只展示与采集输入"],
    ["controller 控制器层", "LoginController、ProductController、CartController、CheckoutController 等", "事件调度"],
    ["service 客户端业务", "CartModel、PriceEstimator", "购物车副本、金额预估并标注「预估」"],
    ["comm / dto", "ClientConnection、ProtocolCodec、各请求/应答对象", "通信与数据载体"],
], widths=[2.8, 7.4, 6.4])

h2("1.4 数据库详细设计及说明")
p("本系统共 10 张表，字段类型统一用 INTEGER / VARCHAR / TEXT / DECIMAL 表达；主键自增语法在 SQLite 与 "
  "MySQL 中不同，由两套建表脚本分别处理。各表字段如下：")

p("t_user　用户表", indent=False, bold=True)
table(["字段", "类型", "说明"], [
    ["id", "INTEGER", "主键，自增"], ["username", "VARCHAR", "账号，唯一"],
    ["password_hash", "VARCHAR", "密码哈希"], ["salt", "VARCHAR", "随机盐值"],
    ["nickname", "VARCHAR", "昵称"], ["role", "VARCHAR", "顾客 / 店主"],
    ["created_at", "TEXT", "创建时间"],
], widths=[3.2, 3.4, 10.0])

p("t_product　商品表", indent=False, bold=True)
table(["字段", "类型", "说明"], [
    ["id", "INTEGER", "主键，自增"], ["name", "VARCHAR", "商品名称"],
    ["price", "DECIMAL", "单价"], ["stock", "INTEGER", "库存数量"],
    ["description", "TEXT", "描述"], ["status", "VARCHAR", "上架 / 下架"],
    ["created_at", "TEXT", "创建时间"],
], widths=[3.2, 3.4, 10.0])

p("t_cart_item　购物车条目表", indent=False, bold=True)
table(["字段", "类型", "说明"], [
    ["id", "INTEGER", "主键，自增"], ["user_id", "INTEGER", "外键 → t_user"],
    ["product_id", "INTEGER", "外键 → t_product"], ["quantity", "INTEGER", "数量"],
    ["created_at", "TEXT", "加入时间"],
], widths=[3.2, 3.4, 10.0])

p("t_order　订单表", indent=False, bold=True)
table(["字段", "类型", "说明"], [
    ["id", "INTEGER", "主键，自增"], ["order_no", "VARCHAR", "订单号，唯一"],
    ["user_id", "INTEGER", "外键 → t_user"], ["total_amount", "DECIMAL", "商品总额"],
    ["discount_amount", "DECIMAL", "优惠总额"], ["payable_amount", "DECIMAL", "应付金额"],
    ["status", "VARCHAR", "待付款/已付款/已完成/已取消/售后中/已退款"],
    ["created_at", "TEXT", "下单时间"], ["paid_at", "TEXT", "付款时间"],
], widths=[3.2, 3.4, 10.0])

p("t_order_item　订单明细表", indent=False, bold=True)
table(["字段", "类型", "说明"], [
    ["id", "INTEGER", "主键，自增"], ["order_id", "INTEGER", "外键 → t_order"],
    ["product_id", "INTEGER", "外键 → t_product"], ["product_name", "VARCHAR", "商品名快照"],
    ["price_snapshot", "DECIMAL", "成交单价快照"], ["quantity", "INTEGER", "数量"],
    ["subtotal", "DECIMAL", "小计"],
], widths=[3.2, 3.4, 10.0])

p("t_promotion　促销活动表", indent=False, bold=True)
table(["字段", "类型", "说明"], [
    ["id", "INTEGER", "主键，自增"], ["name", "VARCHAR", "活动名称"],
    ["type", "VARCHAR", "玩法标识（对应实现类名映射）"], ["params", "TEXT", "参数（JSON）"],
    ["status", "VARCHAR", "启用 / 停用"], ["created_at", "TEXT", "创建时间"],
], widths=[3.2, 3.4, 10.0])

p("t_promotion_scope　促销适用范围表", indent=False, bold=True)
table(["字段", "类型", "说明"], [
    ["id", "INTEGER", "主键，自增"], ["promotion_id", "INTEGER", "外键 → t_promotion"],
    ["product_id", "INTEGER", "外键 → t_product"],
], widths=[3.2, 3.4, 10.0])

p("t_coupon　优惠券模板表", indent=False, bold=True)
table(["字段", "类型", "说明"], [
    ["id", "INTEGER", "主键，自增"], ["name", "VARCHAR", "券名称"],
    ["type", "VARCHAR", "满减 / 抵扣"], ["threshold", "DECIMAL", "使用门槛"],
    ["amount", "DECIMAL", "面额"], ["status", "VARCHAR", "启用 / 停用"],
], widths=[3.2, 3.4, 10.0])

p("t_user_coupon　用户持有券表", indent=False, bold=True)
table(["字段", "类型", "说明"], [
    ["id", "INTEGER", "主键，自增"], ["user_id", "INTEGER", "外键 → t_user"],
    ["coupon_id", "INTEGER", "外键 → t_coupon"], ["status", "VARCHAR", "未使用/已使用/已过期"],
    ["received_at", "TEXT", "领取时间"], ["used_at", "TEXT", "使用时间"],
], widths=[3.2, 3.4, 10.0])

p("t_after_sale　售后单表", indent=False, bold=True)
table(["字段", "类型", "说明"], [
    ["id", "INTEGER", "主键，自增"], ["order_id", "INTEGER", "外键 → t_order"],
    ["user_id", "INTEGER", "外键 → t_user"], ["reason", "TEXT", "申请原因"],
    ["status", "VARCHAR", "待审核 / 已通过 / 已拒绝"],
    ["created_at", "TEXT", "申请时间"], ["handled_at", "TEXT", "处理时间"],
], widths=[3.2, 3.4, 10.0])

p("表间关系：用户一对多购物车条目、一对多订单；订单一对多订单明细；促销与商品多对多，"
  "经中间表 t_promotion_scope 拆解；用户与优惠券多对多，经 t_user_coupon 拆解；"
  "订单与售后单一对一。订单明细中的商品名与成交单价采用快照保存，避免商品后续调价导致历史订单金额漂移。")
p("约束与索引：username、order_no 设唯一约束；外键关系见上表。高频查询列（订单与购物车按 user_id、"
  "订单明细按 order_id）建立索引。事务边界：结算时扣减库存与写入订单、明细在同一事务内，"
  "任一步失败整体回滚，保证数据一致性。")

h2("1.5 通信协议详细设计")
p("报文由 16 字节定长报文头与变长数据体组成：")
table(
    ["字段", "长度", "说明"],
    [
        ["魔数", "2 字节", "固定值 0xAA55，用于快速识别非法数据"],
        ["版本号", "1 字节", "协议版本，当前 0x01"],
        ["类型", "1 字节", "1=请求，2=应答，9=服务端推送"],
        ["功能码", "4 字节", "标识具体操作"],
        ["序列号", "4 字节", "请求与应答的配对标识"],
        ["数据长度", "4 字节", "数据体字节数，解决粘包与半包问题"],
        ["数据体", "变长", "JSON 文本序列化"],
    ],
    widths=[2.4, 2.0, 12.2],
)
p("完整功能码表：")
table(
    ["方向", "功能码", "含义"],
    [
        ["请求 / 应答", "1001 0000 / 2001 0000", "请求商品列表 / 返回列表"],
        ["请求 / 应答", "1002 xxxx / 2002 xxxx", "加购 xxxx 号商品 / 返回结果"],
        ["请求 / 应答", "1003 0000 / 2003 0000", "查询购物车 / 返回内容"],
        ["请求 / 应答", "1004 0000 / 2004 0000", "提交结算 / 返回结果（含计算明细）"],
        ["请求 / 应答", "1005 0000 / 2005 0000", "发起售后申请 / 返回受理结果"],
        ["请求 / 应答", "1006 xxxx / 2006 xxxx", "领取 xxxx 号优惠券 / 返回结果"],
        ["请求 / 应答", "1007 0000 / 2007 0000", "用户登录 / 返回结果与角色"],
        ["请求 / 应答", "1008 0000 / 2008 0000", "查询界面主题配置 / 返回配置"],
        ["请求 / 应答", "1009 0000 / 2009 0000", "心跳检测 / 心跳应答"],
        ["请求 / 应答", "1010 0000 / 2010 0000", "商品维护（店主）/ 返回结果"],
        ["请求 / 应答", "1011 0000 / 2011 0000", "促销配置（店主）/ 返回结果"],
        ["请求 / 应答", "1012 0000 / 2012 0000", "售后审核（店主）/ 返回结果"],
        ["请求 / 应答", "1013 0000 / 2013 0000", "订单查询（店主）/ 返回列表"],
        ["推送", "9001 0000", "通知订单状态变更"],
        ["推送", "9002 0000", "通知促销活动开始"],
    ],
    widths=[2.6, 5.2, 8.8],
)
p("错误码定义：")
table(
    ["错误码段", "含义", "示例"],
    [
        ["1xxx", "请求不合法", "1001 功能码无法识别、1002 数据体格式错误"],
        ["2xxx", "业务规则拒绝", "2001 库存不足、2002 优惠券已过期、2003 订单状态不允许退货、"
         "2004 账号或密码错误、2005 无权限（顾客调用店主功能）"],
    ],
    widths=[2.6, 4.0, 10.0],
)
p("编解码要点：按长度字段切分，不假设一次 read 即一条完整报文；长度以 UTF-8 字节数计；"
  "应答通过序列号配对，支持长连接上的并发请求；业务异常以错误码加消息在应答中传递，不断开连接。"
  "下图为一次结算请求的完整交互：")
figure("图 2", "图 2　结算下单通信时序图")

# ================================================================ 第二章
h1("二、技术迭代和架构迭代的说明")

h2("2.1 技术迭代")
p("与概要设计相比，技术选型未发生任何变化：Java Swing 客户端、手写 JDBC + DAO 持久层、"
  "SQLite 起步可切 MySQL、JSON 序列化、线程池并发模型、数据库条件更新、策略 + 装饰 + 反射的促销引擎等均保持不变。"
  "详细设计阶段只是把选型落到具体类名、功能码与表字段上，属于从粗到细的细化，而非修改。")

h2("2.2 架构迭代")
p("六层结构、模块边界、通信模型、促销引擎的分工均未被推翻。详细设计阶段的新增内容有三：其一，"
  "把「促销引擎」从概念落成 PriceCalculator 接口加三个策略实现、装饰器与反射工厂的类结构；"
  "其二，把「10 张表」从实体清单落成字段明细与约束、索引、事务说明；其三，补齐了店主端功能码"
  "（1010–1013）与心跳（1009）。")
p("结论：本篇未推翻任何既有决策，与概要设计保持一致，全部改动均为从粗到细的自然细化。")

# ================================================================ 第三章
h1("三、计划及计划实现的偏差情况说明")
p("对照六周里程碑，截至本篇（第 3 周）的进度如下：")
table(
    ["周次", "计划目标", "当前状态"],
    [
        ["第 1 周", "需求定稿与环境预热", "已完成：需求定稿、开发环境就绪，Socket 收发与 JDBC 读写各跑通一个最小例程"],
        ["第 2 周", "通信层打通", "基本完成：协议报文定义与编解码已定稿，两端联调、登录与商品列表已打通"],
        ["第 3 周", "数据层打通", "进行中：建库建表、DAO 接口与 JDBC 实现正在落地，商品详情与购物车待接通"],
        ["第 4 周", "促销引擎", "未开始"],
        ["第 5 周", "难点攻坚", "未开始"],
        ["第 6 周", "收尾交付", "未开始"],
    ],
    widths=[2.0, 4.6, 10.0],
)
p("偏差情况：整体进度相对计划略有滞后，滞后集中在「编码实现」这条线上，而「设计文档」这条线反而超前——"
  "需求、概要设计、详细设计三篇报告已提前完成，为后续编码奠定了清晰基础。滞后原因有二：其一，"
  "本项目的 Socket、并发、设计模式、反射、JDBC 等技术对笔者多为首次接触，需要边学边做，学习成本高于预期；"
  "其二，前期在设计上投入精力较多，力求把分层、协议、促销引擎这些核心机制想清楚再动手，客观上压缩了编码时间。"
  "后续将先搭空骨架让类结构立起来，再逐层填充实现，争取在第 4 周前补回第 3、4 周的欠账。")

# ================================================================ 第四章
h1("四、完全掌握的部分和困难的部分")
p("目前已经完全掌握的部分：")
bullets([
    "Java 基础语法与面向对象（类、接口、继承、多态）——这是全部设计能够展开的基础，已能稳定运用；",
    "SQL 基础（DDL 建表、DML 增删改查）与 JDBC 读写——已跑通「对象存成表、表读回对象」的最小闭环，理解映射本质；",
    "自定义协议报文的设计与编解码思路——16 字节定长头加长度字段切分解决粘包半包，已能讲清并落到代码；",
    "Swing 界面基础——容器、布局管理器、常用控件与事件监听，能搭建可用的桌面界面。",
])
p("目前仍存在困难的部分：")
bullets([
    "多线程与并发控制——线程池、锁与竞态条件的正确性理解尚不扎实，库存互斥目前只能靠数据库条件更新兜底；",
    "反射与设计模式的落地——策略、装饰、反射三者的协同装配，仍处于「理解概念、未完全跑通代码」的状态；",
    "事务边界——结算时扣库存、写订单、写明细三者的一致性保证，事务的开启与回滚时机还需反复验证；",
    "从设计文档到代码的衔接——类图要落到可编译、可运行的类结构，中间仍有磨合成本。",
])
p("拟采取的解决思路：并发与互斥先用「超卖复现实验」把问题做成可复现的对比，再以数据库事务为正确性保证、"
  "JVM 锁仅作优化；反射与设计模式先跑通「类名写入配置 → Class.forName 实例化 → 调用 calculate」的最小例程，"
  "再逐步叠加装饰器形成计算链；事务统一用 JDBC 事务 API 在结算入口 try-catch，失败整体回滚；"
  "代码衔接则严格按已定义的包结构先搭空骨架，让类结构先立起来，再逐类填充实现。")

doc.save(OUT)
print("saved:", OUT)

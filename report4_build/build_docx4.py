# -*- coding: utf-8 -*-
"""构建《微商系统课程设计报告（第四篇）》Word 文档（代码实现进展）。"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

BUILD = r"E:/JLU/oopwork/oop-work-WeChat-merchant-system/report4_build"
OUT = os.environ.get(
    "REPORT_OUT",
    r"E:/JLU/oopwork/oop-work-WeChat-merchant-system/微商系统课程设计-第四篇报告.docx")

BODY_FONT = "宋体"
HEAD_FONT = "黑体"
ASCII_FONT = "Times New Roman"
MONO_FONT = "Consolas"

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
    "第四篇　代码实现进展 · 技术问题与解决 · 掌握与困难 · 进度偏差 · 下周计划"

table(
    ["项目名称", "基于 C/S 架构的微商（社交电商）系统"],
    [
        ["系统类型", "PC 端 C/S 桌面应用，单店经营、多用户访问"],
        ["技术路线", "Java 服务端 + Java Swing 客户端，TCP/IP 自定义协议，SQLite 数据库"],
        ["开发方式", "独立完成"],
        ["文档版本", "v1.0"],
        ["完成日期", "2026 年 9 月 22 日"],
        ["姓名 / 学号", "王彦如 / 55250408"],
        ["班级 / 指导教师", "11 班 / 【待确认：指导教师】"],
    ],
    widths=[3.6, 12.6],
    size=10.5,
)

doc.add_page_break()

# ================================================================ 一、代码实现
h1("一、代码实现")
p("前三个星期以需求与设计为主，代码仅跑通了 Socket 收发与 JDBC 读写两个最小例程。"
  "本篇（第 4 周）正式进入编码阶段，按照既定的六层包结构先把骨架立起来，再逐层填充实现。"
  "本周的目标是把「登录 → 商品浏览 → 加购物车 → 结算下单」这条主链路从客户端到数据库真正打通，"
  "并把促销引擎的骨架搭出来。下面按业务逻辑、通信、持久层、UI 四个方面分别说明。")

# ---------------- 1.1 业务逻辑
h2("1.1 主要业务逻辑实现")
p("业务逻辑集中在服务端 service 包，客户端 service 包只维护购物车副本与金额预估，不做权威计算。"
  "本周已落地的业务类与完成状态如下：")
table(
    ["业务类", "对应功能", "本周完成状态"],
    [
        ["UserService", "注册、登录、密码加盐哈希校验", "已完成，可注册、登录并按角色返回"],
        ["ProductService", "商品分页查询、按状态过滤上架商品", "已完成，列表与详情可正常返回"],
        ["CartService", "加购、改数量、删除、查询购物车", "已完成，重复加购合并数量"],
        ["OrderService", "结算、事务内扣库存、写订单与明细", "主流程已跑通，装饰叠加金额待联调"],
        ["PromotionService", "从配置装配促销计算链并调用", "策略与反射已跑通，装饰器叠加在调试"],
        ["AfterSaleService", "售后申请与状态流转", "未开始（列入第 5 周）"],
    ],
    widths=[3.2, 7.4, 6.0],
)
p("几个关键实现点说明如下。")

p("（一）密码加盐哈希", indent=False, bold=True)
p("注册时为每个用户随机生成一段盐值（UUID 去掉横线），与明文密码拼接后做 SHA-256 摘要，"
  "只把盐值与摘要后的哈希串写入 t_user，数据库中不出现任何明文密码。登录时按账号取出盐值，"
  "对本次输入做同样的哈希后再比对，而不是把存进去的哈希解密回来——单向摘要本就不可逆。"
  "这样即使数据库被人看到，也无法直接还原出用户密码。")

p("（二）购物车合并与勾选结算", indent=False, bold=True)
p("同一用户对同一商品重复加购时，不插入新行，而是在 t_cart_item 上把 quantity 加上本次数量。"
  "数量必须为正整数，非正数量直接在服务端二次校验拒绝。结算时只处理购物车里被勾选的条目，"
  "未勾选项保持在购物车内，允许用户下次继续结算。")

p("（三）结算事务与库存扣减", indent=False, bold=True)
p("结算入口在 OrderService.checkout() 中开启一个 JDBC 事务：先把 autoCommit 关掉，"
  "再在同一连接内依次执行「条件更新扣库存 → 写订单主表 → 写订单明细」。"
  "库存扣减采用第一篇就定下的条件更新写法：")
code("UPDATE t_product SET stock = stock - ?\n"
     " WHERE id = ? AND stock >= ?")
p("执行后立刻检查受影响行数：返回 1 表示扣减成功，返回 0 表示库存不足，"
  "立即回滚整个事务并向客户端返回「2001 库存不足」。多件商品在同一事务内逐条执行，"
  "任一条影响行数为 0 即整体回滚，不出现「扣了一半」的中间状态。订单明细里的商品名与成交单价"
  "取的是下单当时的快照，而不是事后再去商品表查——这样即使商品后来调价或下架，历史订单金额也不会漂移。")

p("（四）促销引擎的落地", indent=False, bold=True)
p("PriceCalculator 接口只定义一个方法 calculate(购物车明细, 上下文)。"
  "DiscountStrategy、MarkdownStrategy、CouponStrategy 三个实现类各自负责一种玩法。"
  "PromotionFactory 启动时读配置文件里的「玩法标识 → 全限定类名」映射，"
  "用 Class.forName(name).getDeclaredConstructor().newInstance() 反射实例化，"
  "再按配置里 promotion.chain 的顺序用装饰器逐层包裹。业务层拿到的永远是一个最外层 PriceCalculator，"
  "调一次 calculate 就拿到最终应付金额，不关心链上套了几层。")
p("本周先跑通了「单一策略直算」与「反射按配置实例化」两段，"
  "即配置里写折扣类名就能算出打折后的价；装饰器把两个玩法串起来的链路在联调，"
  "主要问题是装饰器内部要把上一层算完的中间值透传下去，边界还在收敛。")

# ---------------- 1.2 通信
h2("1.2 通信实现")
p("通信层两端共用一套 ProtocolCodec。服务端 comm 包下有 ServerSocket 监听、"
  "线程池为每条连接分配一个处理线程、RequestHandler 按功能码分发到对应 Service；"
  "客户端 comm 包下有 ClientConnection 维护一条长连接。")
p("（一）16 字节报文头的编解码", indent=False, bold=True)
p("严格按照第三篇详细设计落地：魔数 2 字节（0xAA55）、版本 1 字节、类型 1 字节、"
  "功能码 4 字节、序列号 4 字节、数据长度 4 字节，共 16 字节；数据体是 UTF-8 编码的 JSON 文本。"
  "编码时先把对象序列化成 JSON 字节数组，再按上述字段顺序拼到一个 ByteArrayOutputStream 里一次性写出；"
  "解码时先从输入流里凑满 16 字节，校验魔数合法后读出长度 len，再继续读满 len 字节的数据体，"
  "把它交给 JSON 解析器转成 Map 或对应 DTO。")

p("（二）粘包与半包", indent=False, bold=True)
p("TCP 是字节流，一次 read() 返回多少字节并不等于「一条完整报文」。最初直接 read 到一个固定缓冲区，"
  "结果要么一次读到两个包、要么只读到半个包。现在的做法是：在连接对象内部维护一个 ByteArrayOutputStream 缓存，"
  "每次把 read 到的字节追加进去，然后循环尝试「按 16 字节头 → 按长度字段取数据体」拆包，"
  "拆出来一条就交给上层处理一条，剩下不足一条的字节留在缓存里等下一次 read。"
  "长度字段按 UTF-8 字节数写，不按字符串长度写——这个坑见第二节。")

p("（三）功能码分发", indent=False, bold=True)
p("RequestHandler 内部维护一个 Map<Integer, Handler>，key 是功能码，value 是真正处理这个请求的 lambda。"
  "收到请求后按功能码查表，查不到就回一个「1001 功能码无法识别」。新增功能只要往 Map 里塞一个新条目，"
  "不需要改分发主流程。目前已经注册的功能码与第三篇详细设计完全一致：1001 商品列表、1002 加购、"
  "1003 查购物车、1004 结算、1007 登录等，店主端 1010–1013 与推送 9001/9002 暂时留空未注册。")

p("（四）多客户端并发", indent=False, bold=True)
p("服务端主线程只做 ServerSocket.accept()，每 accept 到一条新连接就丢进一个固定大小的线程池"
  "（Executors.newFixedThreadPool(8)）去处理。每个连接对应一个独立的处理线程，"
  "线程之间不共享可变状态，业务数据全部走数据库；共享资源只有 SQLite 连接，由连接池统一发放。")

# ---------------- 1.3 持久层
h2("1.3 持久层（数据库）实现")
p("持久层按第三篇的设计对外只暴露 DAO 接口，JDBC 实现放在 dao.jdbc 子包下，"
  "业务层 import 的是接口而不是实现类。")

p("（一）建库建表", indent=False, bold=True)
p("写了一份 schema.sql，包含第三篇设计的全部 10 张表：t_user、t_product、t_cart_item、"
  "t_order、t_order_item、t_promotion、t_promotion_scope、t_coupon、t_user_coupon、t_after_sale。"
  "SQLite 下主键用 INTEGER PRIMARY KEY AUTOINCREMENT，username 与 order_no 加 UNIQUE 约束，"
  "高频查询列（user_id、order_id、product_id）建了普通索引。建库时预置了一个店主账号，"
  "店主不开放前台注册，避免权限失控。")

p("（二）DAO 接口与 JDBC 实现", indent=False, bold=True)
p("目前已落地的 DAO 与对应实现如下：")
table(
    ["DAO 接口", "JDBC 实现", "主要方法"],
    [
        ["UserDao", "JdbcUserDao", "findByUsername、insert、update"],
        ["ProductDao", "JdbcProductDao", "listOnShelf、findById、updateStock、updateStatus"],
        ["CartDao", "JdbcCartDao", "listByUser、upsertItem、removeItem、clearChecked"],
        ["OrderDao", "JdbcOrderDao", "insertOrder、insertItem、findByUser、updateStatus"],
        ["PromotionDao", "JdbcPromotionDao", "listEnabled、listScopesByProduct"],
        ["CouponDao", "JdbcCouponDao", "listAvailable、findUserCoupon、markUsed"],
        ["AfterSaleDao", "JdbcAfterSaleDao", "insert、listPending、updateStatus"],
    ],
    widths=[3.4, 4.6, 8.6],
)
p("每个 JdbcXxxDao 内部都做三件事：拿到连接 → 用 PreparedStatement 绑参数（不拼字符串，防注入）"
  "→ 把 ResultSet 一行行映射回领域对象。例如 ProductDao.listOnShelf() 返回 List<Product>，"
  "调用方完全感知不到下面是 SQLite。")

p("（三）连接池与事务", indent=False, bold=True)
p("DBConnectionPool 是个单例，启动时按配置里的连接串（jdbc:sqlite:wechat.db）打开若干条连接放在队列里。"
  "普通查询业务层直接借一条用完就还；结算这种需要事务的业务，先从池里借一条并 setAutoCommit(false)，"
  "整段业务跑完再 commit 并归还；任何一步抛异常就 rollback 并归还。"
  "这样把「借连接、关连接、事务边界」这些模板代码收拢在一个地方，业务方法里只写业务本身。")

p("（四）方言收敛", indent=False, bold=True)
p("分页统一用 LIMIT ? OFFSET ?，时间统一存 ISO-8601 字符串，这样 SQLite 与 MySQL 的差异只在"
  "建表脚本的自增语法那一行，DAO 代码不分叉——将来现场把连接串切到 MySQL，业务层应该零改动。"
  "目前还没有真的切到 MySQL 跑过，这一点是设计上预留的，第 5 周会做一次切换演示。")

# ---------------- 1.4 UI
h2("1.4 UI 实现")
p("客户端按 MVC 组织：View 是一个个 javax.swing.JPanel 子类，Controller 监听按钮事件并调度，"
  "业务规则不写在界面代码里。本周已搭起来的界面与状态如下：")
table(
    ["界面", "主要控件", "本周状态"],
    [
        ["LoginView", "账号/密码输入框、登录/注册按钮", "已完成，登录成功后按角色切主面板"],
        ["ProductListView", "JTable 商品表格、搜索框、分页按钮", "已完成，数据从服务端拉取后刷新"],
        ["ProductDetailView", "名称/价格/库存/描述、数量输入、加购按钮", "已完成"],
        ["CartView", "可勾选的条目 JTable、数量加减、小计", "已完成，勾选与数量变更即时刷新"],
        ["CheckoutView", "结算单、优惠明细行、应付金额、提交按钮", "主流程完成，优惠明细接促销引擎中"],
        ["ResultView", "成功/失败提示、订单号、计算明细", "已完成"],
        ["AfterSaleView / OwnerView / SettingView", "售后表单、店主维护、主题切换", "未开始（列入第 5、6 周）"],
    ],
    widths=[3.6, 7.0, 6.0],
)
p("UI 上的几条硬性约定都在代码里落实了：界面里出现的预估金额一律带「预估」二字；"
  "所有发起网络请求的动作都在独立线程里做，绝不在 Swing 事件 dispatch 线程里阻塞等待应答"
  "（否则界面会整个冻住）；拿到应答后用 SwingUtilities.invokeLater 切回 EDT 再更新表格——这个坑见第二节。"
  "主题切换目前还是把颜色硬编码在每个 View 里，还没抽成可替换的样式表，留到第 6 周收尾时做。")

# ================================================================ 二、技术问题
h1("二、本周遇到的技术问题与解决方法")
p("本周是第一次真正把前几周学的零散 API 拼到一个工程里，踩的坑比写的功能还多。"
  "挑印象最深、也最有代表性的六个问题记录如下。")

p("问题 1：TCP 粘包 / 半包，第一次联调就报错", indent=False, bold=True)
p("现象：客户端发「登录」请求，服务端要么直接抛 JSON 解析异常，要么一次 read 到两条半报文。"
  "原因：把 TCP 当成了「一次 read 等于一条消息」，实际上它是字节流。"
  "解决：在连接对象内部加一个字节缓冲区，循环「先凑够 16 字节头 → 按长度字段凑够数据体 → 拆出一条交给上层」，"
  "剩下不足一条的留在缓冲区等下次 read。这一篇在第一篇设计时就预判到了，但真写代码时还是犯了一遍，"
  "说明设计稿和肌肉记忆之间差一次实操。")

p("问题 2：JSON 里中文按字符串长度算，对端直接错位", indent=False, bold=True)
p("现象：商品名里有中文时，服务端按 String.length() 写长度字段，客户端按这个长度 read 数据体，"
  "结果多读或少读几个字节，整条报文错位。原因：UTF-8 下一个汉字占 3 个字节，而不是 1 个字符。"
  "解决：长度字段一律按 json.getBytes(StandardCharsets.UTF_8).length 来写，两端都用同一个 Charset，"
  "不再出现字符串长度和字节长度的混淆。")

p("问题 3：Swing 在网络回调线程里更新表格，偶发界面错乱", indent=False, bold=True)
p("现象：商品列表刚拉回来时表格偶尔空白，或者隔几秒才刷出来，控制台偶尔冒"
  "ArrayIndexOutOfBoundsException。原因：Swing 有自己的事件 dispatch 线程（EDT），"
  "所有对控件的读写必须在 EDT 上做，而我在网络处理线程里直接 tableModel.addRow()。"
  "解决：网络线程里拿到数据后，用 SwingUtilities.invokeLater(() -> 刷新表格) 把更新动作切回 EDT。"
  "这是 Swing 多线程的铁律，不写就一定踩。")

p("问题 4：结算时扣了库存、写订单失败，库存却没回来", indent=False, bold=True)
p("现象：测试时人为让写订单那一步抛异常，发现商品库存已经少了，但 t_order 里没有这单。"
  "原因：JDBC 默认 autoCommit=true，每条 SQL 执行完就立刻提交，扣库存那条已经落库了。"
  "解决：在 OrderService.checkout() 入口显式 connection.setAutoCommit(false)，"
  "整段业务跑完再 connection.commit()，任何一步 catch 到异常就 connection.rollback()，"
  "finally 里把连接还回池。这是第一篇就定下的事务边界，但只有亲手踩一次「库存没回来」才真正理解为什么要它。")

p("问题 5：反射 newInstance 抛 ClassNotFoundException / NoSuchMethodException", indent=False, bold=True)
p("现象：配置文件里写了 DiscountStrategy，PromotionFactory 启动时直接炸。原因有两个："
  "一是配置里写的是类名简写而不是全限定名（漏了包名）；二是策略类一开始没写无参构造函数，"
  "编译器只生成了带参构造，反射找不到默认构造。解决：配置里一律写全限定类名；"
  "每个策略类显式写一个 public 无参构造，并在启动阶段就把所有策略类反射加载一遍做自检，"
  "有问题早暴露，不要等到结算时才炸。")

p("问题 6：装饰器嵌套顺序不对，同一单算出两个价", indent=False, bold=True)
p("现象：同一车商品，先打折再用券和先用券再打折算出来的应付金额不一样。"
  "这不是 bug，而是业务规则本身——所以第一篇就把「先打折 → 再满减 → 最后用券」的顺序写进了配置，"
  "由 PromotionFactory 按这个顺序包裹装饰器，而不是在策略类里硬编码。"
  "解决：把顺序抽到 promotion.chain 这一行配置里，业务代码不出现顺序常量；"
  "本周正在联调的就是这一段，让链上每一环把上一环的中间价格接过去再做自己的变换。")

# ================================================================ 三、掌握与困难
h1("三、目前完全掌握的部分和困难的部分")
p("经过本周真正写代码，对自己的掌握程度有了比第三篇更实在的判断。")

p("目前已经完全掌握的部分：", indent=False, bold=True)
bullets([
    "Java 面向对象与分层写代码——接口先行、实现下沉、依赖单向向下，已经能按包结构一口气写完一个模块不回头；",
    "JDBC 与 SQLite——连接、PreparedStatement、ResultSet 到领域对象的映射已经写顺，事务的开启/提交/回滚时机清楚；",
    "自定义协议的编解码——16 字节头 + 长度字段 + UTF-8 字节长度，粘包半包用缓冲区循环拆包，已经在两端联调跑通；",
    "Swing 的基本 MVC 结构——View 只画控件、Controller 监听事件、网络请求放后台线程、回数据用 invokeLater 切回 EDT；",
    "密码加盐哈希——随机盐 + SHA-256 单向摘要，注册登录闭环已通。",
])

p("目前仍然困难、心里没底的部分：", indent=False, bold=True)
bullets([
    "多线程与并发的正确性——虽然服务端已经用线程池跑起来了，但「两个线程同时扣同一件商品会不会超卖」"
    "这件事还只在设计上想清楚了，真正的超卖复现实验还没写出来；JVM 分段锁和数据库条件更新的关系也只停留在文档层面；",
    "装饰器 + 反射的链式装配——单一策略和反射实例化已经通了，但多个装饰器层层包裹、"
    "每层既要拿到上层结果又要保留自己的上下文（比如满减门槛、券面额），参数怎么传还在调；",
    "异步事件 / 观察者模式——第一篇设计里写了 EventBus + BlockingQueue 做交易完成后的异步通知，"
    "本周一行代码都没动，是第 5 周最大的不确定项；",
    "从「能跑」到「答辩能讲」——代码里有几处是照着例程抄过来的，被追问「为什么这么写」时还不能立刻答上来，"
    "需要周末把关键路径再过一遍。",
])
p("应对思路：并发问题不再只读博客，按第一篇计划写一个「库存=1、50 线程同时结算」的对比实验，"
  "把超卖和不超卖两组输出都截下来；装饰器链先把两层（打折→满减）跑通再叠第三层（券），不贪多；"
  "异步事件先写一个最小的 EventBus（List<Listener> + 一个 BlockingQueue + 一个消费线程），"
  "不引入任何中间件，把原理跑通再说。")

# ================================================================ 四、进度偏差
h1("四、计划进度偏差情况")
p("课程设计六周已到第四周末，时间过半。对照第一篇定下的里程碑，当前实际进度如下：")
table(
    ["周次", "计划目标", "当前实际状态"],
    [
        ["第 1 周", "需求定稿与环境预热", "已完成"],
        ["第 2 周", "通信层打通", "已完成：编解码、线程池、登录与商品列表联调通过"],
        ["第 3 周", "数据层打通", "已完成：10 张表建表脚本、7 个 DAO、连接池落地"],
        ["第 4 周", "促销引擎", "基本完成：策略接口 + 三个实现 + 反射装配跑通；装饰器叠加联调中；结算主链路已通"],
        ["第 5 周", "难点攻坚", "未开始：异步事件、售后状态流转、店主端"],
        ["第 6 周", "收尾交付", "未开始：主题切换、超卖实验、测试、演示材料"],
    ],
    widths=[1.8, 4.6, 10.2],
)
p("偏差判断：相比第三篇「编码线滞后约一周」的判断，本周通过集中编码把欠账追回来大半，"
  "整体进度从「滞后」修正为「基本对齐、局部略紧」。具体偏差有三：")
numbered([
    "通信层与数据层比计划慢了半周——第一篇原计划第 2 周通通信、第 3 周通数据，实际是第 3 周末才把 DAO 写完，"
    "本周（第 4 周）才真正把登录+商品+购物车+结算这条主链路端到端跑通；",
    "促销引擎比原计划稍紧——第 4 周里程碑是「策略、装饰、反射三者协同」，目前策略与反射已通，"
    "装饰器叠加还差最后联调，预计本周内（第 5 周初）补完，不影响大节奏；",
    "后半段的任务还在原地——异步事件、售后、店主端、主题切换、超卖实验这五项全部压在第 5、6 两周，"
    "时间偏紧。其中「异步事件 + 消息队列」是第一篇列的加分难点，如果第 5 周中仍跑不通，"
    "会按第一篇的降级预案把它降级为「同步处理 + 日志」，不留下一个讲不清的半成品，把剩余精力压在售后与店主端上。",
])
p("总体结论：设计时间过半，编码进度从「落后一周」追到「落后约三到五天」，属于可控偏差；"
  "设计文档线（已完成前三篇）继续超前，给后续编码留了清晰的施工图。后续两周按「先主流程、后加分项」排序，"
  "保证答辩时一条完整业务流能现场跑通。")

# ================================================================ 五、下周计划
h1("五、下周（第 5 周）拟解决的问题与方法")
p("下周是难度最大的一周，目标是把剩下的硬骨头啃下来，并把第 4 周没收尾的装饰器链补完。"
  "按优先级列如下：")
table(
    ["优先级", "拟解决问题", "解决方法"],
    [
        ["P0", "促销装饰器叠加链收尾",
         "先跑通两层（打折→满减），再叠第三层（券）；每加一层写一组金额手算用例做对照，"
         "保证「先打折后用券」的顺序与配置一致；"],
        ["P0", "结算事务与超卖复现实验",
         "写一个独立的测试入口：把某商品库存设为 1，起 50 个线程同时提交结算，"
         "先跑无保护版本复现超卖，再跑条件更新版本展示只有 1 单成功，两组输出截图存档作为答辩素材；"],
        ["P0", "售后状态流转",
         "AfterSaleService 实现：顾客申请 → 状态置「待审核」→ 店主审核通过则回补库存并把订单改为「已退款」，"
         "拒绝则订单回到「已完成」；状态转换用状态机式的 if-else 收敛在一个方法里；"],
        ["P1", "店主端核心功能",
         "店主登录后能新增/编辑/上下架商品、查看订单列表、审核售后单；功能码 1010–1013 补齐注册；"],
        ["P1", "异步事件最小实现",
         "手写 EventBus：维护一个 List<Listener> 和一个 ArrayBlockingQueue，"
         "结算成功后往队列里丢 OrderCompletedEvent，独立线程消费，分别写订单日志、更新销售统计；"
         "不引入任何外部 MQ；"],
        ["P2", "心跳与断线重连",
         "客户端每隔 30 秒发一次 1009 心跳，服务端回 2009；连续两次未收到应答则判定连接断开并重连；"
         "若 P0/P1 超时则按降级预案砍掉，在大报告中注明。"],
    ],
    widths=[1.4, 4.6, 10.6],
)
p("下周的节奏安排：周内前半段集中攻 P0（装饰器收尾 + 超卖实验 + 售后），后半段做 P1（店主端 + 异步事件）；"
  "P2 作为机动，能做则做。每周日晚上做一次自验：把「登录→浏览→加购→促销→结算→售后」整条业务流在本机跑一遍，"
  "有任何一处走不通就当周修，不带病进入下一周。")

p("以上为第四篇报告全部内容。", indent=True)

doc.save(OUT)
print("saved:", OUT)

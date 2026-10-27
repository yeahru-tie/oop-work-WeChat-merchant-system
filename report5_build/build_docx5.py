# -*- coding: utf-8 -*-
"""构建《微商系统课程设计报告（第五篇）》Word 文档。"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

OUT = os.environ.get(
    "REPORT_OUT",
    r"E:/JLU/oopwork/oop-work-WeChat-merchant-system/微商系统课程设计-第五篇报告.docx")

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
    sec.left_margin = Cm(2.0); sec.right_margin = Cm(2.0)
    sec.top_margin = Cm(1.8); sec.bottom_margin = Cm(1.8)


def h1(t): doc.add_heading(t, level=1)
def h2(t): doc.add_heading(t, level=2)


def p(text, size=12, indent=True, bold=False, italic=False):
    par = doc.add_paragraph()
    if indent:
        par.paragraph_format.first_line_indent = Pt(size * 2)
    r = par.add_run(); r.text = text
    _set_run(r, size=size, bold=bold, italic=italic)
    return par


def bullets(items, size=12):
    for it in items:
        par = doc.add_paragraph(style="List Bullet")
        par.paragraph_format.space_after = Pt(1)
        par.paragraph_format.line_spacing = 1.25
        r = par.add_run(); r.text = it
        _set_run(r, size=size)


def numbered(items, size=12):
    for it in items:
        par = doc.add_paragraph(style="List Number")
        par.paragraph_format.space_after = Pt(1)
        par.paragraph_format.line_spacing = 1.25
        r = par.add_run(); r.text = it
        _set_run(r, size=size)


def code(text):
    par = doc.add_paragraph()
    par.paragraph_format.left_indent = Cm(0.6)
    par.paragraph_format.space_before = Pt(2)
    par.paragraph_format.space_after = Pt(4)
    par.paragraph_format.line_spacing = 1.1
    r = par.add_run(); r.text = text
    _set_run(r, font=MONO_FONT, size=9.5)
    pPr = par._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear"); shd.set(qn("w:fill"), "F4F5F7")
    pPr.append(shd)


def table(headers, rows, widths=None, size=9):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    for i, htext in enumerate(headers):
        cell = t.rows[0].cells[i]; cell.text = ""
        r = cell.paragraphs[0].add_run(); r.text = htext
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
                r = para.add_run(); r.text = line
                _set_run(r, size=size)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    doc.add_paragraph().paragraph_format.space_after = Pt(0)
    return t


setup_styles()

# ============== 封面 ==============
title = doc.add_paragraph()
title.alignment = WD_ALIGN_PARAGRAPH.CENTER
title.paragraph_format.space_after = Pt(4)
_set_run(title.add_run(), font=HEAD_FONT, size=22, bold=True).text = "微商系统课程设计报告"

sub = doc.add_paragraph()
sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
sub.paragraph_format.space_after = Pt(20)
_set_run(sub.add_run(), font=HEAD_FONT, size=13).text = \
    "第五篇　代码实现讨论 · 本周技术问题 · 优化想法 · 收尾周计划"

table(
    ["项目名称", "基于 C/S 架构的微商（社交电商）系统"],
    [
        ["系统类型", "PC 端 C/S 桌面应用，单店经营、多用户访问"],
        ["技术路线", "Java 服务端 + Java Swing 客户端，TCP/IP 自定义协议，SQLite 数据库"],
        ["开发方式", "独立完成"],
        ["文档版本", "v1.0"],
        ["完成日期", "2026 年 9 月 29 日"],
        ["姓名 / 学号", "王彦如 / 55250408"],
        ["班级 / 指导教师", "11 班 / 【待确认：指导教师】"],
    ],
    widths=[3.6, 12.6], size=10.5,
)
doc.add_page_break()

# ============== 一、代码实现讨论 ==============
h1("一、代码实现讨论")

h2("1.1 对原设计的迭代：继承了什么，推翻了什么")
p("第四周把主链路跑通后，本周（第 5 周）集中把售后、店主端、促销装饰器叠加和异步事件这几块补完。"
  "真正写代码回过头看设计稿，哪些决策站住了、哪些被迫改了，记录如下。")

p("（一）坚持继承下来的部分", indent=False, bold=True)
bullets([
    "六层结构与单向依赖——ui→controller→service→comm→dao 这条依赖方向从头到尾没有反向过，"
    "DAO 里没有出现过一句 Swing 类的 import，UI 里没有出现过一句 SQL；",
    "16 字节定长报文头 + 长度字段 + UTF-8 字节数——这套协议在第四周踩过坑之后没再动过，"
    "本周新增的售后（1005）、店主端（1010–1013）功能码直接往 Map 里注册，编解码零改动；",
    "DAO 接口 + JDBC 实现分离——业务层只 import UserDao、OrderDao 这些接口，"
    "JdbcXxxDao 替换实现时业务层一行没改，「持久层柔性」从设计变成了可验证的事实；",
    "促销引擎三模式分工——策略管选玩法、装饰管叠加、反射管装配，三者的边界在本周把三层装饰器"
    "（折扣→满减→券）真正套起来之后依然清晰，没有出现职责互相渗透；",
    "库存条件更新 + 事务回滚——结算扣库存的 SQL 和第四周一字未改，本周售后审核通过回补库存"
    "用的是对称的 UPDATE ... SET stock = stock + ?，放在同一事务里。",
])

p("（二）被迫推翻或降级的部分", indent=False, bold=True)
p("设计稿毕竟是设计稿，真写代码时至少有四处做了调整：")
numbered([
    "异步事件从「观察者 + BlockingQueue 完整异步」降级为「同步发布 + 内存事件日志」。"
    "原计划是结算成功后丢事件到队列、独立线程消费写订单日志和统计。实际写的时候发现："
    "单机 SQLite 下同步写日志也就几毫秒，单独开一个消费线程反而引入了「事件丢失怎么办、消费失败怎么办」"
    "一堆新问题。最后保留了 EventBus 的发布—订阅接口（业务层只调 publish，不知道谁在听），"
    "但监听器是同步直接调用的，事件日志写在内存里、关机即失。这是有意识的降级，不是漏做。",
    "客户端购物车从「维护本地副本 + 后台同步」简化为「不存副本，进购物车界面就拉一次」。"
    "第四周按设计写了 CartModel 在客户端缓存一份购物车，结果两个窗口同时改数量时本地副本和服务端对不上，"
    "排查花了半天。第五周直接删掉 CartModel，所有购物车数据都以服务端为准，客户端只在界面打开和"
    "数量变更后各拉一次。代码量少了一半，bug 也没了。",
    "UI 主题从「3 套完整主题」降级为「1 套默认主题 + 一个可替换的颜色常量类」。"
    "原计划做浅色/深色/护眼三套皮肤，本周时间不够，抽了一个 Theme 类集中颜色和字体，"
    "切换主题就是换 Theme 的静态字段值。第三周的「3 套」在大报告里如实说明为「预留扩展点，只交付 1 套 + 可切换机制」。",
    "心跳与断线重连直接砍掉。第四周列的 P2，本周评估下来：单机演示场景下客户端和服务端都在同一台机器，"
    "根本不会断连；硬写心跳反而要引入定时器和重连状态机，答辩时多一个被问的点。按第一篇的降级预案主动放弃，"
    "在大报告中注明。",
])

h2("1.2 MVC 模式的体验")
p("客户端是严格按 MVC 写的：View 是 JPanel 子类，只做两件事——把控件摆出来、把控件上的事件转给 Controller；"
  "Controller 持有 View 和 Service 的引用，按钮按下时调 Service 发请求、拿到回调后再让 View 刷新；"
  "Model（CartModel、PriceEstimator）只放数据，不含界面逻辑。")
p("说几句真实体验：")
bullets([
    "最开始写 ProductListView 的时候，图省事直接在「加购」按钮的监听器里写了发 Socket 请求的代码，"
    "结果那个类 300 行，界面布局和网络请求搅在一起，改个按钮位置都怕碰坏网络逻辑。"
    "痛过一次之后才老老实实把请求挪到 ProductController，View 里只留 view.showCartResult(result)。",
    "分层真正的好处在改需求时显现：本周加「按价格升序排序」，只改了 ProductListView 加一个排序按钮、"
    "ProductController 加一行调用，Service 和 DAO 一行没动；如果当初逻辑写在 View 里，这种小需求就得"
    "在界面代码里翻半天。",
    "坚持分层不是没有代价：类的数量一下子多了，一个简单的登录要拆 LoginView、LoginController、"
    "UserService（客户端侧）三个文件。大一第一次写项目时觉得是「脱裤子放屁」，"
    "写到第六周回头看，这种啰嗦换来的是每个类都能单独读懂。",
])
p("结论：MVC 在这个项目里是真用了，不是 PPT 上贴个图。跨层红线（View 不碰 SQL、DAO 不碰 Swing）"
  "在 code review 时自己一条一条对过，没有破过。")

h2("1.3 设计模式的使用")
p("PPT 点名要的「反射 + 策略 + 装饰」三件套都落地了，另外还顺带用了几个：")
table(
    ["模式", "在本项目中的落点", "解决了什么"],
    [
        ["策略 Strategy",
         "PriceCalculator 接口 + DiscountStrategy/MarkdownStrategy/CouponStrategy",
         "三种促销玩法各自封装，新增玩法只加类不改旧码"],
        ["装饰 Decorator",
         "PromotionDecorator 抽象类，FullReduceDecorator、CouponDecorator 逐层包装",
         "折扣→满减→券三层叠加，顺序写在配置里，链长可任意伸缩"],
        ["反射 Reflection",
         "PromotionFactory 按配置文件里的全限定类名 Class.forName().newInstance()",
         "促销玩法配置化，加新玩法不动核心交易代码"],
        ["工厂 Factory",
         "PromotionFactory 内部封装「怎么把一堆策略拼成一条计算链」",
         "业务层不需要知道链怎么组装，拿一个 PriceCalculator 用即可"],
        ["单例 Singleton",
         "DBConnectionPool、Config、Logger 三个全局唯一对象",
         "连接池和配置全局共享，避免重复打开"],
        ["观察者 Observer",
         "EventBus + OrderCompletedListener，结算成功后同步通知监听器",
         "下单主流程和「写日志、统计销售额」解耦，后者将来可换成异步"],
        ["状态 State（退化版）",
         "Order.status 字段 + AfterSaleService 里的状态转换表",
         "待付款→已付款→已完成→售后中→已退款，状态转换收敛在一处"],
    ],
    widths=[2.6, 7.0, 6.6],
)
p("必须诚实说明：观察者模式这一版是「退化版」——没有用 BlockingQueue 做真正的异步，"
  "监听器是同步直接调用的（原因见 1.1）。但 EventBus 的发布—订阅接口是真的，"
  "OrderService 只调 eventBus.publish(new OrderCompletedEvent(...))，不知道有几个监听器、监听器做什么。"
  "将来要把它升级成异步，只需要把 EventBus 内部改成「丢队列 + 消费线程」，发布端代码零改动——"
  "这正是当初抽这一层的意义。")

h2("1.4 多线程的使用")
p("这个项目多线程用在四个地方，每一处都能说清楚「为什么必须开线程、开的是哪条线程」：")
numbered([
    "服务端连接模型——ServerSocket.accept() 在主线程阻塞，每 accept 到一条新连接就丢进一个固定大小的"
    "线程池（newFixedThreadPool(8)），由池里的工作线程负责这条连接上的全部读写。"
    "好处：一个客户端卡住不会阻塞其他客户端；代价：连接之间共享数据要靠数据库，不能靠内存。",
    "客户端网络请求——Swing 的 EDT（事件 dispatch 线程）绝不能做阻塞操作，所以每个按钮事件里"
    "都 new 一个 Thread 去发 Socket 请求，拿到应答后用 SwingUtilities.invokeLater 切回 EDT 更新表格。"
    "本周没有用 SwingWorker，手写线程更直观，但代价是要自己处理异常。",
    "库存互斥——这是设计上最关键的一处。最初想在 JVM 里按商品 id 分段加锁，"
    "后来按第一篇的判断把正确性交给数据库条件更新（UPDATE ... WHERE stock >= ?），"
    "JVM 层锁没真加。本周写了超卖复现实验：库存设为 1、起 50 个线程同时结算，"
    "条件更新版本下成功订单数稳定为 1，其余 49 个返回库存不足；无保护版本下能复现超卖。",
    "（预留）事件消费线程——EventBus 当前是同步的，但接口已经按「将来要异步」设计，"
    "真正异步化时再开一条消费线程从 BlockingQueue 取事件即可，不影响业务代码。",
])

h2("1.5 自认为比较经典的几个处理")
p("写了六周代码，回头看自己觉得处理得比较干净、答辩时愿意主动讲的有下面几处：")
bullets([
    "按长度字段切分字节流：连接对象里维护一个 ByteArrayOutputStream 缓存，循环「凑 16 字节头 → "
    "按长度字段凑数据体 → 拆出一条交上层」，粘包半包一次解决，且不假设 read 一次返回多少；",
    "条件更新防超卖：把「查库存 → 判断 → 扣库存」三步合并成一条带 WHERE 的 UPDATE，"
    "把并发正确性交给数据库事务，比在应用层加锁更可靠，也更容易讲清；",
    "订单明细做快照：t_order_item 里冗余了 product_name 和 price_snapshot，"
    "商品后来调价/下架不影响历史订单金额——这是从真实电商系统里学来的一招；",
    "DTO 与领域对象隔离：网络上传的是 dto.LoginRequest、dto.ProductView，"
    "数据库里是 model.User、model.Product，两者不混用。这样改协议字段不会污染领域模型，反之亦然；",
    "PreparedStatement 绑参数：所有 SQL 都用 ? 占位符，字符串拼参数一律不写，从根上防 SQL 注入；",
    "事务边界 try-catch-finally：autoCommit(false) → 业务步骤 → commit / catch 里 rollback / "
    "finally 里还连接，模板代码只写一遍。",
])

# ============== 二、本周技术问题 ==============
h1("二、本周遇到的技术问题与解决")
p("本周主要在补售后、店主端、装饰器叠加和超卖实验，新踩的坑比第四周更偏「细节」：")

p("问题 1：SQLite 报 The database file is locked", indent=False, bold=True)
p("现象：起两个客户端同时结算，服务端日志里偶发 SQLiteException: database is locked。"
  "原因：SQLite 同一时刻只允许一个写事务，连接池里放了 8 条连接，两个写事务并行就互相堵。"
  "解决：写操作（结算、售后回补库存）把连接池写连接数压到 1，读操作仍可多连接；"
  "并在 DBConnectionPool 里加了一个轻量重试——拿不到写锁时等 50ms 重试一次，仍失败再报错。"
  "这个问题在单机单客户端下复现不出来，是写超卖实验时才暴露的。")

p("问题 2：多线程下 SimpleDateFormat 偶发输出错乱时间", indent=False, bold=True)
p("现象：订单表里偶尔出现 1970-01-01 这种时间，或者时间字段带奇怪字符。"
  "原因：SimpleDateFormat 是非线程安全的，我把它做成了一个静态常量被多个工作线程同时调 format。"
  "解决：换成 java.time 包下的 DateTimeFormatter（这个类本身就是不可变、线程安全的），"
  "一行替换，问题消失。这是《Java 并发》里最经典的坑之一，亲手踩一次才记住。")

p("问题 3：反射加载促销类，配置文件打错字要等结算时才炸", indent=False, bold=True)
p("现象：把配置里的 DiscountStrategy 写成 DiscountSrategy，平时点浏览、加购都没事，"
  "一点结算就抛 ClassNotFoundException。解决：在服务端启动时就把 EventBus、所有促销策略类全部"
  "反射加载一遍做自检，任何一个类找不到、无参构造缺失，启动直接失败并打印配置行号。"
  "把运行时错误提前到启动时，是这一周学到的重要一招。")

p("问题 4：JTable 刷新数据后选中行丢失、滚动条跳回顶部", indent=False, bold=True)
p("现象：购物车改一次数量，表格重新 setModel 之后，用户刚才勾选的行全没了。"
  "原因：setModel 会把整个表格换掉。解决：刷新前先把当前选中的行号和勾选项记下来，"
  "新数据塞完之后按商品 id 反查重新勾选、把滚动条滚回原位置。这种纯 UI 细节没人教，"
  "是用户（自己）用自己的程序用出来的。")

p("问题 5：订单号并发生成重复", indent=False, bold=True)
p("现象：超卖实验里两个线程几乎同时结算，日志里出现两个相同的 order_no。"
  "原因：最初用 系统时间戳 + 「订单」两个字拼接，两个线程在同一毫秒内拿到同一个时间戳。"
  "解决：order_no 改成 日期 + 一个 AtomicLong 自增序列 + 一个随机后缀，"
  "并且数据库层 order_no 列本来就有 UNIQUE 约束，真撞了会直接抛异常回滚——双保险。")

p("问题 6：售后回补库存和扣库存不对称", indent=False, bold=True)
p("现象：测试「下单→退货→再下单」，发现第一次下单扣了 1 件库存，退货审核通过后库存没加回来。"
  "原因：只在 OrderService 写了扣库存的 SQL，AfterSaleService 里忘了写对称的「stock = stock + ?」。"
  "解决：补回补 SQL，和扣库存放在同一个事务里——审核通过 = 更新订单状态 + 回补库存 + 写售后单状态，"
  "三步同生共死。")

# ============== 三、优化想法 ==============
h1("三、对自己系统的优化想法")
p("本周功能基本闭环，剩下的时间除了收尾，也盘了一下「如果再多给两周，我会怎么改」。"
  "按价值/工作量排序：")
table(
    ["优化方向", "具体做法", "为什么值得做"],
    [
        ["持久层柔性真演示",
         "写一个 MyBatisProductDao 实现同一个 ProductDao 接口，现场切连接配置从 SQLite 换到 MySQL",
         "前几周一直在讲「分层柔性」，但没真切换过；现场跑一次最有说服力"],
        ["服务端监控台",
         "在服务端加一个简单的 JFrame，实时显示在线连接数、今日订单数、今日销售额",
         "把「多用户并发」从抽象概念变成界面上看得见的数字，答辩演示效果好"],
        ["异步事件真升级",
         "把 EventBus 从同步改成 ArrayBlockingQueue + 一条消费线程",
         "第三、四周一直被问「你的异步在哪」，这一刀补上就完整了"],
        ["客户端日志落盘",
         "把收发的报文、异常堆栈写到 logs/ 目录，按天滚动",
         "答辩时万一现场出问题，可以直接翻日志，不用凭印象瞎猜"],
        ["单元测试",
         "对促销引擎写几个手算用例（原价 100、9 折、满 200 减 20、再用 10 元券）",
         "促销金额是最容易被老师现场算错的地方，有测试兜底心里不慌"],
        ["心跳与断线重连",
         "客户端定时发 1009，服务端回 2009，超时重连",
         "本周按降级预案砍了，如果最后一周时间富余再加回来"],
    ],
    widths=[3.2, 6.8, 6.6],
)
p("这些优化里，前两个（MySQL 切换演示、服务端监控台）性价比最高，已经列进第 6 周的机动时间；"
  "异步事件真升级和单元测试视进度而定；心跳如果最后一周没空就保持砍，不硬塞半成品。")

# ============== 四、本周计划 ==============
h1("四、下周（第 6 周）拟解决的问题与方法")
p("第 6 周是最后一周，目标不再是加功能，而是「让现有系统能在答辩台上稳稳跑一遍」。"
  "按优先级：")
table(
    ["优先级", "拟解决问题", "解决方法"],
    [
        ["P0", "UI 主题切换真正可用",
         "把散在各个 View 里的颜色/字体抽到 Theme 类，设置界面里切换时通知所有已打开的窗口刷一遍；"
         "至少交付默认浅色 + 深色两套，不再硬撑三套"],
        ["P0", "全链路自验一遍",
         "按答辩脚本完整走：店主预置商品 → 顾客注册 → 浏览 → 加购 → 结算 → 退货 → 店主审核退货，"
         "任何一处走不通就当周修；同时起两个客户端模拟多用户"],
        ["P0", "超卖实验材料整理",
         "把第四周写的「库存=1、50 线程并发结算」对比实验跑两遍，把无保护版（超卖）和条件更新版（不超卖）"
         "的控制台输出截图，存进答辩 PPT"],
        ["P1", "MySQL 现场切换演示",
         "在本机装一个 MySQL，写一份 schema_mysql.sql，把配置文件里的连接串从 sqlite 切到 mysql，"
         "证明 DAO 代码零改动；切不成就只保留 SQLite，不硬上"],
        ["P1", "大报告与答辩 PPT",
         "把六篇周报整合成最终大报告；PPT 重点放架构图、协议格式、促销引擎类图、结算时序图、"
         "超卖实验对比截图；每人 10 分钟，按 8 分钟讲解 + 2 分钟留质询准备"],
        ["P2", "服务端监控台 / 日志落盘",
         "时间富余就加，加不上就写进「未来工作」章节，不强行交付"],
    ],
    widths=[1.4, 4.4, 10.8],
)
p("节奏安排：周一周二把 P0 做完（主题、全链路自验、超卖实验截图），周三周四做 P1（MySQL 切换、"
  "大报告、答辩 PPT），周五完整彩排两次，把 10 分钟时间卡准。"
  "周末留半天应对突发：比如答辩当天现场环境不一样、数据库初始化失败、某个功能临时走不通。")

p("以上为第五篇报告全部内容。", indent=True)

doc.save(OUT)
print("saved:", OUT)

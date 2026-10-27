# -*- coding: utf-8 -*-
"""第二篇报告：生成 3 张逻辑流程图（Mermaid）与渲染配置。"""
import os, sys, glob
from PIL import Image, ImageChops

BUILD = r"E:/JLU/oopwork/oop-work-WeChat-merchant-system/report2_build"
FIG = os.path.join(BUILD, "fig")
os.makedirs(FIG, exist_ok=True)

CONFIG = """{
  "theme": "base",
  "themeVariables": {
    "fontFamily": "Microsoft YaHei, SimHei, sans-serif",
    "fontSize": "18px",
    "primaryColor": "#eef3fb",
    "primaryBorderColor": "#4a6fa5",
    "primaryTextColor": "#1a1a1a",
    "lineColor": "#4a6fa5"
  },
  "flowchart": { "htmlLabels": true, "curve": "basis", "nodeSpacing": 40, "rankSpacing": 45 }
}
"""

FIGS = {}

FIGS["fig01_login"] = """
flowchart TD
    A["启动客户端"] --> B["输入账号 / 密码"]
    B --> C{"账号已存在 ?"}
    C -- 否（注册） --> D["二次确认密码 · 哈希加盐存储"]
    D --> E["服务端写入 t_user"]
    E --> F["返回注册成功"]
    F --> B
    C -- 是（登录） --> G["服务端查询 t_user · 校验密码哈希"]
    G --> H{"校验通过 ?"}
    H -- 否 --> I["返回错误码 · 提示重试"]
    I --> B
    H -- 是 --> J["返回会话与角色"]
    J --> K{"角色 ?"}
    K -- 顾客 --> L["挂载顾客界面"]
    K -- 店主 --> M["挂载店主界面"]
"""

FIGS["fig02_settle"] = """
flowchart TD
    A["顾客勾选购物车条目 · 提交结算"] --> B["服务端读取勾选项 · 应用促销链"]
    B --> C["计算应付金额 · 生成计算明细"]
    C --> D["事务开始 · 逐条扣减库存"]
    D --> E{"条件更新影响行数 = 1 ?"}
    E -- 否 --> F["回滚事务 · 返回库存不足"]
    E -- 是 --> G["写入订单与明细 · 提交事务"]
    G --> H["发布交易完成事件"]
    H --> I["返回订单号与计算明细"]
"""

FIGS["fig03_codec"] = """
flowchart TD
    A["字节流到达并缓存"] --> B{"缓存字节数 >= 16 ?"}
    B -- 否 --> A
    B -- 是 --> C["读取报文头 · 校验魔数"]
    C -- 出错 --> D["丢弃并记录告警"] --> A
    C -- 正确 --> E["解析数据长度 len"]
    E --> F{"缓存字节数 >= len ?"}
    F -- 否 --> A
    F -- 是 --> G["取出数据体 · JSON 解析"]
    G --> H["按功能码分发处理 · 构造应答写回"]
"""

SCALE = 3


def write_files():
    for name, src in FIGS.items():
        with open(os.path.join(FIG, name + ".mmd"), "w", encoding="utf-8") as f:
            f.write(src.strip() + "\n")
    with open(os.path.join(BUILD, "mermaid-config.json"), "w", encoding="utf-8") as f:
        f.write(CONFIG)
    print("wrote %d mmd files" % len(FIGS))


def measure():
    print("%-4s %-16s %9s %8s %8s" % ("", "figure", "css WxH", "D(cm)", "H(cm)"))
    bad = []
    for p in sorted(glob.glob(os.path.join(FIG, "fig*.png"))):
        if p.endswith("_c.png"):
            continue
        im = Image.open(p).convert("RGB")
        bg = Image.new("RGB", im.size, (255, 255, 255))
        bbox = ImageChops.difference(im, bg).getbbox()
        if bbox:
            pad = 16
            bbox = (max(0, bbox[0] - pad), max(0, bbox[1] - pad),
                    min(im.width, bbox[2] + pad), min(im.height, bbox[3] + pad))
            im = im.crop(bbox)
        im.save(p.replace(".png", "_c.png"), "PNG")
        W, H = im.width / SCALE, im.height / SCALE
        D = min(17.0, 0.02057 * W)
        Hcm = H * (D * 28.35) / W / 28.35
        ok = (W <= 826 and H <= 1167)
        if not ok:
            bad.append((os.path.basename(p), round(W), round(H)))
        print("%-4s %-16s %4dx%-5d %7.1f %8.1f"
              % ("OK " if ok else "!! ", os.path.basename(p)[:-4], W, H, D, Hcm))
    print()
    if bad:
        print("需调整：", bad)
    else:
        print("全部图均符合 A4 适配约束。")


if __name__ == "__main__":
    if "--measure" in sys.argv:
        measure()
    else:
        write_files()

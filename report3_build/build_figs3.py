# -*- coding: utf-8 -*-
"""第三篇报告：生成 2 张图（模块关系图、通信时序图）与渲染配置。"""
import os, sys, glob
from PIL import Image, ImageChops

BUILD = r"E:/JLU/oopwork/oop-work-WeChat-merchant-system/report3_build"
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
  "flowchart": { "htmlLabels": true, "curve": "basis", "nodeSpacing": 36, "rankSpacing": 40 },
  "sequence": { "width": 180, "height": 46, "boxMargin": 10 }
}
"""

FIGS = {}

FIGS["fig01_modules"] = """
flowchart TB
    subgraph CLIENT["客户端进程"]
        direction TB
        UI["ui 视图层：登录 / 商品 / 购物车 / 结算 / 店主界面"]
        CTRL["controller 控制器层：事件调度"]
        CSVC["service 客户端业务层：购物车副本 / 金额预估"]
        CCOMM["comm 通信层：协议编解码 / 连接管理"]
        UI --> CTRL --> CSVC --> CCOMM
    end
    subgraph SERVER["服务端进程"]
        direction TB
        SCOMM["comm 通信层：监听 / 线程池 / 功能码分发"]
        SVC["service 业务层：账号 / 商品 / 订单 / 促销 / 售后"]
        EVENT["event 事件：发布 / 订阅"]
        DAO["dao 持久层：DAO 接口 + JDBC 实现"]
        DB[("数据库：SQLite / MySQL")]
        SCOMM --> SVC --> DAO --> DB
        SVC --> EVENT
    end
    CCOMM <-->|"TCP/IP 自定义协议"| SCOMM
"""

FIGS["fig02_seq"] = """
sequenceDiagram
    participant C as 客户端
    participant S as 服务端
    participant DB as 数据库
    C->>S: 10040000 提交结算 seq=n
    S->>S: 解析报文 · 校验功能码与数据体
    S->>DB: 事务内逐条条件更新扣减库存
    DB-->>S: 影响行数
    alt 库存充足
        S->>DB: 写入订单与订单明细
        S->>S: 发布交易完成事件
        S-->>C: 20040000 结算成功 · 含计算明细 seq=n
    else 库存不足
        S->>DB: 回滚事务
        S-->>C: 20040000 业务失败 · 库存不足 seq=n
    end
    S-->>C: 90010000 推送 · 订单状态变更
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

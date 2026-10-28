# S06 演示商品图片

这些是内置 image_gen 工具于 2026-10-27 生成的无品牌演示素材，不是真实商品照片。用户已选择当前图文列表、后续可再讨论卡片网格（UI04）。原始 PNG 随客户端 JAR 打包，不依赖外部网站；界面运行时等比例缩放，不修改原文件。

`demo-images.json` 以商品编号和名称共同匹配图片。改名、未知商品、图片缺失或加载失败时显示占位图，不拿其他商品图顶替。要替换演示图片，可在本目录放入同名 PNG 后重新构建；调整匹配时同步修改 JSON。已有数据库不因此被覆盖。正式图片上传、分发及维护不在当前 MVP 范围内。

最终资源：[纸质笔记本](demo-notebook.png)、[水杯](demo-cup.png)、[帆布袋](demo-tote.png)。生成方式：内置工具，未使用 API/CLI 回退。以下保存实际提示词，便于复现意图；不保证重新生成逐像素相同。

## demo-notebook.png

```text
Use case: product-mockup
Asset type: square product image for a desktop shopping course demo.
Primary request: a single closed A5 paper notebook, plain fabric cover, visible page edges; clearly stationery, not a laptop.
Style/medium: clean realistic studio product photography, unbranded demonstration product.
Scene/backdrop: light warm neutral seamless studio background.
Composition/framing: one product centered, fully visible with comfortable margins; square composition readable at thumbnail size.
Lighting/mood: soft diffused light, subtle natural shadow.
Constraints: no people, no other objects, no text, no logo, no watermark.
```

## demo-cup.png

```text
Use case: product-mockup
Asset type: square product image for a desktop shopping course demo.
Primary request: a single plain ceramic drinking mug with a handle.
Style/medium: clean realistic studio product photography, unbranded demonstration product.
Scene/backdrop: light warm neutral seamless studio background.
Composition/framing: one product centered, fully visible with comfortable margins; square composition readable at thumbnail size.
Lighting/mood: soft diffused light, subtle natural shadow.
Constraints: no people, no other objects, no text, no logo, no watermark.
```

## demo-tote.png

```text
Use case: product-mockup
Asset type: square product image for a desktop shopping course demo.
Primary request: a single plain natural cotton canvas tote bag with two handles.
Style/medium: clean realistic studio product photography, unbranded demonstration product.
Scene/backdrop: light warm neutral seamless studio background.
Composition/framing: one product centered, fully visible with comfortable margins; square composition readable at thumbnail size.
Lighting/mood: soft diffused light, subtle natural shadow.
Constraints: no people, no other objects, no text, no logo, no watermark.
```

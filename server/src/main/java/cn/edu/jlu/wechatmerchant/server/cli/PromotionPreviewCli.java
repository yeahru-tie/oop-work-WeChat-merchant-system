package cn.edu.jlu.wechatmerchant.server.cli;

import cn.edu.jlu.wechatmerchant.server.application.PromotionPreviewService;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.PriceBreakdown;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.PromotionTerms;
import cn.edu.jlu.wechatmerchant.server.persistence.ProductDao;
import cn.edu.jlu.wechatmerchant.server.persistence.SqliteDatabase;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.SQLException;

/** Local read-only development tool, not a management or checkout endpoint. */
public final class PromotionPreviewCli {
    private PromotionPreviewCli() { }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 4) {
            err.println("用法：PromotionPreviewCli 商品编号 数量 每件直降金额(元) 折扣(折)");
            return 2;
        }
        try {
            long productId = Long.parseLong(args[0]);
            int quantity = Integer.parseInt(args[1]);
            PromotionTerms terms = PromotionTerms.parse(args[2], args[3]);
            var preview = new PromotionPreviewService(new ProductDao(new SqliteDatabase(SqliteDatabase.configuredPath())))
                    .preview(productId, quantity, terms);
            out.print(format(preview));
            return 0;
        } catch (IllegalArgumentException exception) {
            err.println("预览参数无效：" + exception.getMessage());
            return 2;
        } catch (SQLException exception) {
            err.println("预览失败：数据库不可用或模式未就绪，请核对路径及初始化状态。");
            return 1;
        }
    }

    static String format(PromotionPreviewService.Preview preview) {
        var product = preview.product();
        var terms = preview.terms();
        var result = preview.breakdown();
        StringBuilder output = new StringBuilder("单品促销只读试算（未保存或发布活动）\n");
        output.append("商品：").append(product.name()).append("（").append(product.productId()).append("）\n")
                .append("当前单价：").append(money(BigInteger.valueOf(product.priceCents())))
                .append("；数量：").append(result.quantity()).append("；库存参考：").append(product.stock()).append('\n')
                .append("原价合计：").append(money(result.originalTotalCents())).append('\n');
        for (PriceBreakdown.Step step : result.steps()) {
            String label = step.stage() == PriceBreakdown.Stage.DIRECT_REDUCTION
                    ? "每件直降 " + money(BigInteger.valueOf(terms.reductionCents())) : "统一 " + terms.discountText() + " 折";
            output.append(label).append("：").append(money(step.beforeCents())).append(" → ")
                    .append(money(step.afterCents())).append("；本步优惠 ").append(money(step.savingsCents())).append('\n');
        }
        if (result.steps().isEmpty()) output.append("未设置有效减价或折扣，按原价试算。\n");
        output.append("优惠合计：").append(money(result.savingsTotalCents())).append('\n')
                .append("预估金额：").append(money(result.estimatedTotalCents())).append('\n')
                .append("按每件先直降再折扣，四舍五入到分后汇总；本次不保存活动、不占库存、不生成订单。\n");
        return output.toString();
    }

    private static String money(BigInteger cents) { return "¥ " + new BigDecimal(cents, 2).toPlainString(); }
}

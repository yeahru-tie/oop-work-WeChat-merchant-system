package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.protocol.*;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

final class OrderText {
    private OrderText(){}
    static String money(BigInteger value){return "¥ "+new BigDecimal(value,2).toPlainString();}
    static String time(long epoch){return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.ofEpochMilli(epoch));}
    static String status(String value){return switch(value){case "RETURN_REQUESTED" -> "退货处理中";case "REFUNDED" -> "已退款（模拟）";default -> "已成交（模拟）";};}
    static String returnStatus(String value){return switch(value){case "APPROVED" -> "已批准，待明确完成";case "REJECTED" -> "已拒绝，可重新申请";case "COMPLETED" -> "退货完成，已模拟退款";default -> "待管理员审核";};}
    static String history(AfterSalePage page){if(page==null)return "请选择订单并刷新售后记录";if(page.items().isEmpty())return "本订单暂无售后申请";var text=new StringBuilder();for(var item:page.items()){
        text.append("售后 #").append(item.afterSaleId()).append(" · ").append(returnStatus(item.status())).append("\n申请时间：").append(time(item.appliedAt())).append("\n原因：").append(item.reason()).append("\n");
        if(!item.reviewNote().isEmpty())text.append("拒绝说明：").append(item.reviewNote()).append("\n");if(item.reviewedAt()>0)text.append("审核时间：").append(time(item.reviewedAt())).append("\n");if(item.completedAt()>0)text.append("完成时间：").append(time(item.completedAt())).append("\n");
        text.append(item.status().equals("COMPLETED")?"模拟退款：":"预计整单退款：").append(money(item.refundCents())).append("；已用券不返还\n\n");}return text.toString();}
    static String confirmation(QuoteSnapshot quote){
        StringBuilder text=new StringBuilder("请核对本次勾选商品：\n\n");
        for(var item:quote.selectedCart().items())text.append(item.name()).append(" × ").append(item.quantity()).append("    商品优惠后 ").append(money(item.estimatedCents())).append('\n');
        var selected=quote.selectedCart();text.append("\n已选原价：").append(money(selected.totalCents())).append("\n商品优惠：").append(money(selected.totalCents().subtract(selected.estimatedTotalCents())))
                .append("\n优惠券：").append(quote.couponId()==null?"不用券":"已选券 #"+quote.couponId()).append("\n券抵扣：").append(money(quote.couponReductionCents()))
                .append("\n最终金额：").append(money(quote.payableCents())).append("\n\n这是模拟成交，无真实支付；确认后只移除本次选中商品，未选商品保留。报价若变化，本次不会直接成交。");return text.toString();
    }
    static String detail(OrderSnapshot order){
        var text=new StringBuilder("订单 #").append(order.orderId()).append(" · ").append(status(order.status())).append("\n原实付：").append(money(order.paidCents())).append("\n北京时间：").append(time(order.createdAt())).append("\n\n");
        for(var item:order.items()){
            var p=item.promotion();BigInteger qty=BigInteger.valueOf(item.quantity());
            text.append(item.name()).append(" × ").append(item.quantity()).append("\n  成交前单价：").append(money(BigInteger.valueOf(item.priceCents())))
                .append("；直降优惠：").append(money(BigInteger.valueOf(p.originalUnitCents()-p.afterReductionCents()).multiply(qty)))
                .append("；折扣优惠：").append(money(BigInteger.valueOf(p.afterReductionCents()-p.estimatedUnitCents()).multiply(qty)))
                .append("\n  商品优惠后小计：").append(money(item.estimatedCents())).append("\n\n");
        }
        text.append("原价合计：").append(money(order.originalCents())).append("\n商品优惠：").append(money(order.productReductionCents()))
                .append("\n优惠券：").append(order.coupon()==null?"未用券":order.coupon().name()).append("\n券抵扣：").append(money(order.couponReductionCents()))
                .append("\n成交金额：").append(money(order.paidCents())).append("\n\n以上为成交时记录，商品和活动后续修改不会改变此单。整单退货按原实付，已用券不返还。");return text.toString();
    }
}

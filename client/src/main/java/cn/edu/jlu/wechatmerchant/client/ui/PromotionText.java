package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.protocol.PromotionPrice;
import java.math.BigDecimal;
import java.math.BigInteger;

/** Formats authoritative display snapshots without selecting or computing promotions. */
final class PromotionText {
    private PromotionText() { }
    static String activity(PromotionPrice price) {
        if (price==null) return "优惠信息尚未获取，请刷新";
        if (price.reductionCents()==0 && price.discountHundredths()==1000) return "暂无生效优惠";
        return (price.reductionCents()>0 ? "每件减 ¥ "+BigDecimal.valueOf(price.reductionCents(),2).toPlainString() : "")
                +(price.reductionCents()>0 && price.discountHundredths()<1000 ? "，再" : "")
                +(price.discountHundredths()<1000 ? BigDecimal.valueOf(price.discountHundredths(),2).stripTrailingZeros().toPlainString()+"折" : "");
    }
    static String details(PromotionPrice price, int quantity) {
        if (price==null) return "优惠信息未获取，请刷新核对";
        BigInteger count=BigInteger.valueOf(quantity);
        var reduction=BigInteger.valueOf(price.originalUnitCents()-price.afterReductionCents()).multiply(count);
        var discount=BigInteger.valueOf(price.afterReductionCents()-price.estimatedUnitCents()).multiply(count);
        return activity(price)+"\n直降优惠："+CartPanel.money(reduction)+"；折扣优惠："+CartPanel.money(discount);
    }
}

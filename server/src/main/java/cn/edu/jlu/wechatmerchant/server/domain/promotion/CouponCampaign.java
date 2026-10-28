package cn.edu.jlu.wechatmerchant.server.domain.promotion;

/** Millisecond instants; display/input use Asia/Shanghai. Version protects management edits. */
public record CouponCampaign(long campaignId,long version,String name,long thresholdCents,long discountCents,
        long startsAt,long endsAt,int total,int claimed,boolean published,boolean paused) {
    public CouponCampaign {
        if(campaignId<0 || version<0 || (campaignId==0)!=(version==0) || name==null || name.isBlank()
                || name.codePointCount(0,name.length())>80 || thresholdCents<0 || discountCents<=0
                || endsAt<=startsAt || total<1 || claimed<0 || claimed>total || (!published && claimed!=0)) {
            throw new IllegalArgumentException("优惠券名称、金额、总量或起止时间无效。");
        }
    }
    public String claimStatus(long now,boolean owned) {
        if(owned) return "ALREADY_CLAIMED";
        if(!published || paused) return "PAUSED";
        if(now<startsAt) return "NOT_STARTED";
        if(now>=endsAt) return "EXPIRED";
        return claimed>=total ? "SOLD_OUT" : "CAN_CLAIM";
    }
    public String useStatus(long now,java.math.BigInteger subtotal,boolean used) {
        if(used) return "USED";
        if(now<startsAt) return "NOT_STARTED";
        if(now>=endsAt) return "EXPIRED";
        return subtotal.compareTo(java.math.BigInteger.valueOf(thresholdCents))<0 ? "THRESHOLD_NOT_MET" : "USABLE";
    }
    public java.math.BigInteger reductionFor(java.math.BigInteger subtotal) {
        if(subtotal==null || subtotal.signum()<0)throw new IllegalArgumentException("Invalid subtotal.");
        return subtotal.min(java.math.BigInteger.valueOf(discountCents));
    }
    public static long amount(String value,boolean positive) {
        if(value==null || !value.strip().matches("[0-9]{1,20}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("金额最多两位小数。");
        try {
            long cents=new java.math.BigDecimal(value.strip()).movePointRight(2).longValueExact();
            if(cents<0 || (positive && cents==0)) throw new IllegalArgumentException("减免须大于0，门槛不得为负。");
            return cents;
        } catch(ArithmeticException exception) { throw new IllegalArgumentException("金额超出支持范围。"); }
    }
}

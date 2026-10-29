package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.Set;

public record AfterSaleSnapshot(long afterSaleId,long orderId,String returnKey,long version,String status,String reason,String reviewNote,long appliedAt,long reviewedAt,long completedAt,BigInteger refundCents) {
    public AfterSaleSnapshot {
        if(afterSaleId<=0 || orderId<=0 || !ApplyReturnRequest.validKey(returnKey) || version<1 || status==null || !Set.of("REQUESTED","APPROVED","REJECTED","COMPLETED").contains(status)
                || !ApplyReturnRequest.validText(reason) || reviewNote==null || reviewNote.codePointCount(0,reviewNote.length())>500 || appliedAt<0 || reviewedAt<0 || completedAt<0 || refundCents==null || refundCents.signum()<0 || refundCents.bitLength()>128)throw new IllegalArgumentException("Invalid after-sale record.");
        if(status.equals("REQUESTED")?(version!=1 || reviewedAt!=0 || completedAt!=0 || !reviewNote.isEmpty()):(version!=(status.equals("COMPLETED")?3:2)) || reviewedAt<appliedAt || (status.equals("REJECTED")?!ApplyReturnRequest.validText(reviewNote):!reviewNote.isEmpty())
                || (status.equals("COMPLETED")?completedAt<reviewedAt:completedAt!=0))throw new IllegalArgumentException("Invalid after-sale transition.");
    }
    public static AfterSaleSnapshot parse(JsonObject body) throws ProtocolException {
        try{JsonFields.fields(body,"afterSaleId","orderId","returnKey","version","status","reason","reviewNote","appliedAt","reviewedAt","completedAt","refundCents");return new AfterSaleSnapshot(JsonFields.number(body,"afterSaleId"),JsonFields.number(body,"orderId"),JsonFields.text(body,"returnKey"),JsonFields.number(body,"version"),JsonFields.text(body,"status"),JsonFields.text(body,"reason"),JsonFields.text(body,"reviewNote"),JsonFields.number(body,"appliedAt"),JsonFields.number(body,"reviewedAt"),JsonFields.number(body,"completedAt"),JsonFields.big(body,"refundCents"));}
        catch(RuntimeException ex){throw new ProtocolException("Invalid after-sale record.",ex);}
    }
}

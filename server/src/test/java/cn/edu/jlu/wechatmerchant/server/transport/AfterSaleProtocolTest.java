package cn.edu.jlu.wechatmerchant.server.transport;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.support.TransactionFixture;
import com.google.gson.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AfterSaleProtocolTest {
    @TempDir Path folder;
    @Test void networkRejectsWrongRoleForgedFieldsAndCrossOwnerHistoryAndValidatesReceipt() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("protocol.db"))){var cart=f.carts.add(1,1001,1,0);long order=f.orders.checkout(f.buyer,f.request(f.buyer,cart,null,CartSelection.all(cart))).order().orderId();f.start();
            for(int op:new int[]{Messages.APPLY_RETURN,Messages.QUERY_RETURNS}){assertEquals("UNAUTHENTICATED",f.exchange(op,null,new JsonObject()).get("code").getAsString());assertEquals("FORBIDDEN",f.exchange(op,f.admin,new JsonObject()).get("code").getAsString());}
            var command=new ApplyReturnRequest(UUID.randomUUID().toString(),order,"需要退货");var body=TransactionFixture.JSON.toJsonTree(command).getAsJsonObject();body.addProperty("userId",2);assertEquals("INVALID_ARGUMENT",f.exchange(Messages.APPLY_RETURN,f.buyer,body).get("code").getAsString());body.remove("userId");var reply=f.exchange(Messages.APPLY_RETURN,f.buyer,body);var receipt=AfterSaleReceipt.parse(reply.getAsJsonObject("body"),command.returnKey());assertEquals("REQUESTED",receipt.application().status());
            var history=new JsonObject();history.addProperty("mode","HISTORY");history.addProperty("orderId",order);history.addProperty("limit",20);history.addProperty("offset",0);assertEquals("NOT_FOUND",f.exchange(Messages.QUERY_RETURNS,f.other,history).get("code").getAsString());var page=AfterSalePage.parse(f.exchange(Messages.QUERY_RETURNS,f.buyer,history).getAsJsonObject("body"),order,20,0);assertEquals(receipt.application(),page.items().getFirst());history.addProperty("limit",1.5);assertEquals("INVALID_ARGUMENT",f.exchange(Messages.QUERY_RETURNS,f.buyer,history).get("code").getAsString());
            var key=new JsonObject();key.addProperty("mode","BY_KEY");key.addProperty("returnKey",command.returnKey());assertEquals("NOT_FOUND",f.exchange(Messages.QUERY_RETURNS,f.other,key).getAsJsonObject("body").get("state").getAsString());f.sessions.revoke(f.buyer);assertEquals("UNAUTHENTICATED",f.exchange(Messages.APPLY_RETURN,f.buyer,body).get("code").getAsString());assertEquals(1,f.value("SELECT count(*) FROM after_sales"));assertEquals(0,f.value("SELECT count(*) FROM refunds"));
        }
    }
}

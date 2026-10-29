package cn.edu.jlu.wechatmerchant.server.transport;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.server.persistence.NotificationDao;
import cn.edu.jlu.wechatmerchant.server.support.TransactionFixture;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InboxProtocolTest {
    @TempDir Path folder;
    @Test void actualClientReadsAndMarksPersonalAnnouncementWithoutAffectingOtherBuyer() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("tcp.db"))){long message;
            try(var c=f.db.openWriteConnection()){message=NotificationDao.insert(c,null,"tcp-announcement","ANNOUNCEMENT","店铺公告","欢迎查看本店活动说明",f.clock.millis(),null,null);}
            f.start();try(var client=new MerchantClient("127.0.0.1",f.port(),2000,5000)){
                client.login("buyer_a","BuyerA#2026!".toCharArray(),() -> false);
                assertEquals(1,client.messages(20,0,null,() -> false).unreadCount());
                assertEquals(0,client.messages(20,0,message,() -> false).unreadCount());
                assertEquals(0,client.messages(20,0,null,() -> false).unreadCount());
            }
            var body=new JsonObject();body.addProperty("limit",20);body.addProperty("offset",0);
            assertEquals("UNAUTHENTICATED",f.exchange(Messages.LIST_MESSAGES,null,body).get("code").getAsString());
            assertEquals("FORBIDDEN",f.exchange(Messages.LIST_MESSAGES,f.admin,body).get("code").getAsString());
            assertEquals(1,InboxPage.parse(f.exchange(Messages.LIST_MESSAGES,f.other,body).getAsJsonObject("body"),20,0).unreadCount());
            body.addProperty("userId",1);assertEquals("INVALID_ARGUMENT",f.exchange(Messages.LIST_MESSAGES,f.other,body).get("code").getAsString());
            assertEquals(1,f.value("SELECT count(*) FROM notifications"));assertEquals(1,f.value("SELECT count(*) FROM message_reads"));
        }
    }
    @Test void automaticChecksDoNotKeepThirtyMinuteIdleSessionsAlive() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("idle.db"))){f.start();var body=new JsonObject();body.addProperty("limit",20);body.addProperty("offset",0);
            for(int i=0;i<29;i++){f.clock.advance(java.time.Duration.ofMinutes(1));assertEquals("OK",f.exchange(Messages.LIST_MESSAGES,f.buyer,body).get("code").getAsString());}
            f.clock.advance(java.time.Duration.ofMinutes(1));assertEquals("UNAUTHENTICATED",f.exchange(Messages.LIST_MESSAGES,f.buyer,body).get("code").getAsString());
        }
    }
}

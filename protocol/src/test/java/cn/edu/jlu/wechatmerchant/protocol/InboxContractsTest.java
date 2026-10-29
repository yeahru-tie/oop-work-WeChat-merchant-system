package cn.edu.jlu.wechatmerchant.protocol;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.GsonBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

class InboxContractsTest {
    private InboxPage page(){return new InboxPage(List.of(new InboxPage.Item(2,"ORDER","成交","查看原订单",1,false,4L,null),new InboxPage.Item(1,"ANNOUNCEMENT","公告","<html>普通文字</html>",0,true,null,1001L)),20,0,false,1);}
    private com.google.gson.JsonObject json(){return new GsonBuilder().serializeNulls().create().toJsonTree(page()).getAsJsonObject();}
    @Test void roundTripAndPlainText() throws Exception {assertEquals(page(),InboxPage.parse(json(),20,0));}
    @Test void rejectsWrongCorrelationAndUnknownFields(){var j=json();assertThrows(ProtocolException.class,() -> InboxPage.parse(j,20,20));j.addProperty("role","ADMIN");assertThrows(ProtocolException.class,() -> InboxPage.parse(j,20,0));}
    @Test void rejectsCoercionAndWrongAssociations(){var j=json();j.addProperty("unreadCount","1");assertThrows(ProtocolException.class,() -> InboxPage.parse(j,20,0));assertThrows(IllegalArgumentException.class,() -> new InboxPage.Item(1,"ANNOUNCEMENT","公告","内容",0,false,3L,null));}
    @Test void rejectsDuplicateOrderingAndOverlongText(){assertThrows(IllegalArgumentException.class,() -> new InboxPage(List.of(page().items().get(0),page().items().get(0)),20,0,false,1));assertFalse(InboxPage.validText("字".repeat(81),80));assertFalse(InboxPage.validText("  ",80));}
}

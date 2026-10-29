package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Personal inbox projection. A public announcement still has personal read state. */
public record InboxPage(List<Item> items,int limit,int offset,boolean hasMore,long unreadCount) {
    public record Item(long messageId,String kind,String title,String text,long createdAt,boolean read,Long orderId,Long productId) {
        public Item {
            if(messageId<=0 || !Set.of("ORDER","RETURN","ANNOUNCEMENT").contains(kind) || !validText(title,80) || !validText(text,2000) || createdAt<0
                    || orderId!=null && orderId<=0 || productId!=null && productId<=0 || orderId!=null && productId!=null
                    || !kind.equals("ANNOUNCEMENT") && orderId==null || kind.equals("ANNOUNCEMENT") && orderId!=null)throw new IllegalArgumentException("Invalid inbox item.");
        }
    }
    public InboxPage {
        items=List.copyOf(items);
        if(limit<1 || limit>100 || offset<0 || items.size()>limit || hasMore && items.size()!=limit || unreadCount<0)throw new IllegalArgumentException("Invalid inbox page.");
        long last=Long.MAX_VALUE;
        for(var item:items){if(item.messageId()>=last)throw new IllegalArgumentException("Invalid inbox ordering.");last=item.messageId();}
    }
    public static boolean validText(String text,int max){return text!=null && !text.isBlank() && text.codePointCount(0,text.length())<=max;}
    public static InboxPage parse(JsonObject body,int limit,int offset) throws ProtocolException {
        try {
            JsonFields.fields(body,"items","limit","offset","hasMore","unreadCount");
            var array=body.getAsJsonArray("items");if(array.size()>100)throw new IllegalArgumentException("Too many messages.");
            var items=new ArrayList<Item>();
            for(var value:array){var o=value.getAsJsonObject();JsonFields.fields(o,"messageId","kind","title","text","createdAt","read","orderId","productId");
                items.add(new Item(JsonFields.number(o,"messageId"),JsonFields.text(o,"kind"),JsonFields.text(o,"title"),JsonFields.text(o,"text"),JsonFields.number(o,"createdAt"),JsonFields.bool(o,"read"),nullable(o,"orderId"),nullable(o,"productId")));}
            var page=new InboxPage(items,Math.toIntExact(JsonFields.number(body,"limit")),Math.toIntExact(JsonFields.number(body,"offset")),JsonFields.bool(body,"hasMore"),JsonFields.number(body,"unreadCount"));
            if(page.limit()!=limit || page.offset()!=offset)throw new IllegalArgumentException("Wrong inbox correlation.");return page;
        }catch(RuntimeException ex){throw new ProtocolException("Invalid inbox page.",ex);}
    }
    private static Long nullable(JsonObject o,String name){return o.get(name).isJsonNull()?null:JsonFields.number(o,name);}
}

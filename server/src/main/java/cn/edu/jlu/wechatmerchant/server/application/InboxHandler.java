package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.*;
import com.google.gson.*;
import java.sql.SQLException;

public final class InboxHandler {
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private final InboxService service;
    private final IdentityHandler identities;
    public InboxHandler(InboxService service,IdentityHandler identities){this.service=service;this.identities=identities;}
    public boolean handles(int op){return op==Messages.LIST_MESSAGES || op==Messages.READ_MESSAGE;}
    public JsonObject handle(Messages.Request request){
        var denied=identities.requireBuyer(request,request.operation()!=Messages.LIST_MESSAGES).denial();if(denied!=null)return denied;
        try {
            var b=request.body();int limit=Math.toIntExact(JsonFields.number(b,"limit")),offset=Math.toIntExact(JsonFields.number(b,"offset"));InboxPage result;
            if(request.operation()==Messages.LIST_MESSAGES){JsonFields.fields(b,"limit","offset");result=service.list(request.sessionToken(),limit,offset);}
            else{JsonFields.fields(b,"messageId","limit","offset");result=service.read(request.sessionToken(),JsonFields.number(b,"messageId"),limit,offset);}
            return Messages.response(request,"OK","ok",JSON.toJsonTree(result).getAsJsonObject());
        }catch(SecurityException ex){return error(request,ex.getMessage());}
        catch(IllegalArgumentException | ArithmeticException ex){return error(request,"INVALID_ARGUMENT");}
        catch(SQLException ex){System.err.println("Inbox operation failed, database code: "+ex.getErrorCode());return error(request,"INTERNAL_ERROR");}
    }
    private static JsonObject error(Messages.Request request,String code){return Messages.response(request,code,"消息操作未完成，可刷新核对。",new JsonObject());}
}

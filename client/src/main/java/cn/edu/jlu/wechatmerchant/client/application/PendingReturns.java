package cn.edu.jlu.wechatmerchant.client.application;

import cn.edu.jlu.wechatmerchant.protocol.*;
import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Original request only, never credentials or an automatic replay. */
public final class PendingReturns {
    private static final Gson JSON=new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final Path root;private final Map<String,ApplyReturnRequest> memory=new TreeMap<>();
    public PendingReturns(){root=null;}
    public PendingReturns(Path root){this.root=root.toAbsolutePath().normalize();}
    public static Path configuredPath(){return PendingCheckouts.configuredPath().toAbsolutePath().resolveSibling("client-returns");}
    public synchronized Optional<ApplyReturnRequest> first(UserIdentity user) throws IOException {
        if(root==null)return memory.entrySet().stream().filter(e -> e.getKey().startsWith(user.userId()+"-")).map(Map.Entry::getValue).findFirst();
        if(!Files.exists(root))return Optional.empty();try(var stream=Files.list(root)){var files=stream.filter(p -> p.getFileName().toString().startsWith(user.userId()+"-") && p.getFileName().toString().endsWith(".json")).sorted().limit(1001).toList();if(files.size()>1000)throw new IOException("Too many records.");ApplyReturnRequest first=null;for(var p:files){var value=read(user,p);if(first==null)first=value;}return Optional.ofNullable(first);}
    }
    public synchronized void save(UserIdentity user,ApplyReturnRequest request) throws IOException {
        String name=name(user,request.returnKey());if(root==null){var old=memory.putIfAbsent(name,request);if(old!=null && !old.equals(request))throw new IOException("Request conflict.");return;}
        Files.createDirectories(root);var file=root.resolve(name);if(Files.exists(file)){if(!read(user,file).equals(request))throw new IOException("Request conflict.");return;}
        var body=new JsonObject();body.addProperty("userId",user.userId());body.addProperty("username",user.username());body.add("request",JSON.toJsonTree(request));byte[] bytes=JSON.toJson(body).getBytes(StandardCharsets.UTF_8);Path temp=Files.createTempFile(root,"return-",".tmp");
        try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
        try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,file);}
    }
    public synchronized void remove(UserIdentity user,ApplyReturnRequest request) throws IOException {
        String name=name(user,request.returnKey());if(root==null){memory.remove(name);return;}Path file=root.resolve(name);if(Files.exists(file)){if(!read(user,file).equals(request))throw new IOException("Request changed.");Files.delete(file);}
    }
    private ApplyReturnRequest read(UserIdentity user,Path file) throws IOException {
        try{if(Files.size(file)>65536)throw new IllegalArgumentException("Record too large.");var body=JSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),JsonObject.class);JsonFields.fields(body,"userId","username","request");if(JsonFields.number(body,"userId")!=user.userId() || !JsonFields.text(body,"username").equals(user.username()))throw new IllegalArgumentException("Wrong owner.");var value=ApplyReturnRequest.parse(body.getAsJsonObject("request"));if(!file.getFileName().toString().equals(name(user,value.returnKey())))throw new IllegalArgumentException("Wrong filename.");return value;}
        catch(RuntimeException ex){throw new IOException("Recovery record cannot be verified; preserve it.",ex);}
    }
    private static String name(UserIdentity user,String key){if(user==null || user.role()!=UserIdentity.Role.BUYER || !ApplyReturnRequest.validKey(key))throw new IllegalArgumentException("Invalid recovery owner.");return user.userId()+"-"+key+".json";}
}

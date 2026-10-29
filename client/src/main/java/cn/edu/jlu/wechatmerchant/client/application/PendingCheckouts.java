package cn.edu.jlu.wechatmerchant.client.application;

import cn.edu.jlu.wechatmerchant.protocol.*;
import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Only public owner identifiers and frozen request parameters; never passwords or session tokens. */
public final class PendingCheckouts implements CheckoutJournal {
    public record Entry(CheckoutRequest request,List<Long> knownItems){public Entry{Objects.requireNonNull(request);knownItems=CartSelection.validate(knownItems);if(!knownItems.containsAll(request.itemIds()))throw new IllegalArgumentException("Missing checkout context.");}}
    private static final Gson GSON=new GsonBuilder().serializeNulls().setStrictness(Strictness.STRICT).create();
    private final Path root;
    private final Map<String,Entry> memory=new TreeMap<>();
    public PendingCheckouts(){root=null;}
    public PendingCheckouts(Path root){this.root=root.toAbsolutePath().normalize();}
    public static Path configuredPath(){return Path.of(System.getProperty("merchant.clientState","data/client-pending"));}
    public synchronized Optional<Entry> first(UserIdentity owner) throws IOException {
        if(root==null)return memory.entrySet().stream().filter(e -> e.getKey().startsWith(owner.userId()+"-")).map(Map.Entry::getValue).findFirst();
        if(!Files.exists(root))return Optional.empty();
        try(var files=Files.list(root)){
            var records=files.filter(p -> p.getFileName().toString().startsWith(owner.userId()+"-") && p.getFileName().toString().endsWith(".json")).sorted().limit(1001).toList();
            if(records.size()>1000)throw new IOException("Too many pending records.");
            // Validate every file, so a corrupt later record cannot silently permit another purchase.
            Entry first=null;for(Path file:records){var item=read(owner,file);if(first==null)first=item;}return Optional.ofNullable(first);
        }
    }
    public synchronized void save(UserIdentity owner,Entry entry) throws IOException {
        String key=filename(owner,entry.request().checkoutKey());
        if(root==null){var old=memory.putIfAbsent(key,entry);if(old!=null && !old.equals(entry))throw new IOException("Pending request conflict.");return;}
        Files.createDirectories(root);Path target=root.resolve(key);
        if(Files.exists(target)){if(!read(owner,target).equals(entry))throw new IOException("Pending request conflict.");return;}
        JsonObject body=new JsonObject();body.addProperty("userId",owner.userId());body.addProperty("username",owner.username());body.add("request",GSON.toJsonTree(entry.request()));body.add("knownItems",GSON.toJsonTree(entry.knownItems()));
        Path temp=Files.createTempFile(root,"pending-",".tmp");byte[] bytes=GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
        try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,target);}
    }
    public synchronized void remove(UserIdentity owner,Entry entry) throws IOException {
        String name=filename(owner,entry.request().checkoutKey());if(root==null){memory.remove(name);return;}
        Path target=root.resolve(name);if(Files.exists(target)){if(!read(owner,target).equals(entry))throw new IOException("Pending request changed.");Files.delete(target);}
    }
    private Entry read(UserIdentity owner,Path file) throws IOException {
        try{
            if(Files.size(file)>65536)throw new IllegalArgumentException("Pending record too large.");
            JsonObject body=GSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),JsonObject.class);JsonFields.fields(body,"userId","username","request","knownItems");
            if(JsonFields.number(body,"userId")!=owner.userId() || !JsonFields.text(body,"username").equals(owner.username()))throw new IllegalArgumentException("Wrong pending owner.");
            var entry=new Entry(CheckoutRequest.parse(body.getAsJsonObject("request")),CartSelection.parse(body.get("knownItems")));
            if(!file.getFileName().toString().equals(filename(owner,entry.request().checkoutKey())))throw new IllegalArgumentException("Wrong pending filename.");return entry;
        }catch(RuntimeException ex){throw new IOException("Pending record cannot be verified; preserve it for recovery.",ex);}
    }
    private static String filename(UserIdentity owner,String key){if(owner==null || owner.role()!=UserIdentity.Role.BUYER || key==null || !key.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException("Invalid pending owner/key.");return owner.userId()+"-"+key+".json";}
}

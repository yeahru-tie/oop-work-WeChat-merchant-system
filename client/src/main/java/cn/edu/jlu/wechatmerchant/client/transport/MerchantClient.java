package cn.edu.jlu.wechatmerchant.client.transport;

import cn.edu.jlu.wechatmerchant.protocol.FrameCodec;
import cn.edu.jlu.wechatmerchant.protocol.Messages;
import cn.edu.jlu.wechatmerchant.protocol.ProductPage;
import cn.edu.jlu.wechatmerchant.protocol.CartSnapshot;
import cn.edu.jlu.wechatmerchant.protocol.CouponPage;
import cn.edu.jlu.wechatmerchant.protocol.QuoteSnapshot;
import cn.edu.jlu.wechatmerchant.protocol.CartSelection;
import cn.edu.jlu.wechatmerchant.protocol.CheckoutRequest;
import cn.edu.jlu.wechatmerchant.protocol.CheckoutReceipt;
import cn.edu.jlu.wechatmerchant.protocol.OrderSnapshot;
import cn.edu.jlu.wechatmerchant.protocol.OrderPage;
import cn.edu.jlu.wechatmerchant.protocol.ApplyReturnRequest;
import cn.edu.jlu.wechatmerchant.protocol.AfterSaleReceipt;
import cn.edu.jlu.wechatmerchant.protocol.AfterSalePage;
import cn.edu.jlu.wechatmerchant.protocol.ProtocolException;
import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import com.google.gson.JsonObject;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class MerchantClient implements AutoCloseable {
    private final String address;
    private final int port;
    private final int connectTimeoutMillis;
    private final int responseTimeoutMillis;
    private final AtomicReference<Socket> connection = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object requestLock = new Object();
    private final Object identityLock = new Object();
    private long identityGeneration;
    private Credentials credentials;

    private record Credentials(UserIdentity identity, String token) {
        @Override public String toString() { return "Credentials[<redacted>]"; }
    }

    public MerchantClient(String address, int port, int connectTimeoutMillis, int responseTimeoutMillis) {
        if (address == null || address.isBlank() || port < 1 || port > 65535
                || connectTimeoutMillis <= 0 || responseTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Invalid endpoint or timeout.");
        }
        this.address = address;
        this.port = port;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.responseTimeoutMillis = responseTimeoutMillis;
    }

    public String ping() throws IOException {
        return ping(() -> false);
    }

    public String ping(BooleanSupplier cancelled) throws IOException {
        return request(cancelled, Messages::ping, Messages::validatePong);
    }

    public ProductPage listProducts(int limit, int offset, BooleanSupplier cancelled) throws IOException {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("Invalid product pagination.");
        }
        return request(cancelled, id -> {
            JsonObject query = Messages.productList(id, limit, offset);
            String token = token();
            if (token != null) query.addProperty("sessionToken", token);
            return query;
        }, (value, id) -> {
            Messages.Response response = successful(value, id, Messages.PRODUCTS);
            return ProductPage.parse(response.body(), limit, offset);
        });
    }

    public Optional<UserIdentity> identity() {
        synchronized (identityLock) {
            return credentials == null ? Optional.empty() : Optional.of(credentials.identity());
        }
    }

    public CartSnapshot listCart(BooleanSupplier cancelled) throws IOException {
        return cartRequest(Messages.LIST_CART,new JsonObject(),cancelled);
    }

    public cn.edu.jlu.wechatmerchant.protocol.InboxPage messages(int limit,int offset,Long readId,BooleanSupplier cancelled) throws IOException {
        if(limit<1 || limit>100 || offset<0 || readId!=null && readId<=0)throw new IllegalArgumentException("Invalid inbox query.");
        JsonObject body=new JsonObject();body.addProperty("limit",limit);body.addProperty("offset",offset);
        int op=readId==null?Messages.LIST_MESSAGES:Messages.READ_MESSAGE;if(readId!=null)body.addProperty("messageId",readId);
        return request(cancelled,id -> Messages.authenticatedRequest(id,op,token(),body),(value,id) -> cn.edu.jlu.wechatmerchant.protocol.InboxPage.parse(successful(value,id,op+1000).body(),limit,offset));
    }

    public CouponPage listCoupons(String kind,int limit,int offset,BooleanSupplier cancelled) throws IOException {
        return listCoupons(kind,limit,offset,null,cancelled);
    }
    public CouponPage listCoupons(String kind,int limit,int offset,java.util.List<Long> items,BooleanSupplier cancelled) throws IOException {
        JsonObject body=new JsonObject(); body.addProperty("kind",kind); body.addProperty("limit",limit); body.addProperty("offset",offset);
        if(items!=null)body.add("itemIds",new com.google.gson.Gson().toJsonTree(CartSelection.validate(items)));
        return request(cancelled,id -> Messages.authenticatedRequest(id,Messages.LIST_COUPONS,token(),body),
                (value,id) -> CouponPage.parse(successful(value,id,Messages.LIST_COUPONS+1000).body(),kind,limit,offset));
    }
    public CouponPage.Item claimCoupon(long campaign,BooleanSupplier cancelled) throws IOException {
        JsonObject body=new JsonObject(); body.addProperty("campaignId",campaign);
        return request(cancelled,id -> Messages.authenticatedRequest(id,Messages.CLAIM_COUPON,token(),body),(value,id) -> {
            try {
                var coupon=CouponPage.parseItem(successful(value,id,Messages.CLAIM_COUPON+1000).body());
                if(coupon.campaignId()!=campaign || coupon.couponId()<=0)throw new IllegalArgumentException("Claim mismatch.");
                return coupon;
            } catch(RuntimeException ex) { throw new ProtocolException("Invalid claim response.",ex); }
        });
    }
    public QuoteSnapshot quote(long version,Long coupon,BooleanSupplier cancelled) throws IOException {
        return quote(version,coupon,null,cancelled);
    }
    public QuoteSnapshot quote(long version,Long coupon,java.util.List<Long> items,BooleanSupplier cancelled) throws IOException {
        JsonObject body=new JsonObject(); body.addProperty("cartVersion",version);
        if(items!=null)body.add("itemIds",new com.google.gson.Gson().toJsonTree(CartSelection.validate(items)));
        body.add("couponId",coupon==null?com.google.gson.JsonNull.INSTANCE:new com.google.gson.JsonPrimitive(coupon));
        return request(cancelled,id -> Messages.authenticatedRequest(id,Messages.QUOTE_CART,token(),body),
                (value,id) -> QuoteSnapshot.parse(successful(value,id,Messages.QUOTE_CART+1000).body(),version,coupon,items));
    }

    public CheckoutReceipt checkout(CheckoutRequest command,BooleanSupplier cancelled) throws IOException {
        var body=new com.google.gson.GsonBuilder().serializeNulls().create().toJsonTree(command).getAsJsonObject();
        return request(cancelled,id -> Messages.authenticatedRequest(id,Messages.CHECKOUT,token(),body),(value,id) -> CheckoutReceipt.parse(successful(value,id,Messages.CHECKOUT+1000).body(),command.checkoutKey()));
    }
    public CheckoutReceipt checkoutResult(String key,BooleanSupplier cancelled) throws IOException {
        JsonObject body=new JsonObject();body.addProperty("mode","BY_KEY");body.addProperty("checkoutKey",key);
        return request(cancelled,id -> Messages.authenticatedRequest(id,Messages.QUERY_ORDERS,token(),body),(value,id) -> CheckoutReceipt.parse(successful(value,id,Messages.QUERY_ORDERS+1000).body(),key));
    }
    public OrderPage listOrders(int limit,int offset,BooleanSupplier cancelled) throws IOException {
        JsonObject body=new JsonObject();body.addProperty("mode","LIST");body.addProperty("limit",limit);body.addProperty("offset",offset);
        return request(cancelled,id -> Messages.authenticatedRequest(id,Messages.QUERY_ORDERS,token(),body),(value,id) -> OrderPage.parse(successful(value,id,Messages.QUERY_ORDERS+1000).body(),limit,offset));
    }
    public OrderSnapshot order(long order,BooleanSupplier cancelled) throws IOException {
        JsonObject body=new JsonObject();body.addProperty("mode","DETAIL");body.addProperty("orderId",order);
        return request(cancelled,id -> Messages.authenticatedRequest(id,Messages.QUERY_ORDERS,token(),body),(value,id) -> {
            var result=OrderSnapshot.parse(successful(value,id,Messages.QUERY_ORDERS+1000).body());if(result.orderId()!=order)throw new ProtocolException("Wrong order ID.");return result;
        });
    }

    public CartSnapshot addToCart(long product,int quantity,long version,BooleanSupplier cancelled) throws IOException {
        JsonObject body=new JsonObject();
        body.addProperty("productId",product); body.addProperty("quantity",quantity); body.addProperty("cartVersion",version);
        return cartRequest(Messages.ADD_TO_CART,body,cancelled);
    }
    public AfterSaleReceipt applyReturn(ApplyReturnRequest command,BooleanSupplier cancelled) throws IOException {
        var body=new com.google.gson.Gson().toJsonTree(command).getAsJsonObject();return request(cancelled,id -> Messages.authenticatedRequest(id,Messages.APPLY_RETURN,token(),body),(value,id) -> {
            var receipt=AfterSaleReceipt.parse(successful(value,id,Messages.APPLY_RETURN+1000).body(),command.returnKey());if(receipt.application()!=null && receipt.application().orderId()!=command.orderId())throw new ProtocolException("Wrong application order.");return receipt;});
    }
    public AfterSaleReceipt returnResult(String key,BooleanSupplier cancelled) throws IOException {
        var body=new JsonObject();body.addProperty("mode","BY_KEY");body.addProperty("returnKey",key);return request(cancelled,id -> Messages.authenticatedRequest(id,Messages.QUERY_RETURNS,token(),body),(value,id) -> AfterSaleReceipt.parse(successful(value,id,Messages.QUERY_RETURNS+1000).body(),key));
    }
    public AfterSalePage returnHistory(long order,int limit,int offset,BooleanSupplier cancelled) throws IOException {
        var body=new JsonObject();body.addProperty("mode","HISTORY");body.addProperty("orderId",order);body.addProperty("limit",limit);body.addProperty("offset",offset);return request(cancelled,id -> Messages.authenticatedRequest(id,Messages.QUERY_RETURNS,token(),body),(value,id) -> AfterSalePage.parse(successful(value,id,Messages.QUERY_RETURNS+1000).body(),order,limit,offset));
    }

    public CartSnapshot editCart(long item,Integer quantity,long version,BooleanSupplier cancelled) throws IOException {
        JsonObject body=new JsonObject();
        body.addProperty("itemId",item); body.addProperty("action",quantity==null ? "REMOVE" : "SET");
        body.addProperty("cartVersion",version);
        if (quantity!=null) body.addProperty("quantity",quantity);
        return cartRequest(Messages.EDIT_CART,body,cancelled);
    }

    private CartSnapshot cartRequest(int operation,JsonObject body,BooleanSupplier cancelled) throws IOException {
        return request(cancelled,id -> Messages.authenticatedRequest(id,operation,token(),body),
                (value,id) -> CartSnapshot.parse(successful(value,id,operation+1000).body()));
    }

    public UserIdentity login(String username, char[] password, BooleanSupplier cancelled) throws IOException {
        final long attempt;
        synchronized (identityLock) {
            if (credentials != null) throw new IllegalStateException("Log out before switching accounts.");
            attempt = ++identityGeneration;
        }
        return request(cancelled, id -> Messages.login(id, username, password), (value, id) -> {
            Messages.Response response = successful(value, id, Messages.LOGIN + 1000);
            UserIdentity identity = UserIdentity.parse(response.body());
            if (!identity.username().equals(username) || identity.role() != UserIdentity.Role.BUYER
                    || response.sessionToken() == null || !response.sessionToken().matches("[A-Za-z0-9_-]{43}")) {
                throw new ProtocolException("Invalid login identity or token.");
            }
            synchronized (identityLock) {
                if (attempt != identityGeneration || closed.get() || cancelled.getAsBoolean()) {
                    throw new InterruptedIOException("Login was cancelled.");
                }
                credentials = new Credentials(identity, response.sessionToken());
            }
            return identity;
        });
    }

    public UserIdentity currentUser(BooleanSupplier cancelled) throws IOException {
        return request(cancelled, id -> Messages.authenticatedRequest(id, Messages.CURRENT_USER, token(), new JsonObject()),
                (value, id) -> UserIdentity.parse(successful(value, id, Messages.CURRENT_USER + 1000).body()));
    }

    public void logout(BooleanSupplier cancelled) throws IOException {
        String previous = token();
        clearLocalIdentity();
        if (previous == null) return;
        request(cancelled, id -> Messages.authenticatedRequest(id, Messages.LOGOUT, previous, new JsonObject()),
                (value, id) -> successful(value, id, Messages.LOGOUT + 1000));
    }

    public void clearLocalIdentity() {
        synchronized (identityLock) {
            identityGeneration++;
            credentials = null;
        }
    }

    private String token() {
        synchronized (identityLock) {
            return credentials == null ? null : credentials.token();
        }
    }

    private Messages.Response successful(JsonObject value, String id, int operation) throws IOException {
        Messages.Response response = Messages.validateResponse(value, id, operation);
        if (!"OK".equals(response.code())) {
            if ("UNAUTHENTICATED".equals(response.code())) clearLocalIdentity();
            throw new RemoteServiceException(response.code());
        }
        return response;
    }

    private <T> T request(BooleanSupplier cancelled, Function<String, JsonObject> createRequest,
            ReplyReader<T> readReply) throws IOException {
        synchronized (requestLock) {
            checkActive(cancelled);
            Socket socket = connection.get();
            if (socket == null || socket.isClosed()) {
                socket = new Socket();
                connection.set(socket);
            }
            try {
                checkActive(cancelled);
                if (!socket.isConnected()) {
                    socket.connect(new InetSocketAddress(address, port), connectTimeoutMillis);
                }
                String requestId = UUID.randomUUID().toString();
                FrameCodec.write(socket.getOutputStream(), createRequest.apply(requestId));
                var response = FrameCodec.read(socket, responseTimeoutMillis);
                if (response == null) {
                    throw new EOFException("Server disconnected.");
                }
                return readReply.read(response, requestId);
            } catch (IOException | RuntimeException exception) {
                connection.compareAndSet(socket, null);
                closeSocket(socket);
                throw exception;
            }
        }
    }

    private void checkActive(BooleanSupplier cancelled) throws IOException {
        if (closed.get()) {
            throw new IOException("Client is closed.");
        }
        if (cancelled.getAsBoolean()) {
            throw new InterruptedIOException("Request cancelled.");
        }
    }

    /** Must not acquire requestLock: closing the socket cancels a blocked read/connect. */
    public void disconnect() {
        synchronized (identityLock) {
            identityGeneration++;
        }
        closeSocket(connection.getAndSet(null));
    }

    @Override
    public void close() {
        closed.set(true);
        clearLocalIdentity();
        disconnect();
    }

    private static void closeSocket(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // The connection is already unusable and is never reused.
            }
        }
    }

    @FunctionalInterface
    private interface ReplyReader<T> {
        T read(JsonObject response, String requestId) throws IOException;
    }
}

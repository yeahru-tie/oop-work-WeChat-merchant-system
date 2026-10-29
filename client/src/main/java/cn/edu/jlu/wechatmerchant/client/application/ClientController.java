package cn.edu.jlu.wechatmerchant.client.application;

import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.client.transport.RemoteServiceException;
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
import cn.edu.jlu.wechatmerchant.protocol.AfterSalePage;
import cn.edu.jlu.wechatmerchant.protocol.AfterSaleReceipt;
import cn.edu.jlu.wechatmerchant.protocol.ProtocolException;
import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** All action methods and state callbacks run on the EDT; only close may run on any thread. */
public final class ClientController implements AutoCloseable {
    public static final int PAGE_SIZE = 20;

    public enum CatalogStatus { NOT_LOADED, LOADING, READY, EMPTY, ERROR, CANCELLED }
    public enum Action { NONE, PRODUCTS, PROBE, LOGIN, LOGOUT, CART_READ, CART_WRITE, COUPONS_READ, COUPON_CLAIM, QUOTE, CHECKOUT, RECONCILE, FINALIZE_CHECKOUT, ORDERS_READ, RETURN_WRITE, RETURN_QUERY, RETURN_FINALIZE, RETURN_HISTORY, MESSAGES }
    public enum CartStatus { GUEST, NOT_LOADED, LOADING, READY, ERROR, UNCERTAIN }
    public record CartState(CartStatus status, CartSnapshot contents, String message) { }
    public record CouponState(CouponPage page,QuoteSnapshot quote,Long selectedCoupon,boolean active,String message,long receivedNanos) {
        public boolean fresh() {
            return quote!=null && java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-receivedNanos)<quote.validUntil()-quote.quotedAt();
        }
    }
    private static CouponState emptyCoupons(){return new CouponState(null,null,null,false,"可打开领券/选券查看优惠券",0);}
    public record OrdersState(OrderPage page,OrderSnapshot detail,PendingCheckouts.Entry pending,QuoteSnapshot confirmation,
            java.util.List<Long> selectedItems,java.util.List<Long> knownItems,boolean storageBlocked,String message) {
        public OrdersState {if(selectedItems!=null)selectedItems=CartSelection.validate(selectedItems);knownItems=CartSelection.validate(knownItems);}
    }
    private static OrdersState emptyOrders(){return new OrdersState(null,null,null,null,null,java.util.List.of(),false,"尚无订单数据");}
    public record ReturnsState(AfterSalePage history,ApplyReturnRequest pending,boolean storageBlocked,String message){}
    private static ReturnsState emptyReturns(){return new ReturnsState(null,null,false,"选择订单查看售后记录");}
    public record InboxState(cn.edu.jlu.wechatmerchant.protocol.InboxPage page,String message){}
    private static InboxState emptyInbox(){return new InboxState(null,"登录后查看订单通知与优惠公告");}

    public record CatalogState(CatalogStatus status, ProductPage page, int targetOffset, String message) {
    }

    public record State(boolean busy, String connectionMessage, CatalogState catalog, UserIdentity identity,
            String authMessage, Action action, CartState cart,CouponState coupons,OrdersState orders,ReturnsState returns,InboxState inbox) {
        public State(boolean busy,String connectionMessage,CatalogState catalog,UserIdentity identity,String authMessage,Action action,CartState cart,CouponState coupons,OrdersState orders,ReturnsState returns){this(busy,connectionMessage,catalog,identity,authMessage,action,cart,coupons,orders,returns,emptyInbox());}
        public State(boolean busy,String connectionMessage,CatalogState catalog,UserIdentity identity,String authMessage,Action action,CartState cart,CouponState coupons,OrdersState orders){this(busy,connectionMessage,catalog,identity,authMessage,action,cart,coupons,orders,emptyReturns());}
        public State(boolean busy,String connectionMessage,CatalogState catalog,UserIdentity identity,String authMessage,Action action,CartState cart,CouponState coupons){this(busy,connectionMessage,catalog,identity,authMessage,action,cart,coupons,emptyOrders());}
        public State(boolean busy,String connectionMessage,CatalogState catalog,UserIdentity identity,String authMessage,Action action,CartState cart) {
            this(busy,connectionMessage,catalog,identity,authMessage,action,cart,emptyCoupons());
        }
        public State(boolean busy, String connectionMessage, CatalogState catalog, UserIdentity identity,
                String authMessage, Action action) {
            this(busy,connectionMessage,catalog,identity,authMessage,action,
                    identity==null ? guestCart() : unloadedCart());
        }
        public State(boolean busy, String connectionMessage, CatalogState catalog) {
            this(busy, connectionMessage, catalog, null, "尚未登录", Action.NONE);
        }
        public boolean canGoPrevious() {
            return !busy && catalog.targetOffset() > 0;
        }

        public boolean canGoNext() {
            return !busy && catalog.page() != null && catalog.page().hasMore()
                    && catalog.targetOffset() <= Integer.MAX_VALUE - PAGE_SIZE;
        }
        public boolean canModifyCart() {
            return !busy && identity!=null && cart.status()==CartStatus.READY && cart.contents()!=null && orders.pending()==null && !orders.storageBlocked();
        }
        public boolean canCancel(){return busy && action!=Action.FINALIZE_CHECKOUT && action!=Action.RETURN_FINALIZE;}
        public boolean canApplyReturn(){return !busy && identity!=null && returns.pending()==null && !returns.storageBlocked() && orders.detail()!=null && orders.detail().status().equals("COMPLETED") && returns.history()!=null && returns.history().orderId()==orders.detail().orderId();}
        public java.util.List<Long> selectedIds(){return cart.contents()==null?java.util.List.of():orders.selectedItems()==null?CartSelection.all(cart.contents()):orders.selectedItems().stream().filter(CartSelection.all(cart.contents())::contains).toList();}
        public CartSnapshot selectedCart(){return cart.contents()==null?null:CartSelection.select(cart.contents(),selectedIds());}
    }

    private final MerchantClient client;
    private final CheckoutJournal pendingStore;
    private final PendingReturns pendingReturns;
    private final Consumer<State> listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("client-network-", 0).factory());
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private long nextInboxCheck;
    private UserIdentity inboxOwner;
    private volatile State state = new State(false, "尚未测试连接",
            new CatalogState(CatalogStatus.NOT_LOADED, null, 0, "尚未加载商品"));

    public ClientController(MerchantClient client, Consumer<State> listener) {
        this(client,listener,new PendingCheckouts());
    }
    public ClientController(MerchantClient client,Consumer<State> listener,CheckoutJournal pendingStore){
        this(client,listener,pendingStore,new PendingReturns());
    }
    public ClientController(MerchantClient client,Consumer<State> listener,CheckoutJournal pendingStore,PendingReturns pendingReturns){
        this.client = client;
        this.listener = listener;
        this.pendingStore=pendingStore;
        this.pendingReturns=pendingReturns;
    }

    public State state() {
        requireEdt();
        return state;
    }

    public void refreshProducts() {
        requireEdt();
        loadProducts(state.catalog().targetOffset());
    }
    public void locateProduct(long product){
        requireEdt();if(closed.get() || state.busy() || product<=0)return;
        long attempt=generation.incrementAndGet();State before=state;
        publish(attempt,snapshot(before.connectionMessage(),new CatalogState(CatalogStatus.LOADING,null,0,"正在定位关联商品…"),before.authMessage(),Action.PRODUCTS));
        submit(attempt,() -> {CatalogState result;try{
            int offset=0;ProductPage page;boolean found=false;
            do{page=client.listProducts(PAGE_SIZE,offset,() -> closed.get() || generation.get()!=attempt);found=page.items().stream().anyMatch(p -> p.productId()==product);if(found || !page.hasMore() || !page.items().isEmpty() && page.items().getLast().productId()>product)break;offset=Math.addExact(offset,PAGE_SIZE);}while(offset<=20000);
            result=found?new CatalogState(CatalogStatus.READY,page,offset,"已定位商品 #"+product):new CatalogState(CatalogStatus.EMPTY,null,0,"关联商品当前不可查看，可刷新商品目录");
        }catch(IOException | RuntimeException ex){result=new CatalogState(CatalogStatus.ERROR,null,0,"关联商品读取失败，请刷新重试");}
        publish(attempt,snapshot(before.connectionMessage(),result,client.identity().isEmpty()?"请登录后查看私人内容":before.authMessage(),Action.NONE));});
    }

    public void previousPage() {
        requireEdt();
        if (state.canGoPrevious()) {
            loadProducts(Math.max(0, state.catalog().targetOffset() - PAGE_SIZE));
        }
    }

    public void nextPage() {
        requireEdt();
        if (state.canGoNext()) {
            loadProducts(state.catalog().targetOffset() + PAGE_SIZE);
        }
    }

    private void loadProducts(int offset) {
        if (closed.get() || state.busy()) {
            return;
        }
        long attempt = generation.incrementAndGet();
        String connectionMessage = state.connectionMessage();
        String authMessage = state.authMessage();
        publish(attempt, snapshot(connectionMessage,
                new CatalogState(CatalogStatus.LOADING, null, offset, "正在加载商品…"), authMessage, Action.PRODUCTS));
        submit(attempt, () -> {
            CatalogState result;
            String notice = authMessage;
            try {
                ProductPage page = client.listProducts(PAGE_SIZE, offset,
                        () -> closed.get() || generation.get() != attempt);
                boolean empty = page.items().isEmpty();
                String message = empty ? (offset == 0 ? "暂无商品" : "本页暂无商品，可返回上一页或刷新")
                        : "已加载本页 " + page.items().size() + " 件商品";
                result = new CatalogState(empty ? CatalogStatus.EMPTY : CatalogStatus.READY, page, offset, message);
            } catch (IOException | RuntimeException exception) {
                result = new CatalogState(CatalogStatus.ERROR, null, offset, productFailure(exception));
                if (exception instanceof RemoteServiceException remote && "UNAUTHENTICATED".equals(remote.code())) {
                    notice = "登录已失效，请重新登录";
                }
            }
            publish(attempt, snapshot(connectionMessage, result, notice, Action.NONE));
        });
    }

    public void testConnection() {
        requireEdt();
        if (closed.get() || state.busy()) {
            return;
        }
        long attempt = generation.incrementAndGet();
        CatalogState catalog = state.catalog();
        String authMessage = state.authMessage();
        publish(attempt, snapshot("正在测试连接…", catalog, authMessage, Action.PROBE));
        submit(attempt, () -> {
            String result;
            try {
                client.ping(() -> closed.get() || generation.get() != attempt);
                result = "通信测试通过";
            } catch (SocketTimeoutException exception) {
                result = "连接超时，请检查服务端后重试";
            } catch (ConnectException exception) {
                result = "无法连接，请先启动服务端";
            } catch (ProtocolException exception) {
                result = "服务端应答无效，请重新测试";
            } catch (IOException exception) {
                result = "连接已断开，请重新测试";
            } catch (RuntimeException exception) {
                result = "连接测试失败，请重试";
                System.err.println("Connection test failed: " + exception.getClass().getSimpleName());
            }
            publish(attempt, snapshot(result, catalog, authMessage, Action.NONE));
        });
    }

    public void login(String username, char[] password) {
        inboxOwner=null;nextInboxCheck=0;
        requireEdt();
        if (closed.get() || state.busy() || state.identity() != null) {
            Arrays.fill(password, '\0');
            return;
        }
        long attempt = generation.incrementAndGet();
        CatalogState catalog = state.catalog();
        String connection = state.connectionMessage();
        publish(attempt, snapshot(connection, catalog, "正在登录…", Action.LOGIN));
        submit(attempt, () -> {
            String message;
            OrdersState recovered=emptyOrders();ReturnsState recoveredReturns=emptyReturns();
            CartState cart=unloadedCart();
            try {
                UserIdentity user = client.login(username, password, () -> closed.get() || generation.get() != attempt);
                message = "已登录：" + user.displayName();
                try{var pending=pendingStore.first(user).orElse(null);if(pending!=null)recovered=new OrdersState(null,null,pending,null,pending.request().itemIds(),pending.knownItems(),false,"有一笔待核对成交，请先核对本次结果");}
                catch(IOException ex){recovered=new OrdersState(null,null,null,null,null,java.util.List.of(),true,"本地成交核对记录不可用，请保留记录并检查data/client-pending，暂不能新成交");}
                try{var pending=pendingReturns.first(user).orElse(null);if(pending!=null)recoveredReturns=new ReturnsState(null,pending,false,"有待核对售后申请，订单 #"+pending.orderId()+"；请核对或原键重试");}
                catch(IOException ex){recoveredReturns=new ReturnsState(null,null,true,"售后恢复记录不可用，请保留data/client-returns并检查，暂不能新申请");}
            } catch (IOException | RuntimeException exception) {
                message = loginFailure(exception);
            } finally {
                Arrays.fill(password, '\0');
            }
            State next=snapshot(connection,catalog,message,Action.NONE,cart);publish(attempt,next.identity()==null?next:withReturns(withOrders(next,recovered),recoveredReturns));
        });
    }

    public void logout() {
        requireEdt();
        if (closed.get() || state.busy() || state.identity() == null) return;
        long attempt = generation.incrementAndGet();
        CatalogState catalog = state.catalog();
        String connection = state.connectionMessage();
        // Hide all private content immediately, before the network worker can run.
        publish(attempt,new State(true,connection,catalog,null,"正在退出…",Action.LOGOUT,guestCart()));
        submit(attempt, () -> {
            String message;
            try {
                client.logout(() -> closed.get() || generation.get() != attempt);
                message = "已退出登录";
            } catch (IOException | RuntimeException exception) {
                message = "已在本机退出，服务端未确认；原会话将超时失效";
            }
            publish(attempt, snapshot(connection, catalog, message, Action.NONE));
        });
    }

    public void cancel() {
        requireEdt();
        if (closed.get() || !state.canCancel()) {
            return;
        }
        long attempt = generation.incrementAndGet();
        Action action = state.action();
        if (action == Action.LOGIN || action == Action.LOGOUT) client.clearLocalIdentity();
        client.disconnect();
        if(action==Action.RETURN_WRITE || action==Action.RETURN_QUERY){
            var old=state.returns();publish(attempt,withReturns(snapshot(state.connectionMessage(),state.catalog(),state.authMessage(),Action.NONE),new ReturnsState(old.history(),old.pending(),old.storageBlocked(),"已取消等待，售后申请结果待核对；请核对原请求，不要新建申请")));
        } else if(action==Action.RETURN_HISTORY){
            var old=state.returns();publish(attempt,withReturns(snapshot(state.connectionMessage(),state.catalog(),state.authMessage(),Action.NONE),new ReturnsState(null,old.pending(),old.storageBlocked(),"已取消售后记录加载，请刷新订单详情")));
        } else if(action==Action.CHECKOUT || action==Action.RECONCILE){
            var old=state.orders();publish(attempt,withOrders(snapshot(state.connectionMessage(),state.catalog(),state.authMessage(),Action.NONE),new OrdersState(old.page(),old.detail(),old.pending(),null,old.selectedItems(),old.knownItems(),old.storageBlocked(),"已取消等待，成交结果仍待核对；请核对本次，不要新建购买")));
        } else if(action==Action.ORDERS_READ){
            var old=state.orders();publish(attempt,withOrders(snapshot(state.connectionMessage(),state.catalog(),state.authMessage(),Action.NONE),new OrdersState(null,null,old.pending(),null,old.selectedItems(),old.knownItems(),old.storageBlocked(),"已取消订单加载，可刷新重试")));
        } else if (action==Action.CART_READ || action==Action.CART_WRITE) {
            publish(attempt,snapshot(state.connectionMessage(),state.catalog(),state.authMessage(),Action.NONE,
                    new CartState(action==Action.CART_WRITE ? CartStatus.UNCERTAIN : CartStatus.ERROR,null,
                            action==Action.CART_WRITE ? "操作结果未确认，请先刷新购物车核对，勿重复加入" : "已取消购物车加载，请刷新重试")));
        } else if(action==Action.COUPONS_READ || action==Action.COUPON_CLAIM || action==Action.QUOTE) {
            CouponState before=state.coupons();
            publish(attempt,withCoupons(snapshot(state.connectionMessage(),state.catalog(),state.authMessage(),Action.NONE),
                    new CouponState(null,null,before.selectedCoupon(),true,action==Action.COUPON_CLAIM?"领券结果未确认，请先刷新核对本人券，勿重复领取":"已取消，请刷新报价或重新选券",0)));
        } else if (state.catalog().status() == CatalogStatus.LOADING) {
            publish(attempt, snapshot(state.connectionMessage(), new CatalogState(CatalogStatus.CANCELLED,
                    null, state.catalog().targetOffset(), "已取消商品加载，可点击刷新重试"), state.authMessage(), Action.NONE));
        } else if (action == Action.LOGIN || action == Action.LOGOUT) {
            publish(attempt, snapshot(state.connectionMessage(), state.catalog(),
                    action == Action.LOGIN ? "已取消登录" : "已在本机退出，服务端未确认", Action.NONE));
        } else {
            publish(attempt, snapshot(action==Action.MESSAGES?"已取消消息检查，可刷新核对":"已取消连接测试", state.catalog(), state.authMessage(), Action.NONE));
        }
    }

    private State snapshot(String connection, CatalogState catalog, String authMessage, Action action) {
        UserIdentity identity=client.identity().orElse(null);
        CartState cart=identity!=null && identity.equals(state.identity()) ? state.cart() : unloadedCart();
        return snapshot(connection,catalog,authMessage,action,cart);
    }

    private State snapshot(String connection,CatalogState catalog,String authMessage,Action action,CartState cart) {
        UserIdentity identity=client.identity().orElse(null);
        CouponState coupons=identity!=null && identity.equals(state.identity())?state.coupons():emptyCoupons();
        if(cart.status()!=CartStatus.READY)coupons=new CouponState(null,null,coupons.selectedCoupon(),coupons.active(),"购物车更新后将重新报价",0);
        OrdersState orders=identity!=null && identity.equals(state.identity())?state.orders():emptyOrders();
        if(cart.contents()!=null)orders=reconcile(orders,cart.contents());
        if(action!=Action.NONE)orders=new OrdersState(orders.page(),orders.detail(),orders.pending(),null,orders.selectedItems(),orders.knownItems(),orders.storageBlocked(),orders.message());
        ReturnsState returns=identity!=null && identity.equals(state.identity())?state.returns():emptyReturns();
        InboxState inbox=identity!=null && identity.equals(state.identity())?state.inbox():emptyInbox();
        return new State(action!=Action.NONE,connection,catalog,identity,authMessage,action,identity==null ? guestCart() : cart,coupons,orders,returns,inbox);
    }

    public void refreshCart() {
        runCart(false,cancelled -> client.listCart(cancelled),"购物车已刷新");
    }

    public void addToCart(long product,int quantity) {
        requireEdt();
        if (!state.canModifyCart()) return;
        long version=state.cart().contents().cartVersion();
        runCart(true,true,cancelled -> verifiedWrite(client.addToCart(product,quantity,version,cancelled),version),"已加入购物车");
    }

    public void editCart(long item,Integer quantity) {
        requireEdt();
        if (!state.canModifyCart()) return;
        long version=state.cart().contents().cartVersion();
        runCart(true,cancelled -> verifiedWrite(client.editCart(item,quantity,version,cancelled),version),
                quantity==null ? "已移除条目" : "数量已保存");
    }

    private void runCart(boolean write,CartRequest request,String success) {
        runCart(write,false,request,success);
    }

    private void runCart(boolean write,boolean recoverRejectedAdd,CartRequest request,String success) {
        requireEdt();
        if (closed.get() || state.busy() || state.identity()==null) return;
        long attempt=generation.incrementAndGet();
        String connection=state.connectionMessage();
        String auth=state.authMessage();
        CatalogState catalog=state.catalog();
        CouponState previousCoupons=state.coupons();
        publish(attempt,snapshot(connection,catalog,auth,write ? Action.CART_WRITE : Action.CART_READ,
                new CartState(CartStatus.LOADING,null,write ? "正在保存购物车…" : "正在加载购物车…")));
        submit(attempt,() -> {
            CartState result;
            CouponState nextCoupons=new CouponState(null,null,previousCoupons.selectedCoupon(),previousCoupons.active(),"请刷新报价",0);
            String notice=auth;
            try {
                result=readyCart(request.run(() -> closed.get() || generation.get()!=attempt),success);
            } catch (IOException | RuntimeException exception) {
                if (recoverRejectedAdd && exception instanceof RemoteServiceException remote && "OUT_OF_STOCK".equals(remote.code())) {
                    // The server explicitly rejected the write. Refresh only; never replay the add.
                    publish(attempt,snapshot(connection,catalog,auth,Action.CART_READ,
                            new CartState(CartStatus.LOADING,null,"库存不足，本次未加入；正在更新购物车…")));
                    try {
                        result=readyCart(client.listCart(() -> closed.get() || generation.get()!=attempt),
                                "库存不足，本次未加入；请调整数量后再次点击加入购物车");
                    } catch (IOException | RuntimeException refreshFailure) {
                        result=new CartState(CartStatus.ERROR,null,
                                "本次因库存不足未加入；"+cartFailure(refreshFailure,false));
                    }
                } else {
                    result=new CartState(write && !(exception instanceof RemoteServiceException) ? CartStatus.UNCERTAIN : CartStatus.ERROR,
                            null,cartFailure(exception,write));
                }
                if (client.identity().isEmpty()) notice="登录已失效，请重新登录";
            }
            OrdersState selection=result.contents()==null?state.orders():reconcile(state.orders(),result.contents());
            if(result.status()==CartStatus.READY && previousCoupons.active() && selection.pending()==null && !selection.storageBlocked()) {
                try {
                    var read=readQuote(null,result.contents().cartVersion(),previousCoupons.selectedCoupon(),selection.selectedItems(),previousCoupons.quote(),() -> closed.get() || generation.get()!=attempt);
                    result=read.cartChanged()?new CartState(CartStatus.ERROR,null,"购物车已变化，请先刷新购物车核对"):readyCart(read.cart(),result.message());
                    nextCoupons=read.coupons();
                } catch(IOException | RuntimeException ex) {
                    nextCoupons=new CouponState(null,null,previousCoupons.selectedCoupon(),true,"购物车已核对，但报价未更新，请刷新报价或重新选券",0);
                    if(ex instanceof RemoteServiceException remote && "CART_CHANGED".equals(remote.code()))result=new CartState(CartStatus.ERROR,null,"购物车已变化，请先刷新购物车核对");
                }
            }
            State next=snapshot(connection,catalog,notice,Action.NONE,result);
            publish(attempt,next.identity()==null?next:withCoupons(next,nextCoupons));
        });
    }

    public void refreshCoupons(String kind,int offset) { runCoupon(Action.COUPONS_READ,kind,offset,state.coupons().selectedCoupon(),null); }
    public void claimCoupon(long campaign) { runCoupon(Action.COUPON_CLAIM,"OWNED",0,state.coupons().selectedCoupon(),campaign); }
    public void selectCoupon(Long coupon) { runCoupon(Action.QUOTE,"OWNED",0,coupon,null); }
    public void refreshQuote() { runCoupon(Action.QUOTE,"OWNED",0,state.coupons().selectedCoupon(),null); }
    /** Called by the application timer; never inserts a query ahead of an active/recovering write. */
    public void recheckQuoteIfExpired(){
        requireEdt();if(state.canModifyCart() && state.coupons().quote()!=null && !state.coupons().fresh())refreshQuote();
    }
    private void runCoupon(Action action,String kind,int offset,Long selection,Long campaign) {runCoupon(action,kind,offset,selection,campaign,false);}
    private void runCoupon(Action action,String kind,int offset,Long selection,Long campaign,boolean preparing) {
        requireEdt(); if(closed.get() || !state.canModifyCart())return;
        long attempt=generation.incrementAndGet(); State before=state;
        publish(attempt,withCoupons(snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),action),
                new CouponState(null,null,selection,true,"正在更新优惠券和报价…",0)));
        submit(attempt,() -> {
            CouponState coupons; CartState cart=before.cart();boolean autoCancelled=false;
            if(preparing){
                try{var pending=pendingStore.first(before.identity()).orElse(null);if(pending!=null){var old=before.orders();State next=snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.NONE);
                    publish(attempt,withOrders(next,new OrdersState(old.page(),old.detail(),pending,null,pending.request().itemIds(),pending.knownItems(),false,"发现待核对成交，请先核对本次结果")));return;}}
                catch(IOException ex){var old=before.orders();publish(attempt,withOrders(snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.NONE),new OrdersState(old.page(),old.detail(),null,null,old.selectedItems(),old.knownItems(),true,"本地核对记录不可用，未发送成交，请检查data/client-pending")));return;}
            }
            try {
                java.util.function.BooleanSupplier cancelled=() -> closed.get() || generation.get()!=attempt;
                if(campaign!=null)client.claimCoupon(campaign,cancelled);
                var page=client.listCoupons(kind,PAGE_SIZE,offset,before.selectedIds(),cancelled);
                var read=readQuote(page,cart.contents().cartVersion(),selection,before.selectedIds(),before.coupons().quote(),cancelled);
                cart=read.cartChanged()?new CartState(CartStatus.ERROR,null,"购物车已在其他窗口变化，请先刷新购物车"):readyCart(read.cart(),before.cart().message());
                coupons=read.coupons();autoCancelled=read.cancelledCoupon();
                if(campaign!=null)coupons=new CouponState(coupons.page(),coupons.quote(),coupons.selectedCoupon(),true,"领取已核对，请手动选择要使用的券；"+coupons.message(),coupons.receivedNanos());
            } catch(IOException | RuntimeException ex) {
                coupons=new CouponState(null,null,selection,true,couponFailure(ex,action==Action.COUPON_CLAIM),0);
                if(ex instanceof RemoteServiceException remote && "CART_CHANGED".equals(remote.code()))cart=new CartState(CartStatus.ERROR,null,"购物车已在其他窗口变化，请先刷新购物车");
            }
            State next=snapshot(before.connectionMessage(),before.catalog(),client.identity().isEmpty()?"登录已失效，请重新登录":before.authMessage(),Action.NONE,cart);
            if(next.identity()!=null){next=withCoupons(next,coupons);if(preparing && !autoCancelled && coupons.quote()!=null && coupons.quote().ready() && !coupons.quote().itemIds().isEmpty()){
                var old=next.orders();next=withOrders(next,new OrdersState(old.page(),old.detail(),old.pending(),coupons.quote(),old.selectedItems(),old.knownItems(),old.storageBlocked(),"请核对商品和金额后，二次确认模拟成交"));}}
            publish(attempt,next);
        });
    }
    private record CouponRead(CouponState coupons,CartSnapshot cart,boolean cartChanged,boolean cancelledCoupon){}
    private CouponRead readQuote(CouponPage page,long version,Long selection,java.util.List<Long> items,QuoteSnapshot previous,java.util.function.BooleanSupplier cancelled) throws IOException {
        long sent=System.nanoTime();var quote=client.quote(version,selection,items,cancelled);
        if(selection==null || quote.ready())return new CouponRead(new CouponState(page,quote,selection,true,quoteMessage(previous,quote),sent),quote.cart(),false,false);
        String notice="优惠券已自动取消（"+couponReason(quote.couponStatus())+"），已改为不用券；";
        try{
            sent=System.nanoTime();var without=client.quote(version,null,items,cancelled);
            String amount=without.ready()?"新预估应付 "+money(without.payableCents())+"，成交前请重新确认":without.itemIds().isEmpty()?"请先勾选要结算的商品":"请刷新购物车核对";
            return new CouponRead(new CouponState(page,without,null,true,notice+amount,sent),without.cart(),false,true);
        }catch(IOException | RuntimeException ex){
            boolean changed=ex instanceof RemoteServiceException remote && "CART_CHANGED".equals(remote.code());
            return new CouponRead(new CouponState(page,null,null,true,notice+"无券报价尚未取得，请刷新核对，旧应付已清除",0),quote.cart(),changed,true);
        }
    }
    private static State withCoupons(State state,CouponState coupons) {
        return new State(state.busy(),state.connectionMessage(),state.catalog(),state.identity(),state.authMessage(),state.action(),state.cart(),coupons,state.orders(),state.returns(),state.inbox());
    }
    private static State withOrders(State s,OrdersState orders){return new State(s.busy(),s.connectionMessage(),s.catalog(),s.identity(),s.authMessage(),s.action(),s.cart(),s.coupons(),orders,s.returns(),s.inbox());}
    private static State withReturns(State s,ReturnsState returns){return new State(s.busy(),s.connectionMessage(),s.catalog(),s.identity(),s.authMessage(),s.action(),s.cart(),s.coupons(),s.orders(),returns,s.inbox());}
    private static State withInbox(State s,InboxState inbox){return new State(s.busy(),s.connectionMessage(),s.catalog(),s.identity(),s.authMessage(),s.action(),s.cart(),s.coupons(),s.orders(),s.returns(),inbox);}

    /** Called on the EDT timer; never queues behind a transaction or an open confirmation. */
    public void pollMessagesIfDue(){
        requireEdt();if(closed.get() || state.identity()==null)return;
        if(!state.identity().equals(inboxOwner)){inboxOwner=state.identity();nextInboxCheck=0;}
        if(state.busy() || state.orders().confirmation()!=null || state.orders().pending()!=null || state.returns().pending()!=null || System.nanoTime()<nextInboxCheck)return;
        refreshMessages(state.inbox().page()==null?0:state.inbox().page().offset(),null);
    }
    public void refreshMessages(int offset,Long readId){
        requireEdt();if(closed.get() || state.busy() || state.identity()==null || state.orders().confirmation()!=null)return;
        State before=state;long attempt=generation.incrementAndGet();nextInboxCheck=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        publish(attempt,withInbox(snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.MESSAGES),new InboxState(before.inbox().page(),readId==null?"正在检查站内消息…":"正在标记已读…")));
        submit(attempt,() -> {InboxState result;try{var page=client.messages(PAGE_SIZE,offset,readId,() -> closed.get() || generation.get()!=attempt);result=new InboxState(page,page.items().isEmpty()?"本页暂无消息":"未读 "+page.unreadCount()+" 条 · 自动检查间隔15秒");}
            catch(IOException | RuntimeException ex){result=new InboxState(before.inbox().page(),"消息检查未完成，可刷新核对；原已读状态以服务端为准");}
            State next=snapshot(before.connectionMessage(),before.catalog(),client.identity().isEmpty()?"登录已失效，请重新登录":before.authMessage(),Action.NONE);publish(attempt,next.identity()==null?next:withInbox(next,result));});
    }
    private static OrderPage updateOrderPage(OrderPage page,OrderSnapshot detail){if(page==null || detail==null)return page;return new OrderPage(page.items().stream().map(row -> row.orderId()==detail.orderId()?new OrderPage.Summary(row.orderId(),row.createdAt(),detail.status(),row.itemCount(),row.paidCents()):row).toList(),page.limit(),page.offset(),page.hasMore());}
    private static OrdersState reconcile(OrdersState old,CartSnapshot cart){
        var all=CartSelection.all(cart);java.util.List<Long> selected;
        if(old.selectedItems()==null)selected=all;
        else {var chosen=new java.util.TreeSet<>(old.selectedItems());chosen.retainAll(all);for(long id:all)if(!old.knownItems().contains(id))chosen.add(id);selected=java.util.List.copyOf(chosen);}
        return new OrdersState(old.page(),old.detail(),old.pending(),old.confirmation(),selected,all,old.storageBlocked(),old.message());
    }
    public void selectCartItem(long item,boolean selected){
        requireEdt();if(!state.canModifyCart() || !CartSelection.all(state.cart().contents()).contains(item))return;
        var ids=new java.util.TreeSet<>(state.selectedIds());if(selected)ids.add(item);else ids.remove(item);
        var old=state.orders();State next=withOrders(state,new OrdersState(old.page(),old.detail(),old.pending(),null,java.util.List.copyOf(ids),CartSelection.all(state.cart().contents()),false,old.message()));
        next=withCoupons(next,new CouponState(null,null,state.coupons().selectedCoupon(),true,"已变更勾选，正在重新报价",0));publish(generation.get(),next);
        runCoupon(Action.QUOTE,"OWNED",0,state.coupons().selectedCoupon(),null);
    }
    public void prepareCheckout(){
        requireEdt();if(!state.canModifyCart())return;
        if(state.selectedIds().isEmpty()){var old=state.orders();publish(generation.get(),withOrders(state,new OrdersState(old.page(),old.detail(),old.pending(),null,old.selectedItems(),old.knownItems(),old.storageBlocked(),"请先勾选要结算的商品")));return;}
        runCoupon(Action.QUOTE,"OWNED",0,state.coupons().selectedCoupon(),null,true);
    }
    public void cancelConfirmation(){
        requireEdt();if(state.busy())return;var old=state.orders();if(old.confirmation()==null)return;
        publish(generation.get(),withOrders(state,new OrdersState(old.page(),old.detail(),old.pending(),null,old.selectedItems(),old.knownItems(),old.storageBlocked(),"已取消确认，尚未发送成交请求")));
    }
    public void confirmCheckout(){
        requireEdt();var quote=state.orders().confirmation();if(!state.canModifyCart() || quote==null || !state.coupons().fresh() || quote!=state.coupons().quote())return;
        var request=new CheckoutRequest(java.util.UUID.randomUUID().toString(),quote.cart().cartVersion(),quote.digest(),quote.couponId(),quote.itemIds());
        transact(new PendingCheckouts.Entry(request,CartSelection.all(quote.cart())),true,false);
    }
    public void queryPending(){requireEdt();if(state.orders().pending()!=null)transact(state.orders().pending(),false,true);}
    public void retryPending(){requireEdt();if(state.orders().pending()!=null)transact(state.orders().pending(),false,false);}
    private void transact(PendingCheckouts.Entry entry,boolean initial,boolean query){
        requireEdt();if(closed.get() || state.busy() || state.identity()==null)return;
        State before=state;long attempt=generation.incrementAndGet();var old=before.orders();
        publish(attempt,withOrders(snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),query?Action.RECONCILE:Action.CHECKOUT),
                new OrdersState(old.page(),old.detail(),entry,null,old.selectedItems(),old.knownItems(),false,query?"正在核对本次成交结果…":"正在提交模拟成交，请勿重复购买…")));
        submit(attempt,() -> {
            boolean sent=!initial;OrderSnapshot known=null;
            try{
                if(initial)pendingStore.save(before.identity(),entry);
                sent=true;java.util.function.BooleanSupplier cancelled=() -> closed.get() || generation.get()!=attempt;
                CheckoutReceipt receipt=query?client.checkoutResult(entry.request().checkoutKey(),cancelled):client.checkout(entry.request(),cancelled);
                if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException("Cancelled checkout result.");
                known=receipt.order();
                if(receipt.state().equals("SUCCESS") || receipt.state().equals("FAILED")){
                    acceptTerminal(attempt,before,entry,receipt);return;
                }
                String message=receipt.state().equals("NOT_FOUND")?"暂未查到本次结果，不能据此认定未成交；可继续核对或以原防重键重试本次请求":"本次结果仍待核对，请勿新建购买";
                publish(attempt,withOrders(snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.NONE),new OrdersState(old.page(),old.detail(),entry,null,old.selectedItems(),old.knownItems(),false,message)));
            }catch(IOException | RuntimeException ex){
                var next=snapshot(before.connectionMessage(),before.catalog(),client.identity().isEmpty()?"登录已失效，请重新登录后核对":before.authMessage(),Action.NONE);
                if(next.identity()==null){publish(attempt,next);return;}
                String message=!sent?"本地恢复记录保存失败，未发送成交；请检查data/client-pending后重新登录核对":known!=null?"订单 #"+known.orderId()+" 已成交，但本地核对记录未清理，请继续核对，勿重复购买":"成交结果未确认，请核对本次或原键重试，勿新建购买";
                publish(attempt,withOrders(next,new OrdersState(old.page(),known!=null?known:old.detail(),sent?entry:null,null,old.selectedItems(),old.knownItems(),!sent,message)));
            }
        });
    }
    /** A cancelled/closed generation must never delete a result it has not delivered to its owner. */
    private void acceptTerminal(long attempt,State before,PendingCheckouts.Entry entry,CheckoutReceipt receipt){
        SwingUtilities.invokeLater(() -> {
            if(closed.get() || generation.get()!=attempt || !before.identity().equals(client.identity().orElse(null)))return;
            var old=before.orders();String message=terminalMessage(receipt);
            var accepted=withOrders(snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.FINALIZE_CHECKOUT,new CartState(CartStatus.NOT_LOADED,null,message)),
                    new OrdersState(null,receipt.order()!=null?receipt.order():old.detail(),entry,null,old.selectedItems(),old.knownItems(),false,message+"；正在后台整理本地核对记录"));
            publish(attempt,withCoupons(accepted,new CouponState(null,null,receipt.order()!=null?null:before.coupons().selectedCoupon(),true,message,0)));
            // Queued only after the EDT has accepted the terminal result. No filesystem work on EDT.
            submit(attempt,() -> finishTerminal(attempt,before,entry,receipt));
        });
    }
    private void finishTerminal(long attempt,State before,PendingCheckouts.Entry entry,CheckoutReceipt receipt){
        if(closed.get() || generation.get()!=attempt)return;
        var old=before.orders();String message=terminalMessage(receipt);boolean removed=false;
        try{
            pendingStore.remove(before.identity(),entry);removed=true;var other=pendingStore.first(before.identity()).orElse(null);
            var selected=new java.util.TreeSet<>(before.selectedIds());if(receipt.order()!=null)selected.removeAll(entry.request().itemIds());
            var orders=new OrdersState(null,receipt.order()!=null?receipt.order():old.detail(),other,null,other!=null?other.request().itemIds():java.util.List.copyOf(selected),other!=null?other.knownItems():old.knownItems(),false,message+(other!=null?"；另有待核对成交":""));
            var next=snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.NONE,new CartState(CartStatus.NOT_LOADED,null,message));
            if(next.identity()==null){publish(attempt,next);return;}
            next=withOrders(next,orders);next=withCoupons(next,new CouponState(null,null,receipt.order()!=null?null:before.coupons().selectedCoupon(),true,message,0));publishAndRefresh(attempt,next);
        }catch(IOException | RuntimeException ex){
            var next=snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.NONE);
            if(next.identity()!=null)next=withOrders(next,new OrdersState(null,receipt.order()!=null?receipt.order():old.detail(),removed?null:entry,null,old.selectedItems(),old.knownItems(),removed,
                    message+(removed?"；其他本地核对记录不可验证，请保留文件并检查后重新登录":"；本地记录未清理，请继续核对，勿重复购买")));
            publish(attempt,next);
        }
    }
    private static String terminalMessage(CheckoutReceipt receipt){if(receipt.order()!=null && !receipt.order().status().equals("COMPLETED"))return "原成交订单 #"+receipt.order().orderId()+" 已核对，当前"+(receipt.order().status().equals("REFUNDED")?"已退款（模拟）":"退货处理中")+"，没有重复购买";return receipt.order()!=null?"模拟成交成功，订单 #"+receipt.order().orderId()+"，可在我的订单查看":"本次未成交："+checkoutReason(receipt.failureCode())+"；请刷新并重新确认";}
    private void publishAndRefresh(long attempt,State next){
        publish(attempt,next);SwingUtilities.invokeLater(() -> {if(!closed.get() && generation.get()==attempt && state==next && next.identity()!=null)refreshCart();});
    }
    public void refreshOrders(int offset){loadOrders(offset,null);}
    public void loadOrder(long id){loadOrders(0,id);}
    public void loadReturnHistory(int offset){
        requireEdt();if(state.orders().detail()==null || state.busy() || state.identity()==null || closed.get())return;long id=state.orders().detail().orderId(),attempt=generation.incrementAndGet();State before=state;
        publish(attempt,withReturns(snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.RETURN_HISTORY),new ReturnsState(null,before.returns().pending(),before.returns().storageBlocked(),"正在加载售后历史…")));
        submit(attempt,() -> {AfterSalePage page=null;OrderSnapshot detail=null;String message;
            try{detail=client.order(id,() -> closed.get() || generation.get()!=attempt);page=client.returnHistory(id,PAGE_SIZE,offset,() -> closed.get() || generation.get()!=attempt);message="售后历史已加载，申请/批准均不等于已退款";}
            catch(IOException | RuntimeException ex){message="售后记录加载失败，请刷新订单详情";}
            var next=snapshot(before.connectionMessage(),before.catalog(),client.identity().isEmpty()?"登录已失效，请重新登录":before.authMessage(),Action.NONE);
            if(next.identity()!=null){var old=next.orders();next=withOrders(next,new OrdersState(updateOrderPage(old.page(),detail),detail,old.pending(),null,old.selectedItems(),old.knownItems(),old.storageBlocked(),old.message()));next=withReturns(next,new ReturnsState(page,before.returns().pending(),before.returns().storageBlocked(),message));}publish(attempt,next);
        });
    }
    private void loadOrders(int offset,Long id){
        requireEdt();if(closed.get() || state.busy() || state.identity()==null)return;
        State before=state;long attempt=generation.incrementAndGet();var old=before.orders();
        publish(attempt,withOrders(snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.ORDERS_READ),new OrdersState(id==null?null:old.page(),null,old.pending(),null,old.selectedItems(),old.knownItems(),old.storageBlocked(),"正在加载本人订单…")));
        submit(attempt,() -> {
            OrderPage page=id==null?null:old.page();OrderSnapshot detail=null;AfterSalePage history=null;String message,returnMessage="选择订单查看售后记录";
            try{if(id==null)page=client.listOrders(PAGE_SIZE,offset,() -> closed.get() || generation.get()!=attempt);else {detail=client.order(id,() -> closed.get() || generation.get()!=attempt);try{history=client.returnHistory(id,PAGE_SIZE,0,() -> closed.get() || generation.get()!=attempt);returnMessage="售后历史已加载；只有管理员明确完成才退款/补库存";}catch(IOException | RuntimeException ex){returnMessage="售后历史加载失败，请刷新订单详情";}}message=id==null?(page.items().isEmpty()?"本页暂无订单":"本人订单已加载"):"历史成交明细已加载";}
            catch(IOException | RuntimeException ex){message="订单加载失败，请刷新重试";}
            var next=snapshot(before.connectionMessage(),before.catalog(),client.identity().isEmpty()?"登录已失效，请重新登录":before.authMessage(),Action.NONE);
            publish(attempt,next.identity()==null?next:withReturns(withOrders(next,new OrdersState(updateOrderPage(page,detail),detail,old.pending(),null,old.selectedItems(),old.knownItems(),old.storageBlocked(),message)),new ReturnsState(history,before.returns().pending(),before.returns().storageBlocked(),before.returns().pending()!=null?before.returns().message():returnMessage)));
        });
    }
    public void applyReturn(long order,String reason){
        requireEdt();if(!state.canApplyReturn() || state.orders().detail().orderId()!=order)return;
        if(!ApplyReturnRequest.validText(reason)){publish(generation.get(),withReturns(state,new ReturnsState(state.returns().history(),null,false,"请填写1—500字的申请原因")));return;}
        runReturn(new ApplyReturnRequest(java.util.UUID.randomUUID().toString(),order,reason.strip()),true,false);
    }
    public void queryPendingReturn(){requireEdt();if(state.returns().pending()!=null)runReturn(state.returns().pending(),false,true);}
    public void retryPendingReturn(){requireEdt();if(state.returns().pending()!=null)runReturn(state.returns().pending(),false,false);}
    private void runReturn(ApplyReturnRequest request,boolean initial,boolean query){
        if(closed.get() || state.busy() || state.identity()==null)return;State before=state;long attempt=generation.incrementAndGet();
        publish(attempt,withReturns(snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),query?Action.RETURN_QUERY:Action.RETURN_WRITE),new ReturnsState(before.returns().history(),request,false,query?"正在核对原售后申请…":"正在提交整单退货申请，尚未退款…")));
        submit(attempt,() -> {boolean sent=!initial;
            try{
                if(initial){var old=pendingReturns.first(before.identity()).orElse(null);if(old!=null){publish(attempt,withReturns(snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.NONE),new ReturnsState(null,old,false,"发现待核对申请，请先核对原请求")));return;}pendingReturns.save(before.identity(),request);}
                sent=true;var receipt=query?client.returnResult(request.returnKey(),() -> closed.get() || generation.get()!=attempt):client.applyReturn(request,() -> closed.get() || generation.get()!=attempt);
                if(receipt.application()!=null && receipt.application().orderId()!=request.orderId())throw new ProtocolException("Wrong return owner/order.");
                if(receipt.state().equals("SUCCESS") || receipt.state().equals("FAILED")){acceptReturnTerminal(attempt,before,request,receipt);return;}
                publish(attempt,withReturns(snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.NONE),new ReturnsState(before.returns().history(),request,false,"售后结果待核对；暂未查到不能认定未提交，可继续核对或原键重试")));
            }catch(IOException | RuntimeException ex){var next=snapshot(before.connectionMessage(),before.catalog(),client.identity().isEmpty()?"登录已失效，请重登核对售后":before.authMessage(),Action.NONE);if(next.identity()!=null)next=withReturns(next,new ReturnsState(before.returns().history(),sent?request:null,!sent,sent?"申请结果未确认，请核对或原键重试，不要新建申请":"本地售后记录不可用，未发送申请；请保留记录并检查后重新登录"));publish(attempt,next);}
        });
    }
    private void acceptReturnTerminal(long attempt,State before,ApplyReturnRequest request,AfterSaleReceipt receipt){
        SwingUtilities.invokeLater(() -> {
            if(closed.get() || generation.get()!=attempt || !before.identity().equals(client.identity().orElse(null)))return;
            String message=receipt.application()!=null?"申请已核对，售后 #"+receipt.application().afterSaleId()+"；"+switch(receipt.application().status()){case "APPROVED" -> "已批准，待明确完成，尚未退款";case "REJECTED" -> "已拒绝，说明见历史，可重新申请";case "COMPLETED" -> "退货已完成，已模拟退款";default -> "待管理员审核，尚未退款";}:"本次未受理："+returnFailure(receipt.failureCode());
            publish(attempt,withReturns(snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.RETURN_FINALIZE),new ReturnsState(null,request,false,message+"；正在后台整理本地记录")));
            submit(attempt,() -> {if(closed.get() || generation.get()!=attempt)return;boolean removed=false;
                try{pendingReturns.remove(before.identity(),request);removed=true;var other=pendingReturns.first(before.identity()).orElse(null);OrderSnapshot detail=null;AfterSalePage history=null;String notice=message;
                    try{detail=client.order(request.orderId(),() -> closed.get() || generation.get()!=attempt);history=client.returnHistory(request.orderId(),PAGE_SIZE,0,() -> closed.get() || generation.get()!=attempt);}catch(IOException | RuntimeException ex){notice+="；订单/售后详情刷新失败，请重载";}
                    var next=snapshot(before.connectionMessage(),before.catalog(),client.identity().isEmpty()?"登录已失效，请重新登录":before.authMessage(),Action.NONE);if(next.identity()!=null){var old=next.orders();next=withOrders(next,new OrdersState(updateOrderPage(old.page(),detail),detail,old.pending(),null,old.selectedItems(),old.knownItems(),old.storageBlocked(),"售后结果已核对"));next=withReturns(next,new ReturnsState(history,other,false,notice+(other!=null?"；另有待核对申请":"")));}publish(attempt,next);
                }catch(IOException | RuntimeException ex){var next=snapshot(before.connectionMessage(),before.catalog(),before.authMessage(),Action.NONE);if(next.identity()!=null)next=withReturns(next,new ReturnsState(null,removed?null:request,removed,message+"；本地记录未能完整清理，请保留文件并核对，勿重复申请"));publish(attempt,next);}
            });
        });
    }
    private static String returnFailure(String code){return switch(code){case "NOT_FOUND" -> "本人订单不存在";case "STATE_CHANGED" -> "订单已有处理中售后或已退款，请刷新历史";case "IDEMPOTENCY_CONFLICT" -> "原申请参数不一致，请保留记录核查";default -> "售后未能受理，请刷新核对";};}
    public static String checkoutReason(String code){return switch(code){case "PRICE_CHANGED" -> "报价或商品活动已变化";case "COUPON_INVALID" -> "所选券已不可用";case "CART_CHANGED" -> "购物车或勾选条目已变化";case "OUT_OF_STOCK" -> "所选商品库存不足";case "EMPTY_SELECTION" -> "尚未勾选商品";default -> "交易未能完成";};}
    private static String quoteMessage(QuoteSnapshot old,QuoteSnapshot quote) {
        if(quote.couponStatus().equals("EMPTY_SELECTION"))return "请先勾选要结算的商品";
        if(!quote.ready())return "报价暂不可用（"+couponReason(quote.couponStatus())+"），请刷新核对";
        if(old!=null && !old.digest().equals(quote.digest()))return "报价已更新："+(old.payableCents()==null?"待选券":money(old.payableCents()))+" → "+money(quote.payableCents())+"；预估不锁价";
        return quote.couponId()==null?"已明确不用券，报价已更新；预估不锁价":"用券报价已更新；预估不锁价";
    }
    private static String money(java.math.BigInteger cents){return "¥ "+new java.math.BigDecimal(cents,2).toPlainString();}
    public static String couponReason(String status) {
        return switch(status) {
            case "CAN_CLAIM" -> "可领取"; case "ALREADY_CLAIMED" -> "已领取"; case "PAUSED" -> "暂停领取";
            case "NOT_STARTED" -> "未到有效期"; case "EXPIRED" -> "已过期"; case "SOLD_OUT" -> "已领完";
            case "USABLE","APPLIED" -> "可使用"; case "THRESHOLD_NOT_MET" -> "未达到商品优惠后的门槛";
            case "USED" -> "已使用"; case "EMPTY_CART" -> "购物车为空";case "EMPTY_SELECTION" -> "未勾选商品"; default -> "券不存在或不属于当前买家";
        };
    }
    private static String couponFailure(Exception ex,boolean claim) {
        if(ex instanceof RemoteServiceException remote) {
            return switch(remote.code()) {
                case "UNAUTHENTICATED" -> "登录已失效，请重新登录";
                case "FORBIDDEN" -> "当前身份不能使用买家优惠券";
                case "CART_CHANGED" -> "购物车已变化，请先刷新购物车";
                case "PAUSED","NOT_STARTED","EXPIRED","SOLD_OUT" -> couponReason(remote.code())+"，请刷新后重新选择";
                default -> "优惠券操作未完成，请刷新核对";
            };
        }
        return claim?"领券结果未确认，请先刷新核对本人券，勿重复领取":"报价或优惠券加载失败，请刷新核对；旧报价已清除";
    }

    private static CartSnapshot verifiedWrite(CartSnapshot value,long version) throws ProtocolException {
        if (version==Long.MAX_VALUE || value.cartVersion()!=version+1) throw new ProtocolException("Unexpected cart version.");
        return value;
    }

    private static CartState readyCart(CartSnapshot contents,String message) {
        return new CartState(CartStatus.READY,contents,contents.items().isEmpty() ? message+"，购物车为空" : message);
    }
    private static CartState guestCart() { return new CartState(CartStatus.GUEST,null,"登录后查看和保存自己的购物车"); }
    private static CartState unloadedCart() { return new CartState(CartStatus.NOT_LOADED,null,"请刷新加载本人购物车"); }
    private static String cartFailure(Exception exception,boolean write) {
        if (exception instanceof RemoteServiceException remote) {
            return switch(remote.code()) {
                case "UNAUTHENTICATED" -> "登录已失效，请重新登录";
                case "FORBIDDEN" -> "当前身份不能使用买家购物车";
                case "OUT_OF_STOCK" -> "库存不足，请刷新商品和购物车后调整数量";
                case "NOT_FOUND" -> "商品或购物车条目不存在，请刷新核对";
                case "CART_CHANGED" -> "购物车已在其他窗口变化，请刷新后再操作";
                case "CART_LIMIT" -> "购物车最多保存 100 种商品，请刷新后移除部分条目";
                case "INVALID_ARGUMENT" -> "数量或购物车参数无效，请刷新后调整";
                default -> "购物车服务暂不可用，请先刷新核对";
            };
        }
        return write ? "操作结果未确认，请先刷新购物车核对，勿重复加入" : "购物车加载失败，请检查连接后刷新重试";
    }

    @FunctionalInterface private interface CartRequest {
        CartSnapshot run(java.util.function.BooleanSupplier cancelled) throws IOException;
    }

    private void submit(long attempt, Runnable task) {
        try {
            worker.submit(task);
        } catch (RejectedExecutionException exception) {
            // Only shutdown rejects work; closed/generation guards suppress any later callback.
            if (!closed.get()) {
                publish(attempt, new State(false, "网络任务已结束", new CatalogState(CatalogStatus.ERROR,
                        null, state.catalog().targetOffset(), "商品加载已结束，请重新打开客户端")));
            }
        }
    }

    private void publish(long attempt, State next) {
        Runnable update = () -> {
            if (!closed.get() && generation.get() == attempt) {
                state = next;
                listener.accept(next);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private static String productFailure(Exception exception) {
        if (exception instanceof RemoteServiceException rejected) {
            return switch (rejected.code()) {
                case "OPERATION_UNSUPPORTED", "VERSION_UNSUPPORTED" -> "服务端版本不支持商品查询，请检查版本后重试";
                case "INVALID_ARGUMENT" -> "商品查询被拒绝，请点击刷新重试";
                case "UNAUTHENTICATED" -> "登录已失效，请重新登录，或刷新以游客身份浏览";
                default -> "商品服务暂不可用，请稍后刷新重试";
            };
        }
        if (exception instanceof SocketTimeoutException) {
            return "商品查询超时，请检查服务端后刷新重试";
        }
        if (exception instanceof ConnectException) {
            return "无法连接服务端，请启动后点击刷新重试";
        }
        if (exception instanceof ProtocolException) {
            return "商品应答无效，请检查服务端版本后重试";
        }
        if (exception instanceof IOException) {
            return "连接已断开，请点击刷新重试";
        }
        System.err.println("Product load failed: " + exception.getClass().getSimpleName());
        return "商品加载失败，请点击刷新重试";
    }

    private static String loginFailure(Exception exception) {
        if (exception instanceof RemoteServiceException remote) {
            return switch (remote.code()) {
                case "INVALID_CREDENTIALS" -> "账号或密码不正确";
                case "BUSY_RETRY" -> "登录服务繁忙，请稍后重试";
                case "OPERATION_UNSUPPORTED", "VERSION_UNSUPPORTED" -> "服务端版本不支持登录";
                default -> "登录服务暂不可用，请稍后重试";
            };
        }
        if (exception instanceof SocketTimeoutException) return "登录超时，请手动重试";
        if (exception instanceof ConnectException) return "无法连接，请先启动服务端";
        if (exception instanceof ProtocolException) return "登录应答无效，请检查服务端版本";
        return "登录未完成，请检查连接后重试";
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Client actions must run on the EDT.");
        }
    }

    @Override
    public void close() {
        closed.set(true);
        generation.incrementAndGet();
        client.close();
        worker.shutdownNow();
    }
}

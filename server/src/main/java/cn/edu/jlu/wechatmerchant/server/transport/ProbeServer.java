package cn.edu.jlu.wechatmerchant.server.transport;

import cn.edu.jlu.wechatmerchant.protocol.FrameCodec;
import cn.edu.jlu.wechatmerchant.protocol.Messages;
import cn.edu.jlu.wechatmerchant.protocol.NetworkDefaults;
import cn.edu.jlu.wechatmerchant.server.application.ProductQueryHandler;
import cn.edu.jlu.wechatmerchant.server.application.IdentityHandler;
import cn.edu.jlu.wechatmerchant.server.application.CartHandler;
import cn.edu.jlu.wechatmerchant.server.application.CouponHandler;
import cn.edu.jlu.wechatmerchant.server.application.OrderHandler;
import cn.edu.jlu.wechatmerchant.server.application.AfterSaleHandler;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class ProbeServer implements AutoCloseable {
    private final int requestedPort;
    private final int frameTimeoutMillis;
    private final ProductQueryHandler products;
    private final IdentityHandler identities;
    private final CartHandler carts;
    private final CouponHandler coupons;
    private final OrderHandler orders;
    private final AfterSaleHandler returns;
    private final cn.edu.jlu.wechatmerchant.server.application.InboxHandler inbox;
    private final ExecutorService tasks = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private final Semaphore slots = new Semaphore(16);
    private volatile boolean closed;
    private ServerSocket listener;

    public ProbeServer(int port) {
        this(port, NetworkDefaults.SERVER_FRAME_TIMEOUT_MS, null, null, null);
    }

    public ProbeServer(int port, int frameTimeoutMillis) {
        this(port, frameTimeoutMillis, null, null, null);
    }

    public ProbeServer(int port, ProductQueryHandler products) {
        this(port, NetworkDefaults.SERVER_FRAME_TIMEOUT_MS, products, null, null);
    }

    public ProbeServer(int port, ProductQueryHandler products, IdentityHandler identities) {
        this(port, NetworkDefaults.SERVER_FRAME_TIMEOUT_MS, products, identities, null);
    }

    public ProbeServer(int port, ProductQueryHandler products, IdentityHandler identities, CartHandler carts) {
        this(port, NetworkDefaults.SERVER_FRAME_TIMEOUT_MS, products, identities, carts);
    }

    private ProbeServer(int port, int frameTimeoutMillis, ProductQueryHandler products, IdentityHandler identities, CartHandler carts) {
        this(port,frameTimeoutMillis,products,identities,carts,null);
    }

    public ProbeServer(int port,ProductQueryHandler products,IdentityHandler identities,CartHandler carts,CouponHandler coupons) {
        this(port,NetworkDefaults.SERVER_FRAME_TIMEOUT_MS,products,identities,carts,coupons);
    }

    private ProbeServer(int port,int frameTimeoutMillis,ProductQueryHandler products,IdentityHandler identities,CartHandler carts,CouponHandler coupons) {
        this(port,frameTimeoutMillis,products,identities,carts,coupons,null);
    }
    public ProbeServer(int port,ProductQueryHandler products,IdentityHandler identities,CartHandler carts,CouponHandler coupons,OrderHandler orders){
        this(port,NetworkDefaults.SERVER_FRAME_TIMEOUT_MS,products,identities,carts,coupons,orders);
    }
    private ProbeServer(int port,int frameTimeoutMillis,ProductQueryHandler products,IdentityHandler identities,CartHandler carts,CouponHandler coupons,OrderHandler orders) {
        this(port,frameTimeoutMillis,products,identities,carts,coupons,orders,null);
    }
    public ProbeServer(int port,ProductQueryHandler products,IdentityHandler identities,CartHandler carts,CouponHandler coupons,OrderHandler orders,AfterSaleHandler returns){
        this(port,NetworkDefaults.SERVER_FRAME_TIMEOUT_MS,products,identities,carts,coupons,orders,returns);
    }
    private ProbeServer(int port,int frameTimeoutMillis,ProductQueryHandler products,IdentityHandler identities,CartHandler carts,CouponHandler coupons,OrderHandler orders,AfterSaleHandler returns) {
        this(port,frameTimeoutMillis,products,identities,carts,coupons,orders,returns,null);
    }
    public ProbeServer(int port,ProductQueryHandler products,IdentityHandler identities,CartHandler carts,CouponHandler coupons,OrderHandler orders,AfterSaleHandler returns,cn.edu.jlu.wechatmerchant.server.application.InboxHandler inbox){
        this(port,NetworkDefaults.SERVER_FRAME_TIMEOUT_MS,products,identities,carts,coupons,orders,returns,inbox);
    }
    private ProbeServer(int port,int frameTimeoutMillis,ProductQueryHandler products,IdentityHandler identities,CartHandler carts,CouponHandler coupons,OrderHandler orders,AfterSaleHandler returns,cn.edu.jlu.wechatmerchant.server.application.InboxHandler inbox) {
        if (port < 0 || port > 65535 || frameTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Invalid server configuration.");
        }
        requestedPort = port;
        this.frameTimeoutMillis = frameTimeoutMillis;
        this.products = products;
        this.identities = identities;
        this.carts = carts;
        this.coupons = coupons;
        this.orders=orders;
        this.returns=returns;
        this.inbox=inbox;
    }

    public synchronized void start() throws IOException {
        if (closed || listener != null) {
            throw new IllegalStateException("Server already started or closed.");
        }
        ServerSocket candidate = new ServerSocket();
        try {
            candidate.bind(new InetSocketAddress(InetAddress.getByName(NetworkDefaults.ADDRESS), requestedPort));
            listener = candidate;
            tasks.submit(this::acceptConnections);
        } catch (IOException | RuntimeException exception) {
            candidate.close();
            throw exception;
        }
    }

    public synchronized int port() {
        if (listener == null) {
            throw new IllegalStateException("Server is not started.");
        }
        return listener.getLocalPort();
    }

    private void acceptConnections() {
        while (!closed) {
            try {
                Socket socket = listener.accept();
                if (!slots.tryAcquire()) {
                    socket.close();
                    continue;
                }
                connections.add(socket);
                try {
                    tasks.submit(() -> serve(socket));
                } catch (RejectedExecutionException exception) {
                    connections.remove(socket);
                    slots.release();
                    socket.close();
                }
            } catch (IOException exception) {
                if (!closed) {
                    System.err.println("Accept failed: " + exception.getClass().getSimpleName());
                }
                break;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            while (!closed) {
                JsonObject value = FrameCodec.read(socket, frameTimeoutMillis);
                if (value == null) {
                    return;
                }
                Messages.Request request = Messages.parseRequest(value);
                String code;
                String message;
                JsonObject body = new JsonObject();
                if (request.version() != Messages.VERSION) {
                    code = "VERSION_UNSUPPORTED";
                    message = "Unsupported protocol version.";
                } else if (identities != null && identities.handles(request.operation())) {
                    FrameCodec.write(socket.getOutputStream(), identities.handle(request));
                    continue;
                } else if(inbox!=null && inbox.handles(request.operation())) {
                    FrameCodec.write(socket.getOutputStream(),inbox.handle(request));continue;
                } else if(returns!=null && returns.handles(request.operation())) {
                    FrameCodec.write(socket.getOutputStream(),returns.handle(request));continue;
                } else if(orders!=null && orders.handles(request.operation())) {
                    FrameCodec.write(socket.getOutputStream(),orders.handle(request));continue;
                } else if(coupons!=null && coupons.handles(request.operation())) {
                    FrameCodec.write(socket.getOutputStream(),coupons.handle(request));
                    continue;
                } else if (carts != null && carts.handles(request.operation())) {
                    FrameCodec.write(socket.getOutputStream(), carts.handle(request));
                    continue;
                } else if (request.operation() == Messages.LIST_PRODUCTS && products != null) {
                    if (identities != null && request.sessionToken() != null) {
                        JsonObject denied = identities.authorizeBuyer(request);
                        if (denied != null) {
                            FrameCodec.write(socket.getOutputStream(), denied);
                            continue;
                        }
                    }
                    FrameCodec.write(socket.getOutputStream(), products.handle(request));
                    continue;
                } else if (request.operation() != Messages.PING) {
                    code = "OPERATION_UNSUPPORTED";
                    message = "Operation is not implemented.";
                } else if (!request.body().isEmpty()) {
                    code = "INVALID_ARGUMENT";
                    message = "PING body must be empty.";
                } else {
                    code = "OK";
                    message = "pong";
                    body.addProperty("service", "merchant-server");
                }
                FrameCodec.write(socket.getOutputStream(), Messages.response(request, code, message, body));
                if (request.version() != Messages.VERSION) {
                    return;
                }
            }
        } catch (IOException exception) {
            if (!closed) {
                System.err.println("Connection ended: " + exception.getClass().getSimpleName());
            }
        } finally {
            connections.remove(socket);
            slots.release();
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (listener != null) {
            try {
                listener.close();
            } catch (IOException ignored) {
                // Continue closing the accepted connections.
            }
        }
        connections.forEach(socket -> {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Shutdown continues even if an already unusable connection fails to close.
            }
        });
        tasks.shutdownNow();
    }

    public boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException {
        return tasks.awaitTermination(timeout, unit);
    }
}

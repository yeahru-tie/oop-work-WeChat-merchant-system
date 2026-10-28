package cn.edu.jlu.wechatmerchant.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.server.application.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.transport.ProbeServer;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Lives in server tests only so the real client view can exercise TCP and SQLite together. */
@Timeout(15)
class CartCardIntegrationTest {
    @TempDir Path directory;
    @ParameterizedTest
    @ValueSource(ints={0,3})
    void productAddRecoversAfterEveryStockRejection(int initialQuantity) throws Exception {
        try (Fixture fixture=new Fixture(directory.resolve("add-recovery.db"))) {
            int stock=initialQuantity+2;
            try (var connection=fixture.database.openWriteConnection(); var statement=connection.createStatement()) {
                statement.executeUpdate("UPDATE products SET stock="+stock+" WHERE product_id=1001");
            }
            if (initialQuantity>0) fixture.carts.add(1,1001,initialQuantity,0);
            ProductImages images=ProductImages.load();
            AtomicReference<ProductListPanel> view=new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> view.set(new ProductListPanel(images)));
            var updates=new LinkedBlockingQueue<ClientController.State>();
            try (var client=fixture.client(); var controller=new ClientController(client,state -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                view.get().showState(state); updates.add(state);
            })) {
                SwingUtilities.invokeAndWait(() -> view.get().bindCartAction(controller::addToCart,() -> fail("Already signed in")));
                signInAndLoad(controller,updates);
                SwingUtilities.invokeAndWait(controller::refreshProducts);
                await(updates,s -> !s.busy() && s.catalog().status()==ClientController.CatalogStatus.READY);
                for (int retry=0; retry<2; retry++) {
                    int quantityBefore=initialQuantity+retry;
                    long versionBefore=fixture.carts.list(1).cartVersion();
                    int excessiveQuantity=stock-quantityBefore+1;
                    SwingUtilities.invokeAndWait(() -> {
                        ((JSpinner)named(view.get(),"product-quantity")).setValue(excessiveQuantity);
                        ((JButton)named(view.get(),"add-to-cart")).doClick();
                    });
                    await(updates,s -> s.canModifyCart() && s.cart().message().contains("库存不足"));
                    assertEquals(versionBefore,fixture.carts.list(1).cartVersion());
                    assertEquals(quantityBefore,fixture.carts.list(1).items().stream().mapToInt(i -> i.quantity()).sum());
                    SwingUtilities.invokeAndWait(() -> {
                        var add=(JButton)named(view.get(),"add-to-cart");
                        assertTrue(add.isEnabled());
                        assertEquals(excessiveQuantity,((JSpinner)named(view.get(),"product-quantity")).getValue());
                        ((JSpinner)named(view.get(),"product-quantity")).setValue(1);
                        assertTrue(add.isEnabled()); add.doClick();
                    });
                    await(updates,s -> s.canModifyCart() && s.cart().contents().cartVersion()==versionBefore+1);
                    assertEquals(quantityBefore+1,fixture.carts.list(1).items().getFirst().quantity());
                }
                assertEquals(stock,fixture.carts.list(1).items().getFirst().quantity());
                assertEquals(stock,fixture.carts.list(1).items().getFirst().stock());
            }
        }
    }
    @Test void cardQuantityAndRemoveWriteOnlyTheirOwnRowsAndLogoutClearsTheView() throws Exception {
        try (Fixture fixture=new Fixture(directory.resolve("cards.db"))) {
            var before=fixture.carts.add(1,1001,2,0);
            var both=fixture.carts.add(1,1002,1,1);
            long first=before.items().getFirst().itemId(), second=both.items().getLast().itemId();
            ProductImages images=ProductImages.load();
            AtomicReference<CartPanel> view=new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> view.set(new CartPanel(images)));
            var updates=new LinkedBlockingQueue<ClientController.State>();
            try (var client=fixture.client(); var controller=new ClientController(client,state -> { view.get().showState(state); updates.add(state); })) {
                SwingUtilities.invokeAndWait(() -> view.get().bindActions(() -> {},controller::refreshCart,controller::editCart));
                signInAndLoad(controller,updates);
                SwingUtilities.invokeAndWait(() -> {
                    ((JSpinner)named(view.get(),"cart-quantity-"+first)).setValue(3);
                    assertFalse(named(view.get(),"cart-quantity-"+first).isEnabled());
                    assertFalse(named(view.get(),"remove-cart-item-"+second).isEnabled());
                    assertEquals(2,((Container)named(view.get(),"cart-cards")).getComponentCount());
                });
                await(updates,s -> s.canModifyCart() && s.cart().contents().items().getFirst().quantity()==3);
                assertEquals(3,fixture.carts.list(1).items().getFirst().quantity());
                assertEquals(1,fixture.carts.list(1).items().getLast().quantity());
                SwingUtilities.invokeAndWait(() -> ((JButton)named(view.get(),"remove-cart-item-"+second)).doClick());
                await(updates,s -> s.canModifyCart() && s.cart().contents().items().size()==1);
                assertEquals(first,fixture.carts.list(1).items().getFirst().itemId());
                SwingUtilities.invokeAndWait(() -> {
                    controller.logout();
                    assertEquals(0,((Container)named(view.get(),"cart-cards")).getComponentCount());
                });
                await(updates,s -> !s.busy() && s.identity()==null);
            }
        }
    }
    @Test void rejectedCardIncreaseClearsOldCardsAndRefreshAllowsAValidReduction() throws Exception {
        try (Fixture fixture=new Fixture(directory.resolve("stock.db"))) {
            var before=fixture.carts.add(1,1001,2,0); long item=before.items().getFirst().itemId();
            ProductImages images=ProductImages.load();
            AtomicReference<CartPanel> view=new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> view.set(new CartPanel(images)));
            var updates=new LinkedBlockingQueue<ClientController.State>();
            try (var client=fixture.client(); var controller=new ClientController(client,state -> { view.get().showState(state); updates.add(state); })) {
                SwingUtilities.invokeAndWait(() -> view.get().bindActions(() -> {},controller::refreshCart,controller::editCart));
                signInAndLoad(controller,updates);
                try (var c=fixture.database.openWriteConnection(); var s=c.createStatement()) { s.executeUpdate("UPDATE products SET stock=1 WHERE product_id=1001"); }
                SwingUtilities.invokeAndWait(() -> ((JSpinner)named(view.get(),"cart-quantity-"+item)).setValue(3));
                await(updates,s -> !s.busy() && s.cart().status()==ClientController.CartStatus.ERROR);
                SwingUtilities.invokeAndWait(() -> assertEquals(0,((Container)named(view.get(),"cart-cards")).getComponentCount()));
                assertEquals(before.cartVersion(),fixture.carts.list(1).cartVersion());
                assertEquals(2,fixture.carts.list(1).items().getFirst().quantity());
                SwingUtilities.invokeAndWait(controller::refreshCart); await(updates,ClientController.State::canModifyCart);
                SwingUtilities.invokeAndWait(() -> ((JSpinner)named(view.get(),"cart-quantity-"+item)).setValue(1));
                await(updates,s -> s.canModifyCart() && s.cart().contents().items().getFirst().quantity()==1);
                assertEquals(1,fixture.carts.list(1).items().getFirst().quantity());
            }
        }
    }
    private static void signInAndLoad(ClientController controller,BlockingQueue<ClientController.State> updates) throws Exception {
        SwingUtilities.invokeAndWait(() -> controller.login("buyer_a","BuyerA#2026!".toCharArray()));
        await(updates,s -> !s.busy() && s.identity()!=null);
        SwingUtilities.invokeAndWait(controller::refreshCart); await(updates,ClientController.State::canModifyCart);
    }
    private static ClientController.State await(BlockingQueue<ClientController.State> updates,Predicate<ClientController.State> predicate) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime()<deadline) { var state=updates.poll(100,TimeUnit.MILLISECONDS); if (state!=null && predicate.test(state)) return state; }
        throw new AssertionError("Controller state timed out");
    }
    private static Component named(Container parent,String name) {
        for (Component c:parent.getComponents()) {
            if (name.equals(c.getName())) return c;
            if (c instanceof Container child) { var found=find(child,name); if (found!=null) return found; }
        }
        throw new AssertionError("Missing "+name);
    }
    private static Component find(Container parent,String name) {
        for (Component c:parent.getComponents()) {
            if (name.equals(c.getName())) return c;
            if (c instanceof Container child) { var found=find(child,name); if (found!=null) return found; }
        }
        return null;
    }
    private static final class Fixture implements AutoCloseable {
        final SqliteDatabase database;
        final CartService carts;
        final SessionStore sessions=new SessionStore();
        final ProbeServer server;
        Fixture(Path file) throws Exception {
            database=new SqliteDatabase(file); database.initialize(); carts=new CartService(database);
            var identities=new IdentityHandler(new AuthenticationService(new UserDao(database)),sessions);
            server=new ProbeServer(0,new ProductQueryHandler(new ProductCatalog(new ProductDao(database))),identities,new CartHandler(carts,identities));
            server.start();
        }
        MerchantClient client() { return new MerchantClient("127.0.0.1",server.port(),1000,5000); }
        @Override public void close() { server.close(); sessions.clear(); }
    }
}

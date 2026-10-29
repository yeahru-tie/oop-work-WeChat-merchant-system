package cn.edu.jlu.wechatmerchant.client;

import cn.edu.jlu.wechatmerchant.client.ui.ClientWindow;
import cn.edu.jlu.wechatmerchant.client.ui.ProductImages;
import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.client.application.PendingCheckouts;
import cn.edu.jlu.wechatmerchant.client.application.PendingReturns;
import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.protocol.NetworkDefaults;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.FontUIResource;

public final class ClientApplication {
    private ClientApplication() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("买家端需要图形桌面环境。");
            System.exit(1);
            return;
        }
        final int port;
        try {
            port = NetworkDefaults.configuredPort();
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
            return;
        }
        // Decode/scale bundled images before the EDT; list rendering uses the cache only.
        ProductImages images = ProductImages.load();
        var theme=cn.edu.jlu.wechatmerchant.desktop.Theme.configure("client");
        SwingUtilities.invokeLater(() -> {
            cn.edu.jlu.wechatmerchant.desktop.Theme.install(theme);
            ClientWindow window = new ClientWindow(images);
            ClientController controller = new ClientController(
                    new MerchantClient(NetworkDefaults.ADDRESS, port, NetworkDefaults.CONNECT_TIMEOUT_MS,
                            NetworkDefaults.RESPONSE_TIMEOUT_MS), window::showState,new PendingCheckouts(PendingCheckouts.configuredPath()),new PendingReturns(PendingReturns.configuredPath()));
            window.bindActions(controller::testConnection, controller::cancel, controller::refreshProducts,
                    controller::previousPage, controller::nextPage);
            window.bindIdentityActions(controller::login, controller::logout, controller::cancel);
            window.bindCartActions(controller::refreshCart,controller::addToCart,controller::editCart);
            window.bindCouponActions(controller::refreshCoupons,controller::claimCoupon,controller::selectCoupon,controller::refreshQuote);
            window.bindOrderActions(controller::selectCartItem,controller::prepareCheckout,controller::confirmCheckout,controller::cancelConfirmation,controller::refreshOrders,controller::loadOrder,controller::queryPending,controller::retryPending);
            window.bindReturnActions(controller::applyReturn,controller::loadReturnHistory,controller::queryPendingReturn,controller::retryPendingReturn);
            window.bindMessageActions(controller::refreshMessages,controller::loadOrder,controller::locateProduct);
            window.showState(controller.state());
            javax.swing.Timer quoteBoundary=new javax.swing.Timer(1000,event -> {controller.recheckQuoteIfExpired();controller.pollMessagesIfDue();});
            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    quoteBoundary.stop();
                    controller.close();
                }
            });
            window.setVisible(true);
            quoteBoundary.start();
            controller.refreshProducts();
        });
    }
}

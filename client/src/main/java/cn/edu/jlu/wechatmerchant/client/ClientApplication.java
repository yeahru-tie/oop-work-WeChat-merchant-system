package cn.edu.jlu.wechatmerchant.client;

import cn.edu.jlu.wechatmerchant.client.ui.ClientWindow;
import cn.edu.jlu.wechatmerchant.client.ui.ProductImages;
import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.client.application.PendingCheckouts;
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
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | UnsupportedLookAndFeelException exception) {
                System.err.println("系统外观不可用，使用默认外观：" + exception.getMessage());
            }
            FontUIResource font = new FontUIResource("Microsoft YaHei UI", Font.PLAIN, 14);
            UIManager.put("Label.font", font);
            UIManager.put("TabbedPane.font", font);
            UIManager.put("Button.font", font);
            UIManager.put("List.font", font);
            UIManager.put("TextArea.font", font);
            UIManager.put("TextField.font", font);
            UIManager.put("PasswordField.font", font);
            ClientWindow window = new ClientWindow(images);
            ClientController controller = new ClientController(
                    new MerchantClient(NetworkDefaults.ADDRESS, port, NetworkDefaults.CONNECT_TIMEOUT_MS,
                            NetworkDefaults.RESPONSE_TIMEOUT_MS), window::showState,new PendingCheckouts(PendingCheckouts.configuredPath()));
            window.bindActions(controller::testConnection, controller::cancel, controller::refreshProducts,
                    controller::previousPage, controller::nextPage);
            window.bindIdentityActions(controller::login, controller::logout, controller::cancel);
            window.bindCartActions(controller::refreshCart,controller::addToCart,controller::editCart);
            window.bindCouponActions(controller::refreshCoupons,controller::claimCoupon,controller::selectCoupon,controller::refreshQuote);
            window.bindOrderActions(controller::selectCartItem,controller::prepareCheckout,controller::confirmCheckout,controller::cancelConfirmation,controller::refreshOrders,controller::loadOrder,controller::queryPending,controller::retryPending);
            window.showState(controller.state());
            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    controller.close();
                }
            });
            window.setVisible(true);
            controller.refreshProducts();
        });
    }
}

package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.desktop.LoginDialog;
import cn.edu.jlu.wechatmerchant.protocol.QuoteSnapshot;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.util.function.BiConsumer;

public final class ClientWindow extends JFrame {
    private static final long serialVersionUID = 1L;
    private final JLabel connectionStatus = new JLabel("尚未测试连接");
    private final JButton testConnection = new JButton("测试连接");
    private final JButton cancelConnection = new JButton("取消");
    private final ProductListPanel products;
    private final JLabel identity = new JLabel("游客");
    private final JLabel authStatus = new JLabel("尚未登录");
    private final JButton login = new JButton("登录");
    private LoginDialog loginDialog;
    private ClientController.State shownState;
    private final CartPanel cart;
    private final JTabbedPane navigation=new JTabbedPane();
    private BiConsumer<String,char[]> loginSubmit;
    private Runnable loginCancel;
    private Runnable cartRefresh;
    private boolean pendingCartLogin;
    private final OrdersPanel orders=new OrdersPanel();
    private javax.swing.JDialog confirmationDialog;
    private QuoteSnapshot shownConfirmation;
    private Runnable confirmCheckout,cancelCheckout;
    private boolean pendingOrdersLogin;

    public ClientWindow(ProductImages images) {
        super("微商系统 · 买家端");
        products = new ProductListPanel(images);
        cart = new CartPanel(images);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(800, 640));

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        JLabel heading = new JLabel("微商系统");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24f));
        JPanel header = new JPanel(new BorderLayout());
        header.add(heading, BorderLayout.WEST);
        JPanel identityBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        identity.putClientProperty("html.disable", Boolean.TRUE);
        authStatus.putClientProperty("html.disable", Boolean.TRUE);
        identityBar.add(identity);
        identityBar.add(login);
        header.add(identityBar, BorderLayout.EAST);
        content.add(header, BorderLayout.NORTH);

        navigation.addTab("商品", products);
        navigation.addTab("购物车", cart);
        navigation.addTab("我的订单", orders);
        navigation.addTab("消息", unavailablePage("订单通知与优惠公告功能尚未开放。"));
        navigation.addTab("设置", unavailablePage("主题设置功能尚未开放。"));
        content.add(navigation, BorderLayout.CENTER);
        JPanel connectionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        connectionBar.add(new JLabel("连接测试："));
        connectionBar.add(connectionStatus);
        connectionBar.add(testConnection);
        connectionBar.add(cancelConnection);
        testConnection.setEnabled(false);
        cancelConnection.setEnabled(false);
        JPanel footer = new JPanel(new GridLayout(0, 1, 0, 6));
        footer.add(authStatus);
        footer.add(connectionBar);
        content.add(footer, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(960, 800);
        setLocationRelativeTo(null);
    }

    public void bindIdentityActions(BiConsumer<String, char[]> submit, Runnable logout, Runnable cancel) {
        loginSubmit=submit;
        loginCancel=cancel;
        login.addActionListener(event -> {
            if (shownState != null && shownState.identity() != null) {
                logout.run();
                return;
            }
            openLogin();
        });
    }

    private void openLogin() {
        if (shownState==null || shownState.busy() || shownState.identity()!=null || loginSubmit==null) return;
        if (loginDialog!=null && loginDialog.isDisplayable()) { loginDialog.toFront(); return; }
        loginDialog=new LoginDialog(this,"买家登录",loginSubmit,loginCancel);
        loginDialog.setVisible(true);
    }

    public void bindCartActions(Runnable refresh,BiConsumer<Long,Integer> add,BiConsumer<Long,Integer> edit) {
        cartRefresh=refresh;
        products.bindCartAction(add,this::openLogin);
        cart.bindActions(this::openLogin,refresh,edit);
        navigation.addChangeListener(event -> {
            if (navigation.getSelectedIndex()!=1 || shownState==null) { pendingCartLogin=false; return; }
            if (shownState.identity()==null) {
                if (shownState.busy()) pendingCartLogin=true;
                else openLogin();
            } else if (!shownState.busy()) refresh.run();
        });
    }

    public void bindActions(Runnable test, Runnable cancel, Runnable refresh, Runnable previous, Runnable next) {
        testConnection.addActionListener(event -> test.run());
        cancelConnection.addActionListener(event -> cancel.run());
        testConnection.setEnabled(true);
        products.bindActions(refresh, previous, next);
    }

    public void bindCouponActions(java.util.function.BiConsumer<String,Integer> refresh,java.util.function.LongConsumer claim,
            java.util.function.Consumer<Long> select,Runnable quote) {
        cart.bindCouponActions(refresh,claim,select,quote,this::openLogin);
    }
    public void bindOrderActions(java.util.function.BiConsumer<Long,Boolean> select,Runnable prepare,Runnable confirm,Runnable cancel,
            java.util.function.IntConsumer list,java.util.function.LongConsumer detail,Runnable query,Runnable retry){
        confirmCheckout=confirm;cancelCheckout=cancel;cart.bindCheckoutActions(select,prepare,query,retry);orders.bind(this::openLogin,list,detail,query,retry);
        navigation.addChangeListener(event -> {if(navigation.getSelectedIndex()!=2 || shownState==null){pendingOrdersLogin=false;return;}if(shownState.identity()==null){if(shownState.busy())pendingOrdersLogin=true;else openLogin();}else if(!shownState.busy())list.accept(0);});
    }

    public void showState(ClientController.State state) {
        shownState = state;
        connectionStatus.setText(state.connectionMessage());
        testConnection.setEnabled(!state.busy());
        cancelConnection.setEnabled(state.busy());
        products.showState(state);
        cart.showState(state);
        orders.showState(state);
        QuoteSnapshot confirmation=state.identity()==null?null:state.orders().confirmation();
        if(confirmation==null && confirmationDialog!=null){confirmationDialog.dispose();confirmationDialog=null;shownConfirmation=null;}
        if(confirmation!=null && confirmation!=shownConfirmation && confirmCheckout!=null){
            shownConfirmation=confirmation;SwingUtilities.invokeLater(() -> {
                if(shownState!=state || state.orders().confirmation()!=confirmation)return;
                var panel=new CheckoutConfirmationPanel(confirmation,() -> shownState!=null && shownState.orders().confirmation()==confirmation && shownState.coupons().fresh(),confirmCheckout,cancelCheckout);
                confirmationDialog=new javax.swing.JDialog(this,"确认模拟成交",java.awt.Dialog.ModalityType.DOCUMENT_MODAL);confirmationDialog.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
                confirmationDialog.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosing(java.awt.event.WindowEvent event){cancelCheckout.run();}});
                confirmationDialog.setContentPane(panel);confirmationDialog.pack();confirmationDialog.setMinimumSize(new Dimension(600,480));confirmationDialog.setLocationRelativeTo(this);confirmationDialog.setVisible(true);
            });
        }
        identity.setText(state.identity() == null ? "游客" : state.identity().displayName());
        authStatus.setText(state.authMessage());
        login.setText(state.identity() == null ? "登录" : "退出登录");
        login.setEnabled(!state.busy());
        if (loginDialog != null && loginDialog.isDisplayable()) {
            if (state.identity() != null) loginDialog.dispose();
            else loginDialog.showProgress(state.action() == ClientController.Action.LOGIN, state.authMessage());
        }
        if (!state.busy() && state.identity()!=null && state.cart().status()==ClientController.CartStatus.NOT_LOADED && cartRefresh!=null) {
            SwingUtilities.invokeLater(() -> {
                if (shownState==state) cartRefresh.run();
            });
        }
        if (pendingCartLogin && !state.busy() && state.identity()==null && navigation.getSelectedIndex()==1) {
            pendingCartLogin=false;
            SwingUtilities.invokeLater(() -> {
                if (shownState==state && navigation.getSelectedIndex()==1) openLogin();
            });
        }
        if(pendingOrdersLogin && !state.busy() && state.identity()==null && navigation.getSelectedIndex()==2){pendingOrdersLogin=false;SwingUtilities.invokeLater(() -> {if(shownState==state && navigation.getSelectedIndex()==2)openLogin();});}
    }

    private static JPanel unavailablePage(String message) {
        JPanel page = new JPanel(new BorderLayout());
        page.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        page.add(new JLabel(message, SwingConstants.CENTER), BorderLayout.CENTER);
        return page;
    }
}

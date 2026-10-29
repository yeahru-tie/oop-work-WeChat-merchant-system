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
    private final cn.edu.jlu.wechatmerchant.desktop.NavigationPane navigation=new cn.edu.jlu.wechatmerchant.desktop.NavigationPane("微商系统");
    private BiConsumer<String,char[]> loginSubmit;
    private Runnable loginCancel;
    private Runnable cartRefresh;
    private boolean pendingCartLogin;
    private final OrdersPanel orders=new OrdersPanel();
    private final InboxPanel inbox=new InboxPanel();
    private javax.swing.JDialog confirmationDialog;
    private QuoteSnapshot shownConfirmation;
    private Runnable confirmCheckout,cancelCheckout;
    private boolean pendingOrdersLogin;
    private boolean relatedNavigation;

    public ClientWindow(ProductImages images) {
        super("微商系统 · 买家端");
        products = new ProductListPanel(images);
        cart = new CartPanel(images);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(800, 640));

        navigation.addTab("商品", products);
        navigation.addTab("购物车", cart);
        navigation.addTab("我的订单", orders);
        navigation.addTab("消息", inbox);
        var settings=new JPanel(new BorderLayout(0,24));
        settings.add(new cn.edu.jlu.wechatmerchant.desktop.ThemeSettingsPanel(),BorderLayout.CENTER);
        var connectionCard=new cn.edu.jlu.wechatmerchant.desktop.SurfacePanel(new BorderLayout(16,16));
        connectionCard.add(cn.edu.jlu.wechatmerchant.desktop.Ui.label("服务连接"),BorderLayout.NORTH);
        connectionCard.add(connectionStatus,BorderLayout.CENTER);
        testConnection.setText("检测连接");
        connectionCard.add(cn.edu.jlu.wechatmerchant.desktop.Ui.row(testConnection,cancelConnection),BorderLayout.SOUTH);
        settings.add(connectionCard,BorderLayout.SOUTH);navigation.addTab("设置",settings);
        identity.putClientProperty("html.disable",true);authStatus.putClientProperty("html.disable",true);
        navigation.accountArea().add(identity);navigation.accountArea().add(login);
        testConnection.setEnabled(false);cancelConnection.setVisible(false);
        var content=new JPanel(new BorderLayout());content.add(navigation,BorderLayout.CENTER);
        authStatus.setBorder(BorderFactory.createEmptyBorder(10,24,10,24));authStatus.setVisible(false);content.add(authStatus,BorderLayout.SOUTH);
        setContentPane(content);
        var screen=java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(960,screen.width-32),Math.min(680,screen.height-32)));
        setSize(Math.min(1280,screen.width-40),Math.min(880,screen.height-40));
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
        navigation.addChangeListener(event -> {if(relatedNavigation)return;if(navigation.getSelectedIndex()!=2 || shownState==null){pendingOrdersLogin=false;return;}if(shownState.identity()==null){if(shownState.busy())pendingOrdersLogin=true;else openLogin();}else if(!shownState.busy())list.accept(0);});
    }

    public void showState(ClientController.State state) {
        shownState = state;
        connectionStatus.setText(state.connectionMessage());
        testConnection.setEnabled(!state.busy());
        cancelConnection.setEnabled(state.canCancel());cancelConnection.setVisible(state.canCancel());
        products.showState(state);
        cart.showState(state);
        orders.showState(state);
        inbox.showState(state);
        long unread=state.identity()==null || state.inbox().page()==null?0:state.inbox().page().unreadCount();
        navigation.setTitleAt(3,unread==0?"消息":"消息 · "+(unread>99?"99+":unread));
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
        authStatus.setText(state.authMessage());authStatus.setVisible(cn.edu.jlu.wechatmerchant.desktop.NoticeBar.important(state.authMessage()));
        login.setText(state.identity() == null ? "登录" : "退出登录");
        login.setEnabled(!state.busy());login.putClientProperty("theme.primary",state.identity()==null);login.putClientProperty("theme.ghost",state.identity()!=null);
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
    public void bindReturnActions(java.util.function.BiConsumer<Long,String> submit,java.util.function.IntConsumer history,Runnable query,Runnable retry){orders.bindReturns(submit,history,query,retry);}
    public void bindMessageActions(java.util.function.BiConsumer<Integer,Long> query,java.util.function.LongConsumer order,java.util.function.LongConsumer product){
        inbox.bind(query,id -> {relatedNavigation=true;try{navigation.setSelectedIndex(2);}finally{relatedNavigation=false;}order.accept(id);},id -> {navigation.setSelectedIndex(0);products.focusProduct(id);product.accept(id);},this::openLogin);
        navigation.addChangeListener(e -> {if(navigation.getSelectedIndex()==3 && shownState!=null && !shownState.busy()){if(shownState.identity()==null)openLogin();else query.accept(0,null);}});
    }

    private static JPanel unavailablePage(String message) {
        JPanel page = new JPanel(new BorderLayout());
        page.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        page.add(new JLabel(message, SwingConstants.CENTER), BorderLayout.CENTER);
        return page;
    }
}

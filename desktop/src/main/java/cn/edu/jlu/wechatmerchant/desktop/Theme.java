package cn.edu.jlu.wechatmerchant.desktop;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

/** Local presentation only; no protocol, database or business dependencies. */
public final class Theme {
    public enum Mode { LIGHT, DARK }
    private static Mode mode=Mode.LIGHT;
    private static Path file=Path.of("data/client-theme.txt");
    private Theme(){}
    public static Mode configure(String application){file=Path.of(System.getProperty("merchant.themeFile","data/"+application+"-theme.txt"));return load(file);}
    public static Mode load(Path path){try{if(Files.size(path)>32)return Mode.LIGHT;return Mode.valueOf(Files.readString(path,StandardCharsets.UTF_8).strip());}catch(IOException | IllegalArgumentException ex){return Mode.LIGHT;}}
    public static void store(Path path,Mode value) throws IOException {
        Path target=path.toAbsolutePath().normalize();Files.createDirectories(target.getParent());Path temporary=Files.createTempFile(target.getParent(),"theme-",".tmp");
        try{Files.writeString(temporary,value.name()+"\n",StandardCharsets.UTF_8);try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temporary);}
    }
    public static void store(Mode value) throws IOException {store(file,value);}
    public static Mode current(){return mode;}
    public static Color background(){return color(0xf6f7f5,0x171e26);}
    public static Color surface(){return color(0xffffff,0x222c38);}
    public static Color foreground(){return color(0x20343c,0xe5edf2);}
    public static Color muted(){return color(0x60767e,0xa6b8c5);}
    public static Color accent(){return color(0x087f83,0x51c8c2);}
    public static Color line(){return color(0xdce6e7,0x3d4d5d);}
    private static Color color(int light,int dark){return new ColorUIResource(mode==Mode.LIGHT?light:dark);}
    public static void install(Mode value){
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Theme changes require EDT.");mode=value;
        try{UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel());}catch(UnsupportedLookAndFeelException ex){throw new IllegalStateException(ex);}
        var font=new FontUIResource("Microsoft YaHei UI",Font.PLAIN,14);
        for(Object key:java.util.List.copyOf(UIManager.getDefaults().keySet()))if(key instanceof String name && name.endsWith(".font"))UIManager.put(name,font);
        for(String component:new String[]{"Panel","Viewport","TabbedPane","ScrollPane","SplitPane","ToolBar"})UIManager.put(component+".background",background());
        for(String component:new String[]{"Label","Button","ToggleButton","TextField","PasswordField","TextArea","List","Table","TabbedPane","CheckBox","RadioButton","ComboBox","Spinner","FormattedTextField"}){
            UIManager.put(component+".foreground",foreground());UIManager.put(component+".background",surface());
        }
        UIManager.put("TextField.caretForeground",foreground());UIManager.put("TextArea.caretForeground",foreground());UIManager.put("PasswordField.caretForeground",foreground());
        UIManager.put("List.selectionBackground",accent());UIManager.put("List.selectionForeground",mode==Mode.LIGHT?Color.WHITE:background());
        UIManager.put("TextField.selectionBackground",accent());UIManager.put("TextArea.selectionBackground",accent());
        UIManager.put("ButtonUI",ThemeButtonUI.class.getName());UIManager.put("TabbedPaneUI",ThemeTabsUI.class.getName());UIManager.put("ScrollBarUI",ThemeScrollUI.class.getName());
        UIManager.put("SpinnerUI",QuantitySpinnerUI.class.getName());
        UIManager.put("TabbedPane.selected",surface());UIManager.put("TabbedPane.contentBorderInsets",new Insets(12,0,0,0));
        for(String component:new String[]{"TextField","PasswordField","FormattedTextField","ComboBox","Spinner"})UIManager.put(component+".border",new Outline(10,7));
        UIManager.put("ComboBox.border",new Outline(10,2));UIManager.put("Spinner.border",new Outline(10,4));UIManager.put("FormattedTextField.border",BorderFactory.createEmptyBorder(2,4,2,4));
        for(String component:new String[]{"TextField","PasswordField","FormattedTextField","TextArea"})UIManager.put(component+".inactiveForeground",muted());
        UIManager.put("control",surface());UIManager.put("controlShadow",line());UIManager.put("controlText",foreground());UIManager.put("TableHeader.background",surface());UIManager.put("TableHeader.foreground",foreground());
        UIManager.put("ScrollPane.border",BorderFactory.createEmptyBorder());UIManager.put("SplitPane.border",BorderFactory.createEmptyBorder());UIManager.put("SplitPane.dividerSize",10);
        UIManager.put("CheckBox.background",background());UIManager.put("RadioButton.background",background());
        for(Window window:Window.getWindows()){SwingUtilities.updateComponentTreeUI(window);restyle(window);window.invalidate();window.validate();window.repaint();}
    }
    public static void restyle(Component root){
        if(root instanceof JComponent c){if(Boolean.TRUE.equals(c.getClientProperty("theme.muted")))c.setForeground(muted());else if(Boolean.TRUE.equals(c.getClientProperty("theme.accent")))c.setForeground(accent());else c.setForeground(foreground());if(c instanceof JPanel)c.setBackground(Boolean.TRUE.equals(c.getClientProperty("theme.surface"))?surface():background());}
        if(root instanceof Container container)for(Component child:container.getComponents())restyle(child);
    }
    public static void primary(JButton button){button.putClientProperty("theme.primary",true);button.repaint();}
    public static void muted(JComponent text){text.putClientProperty("theme.muted",true);text.setForeground(muted());}
    public static javax.swing.border.Border inputBorder(){return new Outline(12,8);}
    public static javax.swing.border.Border cardBorder(){return new AbstractBorder(){
        @Override public Insets getBorderInsets(Component c){return new Insets(24,24,24,24);}
        @Override public Insets getBorderInsets(Component c,Insets i){i.set(24,24,24,24);return i;}
        @Override public void paintBorder(Component c,Graphics g,int x,int y,int w,int h){var p=(Graphics2D)g.create();p.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);p.setColor(c instanceof JComponent value && Boolean.TRUE.equals(value.getClientProperty("theme.selected"))?accent():line());p.drawRoundRect(x+4,y+3,w-9,h-11,20,20);p.dispose();}
    };}
    static final class Outline extends AbstractBorder {
        private final int radius,padding;Outline(int radius,int padding){this.radius=radius;this.padding=padding;}
        @Override public Insets getBorderInsets(Component c){return new Insets(padding,padding,padding,padding);}
        @Override public Insets getBorderInsets(Component c,Insets insets){insets.set(padding,padding,padding,padding);return insets;}
        @Override public void paintBorder(Component c,Graphics g,int x,int y,int w,int h){var g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g2.setColor(c.hasFocus()?accent():line());g2.drawRoundRect(x,y,w-1,h-1,radius,radius);g2.dispose();}
    }
}

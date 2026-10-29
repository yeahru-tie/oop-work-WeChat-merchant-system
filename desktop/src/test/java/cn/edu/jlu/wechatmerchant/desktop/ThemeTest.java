package cn.edu.jlu.wechatmerchant.desktop;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeTest {
    @TempDir Path folder;
    @Test void preferenceSurvivesReloadAndInvalidPreferenceFallsBackWithoutOverwriting() throws Exception {
        Path p=folder.resolve("space theme.txt");Theme.store(p,Theme.Mode.DARK);assertEquals(Theme.Mode.DARK,Theme.load(p));Theme.store(p,Theme.Mode.LIGHT);assertEquals(Theme.Mode.LIGHT,Theme.load(p));
        Files.writeString(p,"damaged");byte[] before=Files.readAllBytes(p);assertEquals(Theme.Mode.LIGHT,Theme.load(p));assertArrayEquals(before,Files.readAllBytes(p));assertEquals(1,Files.list(folder).count());
    }
    @Test void bothThemesRenderActualControlsAndHaveDistinctReadableSurfaces() throws Exception {
        SwingUtilities.invokeAndWait(() -> {try{for(var mode:Theme.Mode.values()){
            Theme.install(mode);assertNotEquals(Theme.foreground(),Theme.surface());assertNotEquals(Theme.accent(),Theme.background());
            var panel=new ThemeSettingsPanel();panel.setSize(700,460);layout(panel);var image=new java.awt.image.BufferedImage(700,460,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();panel.paint(g);g.dispose();
            var spinner=new JSpinner(new SpinnerNumberModel(2,1,100,1));spinner.setSize(104,36);layout(spinner);var field=((JSpinner.NumberEditor)spinner.getEditor()).getTextField();assertTrue(field.getHeight()>=field.getFontMetrics(field.getFont()).getHeight(),"Quantity must remain readable in both themes");assertEquals("2",field.getText());
            assertEquals(mode,Theme.current());
        }}finally{Theme.install(Theme.Mode.LIGHT);}});
    }
    private static void layout(java.awt.Container c){c.doLayout();for(var child:c.getComponents())if(child instanceof java.awt.Container nested)layout(nested);}
    @Test void actualSettingsButtonsPersistAndApplyTheChosenTheme() throws Exception {
        String old=System.getProperty("merchant.themeFile");Path path=folder.resolve("settings.txt");System.setProperty("merchant.themeFile",path.toString());Theme.configure("client");
        try{SwingUtilities.invokeAndWait(() -> {Theme.install(Theme.Mode.LIGHT);var panel=new ThemeSettingsPanel();((JButton)find(panel,"theme-dark")).doClick();});
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);var done=new java.util.concurrent.atomic.AtomicBoolean();while(System.nanoTime()<deadline){SwingUtilities.invokeAndWait(() -> done.set(Theme.current()==Theme.Mode.DARK));if(done.get())break;Thread.sleep(10);}assertTrue(done.get());assertEquals(Theme.Mode.DARK,Theme.load(path));
        }finally{if(old==null)System.clearProperty("merchant.themeFile");else System.setProperty("merchant.themeFile",old);Theme.configure("client");SwingUtilities.invokeAndWait(() -> Theme.install(Theme.Mode.LIGHT));}
    }
    private static java.awt.Component find(java.awt.Container c,String name){for(var child:c.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof java.awt.Container nested){var result=find(nested,name);if(result!=null)return result;}}return null;}
}

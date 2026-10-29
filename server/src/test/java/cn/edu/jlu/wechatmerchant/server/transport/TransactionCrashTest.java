package cn.edu.jlu.wechatmerchant.server.transport;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.support.TransactionFixture;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Kills only the helper JVM created by this test; never touches a running user application. */
@Timeout(30)
class TransactionCrashTest {
    @TempDir Path folder;
    @ParameterizedTest @ValueSource(strings={"UNCOMMITTED","COMMITTED_REPLY_LOST"})
    void forcedServerTerminationRecoversWholeTransactionAndOriginalKey(String stage) throws Exception {
        Path file=folder.resolve("崩溃 核对.db"),log=folder.resolve("helper.log");
        try(var f=new TransactionFixture(file)){
            f.carts.add(1,1001,2,0);long coupon=f.coupon();
            var javaExecutable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");
            String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
            var process=new ProcessBuilder(javaExecutable.toString(),"--enable-native-access=ALL-UNNAMED","-cp",classpath,ServerProcess.class.getName(),file.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                int port=awaitPort(process,log);CheckoutRequest command;
                if(stage.equals("UNCOMMITTED")){
                    try(var client=new MerchantClient("127.0.0.1",port,1000,5000);var pool=Executors.newSingleThreadExecutor()){
                        client.login("buyer_a","BuyerA#2026!".toCharArray(),() -> false);var q=client.quote(1,coupon,() -> false);command=new CheckoutRequest(java.util.UUID.randomUUID().toString(),1,q.digest(),coupon,q.itemIds());
                        try(var reader=f.db.openReadOnlyConnection()){
                            reader.setAutoCommit(false);try(var s=reader.createStatement();var r=s.executeQuery("SELECT stock FROM products")){assertTrue(r.next());}
                            var pending=pool.submit(() -> client.checkout(command,() -> false));Path journal=Path.of(file+"-journal");long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                            while((!Files.exists(journal) || Files.size(journal)==0) && System.nanoTime()<until)Thread.sleep(5);
                            assertTrue(Files.exists(journal) && Files.size(journal)>0,"Must interrupt a real uncommitted write");kill(process);assertInstanceOf(IOException.class,assertThrows(ExecutionException.class,() -> pending.get(6,TimeUnit.SECONDS)).getCause());reader.rollback();
                        }
                    }
                }else{
                    var committed=new CountDownLatch(1);var release=new CountDownLatch(1);
                    try(var proxy=new ServerSocket(0);var pool=Executors.newVirtualThreadPerTaskExecutor()){
                        var forwarding=pool.submit(() -> {try(var downstream=proxy.accept();var upstream=new Socket("127.0.0.1",port)){
                            while(true){var request=FrameCodec.read(downstream,5000);if(request==null)return;FrameCodec.write(upstream.getOutputStream(),request);var reply=FrameCodec.read(upstream,5000);
                                if(request.get("operation").getAsInt()==Messages.CHECKOUT){assertEquals("SUCCESS",reply.getAsJsonObject("body").get("state").getAsString());committed.countDown();assertTrue(release.await(6,TimeUnit.SECONDS));return;}
                                FrameCodec.write(downstream.getOutputStream(),reply);
                            }
                        }catch(IOException | InterruptedException ex){throw new RuntimeException(ex);}});
                        try(var client=new MerchantClient("127.0.0.1",proxy.getLocalPort(),1000,5000)){
                            client.login("buyer_a","BuyerA#2026!".toCharArray(),() -> false);var q=client.quote(1,coupon,() -> false);command=new CheckoutRequest(java.util.UUID.randomUUID().toString(),1,q.digest(),coupon,q.itemIds());var pending=pool.submit(() -> client.checkout(command,() -> false));
                            try{assertTrue(committed.await(6,TimeUnit.SECONDS));kill(process);}finally{release.countDown();}
                            assertInstanceOf(IOException.class,assertThrows(ExecutionException.class,() -> pending.get(6,TimeUnit.SECONDS)).getCause());forwarding.get(6,TimeUnit.SECONDS);
                        }finally{release.countDown();}
                    }
                }
                f.db.initialize();var recovered=f.orders.byKey(f.buyer,command.checkoutKey());
                if(stage.equals("UNCOMMITTED")){assertEquals("NOT_FOUND",recovered.state());assertEquals(20,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(0,f.value("SELECT used FROM user_coupons"));assertEquals(0,f.value("SELECT count(*) FROM orders"));assertEquals(0,f.value("SELECT count(*) FROM order_items"));assertEquals(1,f.carts.list(1).cartVersion());assertEquals(2,f.carts.list(1).items().getFirst().quantity());}
                else{assertEquals("SUCCESS",recovered.state());assertEquals(18,f.value("SELECT stock FROM products WHERE product_id=1001"));assertTrue(f.carts.list(1).items().isEmpty());}
                var result=f.orders.checkout(f.buyer,command);assertEquals("SUCCESS",result.state());assertEquals(result,f.orders.checkout(f.buyer,command));if(recovered.order()!=null)assertEquals(recovered,result);
                assertEquals(1,f.value("SELECT count(*) FROM orders"));assertEquals(1,f.value("SELECT count(*) FROM order_items"));assertEquals(18,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(1,f.value("SELECT used FROM user_coupons"));assertEquals(result.order().orderId(),f.value("SELECT used_order_id FROM user_coupons"));assertEquals(2,f.carts.list(1).cartVersion());assertTrue(f.carts.list(1).items().isEmpty());f.assertConsistent();
            }finally{if(process.isAlive())kill(process);process.getOutputStream().close();}
        }
    }
    private static int awaitPort(Process process,Path log) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<until){if(Files.exists(log)){for(String line:Files.readAllLines(log)){if(line.startsWith("READY "))return Integer.parseInt(line.substring(6));}}if(!process.isAlive())throw new AssertionError("Helper exited: "+Files.readString(log));Thread.sleep(10);}throw new AssertionError("Helper did not start");
    }
    private static void kill(Process process) throws Exception {process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));}
    public static final class ServerProcess {
        public static void main(String[] args) throws Exception {try(var f=new TransactionFixture(Path.of(args[0]))){f.start();System.out.println("READY "+f.port());System.out.flush();System.in.read();}}
    }
}

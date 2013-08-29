package hzg.wpn.mtango;

import hzg.wpn.mtango.command.Command;
import hzg.wpn.mtango.command.CommandImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import wpn.hdri.tango.proxy.TangoProxyWrapper;

import java.lang.reflect.Method;
import java.util.concurrent.*;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 29.08.13
 */
public class CacheableCommandExecutionStrategyTest {
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    private TangoProxyWrapper mockProxy;
    private Method readAttrMtd;

    @Before
    public void before() throws Exception {
        mockProxy = mock(TangoProxyWrapper.class);

        when(mockProxy.readAttribute("someAttr")).then(new Answer<Object>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                Thread.sleep(1000);
                return "Hello from mock Tango!";
            }
        });
        when(mockProxy.readAttribute("someOtherAttr")).then(new Answer<Object>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                Thread.sleep(1000);
                return "Another hello from mock Tango!";
            }
        });

        readAttrMtd = TangoProxyWrapper.class.getDeclaredMethod("readAttribute", String.class);
    }

    @Test
    public void testExecute_sameCmds() throws Exception {
        final CacheableCommandExecutionStrategy instance = new CacheableCommandExecutionStrategy(200);

        final Command cmd1 = new CommandImpl(mockProxy, readAttrMtd, "someAttr");

        final Command cmd2 = new CommandImpl(mockProxy, readAttrMtd, new String("someAttr"));//force different objects

        final CyclicBarrier startStop = new CyclicBarrier(3);
        Future<Object> fCmd1 = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                startStop.await();
                return instance.execute(cmd1);
            }
        });

        Future<Object> fCmd2 = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                startStop.await();
                return instance.execute(cmd2);
            }
        });

        startStop.await();
        long start = System.nanoTime();

        Object resCmd1 = fCmd1.get();
        Object resCmd2 = fCmd2.get();

        long end = System.nanoTime();
        long delta = end - start;

        //assert results are the same
        assertEquals("Hello from mock Tango!", resCmd1);
        assertEquals("Hello from mock Tango!", resCmd2);
        //assert total execution is less than double invocation time
        assertTrue(TimeUnit.MILLISECONDS.convert(delta, TimeUnit.NANOSECONDS) < 1500);
    }

    @Test
    public void testExecute_differentCmds() throws Exception {
        final CacheableCommandExecutionStrategy instance = new CacheableCommandExecutionStrategy(200);

        final Command cmd1 = new CommandImpl(mockProxy, readAttrMtd, "someAttr");

        final Command cmd2 = new CommandImpl(mockProxy, readAttrMtd, "someOtherAttr");

        final CyclicBarrier startStop = new CyclicBarrier(3);
        Future<Object> fCmd1 = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                startStop.await();
                return instance.execute(cmd1);
            }
        });

        Future<Object> fCmd2 = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                startStop.await();
                return instance.execute(cmd2);
            }
        });

        startStop.await();
        long start = System.nanoTime();

        Object resCmd1 = fCmd1.get();
        Object resCmd2 = fCmd2.get();

        long end = System.nanoTime();
        long delta = end - start;

        //assert results are the same
        assertEquals("Hello from mock Tango!", resCmd1);
        assertEquals("Another hello from mock Tango!", resCmd2);
        //assert total execution is almost equal to a single invocation time
        assertTrue(TimeUnit.MILLISECONDS.convert(delta, TimeUnit.NANOSECONDS) < 1100);
    }

    @Test
    public void testExecute_invalidatedCache() throws Exception {
        final CacheableCommandExecutionStrategy instance = new CacheableCommandExecutionStrategy(200);

        final Command cmd1 = new CommandImpl(mockProxy, readAttrMtd, "someAttr");

        final Command cmd2 = new CommandImpl(mockProxy, readAttrMtd, new String("someAttr"));//force different objects

        long start = System.nanoTime();
        instance.execute(cmd1);

        Thread.sleep(500);

        instance.execute(cmd2);
        long end = System.nanoTime();
        long delta = end - start;

        //assert total execution is greater than double invocation time
        assertTrue(TimeUnit.MILLISECONDS.convert(delta, TimeUnit.NANOSECONDS) > 2250);
    }
}
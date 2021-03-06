package com.tencent.mig.tmq;

import com.tencent.mig.tmq.simple.ModeEnum;
import com.tencent.mig.tmq.simple.SimpleTmqMsg;

import org.junit.Before;
import org.junit.Test;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 严格模式测试
 */
public class StrictModeTest extends BaseTest {
    @Before
    public void setUp()
    {
        TMQ.switchExpectMode(ModeEnum.STRICT);
    }

    /**
     * 预期收到1条指定消息，实际发出符合条件的1条消息，TMQ校验通过。
     */
    @Test
    public void testStrictOneMessage() throws Exception {
        TMQ.iCareWhatMsg(new SimpleTmqMsg("UnitTest", "1"));
        Timer timer = new Timer();
        timer.schedule(new TimerTask(){
            @Override
            public void run() {
                TMQ.report("UnitTest", "1");
            }
        }, ASYNC_TASK_TIMEOUT);
        TMQ.await(AWAIT_TIMEOUT);
        assertTrue(TMQ.check());
    }

    /**
     * 预期收到n条指定消息，实际发出符合条件的n条消息且顺序与预期一致，TMQ校验通过。
     */
    @Test
    public void testStrictMoreMessage() throws Exception {
        TMQ.iCareWhatMsg(new SimpleTmqMsg("UnitTest", "1"),
                new SimpleTmqMsg("UnitTest", "2")
                , new SimpleTmqMsg("UnitTest2", "1")
                , new SimpleTmqMsg("UnitTest2", "2"));
        Timer timer = new Timer();
        timer.schedule(new TimerTask(){
            @Override
            public void run() {
                TMQ.report("UnitTest", "1");
                TMQ.report("UnitTest", "2");
                TMQ.report("UnitTest2", "1");
                TMQ.report("UnitTest2", "2");
            }
        }, ASYNC_TASK_TIMEOUT);
        TMQ.await(AWAIT_TIMEOUT);
        assertTrue(TMQ.check());
    }

    /**
     * 预期消息只填null，语义为预期不会收到任何消息，如有收到任意条消息，TMQ校验不过；
     * 预期消息只填SimpleTmqMsg.NULL，语义与只填null一致，如有收到任意条消息，TMQ校验不过；
     * 预期消息只填SimpleTmqMsg.NULL，如未收到任何消息，TMQ校验通过；
     * 预期消息只填SimpleTmqMsg.KEY_MATCHED_NULL，如未收到任何消息，TMQ校验通过；
     */
    @Test
    public void testStrictZeroMessageExclusive() throws Exception {
        // 预期收不到任何消息
        TMQ.iCareWhatMsg(null);
        Timer timer = new Timer();
        timer.schedule(new TimerTask(){
            @Override
            public void run() {
                TMQ.report("UnitTest", "1");
                TMQ.report("UnitTest", "2");
                TMQ.report("UnitTest2", "1");
                TMQ.report("UnitTest2", "2");
            }
        }, ASYNC_TASK_TIMEOUT);
        TimeUnit.SECONDS.sleep(WAIT_TIMEOUT);
        TMQ.await(AWAIT_TIMEOUT);
        assertFalse(TMQ.check());

        // 预期收不到任何消息
        TMQ.iCareWhatMsg(SimpleTmqMsg.NULL);
        timer.schedule(new TimerTask(){
            @Override
            public void run() {
                TMQ.report("UnitTest", "1");
                TMQ.report("UnitTest", "2");
                TMQ.report("UnitTest2", "1");
                TMQ.report("UnitTest2", "2");
            }
        }, ASYNC_TASK_TIMEOUT);
        TimeUnit.SECONDS.sleep(WAIT_TIMEOUT);
        TMQ.await(AWAIT_TIMEOUT);
        assertFalse(TMQ.check());

        // 预期收不到任何消息
        TMQ.iCareWhatMsg(SimpleTmqMsg.NULL);
        TimeUnit.SECONDS.sleep(WAIT_TIMEOUT);
        TMQ.await(AWAIT_TIMEOUT);
        assertTrue(TMQ.check());

        // 预期收不到任何消息
        TMQ.iCareWhatMsg(SimpleTmqMsg.KEY_MATCHED_NULL);
        TimeUnit.SECONDS.sleep(WAIT_TIMEOUT);
        TMQ.await(AWAIT_TIMEOUT);
        assertTrue(TMQ.check());
    }

    /**
     * 预期收到n条指定消息，实际收到1条不符合预期的消息，TMQ校验不过；
     * 预期收到n条指定消息，实际收到的消息顺序与预期不符，TMQ校验不过；
     */
    @Test
    public void testStrictMoreMessageExclusive() throws Exception {
        // 预期收不到任何非预期消息
        TMQ.iCareWhatMsg(new SimpleTmqMsg("UnitTest", "1"),
                new SimpleTmqMsg("UnitTest", "2")
                , new SimpleTmqMsg("UnitTest2", "1")
                , new SimpleTmqMsg("UnitTest2", "2"));
        Timer timer = new Timer();
        timer.schedule(new TimerTask(){
            @Override
            public void run() {
                TMQ.report("UnitTest", "1");
                TMQ.report("UnitTest", "2");
                TMQ.report("UnitTest2", "1");
                // 第4个消息不在预期序列
                TMQ.report("UnitTest2", "3");
            }
        }, ASYNC_TASK_TIMEOUT);
        TMQ.await(AWAIT_TIMEOUT);
        assertFalse(TMQ.check());

        // 收到的消息顺序与预期不符
        TMQ.iCareWhatMsg(new SimpleTmqMsg("UnitTest", "1"),
                new SimpleTmqMsg("UnitTest", "2")
                , new SimpleTmqMsg("UnitTest2", "1")
                , new SimpleTmqMsg("UnitTest2", "2"));
        timer.schedule(new TimerTask(){
            @Override
            public void run() {
                TMQ.report("UnitTest", "1");
                TMQ.report("UnitTest", "2");
                // 第3、4个消息和预期不一致
                TMQ.report("UnitTest2", "2");
                TMQ.report("UnitTest2", "1");
            }
        }, ASYNC_TASK_TIMEOUT);
        TMQ.await(AWAIT_TIMEOUT);
        assertFalse(TMQ.check());
    }

    /**
     * 设置只关注的消息类型后，收到的其他类型消息不再作为判定依据
     * @throws Exception
     */
    @Test
    public void testStrictMoreMessageFilter() throws Exception {
        // 预期收不到任何非预期消息
        TMQ.iCareWhatMsg(new SimpleTmqMsg("UnitTest", "1"),
                new SimpleTmqMsg("UnitTest", "2")
                , new SimpleTmqMsg("UnitTest2", "1")
                , new SimpleTmqMsg("UnitTest2", "2"));

        // 只关注"UnitTest", "UnitTest2"消息，对于其他的消息不关注，出现也不作为判断依据
        TMQ.iCareWhatType("UnitTest", "UnitTest2");

        Timer timer = new Timer();
        timer.schedule(new TimerTask(){
            @Override
            public void run() {
                TMQ.report("UnitTest", "1");
                TMQ.report("UnitTest", "2");
                TMQ.report("UnitTest2", "1");
                // 不关注的消息
                TMQ.report("UnitTest3", "1");
                TMQ.report("UnitTest2", "2");
                // 不关注的消息
                TMQ.report("UnitTest3", "2");
            }
        }, ASYNC_TASK_TIMEOUT);
        TMQ.await(AWAIT_TIMEOUT);
        assertTrue(TMQ.check());
    }

    @Test
    public void testStrictMessagePre() throws Exception {
        // 提前报告的消息
        TMQ.report("UnitTest", "1");

        TMQ.iCareWhatMsg(new SimpleTmqMsg("UnitTest", "1"));
        Timer timer = new Timer();
        timer.schedule(new TimerTask(){
            @Override
            public void run() {
                TMQ.report("UnitTest", "1");
            }
        }, ASYNC_TASK_TIMEOUT);
        TMQ.await(AWAIT_TIMEOUT);
        assertTrue(TMQ.check());
    }
}
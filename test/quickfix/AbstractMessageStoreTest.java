package quickfix;

import java.io.IOException;
import java.util.ArrayList;

import junit.framework.TestCase;

public abstract class AbstractMessageStoreTest extends TestCase {
    private SessionID sessionID;
    private MessageStore store;
    
    // Automatically disable tests if database isn't available
    private boolean testEnabled = true;
    
    public AbstractMessageStoreTest() {
        super();
    }
    
    public AbstractMessageStoreTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
    	if (!testEnabled) {
    		return;
    	}
        long now = System.currentTimeMillis();
        sessionID = new SessionID("FIX.4.2", "SENDER-" + now, "TARGET-" + now);
        store = getMessageStoreFactory().create(sessionID);
        assertEquals("wrong store type", getMessageStoreClass(), store.getClass());
        super.setUp();
    }

    protected abstract MessageStoreFactory getMessageStoreFactory() throws Exception;

    protected abstract Class getMessageStoreClass();

    protected MessageStore getStore() {
        return store;
    }

    public void testMessageStoreSequenceNumbers() throws Exception {
    	if (!testEnabled) {
    		return;
    	}
    	
        store.reset();
        assertEquals("wrong value", 1, store.getNextSenderMsgSeqNum());
        assertEquals("wrong value", 1, store.getNextTargetMsgSeqNum());

        store.setNextSenderMsgSeqNum(123);
        assertEquals("wrong value", 123, store.getNextSenderMsgSeqNum());

        store.incrNextSenderMsgSeqNum();
        assertEquals("wrong value", 124, store.getNextSenderMsgSeqNum());

        store.setNextTargetMsgSeqNum(321);
        assertEquals("wrong value", 321, store.getNextTargetMsgSeqNum());

        store.incrNextTargetMsgSeqNum();
        assertEquals("wrong value", 322, store.getNextTargetMsgSeqNum());

        // test reset again after values have been set
        store.reset();
        assertEquals("wrong value", 1, store.getNextSenderMsgSeqNum());
        assertEquals("wrong value", 1, store.getNextTargetMsgSeqNum());
    }

    protected SessionID getSessionID() {
        return sessionID;
    }

    public void testMessageStorageMessages() throws Exception {
    	if (!testEnabled) {
    		return;
    	}

    	assertTrue("set failed", store.set(111, "message2"));
        assertTrue("set failed", store.set(113, "message1"));
        assertTrue("set failed", store.set(120, "message3"));

        ArrayList messages = new ArrayList();
        store.get(100, 115, messages);
        assertEquals("wrong # of messages", 2, messages.size());
        assertEquals("wrong message", "message2", messages.get(0));
        assertEquals("wrong message", "message1", messages.get(1));
    }

    public void testRefreshableMessageStore() throws Exception {
        if (store instanceof RefreshableMessageStore) {
            RefreshableMessageStore rstore = (RefreshableMessageStore)store;
            assertEquals("wrong value", 1, rstore.getNextSenderMsgSeqNum());
            assertEquals("wrong value", 1, rstore.getNextTargetMsgSeqNum());

            MessageStore anotherStore = getMessageStoreFactory().create(sessionID);
            assertEquals("wrong value", 1, anotherStore.getNextSenderMsgSeqNum());
            assertEquals("wrong value", 1, anotherStore.getNextTargetMsgSeqNum());
            
            anotherStore.setNextSenderMsgSeqNum(2);
            anotherStore.setNextTargetMsgSeqNum(2);
 
            assertEquals("wrong value", 2, anotherStore.getNextSenderMsgSeqNum());
            assertEquals("wrong value", 2, anotherStore.getNextTargetMsgSeqNum());
            closeMessageStore(anotherStore);
            
            assertEquals("wrong value", 1, rstore.getNextSenderMsgSeqNum());
            assertEquals("wrong value", 1, rstore.getNextTargetMsgSeqNum());
            
            rstore.refresh();
            
            assertEquals("wrong value", 2, anotherStore.getNextSenderMsgSeqNum());
            assertEquals("wrong value", 2, anotherStore.getNextTargetMsgSeqNum());
            
            assertEquals("wrong value", 2, rstore.getNextSenderMsgSeqNum());
            assertEquals("wrong value", 2, rstore.getNextTargetMsgSeqNum());
            
       }
    }

    protected void closeMessageStore(MessageStore store) throws IOException {
        // does nothing, by default
    }
    
    protected String getConfigurationFileName() {
        return "test/test.cfg";
    }

	protected void setTestEnabled(boolean b) {
		testEnabled = b;
	}
}
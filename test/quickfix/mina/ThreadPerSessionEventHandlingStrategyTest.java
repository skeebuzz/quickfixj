package quickfix.mina;

import java.util.Date;

import junit.framework.TestCase;
import quickfix.ConfigError;
import quickfix.DefaultSessionFactory;
import quickfix.FixVersions;
import quickfix.MemoryStoreFactory;
import quickfix.Message;
import quickfix.Responder;
import quickfix.ScreenLogFactory;
import quickfix.Session;
import quickfix.SessionFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.UnitTestApplication;
import quickfix.field.MsgSeqNum;
import quickfix.field.SenderCompID;
import quickfix.field.SendingTime;
import quickfix.field.TargetCompID;
import quickfix.field.converter.UtcTimestampConverter;
import quickfix.fix40.Logon;
import edu.emory.mathcs.backport.java.util.concurrent.BlockingQueue;

public class ThreadPerSessionEventHandlingStrategyTest extends TestCase {
    private final class ThreadPerSessionEventHandlingStrategyUnderTest extends
            ThreadPerSessionEventHandlingStrategy {
        public boolean dispatcherThreadStarted;
        public Exception getNextMessageException;
        public int getMessageCount = 1;

        protected void startDispatcherThread(
                ThreadPerSessionEventHandlingStrategy.MessageDispatchingThread dispatcher) {
            dispatcherThreadStarted = true;
        }

        Message getNextMessage(BlockingQueue messages) throws InterruptedException {
            if (getMessageCount-- == 0) {
                throw new InterruptedException("END COUNT");
            }
            if (getNextMessageException != null) {
                if (getNextMessageException instanceof InterruptedException) {
                    throw (InterruptedException) getNextMessageException;
                }
                throw (RuntimeException) getNextMessageException;
            }
            return super.getNextMessage(messages);
        }
    }

    public void testEventHandling() throws Exception {
        SessionID sessionID = new SessionID(FixVersions.BEGINSTRING_FIX40, "TW", "ISLD");
        Session session = setUpSession(sessionID);
        Message message = new Logon();
        message.getHeader().setString(SenderCompID.FIELD, "TW");
        message.getHeader().setString(TargetCompID.FIELD, "ISLD");
        message.getHeader().setString(SendingTime.FIELD, UtcTimestampConverter.convert(new Date(), false));
        message.getHeader().setInt(MsgSeqNum.FIELD, 1);
        ThreadPerSessionEventHandlingStrategyUnderTest strategy = new ThreadPerSessionEventHandlingStrategyUnderTest();

        strategy.onMessage(session, message);
        assertEquals(1, strategy.getMessages(sessionID).size());
        assertTrue(strategy.dispatcherThreadStarted);
        strategy.dispatcherThreadStarted = false;

        strategy.onMessage(session, message);
        assertEquals(2, strategy.getMessages(sessionID).size());
        assertFalse(strategy.dispatcherThreadStarted);
        session.setResponder(new Responder() {
        
            public String getRemoteIPAddress() {
                return null;
            }
        
            public void disconnect() {
        
            }
        
            public boolean send(String data) {
                return false;
            }
        
        });
        strategy.getDispatcher(sessionID).run();
    }

    public void testEventHandlingInterruptInRun() throws Exception {
        SessionID sessionID = new SessionID(FixVersions.BEGINSTRING_FIX40, "TW", "ISLD");
        Session session = setUpSession(sessionID);
        Message message = new Logon();
        ThreadPerSessionEventHandlingStrategyUnderTest strategy = new ThreadPerSessionEventHandlingStrategyUnderTest();

        strategy.onMessage(session, message);
        strategy.getNextMessageException = new InterruptedException("TEST");
        strategy.getDispatcher(sessionID).run();
    }

    public void testEventHandlingRuntimeException() throws Exception {
        SessionID sessionID = new SessionID(FixVersions.BEGINSTRING_FIX40, "TW", "ISLD");
        Session session = setUpSession(sessionID);
        Message message = new Logon();
        ThreadPerSessionEventHandlingStrategyUnderTest strategy = new ThreadPerSessionEventHandlingStrategyUnderTest();

        strategy.onMessage(session, message);
        strategy.getNextMessageException = new NullPointerException("TEST");
        strategy.getDispatcher(sessionID).run();
    }

    private Session setUpSession(SessionID sessionID) throws ConfigError {
        DefaultSessionFactory sessionFactory = new DefaultSessionFactory(new UnitTestApplication(),
                new MemoryStoreFactory(), new ScreenLogFactory(true, true, true));
        SessionSettings settings = new SessionSettings();
        settings.setString(SessionFactory.SETTING_CONNECTION_TYPE,
                SessionFactory.ACCEPTOR_CONNECTION_TYPE);
        settings.setString(Session.SETTING_USE_DATA_DICTIONARY, "N");
        settings.setString(Session.SETTING_START_TIME, "00:00:00");
        settings.setString(Session.SETTING_END_TIME, "00:00:00");
        Session session = sessionFactory.create(sessionID, settings);
        return session;
    }
}
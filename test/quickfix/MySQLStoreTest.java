package quickfix;


public class MySQLStoreTest extends AbstractMessageStoreTest {
	public MySQLStoreTest(String name) {
		super(name);
		setTestEnabled(MySQLTestSupport.isMySQLAvailable(getConfigurationFileName()));
	}

	protected MessageStoreFactory getMessageStoreFactory() throws ConfigError {
		SessionSettings settings = new SessionSettings(
				getConfigurationFileName());
		return new MySQLStoreFactory(settings);
	}

	protected Class getMessageStoreClass() {
		return MySQLStore.class;
	}
}
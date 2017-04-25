package tigase.archive;

import java.util.Arrays;
import tigase.vhosts.VHostItem;
import tigase.vhosts.VHostItem.DataType;

public class VHostItemHelper {
	
	public static final String ENABLED_KEY = "xep0136Enabled";

	public static final String DEFAULT_STORE_METHOD_KEY = "xep0136DefaultStoreMethod";
	
	public static final String REQUIRED_STORE_METHOD_KEY = "xep0136RequiredStoreMethod";
	
	public static final String RETENTION_TYPE_KEY = "xep0136Retention";
	public static final String RETENTION_PERIOD_KEY = "xep0136RetentionPerion";
	
	private static final DataType[] types = {
		new DataType(ENABLED_KEY, "XEP-0136 - Message Archiving enabled", Boolean.class, true),
		new DataType(DEFAULT_STORE_METHOD_KEY, "XEP-0136 - default store method", String.class, null, new Object[] {
			null,
			StoreMethod.False.toString(),
			StoreMethod.Body.toString(),
			StoreMethod.Message.toString(),
			StoreMethod.Stream.toString()
		}),
		new DataType(REQUIRED_STORE_METHOD_KEY, "XEP-0136 - required store method", String.class, null, new Object[] {
			null,
			StoreMethod.False.toString(),
			StoreMethod.Body.toString(),
			StoreMethod.Message.toString(),
			StoreMethod.Stream.toString()
		}),
		new DataType(RETENTION_TYPE_KEY, "XEP-0136 - retention type", String.class, null, new Object[] {
			RetentionType.userDefined.name(),
			RetentionType.unlimited.name(),
			RetentionType.numberOfDays.name()
		}, new String[] { 
			"User defined",
			"Unlimited",
			"Number of days"
		}),
		new DataType(RETENTION_PERIOD_KEY, "XEP-0136 - retention perion (in days)", Integer.class, null)
	};
	
	public static void register() {
		VHostItem.registerData(Arrays.asList(types));
	}

	public static boolean isEnabled(VHostItem item) {
		return item.isData(ENABLED_KEY);
	}
	
	public static String getDefaultStoreMethod(VHostItem item, String defValue) {
		String val = item.getData(DEFAULT_STORE_METHOD_KEY);
		if (val == null || val.isEmpty()) {
			val = defValue;
		}
		return val;
	}
	
	public static String getRequiredStoreMethod(VHostItem item, String defValue) {
		String val = item.getData(REQUIRED_STORE_METHOD_KEY);
		if (val == null || val.isEmpty()) {
			val = defValue;
		}
		return val;		
	}
	
	public static RetentionType getRetentionType(VHostItem item) {
		String val = item.getData(RETENTION_TYPE_KEY);
		return (val != null && !val.isEmpty()) ? RetentionType.valueOf(val) : RetentionType.userDefined;
	}
	
	public static Integer getRetentionDays(VHostItem item) {
		return item.getData(RETENTION_PERIOD_KEY);
	}
	
}

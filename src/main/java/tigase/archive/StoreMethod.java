package tigase.archive;

import java.util.HashMap;
import java.util.Map;

public enum StoreMethod {

	False,		// 0
	Body,		// 1
	Message,	// 2
	Stream;		// 3
	
	private final String value;
	
	private StoreMethod() {
		this.value = name().toLowerCase();
	}
	
	private static final Map<String,StoreMethod> values = new HashMap<>();
	static {
		values.put(False.toString(), False);
		values.put(Body.toString(), Body);
		values.put(Message.toString(), Message);
		values.put(Stream.toString(), Stream);
	}
	
	public static StoreMethod valueof(String v) {
		if (v == null || v.isEmpty()) {
			return False;
		}
		StoreMethod result = values.get(v);
		if (result == null)
			throw new IllegalArgumentException();
		return result;
	}
	
	@Override
	public String toString() {
		return value;
	}
	
}

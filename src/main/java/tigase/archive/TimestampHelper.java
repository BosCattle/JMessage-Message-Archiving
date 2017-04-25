package tigase.archive;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TimestampHelper {
	
	private final static SimpleDateFormat formatter4 = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
	private final static SimpleDateFormat formatter3 = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	private final static SimpleDateFormat formatter2 = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ssZ");
	private final static SimpleDateFormat formatter = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ss'Z'");

	static {
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		formatter2.setTimeZone(TimeZone.getTimeZone("UTC"));
		formatter3.setTimeZone(TimeZone.getTimeZone("UTC"));
		formatter4.setTimeZone(TimeZone.getTimeZone("UTC"));
	}	
	
	public static Date parseTimestamp(String tmp) throws ParseException {
		if (tmp == null)
			return null;
		Date date = null;
		if (tmp.endsWith("Z")) {
			if (tmp.contains(".")) {
				synchronized (formatter4) {
					date = formatter4.parse(tmp);
				}
			}
			else {
				synchronized (formatter) {
					date = formatter.parse(tmp);
				}
			}
		} else if (tmp.contains(".")) {
			synchronized (formatter3) {
				date = formatter3.parse(tmp);
			}
		} else {
			synchronized (formatter2) {
				date = formatter2.parse(tmp);
			}
		}

		return date;
	}	
	
	public static String format(Date ts) {
		synchronized (formatter2) {
			return formatter2.format(ts);
		}
	}
	
}

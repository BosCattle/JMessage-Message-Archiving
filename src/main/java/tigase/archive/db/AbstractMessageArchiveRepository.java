package tigase.archive.db;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import tigase.archive.AbstractCriteria;
import tigase.xml.Element;

public abstract class AbstractMessageArchiveRepository<Crit extends AbstractCriteria> implements MessageArchiveRepository<Crit> {
	
	private final static SimpleDateFormat formatter2 = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ssZ");
	
	static {
		formatter2.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	
	protected void addCollectionToResults(List<Element> results, String with, Date start) {
		String formattedStart = null;
		synchronized (formatter2) {
			formattedStart = formatter2.format(start);
		}
		results.add(new Element("chat", new String[] { "with", "start" },
				new String[] { with, formattedStart }));		
	}
	
	protected void addMessageToResults(List<Element> results, Date collectionStart, Element msg, Date timestamp, Direction direction, String with) {
		Element item = new Element(direction.toElementName());
		item.addChildren(msg.getChildren());
		item.setAttribute("secs", String.valueOf((timestamp.getTime() - collectionStart.getTime()) / 1000));
		if (with != null) {
			item.setAttribute("with", with);
		}
		results.add(item);
	}
}

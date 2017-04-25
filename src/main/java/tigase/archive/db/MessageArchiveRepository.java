package tigase.archive.db;

import java.util.Date;
import java.util.List;
import java.util.Set;
import tigase.archive.AbstractCriteria;
import tigase.archive.RSM;
import tigase.db.Repository;
import tigase.db.TigaseDBException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

/**
 *
 * @author andrzej
 */
public interface MessageArchiveRepository<Crit extends AbstractCriteria> extends Repository {
	
	enum Direction {
		incoming((short) 1, "from"),
		outgoing((short) 0, "to");
		
		private final short value;
		private final String elemName;
		
		Direction(short val, String elemName) {
			value = val;
			this.elemName = elemName;
		}
		
		public short getValue() {
			return value;
		}
		
		public String toElementName() {
			return elemName;
		}
		
		public static Direction getDirection(BareJID owner, BareJID from) {
			return owner.equals(from) ? outgoing : incoming;
		}
		
		public static Direction getDirection(short val) {
			switch (val) {
				case 1:
					return incoming;
				case 0:
					return outgoing;
				default:
					return null;
			}
		}
		
	}
	
	void archiveMessage(BareJID owner, BareJID buddy, Direction direction, Date timestamp, Element msg, Set<String> tags);
	
	/**
	 * Destroys instance of this repository and releases resources allocated if possible
	 */
	void destroy();
	
	AbstractCriteria newCriteriaInstance();
	
	List<Element> getCollections(BareJID owner, Crit criteria) throws TigaseDBException;
	
	List<Element> getItems(BareJID owner, Crit criteria) throws TigaseDBException;
	
	public void removeItems(BareJID owner, String withJid, Date start, Date end) throws TigaseDBException;
	
	public List<String> getTags(BareJID owner, String startsWith, Crit criteria) throws TigaseDBException;
	
}

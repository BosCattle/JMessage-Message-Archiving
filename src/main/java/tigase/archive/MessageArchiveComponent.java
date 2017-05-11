package tigase.archive;

import java.text.ParseException;

import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import static tigase.archive.MessageArchivePlugin.*;
import tigase.archive.db.MessageArchiveRepository;
import tigase.archive.db.MessageArchiveRepository.Direction;
import tigase.conf.Configurable;
import tigase.conf.ConfigurationException;
import tigase.db.DBInitException;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.osgi.ModulesManagerImpl;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.*;

public class MessageArchiveComponent
				extends AbstractMessageReceiver {
	private static final Logger log = Logger.getLogger(MessageArchiveComponent.class
			.getCanonicalName());
	private static final String			  MSG_ARCHIVE_REPO_CLASS_PROP_KEY =
			"archive-repo-class";
	private static final String           MSG_ARCHIVE_REPO_URI_PROP_KEY =
			"archive-repo-uri";

	private static final boolean			  DEF_TAGS_SUPPORT_PROP_VAL = false;
	private static final String			  TAGS_SUPPORT_PROP_KEY = "tags-support";

	private MessageArchiveRepository msg_repo = null;
	private boolean tagsSupport = false;


	public MessageArchiveComponent() {
		super();
		setName("message-archive");
	}

	@Override
	public void processPacket(Packet packet) {
		if ((packet.getStanzaTo() != null) &&!getComponentId().equals(packet.getStanzaTo())) {
			storeMessage(packet);
			return;
		}
		try {
			try {
				processActionPacket(packet);
			} catch (XMPPException ex) {
				if (log.isLoggable(Level.WARNING)) {
					log.log(Level.WARNING, "internal server while processing packet = " + packet
							.toString(), ex);
				}
				addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
						null, true));
			}
		} catch (PacketErrorTypeException ex) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "error with packet in error state - ignoring packet = {0}",
						packet);
			}
		}
	}

	@Override
	public void release() {
		super.release();
		
		if (msg_repo != null) {
			msg_repo.destroy();
			msg_repo = null;
		}
	}
	

	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> defs   = super.getDefaults(params);
		String              db_uri = (String) params.get(Configurable.USER_REPO_URL_PROP_KEY);

		defs.put(TAGS_SUPPORT_PROP_KEY, DEF_TAGS_SUPPORT_PROP_VAL);
		
		if (db_uri == null) {
			db_uri = (String) params.get(Configurable.GEN_USER_DB_URI);
		}
		if (db_uri != null) {
			defs.put(MSG_ARCHIVE_REPO_URI_PROP_KEY, db_uri);
		}

		return defs;
	}


	@Override
	public String getDiscoDescription() {
		return "Message Archiving (XEP-0136) Support";
	}

	/**
	 * Method description
	 * @param props
	 * @throws tigase.conf.ConfigurationException
	 */
	@Override
	public void setProperties(Map<String, Object> props) throws ConfigurationException {
		try {
			super.setProperties(props);
			
			if (props.containsKey(TAGS_SUPPORT_PROP_KEY)) {
				tagsSupport = (Boolean) props.get(TAGS_SUPPORT_PROP_KEY);
			}
			
			if (props.size() == 1) {
				return;
			}

			Map<String, String> repoProps = new HashMap<String, String>(4);

			for (Entry<String, Object> entry : props.entrySet()) {
				if ((entry.getKey() == null) || (entry.getValue() == null)) {
					continue;
				}
				repoProps.put(entry.getKey(), entry.getValue().toString());
			}

			String repoClsName = (String) props.get(MSG_ARCHIVE_REPO_CLASS_PROP_KEY);
			String uri = (String) props.get(MSG_ARCHIVE_REPO_URI_PROP_KEY);

			if (uri != null) {
				Class<? extends MessageArchiveRepository> repoCls = null;
				if (repoClsName == null)
					repoCls = RepositoryFactory.getRepoClass(MessageArchiveRepository.class, uri);
				else
					{
					try {
						repoCls = (Class<? extends MessageArchiveRepository>) ModulesManagerImpl.getInstance().forName(repoClsName);
					} catch (ClassNotFoundException ex) {
						log.log(Level.SEVERE, "Could not find class " + repoClsName + " an implementation of MessageArchive repository", ex);
						throw new ConfigurationException("Could not find class " + repoClsName + " an implementation of MessageArchive repository", ex);
					}
				}
				if (repoCls == null && repoClsName == null) {
					throw new ConfigurationException("Not found implementation of MessageArchive repository for URI = " + uri);
				}
				MessageArchiveRepository old_msg_repo = msg_repo;
				msg_repo = repoCls.newInstance();
				msg_repo.initRepository(uri, repoProps);
				if (old_msg_repo != null) {
					// if we have old instance and new is initialized then
					// destroy the old one to release resources
					old_msg_repo.destroy();
				}
			} else {
				log.log(Level.SEVERE, "repository uri is NULL!");
			}
		} catch (DBInitException ex) {	
			throw new ConfigurationException("Could not initialize MessageArchive repository", ex);
		} catch (InstantiationException ex) {
			log.log(Level.SEVERE, "Could not initialize MessageArchive repository", ex);
			throw new ConfigurationException("Could not initialize MessageArchive repository", ex);
		} catch (IllegalAccessException ex) {
			log.log(Level.SEVERE, "Could not initialize MessageArchive repository", ex);
			throw new ConfigurationException("Could not initialize MessageArchive repository", ex);
		}
	}


	/**
	 * Method description
	 *
	 *
	 * @param packet
	 *
	 * @throws PacketErrorTypeException
	 * @throws XMPPException
	 */
	protected void processActionPacket(Packet packet)
					throws PacketErrorTypeException, XMPPException {
		for (Element child : packet.getElement().getChildren()) {
			if (child.getName() == "list") {
				switch (packet.getType()) {
				case get :
					listCollections(packet, child);

					break;

				default :
					addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet,
							"Request type is incorrect", false));

					break;
				}
			} else if (child.getName() == "retrieve") {
				switch (packet.getType()) {
				case get :
					getMessages(packet, child);

					break;

				default :
					addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet,
							"Request type is incorrect", false));

					break;
				}
			} else if (child.getName() == "remove") {
				switch (packet.getType()) {
				case set :
					removeMessages(packet, child);

					break;

				default :
					addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet,
							"Request type is incorrect", false));

					break;
				}
			} else if (child.getName() == "tags") {
				switch (packet.getType()) {
				case set :
					queryTags(packet, child);
					break;
				default:
					addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet,
						"Request type is incorrect", false));						
				}
			}
		}
	}

	private void listCollections(Packet packet, Element list) throws XMPPException {
		try {
			AbstractCriteria criteria = msg_repo.newCriteriaInstance();
			criteria.fromElement(list, tagsSupport);

			List<Element> chats = msg_repo.getCollections(packet.getStanzaFrom().getBareJID(), criteria);

			Element retList = new Element(LIST);
			retList.setXMLNS(XEP0136NS);

			if (chats != null && !chats.isEmpty()) {
				retList.addChildren(chats);
			}
			
			RSM rsm = criteria.getRSM();
			if (rsm.getCount() == null || rsm.getCount() != 0)
				retList.addChild(rsm.toElement());
			
			addOutPacket(packet.okResult(retList, 0));
		} catch (ParseException e) {
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Date parsing error", true));
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "Error listing collections", e);
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Database error occured", true));
		}
	}

	private void removeMessages(Packet packet, Element remove) throws XMPPException {
		if ((remove.getAttributeStaticStr("with") == null) || (remove.getAttributeStaticStr(
				"start") == null) || (remove.getAttributeStaticStr("end") == null)) {
			addOutPacket(Authorization.NOT_ACCEPTABLE.getResponseMessage(packet,
					"Parameters with, start, end cannot be null", true));

			return;
		}
		try {
			AbstractCriteria criteria = msg_repo.newCriteriaInstance();
			criteria.fromElement(remove, tagsSupport);

			msg_repo.removeItems(packet.getStanzaFrom().getBareJID(), criteria.getWith(), 
					criteria.getStart(), criteria.getEnd());
			addOutPacket(packet.okResult((Element) null, 0));
		} catch (ParseException e) {
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Date parsing error", true));
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "Error removing messages", e);
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Database error occured", true));
		}
	}

	private void storeMessage(Packet packet) {
		String ownerStr = packet.getAttributeStaticStr(OWNER_JID);
		if (ownerStr != null) {
			packet.getElement().removeAttribute(OWNER_JID);

			BareJID owner    = BareJID.bareJIDInstanceNS(ownerStr);
			Direction direction = Direction.getDirection(owner, packet.getStanzaFrom().getBareJID());
			BareJID buddy    = direction == Direction.outgoing
					? packet.getStanzaTo().getBareJID()
					: packet.getStanzaFrom().getBareJID();

			Element msg = packet.getElement();
			Date timestamp  = null;
			Element delay= msg.findChildStaticStr(Message.MESSAGE_DELAY_PATH);
			if (delay != null) {
				try {
					String stamp = delay.getAttributeStaticStr("stamp");
					timestamp = TimestampHelper.parseTimestamp(stamp);
				} catch (ParseException e1) {}
			} else {
				timestamp = new java.util.Date();
			}			
			
			Set<String> tags = null;
			if (tagsSupport) 
				tags = TagsHelper.extractTags(msg);
			
			msg_repo.archiveMessage(owner, buddy, direction, timestamp, msg, tags);
		} else {
			log.log(Level.INFO, "Owner attribute missing from packet: {0}", packet);
		}
	}

	private void getMessages(Packet packet, Element retrieve) throws XMPPException {
		try {
			AbstractCriteria criteria = msg_repo.newCriteriaInstance();
			criteria.fromElement(retrieve, tagsSupport);

			List<Element> items = msg_repo.getItems(packet.getStanzaFrom().getBareJID(),
					criteria);

			Element retList = new Element("chat");

			if (criteria.getWith() != null)
				retList.setAttribute("with", criteria.getWith());
			if (criteria.getStart() != null)
				retList.setAttribute("start", TimestampHelper.format(criteria.getStart()));
			
			retList.setXMLNS(XEP0136NS);
			if (!items.isEmpty()) {
				retList.addChildren(items);
			}
			
			RSM rsm = criteria.getRSM();
			if (rsm.getCount() == null || rsm.getCount() != 0)
				retList.addChild(rsm.toElement());
			addOutPacket(packet.okResult(retList, 0));
		} catch (ParseException e) {
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Date parsing error", true));
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "Error retrieving messages", e);
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Database error occured", true));
		}
	}
	
	private void queryTags(Packet packet, Element tagsEl) throws XMPPException {
		try {
			AbstractCriteria criteria = msg_repo.newCriteriaInstance();
			criteria.getRSM().fromElement(tagsEl);
			
			String startsWith = tagsEl.getAttributeStaticStr("like");
			if (startsWith == null)
				startsWith = "";
			
			List<String> tags = msg_repo.getTags(packet.getStanzaFrom().getBareJID(), startsWith, criteria);
			
			tagsEl = new Element("tags", new String[] {"xmlns" }, new String[] { AbstractCriteria.QUERTY_XMLNS});
			for (String tag : tags) {
				tagsEl.addChild(new Element("tag", tag));
			}
			
			RSM rsm = criteria.getRSM();
			if (rsm.getCount() == null || rsm.getCount() != 0)
				tagsEl.addChild(rsm.toElement());			
			
			addOutPacket(packet.okResult(tagsEl, 0));
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "Error retrieving messages", e);
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Database error occured", true));
		}
	}
}

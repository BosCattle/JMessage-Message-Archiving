/*
 * MessageArchiveComponent.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */



package tigase.archive;

//~--- non-JDK imports --------------------------------------------------------

import java.text.ParseException;
import java.text.SimpleDateFormat;

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

/**
 *
 * @author andrzej
 */
public class MessageArchiveComponent
				extends AbstractMessageReceiver {
	private static final Logger log = Logger.getLogger(MessageArchiveComponent.class
			.getCanonicalName());
	private static final String			  MSG_ARCHIVE_REPO_CLASS_PROP_KEY =
			"archive-repo-class";
	private static final String           MSG_ARCHIVE_REPO_URI_PROP_KEY =
			"archive-repo-uri";
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
	//~--- fields ---------------------------------------------------------------

	private MessageArchiveRepository msg_repo = null;

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 */
	public MessageArchiveComponent() {
		super();
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		setName("message-archive");
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param packet
	 */
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
						(String) null, true));
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
	
	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param params
	 *
	 * @return
	 */
	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> defs   = super.getDefaults(params);
		String              db_uri = (String) params.get(Configurable.USER_REPO_URL_PROP_KEY);

		if (db_uri == null) {
			db_uri = (String) params.get(Configurable.GEN_USER_DB_URI);
		}
		if (db_uri != null) {
			defs.put(MSG_ARCHIVE_REPO_URI_PROP_KEY, db_uri);
		}

		return defs;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String getDiscoDescription() {
		return "Message Archiving (XEP-0136) Support";
	}

	//~--- set methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param props
	 * @throws tigase.conf.ConfigurationException
	 */
	@Override
	public void setProperties(Map<String, Object> props) throws ConfigurationException {
		try {
			super.setProperties(props);
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
				else {
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

	//~--- methods --------------------------------------------------------------

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
			}
		}
	}

	private void listCollections(Packet packet, Element list) throws XMPPException {
		try {
			RSM rsm = RSM.parseRootElement(list);

			String with     = list.getAttributeStaticStr("with");
			String startStr = list.getAttributeStaticStr("start");
			String stopStr  = list.getAttributeStaticStr("end");

			Date          start = startStr != null ? parseTimestamp(startStr) : null;
			Date          stop  = stopStr != null ? parseTimestamp(stopStr) : null;
			List<Element> chats = msg_repo.getCollections(packet.getStanzaFrom().getBareJID(),
					with, start, stop, rsm);
			Element retList = new Element(LIST);
			retList.setXMLNS(XEP0136NS);

			if (chats != null && !chats.isEmpty()) {
				retList.addChildren(chats);
			}
			
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

	private Date parseTimestamp(String tmp) throws ParseException {
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

	private void removeMessages(Packet packet, Element remove) throws XMPPException {
		if ((remove.getAttributeStaticStr("with") == null) || (remove.getAttributeStaticStr(
				"start") == null) || (remove.getAttributeStaticStr("end") == null)) {
			addOutPacket(Authorization.NOT_ACCEPTABLE.getResponseMessage(packet,
					"Parameters with, start, end cannot be null", true));

			return;
		}
		try {
			String startStr = remove.getAttributeStaticStr("start");
			String stopStr  = remove.getAttributeStaticStr("end");
			Date   start    = parseTimestamp(startStr);
			Date   stop     = parseTimestamp(stopStr);

			msg_repo.removeItems(packet.getStanzaFrom().getBareJID(), remove
					.getAttributeStaticStr("with"), start, stop);
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
					timestamp = parseTimestamp(stamp);
				} catch (ParseException e1) {}
			} else {
				timestamp = new java.util.Date();
			}			
			
			msg_repo.archiveMessage(owner, buddy, direction, timestamp, msg);
		} else {
			log.log(Level.INFO, "Owner attribute missing from packet: {0}", packet);
		}
	}

	//~--- get methods ----------------------------------------------------------

	private void getMessages(Packet packet, Element retrieve) throws XMPPException {
		try {
			RSM rsm = RSM.parseRootElement(
					retrieve);    // new RSM(retrieve.findChild("/retrieve/set"), 30);

			Date          start = parseTimestamp(retrieve.getAttributeStaticStr("start"));
			Date		  end = null;
			if (retrieve.getAttributeStaticStr("end") != null)
				end = parseTimestamp(retrieve.getAttributeStaticStr("end"));
			List<Element> items = msg_repo.getItems(packet.getStanzaFrom().getBareJID(),
					retrieve.getAttributeStaticStr("with"), start, end, rsm);
			String startStr = null;

			synchronized (formatter2) {
				startStr = formatter2.format(start);
			}

			Element retList = new Element("chat", new String[] { "with", "start" },
					new String[] { retrieve.getAttributeStaticStr("with"),
					startStr });

			retList.setXMLNS(XEP0136NS);
			if (!items.isEmpty()) {
				retList.addChildren(items);
			}
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
}


//~ Formatted in Tigase Code Convention on 13/10/15

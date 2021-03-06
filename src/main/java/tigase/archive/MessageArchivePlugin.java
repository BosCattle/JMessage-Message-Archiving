package tigase.archive;

import java.util.Objects;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;

import tigase.server.Message;
import tigase.server.Packet;

import tigase.util.DNSResolver;

import tigase.xml.Element;

import tigase.xmpp.*;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import tigase.server.Iq;
import tigase.xmpp.impl.C2SDeliveryErrorProcessor;

public class MessageArchivePlugin
				extends XMPPProcessor
				implements XMPPProcessorIfc {
	
	public static final String DEFAULT_SAVE = "default-save";
	
	public static final String LIST = "list";

	public static final String OWNER_JID = "owner";

	public static final String REMOVE = "remove";

	public static final String RETRIEVE = "retrieve";

	public static final String  XEP0136NS = "urn:xmpp:archive";
	private static final String ARCHIVE   = "message-archive";
	private static final String AUTO      = "auto";
	private static final String EXPIRE    = "expire";
	private static final String ID        = "message-archive-xep-0136";
	private static final Logger log = Logger.getLogger(MessageArchivePlugin.class
			.getCanonicalName());
	private static final String    MESSAGE  = "message";
	private static final String    SETTINGS = ARCHIVE + "/settings";
	private static final String    XMLNS    = "jabber:client";
	private static final String[][]  ELEMENT_PATHS = { {MESSAGE}, {Iq.ELEM_NAME, AUTO}, 
		{Iq.ELEM_NAME, RETRIEVE}, {Iq.ELEM_NAME, LIST}, {Iq.ELEM_NAME, REMOVE}, {Iq.ELEM_NAME, "pref"},
		{Iq.ELEM_NAME, "tags"} };
	private static final String[] XMLNSS = { Packet.CLIENT_XMLNS, XEP0136NS, 
		XEP0136NS, XEP0136NS, XEP0136NS, XEP0136NS, AbstractCriteria.QUERTY_XMLNS };
	private static final Set<StanzaType> TYPES;
	private static final Element[] DISCO_FEATURES = { new Element("feature", new String[] {
			"var" }, new String[] { XEP0136NS + ":" + AUTO }),
			new Element("feature", new String[] { "var" }, new String[] { XEP0136NS +
					":manage" }) };
	
	private static final String DEFAULT_STORE_METHOD_KEY = "default-store-method";
	private static final String REQUIRED_STORE_METHOD_KEY = "required-store-method";
	
	static {
		HashSet tmpTYPES = new HashSet<StanzaType>();
		tmpTYPES.add(null);
		tmpTYPES.addAll(EnumSet.of(StanzaType.normal, StanzaType.chat, 
			StanzaType.get, StanzaType.set, StanzaType.error, StanzaType.result));
		TYPES = Collections.unmodifiableSet(tmpTYPES);
	}


	private StoreMethod globalDefaultStoreMethod = StoreMethod.Body;
	private StoreMethod globalRequiredStoreMethod = StoreMethod.False;
	
	private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private JID              ma_jid    = null;


	/**
	 * @param settings
	 * @throws TigaseDBException
	 */
	@Override
	public void init(Map<String, Object> settings) throws TigaseDBException {
		super.init(settings);
		
		VHostItemHelper.register();

		String componentJidStr = (String) settings.get("component-jid");

		if (componentJidStr != null) {
			ma_jid = JID.jidInstanceNS(componentJidStr);
		} else {
			String defHost = DNSResolver.getDefaultHostname();

			ma_jid = JID.jidInstanceNS("message-archive", defHost, null);
		}
		log.log(Level.CONFIG, "Loaded message archiving component jid option: {0} = {1}",
				new Object[] { "component-jid",
				ma_jid });
		System.out.println("MA LOADED = " + ma_jid.toString());
		
		if (settings.containsKey(REQUIRED_STORE_METHOD_KEY)) {
			globalRequiredStoreMethod = StoreMethod.valueof((String) settings.get(REQUIRED_STORE_METHOD_KEY));
		}
		if (settings.containsKey(DEFAULT_STORE_METHOD_KEY)) {
			globalDefaultStoreMethod = StoreMethod.valueof((String) settings.get(DEFAULT_STORE_METHOD_KEY));
		}
		if (globalDefaultStoreMethod.ordinal() < globalRequiredStoreMethod.ordinal()) {
			globalDefaultStoreMethod  = globalRequiredStoreMethod;
		}
	}

	/**
	 * Method description
	 * @param packet
	 * @param session
	 * @param repo
	 * @param results
	 * @param settings
	 *
	 * @throws XMPPException
	 */
	@Override
	public void process(Packet packet, XMPPResourceConnection session,
			NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings)
					throws XMPPException {
		if (session == null) {
			return;
		}
		try {
			if (Objects.equals(Message.ELEM_NAME, packet.getElemName())) {
				if (C2SDeliveryErrorProcessor.isDeliveryError(packet))
					return;
				StanzaType type = packet.getType();
				if ((packet.getElement().findChildStaticStr(Message.MESSAGE_BODY_PATH) ==
						null) || ((type != null) && (type != StanzaType.chat) && (type != StanzaType
						.normal))) {
					return;
				}
				boolean auto = getAutoSave(session);
				if (auto && (packet.getElemCDataStaticStr(Message.MESSAGE_BODY_PATH) != null)) {
					StoreMethod storeMethod = getStoreMethod(session);
					if (storeMethod == StoreMethod.False) {
						return;
					}
					Packet result = packet.copyElementOnly();
					result.setPacketTo(ma_jid);
					result.getElement().addAttribute(OWNER_JID, session.getBareJID().toString());
					switch (storeMethod) {
						case Body:
							Element message = result.getElement();
							for (Element elem : message.getChildren()) {
								switch (elem.getName()) {
									case "body":
										break;
									default:
										message.removeChild(elem);
								}
							}
							break;
						default:
							break;
					}
					results.offer(result);
				}
			} else if (Objects.equals(Iq.ELEM_NAME, packet.getElemName())) {
				if (ma_jid.equals(packet.getPacketFrom())) {
					JID    connId = session.getConnectionId(packet.getStanzaTo());
					Packet result = packet.copyElementOnly();
					result.setPacketTo(connId);
					results.offer(result);
					return;
				}
				if ((packet.getType() != StanzaType.get) && (packet.getType() != StanzaType
						.set)) {
					return;
				}
				Element auto = packet.getElement().getChild("auto");
				Element pref = packet.getElement().getChild("pref");
				StoreMethod requiredStoreMethod = getRequiredStoreMethod(session);
				if ((auto == null) && (pref == null)) {
					Packet result = packet.copyElementOnly();
					result.setPacketTo(ma_jid);
					results.offer(result);
				} else if (pref != null) {
					if (packet.getType() == StanzaType.get) {
						Element prefEl = new Element("pref");
						prefEl.setXMLNS(XEP0136NS);
						Element autoEl = new Element("auto");
						autoEl.setAttribute("save", String.valueOf(getAutoSave(session)));
						prefEl.addChild(autoEl);
						Element defaultEl = new Element("default");
						defaultEl.setAttribute("otr", "forbid");
						try {
							RetentionType retentionType = VHostItemHelper.getRetentionType(session.getDomain());
							String expire = null;
							switch (retentionType) {
								case userDefined:
									expire = session.getData(SETTINGS, EXPIRE, null);
									break;
								case numberOfDays:
									Integer retention = VHostItemHelper.getRetentionDays(session.getDomain());
									if (retention != null) {
										expire = String.valueOf(retention.longValue() * 60 * 60 * 24);
									}
									break;
								case unlimited:
									break;
							}
							if (expire != null) {
								defaultEl.setAttribute(EXPIRE, expire);
							}
						} catch (TigaseDBException ex) {
							log.log(Level.WARNING, "could not retrieve expire setting for message archive for user {0}", 
									session.getjid());
						}
						StoreMethod storeMethod = getStoreMethod(session);
						defaultEl.setAttribute("save", storeMethod.toString());
						prefEl.addChild(defaultEl);
						Element methodEl = new Element("method");
						methodEl.setAttribute("type", "auto");
						methodEl.setAttribute("use", "prefer");
						prefEl.addChild(methodEl);
						methodEl = new Element("method");
						methodEl.setAttribute("type", "local");
						methodEl.setAttribute("use", "prefer");
						prefEl.addChild(methodEl);
						methodEl = new Element("method");
						methodEl.setAttribute("type", "manual");
						methodEl.setAttribute("use", "prefer");
						prefEl.addChild(methodEl);
						results.offer(packet.okResult(prefEl, 0));
					} else if (packet.getType() == StanzaType.set) {
						Authorization error = null;
						StoreMethod storeMethod = null;
						Boolean autoSave = null;
						String errorMsg = null;
						String expire = null;
						for (Element elem : pref.getChildren()) {
							switch (elem.getName()) {
								case "default":
									storeMethod = StoreMethod.valueof(elem.getAttributeStaticStr("save"));
									if (storeMethod == StoreMethod.Stream) {
										error = Authorization.FEATURE_NOT_IMPLEMENTED;
										errorMsg = "Value stream of save attribute is not supported";
										break;
									}
									if (storeMethod.ordinal() < requiredStoreMethod.ordinal()) {
										error = Authorization.NOT_ACCEPTABLE;
										errorMsg = "Required minimal message archiving level is " + requiredStoreMethod.toString();
										break;
									}
									String otr = elem.getAttributeStaticStr("otr");
									if (otr != null && !"forbid".equals(otr)) {
										error = Authorization.FEATURE_NOT_IMPLEMENTED;
										errorMsg = "Value " + otr + " of otr attribute is not supported";
									}
									expire = elem.getAttributeStaticStr(EXPIRE);
									if (expire != null) {
										if (RetentionType.userDefined != VHostItemHelper.getRetentionType(session.getDomain())) {
											error = Authorization.NOT_ALLOWED;
											errorMsg = "Expire value is not allowed to be changed by user";
										}
										else {
											try {
												long val = Long.parseLong(expire);
												if (val <= 0) {
													error = Authorization.NOT_ACCEPTABLE;
													errorMsg = "Value of expire attribute must be bigger than 0";
													break;
												}
											} catch (NumberFormatException ex) {
												error = Authorization.BAD_REQUEST;
												errorMsg = "Value of expire attribute must be a number";
												break;
											}
										}
									}
									break;
								case "auto":
									autoSave = Boolean.valueOf(elem.getAttributeStaticStr("save"));
									if (requiredStoreMethod != StoreMethod.False && (autoSave == null || autoSave == false)) {
										error = Authorization.NOT_ACCEPTABLE;
										errorMsg = "Required minimal message archiving level is " + requiredStoreMethod.toString() 
												+ " and that requires automatic archiving to be enabled";
									}
									if (autoSave && !VHostItemHelper.isEnabled(session.getDomain())) {
										error = Authorization.NOT_ALLOWED;
										errorMsg = "Message archiving is not allowed for domain " + session.getDomainAsJID().toString();
									}
									break;
								default: 
									error = Authorization.FEATURE_NOT_IMPLEMENTED;
									errorMsg = null;
							}
						}
						if (error != null) {
							results.offer(error.getResponseMessage(
									packet, errorMsg, true));
						}
						else {
							try {
								if (autoSave != null) {
									this.setAutoSave(session, autoSave);
								}
								if (storeMethod != null) {
									this.setStoreMethod(session, storeMethod);
								}
								if (expire != null) {
									session.setData(SETTINGS, EXPIRE, expire);
								}
								results.offer(packet.okResult((String) null, 0));
							}
							catch (TigaseDBException ex) {
								results.offer(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, null, false));
							}
						}
					} else {
						results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, null,
								true));
					}
				} else {
					String  val  = auto.getAttributeStaticStr("save");
					if (val == null) val = "";
					boolean save = false;
					switch (val) {
						case "true":
						case "1":
							save = true;
							break;
						case "false":
						case "0":
							save = false;
							break;
						default:
							results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet,
									"Save value is incorrect or missing", false));
							return;
					}
					if (!save && requiredStoreMethod != StoreMethod.False) {
						results.offer(Authorization.NOT_ACCEPTABLE.getResponseMessage(packet, 
								"Required minimal message archiving level is " + requiredStoreMethod.toString() 
								+ " and that requires automatic archiving to be enabled", false));
						return;
					}
					if (save && !VHostItemHelper.isEnabled(session.getDomain())) {
						results.offer(Authorization.NOT_ACCEPTABLE.getResponseMessage(packet, 
								"Message archiving is not allowed for domain " + session.getDomainAsJID().toString(), false));
						return;
					}
					try {
						setAutoSave(session, save);
						session.putCommonSessionData(ID + "/" + AUTO, save);
						Element res = new Element("auto");
						res.setXMLNS(XEP0136NS);
						res.setAttribute("save", save
								? "true"
								: "false");
						results.offer(packet.okResult(res, 0));
						return;
					} catch (TigaseDBException ex) {
						log.log(Level.WARNING, "Error setting Message Archive state: {0}", ex
								.getMessage());
						results.offer(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
								"Database error occured", true));
					}
				}
			}
		} catch (NotAuthorizedException ex) {
			log.log(Level.WARNING, "NotAuthorizedException for packet: {0}", packet);
			results.offer(Authorization.NOT_AUTHORIZED.getResponseMessage(packet,
					"You must authorize session first.", true));
		}
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String[][] supElementNamePaths() {
		return ELEMENT_PATHS;
		
	}

	@Override
	public String[] supNamespaces() {
		return XMLNSS;
	}

	@Override
	public Element[] supDiscoFeatures(final XMPPResourceConnection session) {
		return DISCO_FEATURES;
	}

	@Override
	public Set<StanzaType> supTypes() {
		return TYPES;
	}
	

	private boolean getAutoSave(final XMPPResourceConnection session)
					throws NotAuthorizedException {
		StoreMethod requiredStoreMethod = getRequiredStoreMethod(session);

		if (requiredStoreMethod != StoreMethod.False)
			return true;
		
		Boolean auto = (Boolean) session.getCommonSessionData(ID + "/" + AUTO);

		if (auto == null) {
			try {
				String data = session.getData(SETTINGS, AUTO, "false");

				auto = Boolean.parseBoolean(data);
				
				// if message archive is enabled but it is not allowed for domain
				// then we should disable it
				if (!VHostItemHelper.isEnabled(session.getDomain()) && auto) {
					auto = false;
					session.setData(SETTINGS, AUTO, String.valueOf(auto));
				}
				
				session.putCommonSessionData(ID + "/" + AUTO, auto);
			} catch (TigaseDBException ex) {
				log.log(Level.WARNING, "Error getting Message Archive state: {0}", ex
						.getMessage());
				auto = false;
			}
		}

		return auto;
	}

	private StoreMethod getRequiredStoreMethod(XMPPResourceConnection session) {
		return StoreMethod.valueof(VHostItemHelper.getRequiredStoreMethod(session.getDomain(), globalRequiredStoreMethod.toString()));
	}
	
	private StoreMethod getStoreMethod(XMPPResourceConnection session) 
					throws NotAuthorizedException {
		StoreMethod save = (StoreMethod) session.getCommonSessionData(ID + "/" + DEFAULT_SAVE);
		
		if (save == null) {
			try {
				String data = session.getData(SETTINGS, DEFAULT_SAVE, null);
				if (data == null) {
					data = VHostItemHelper.getDefaultStoreMethod(session.getDomain(), globalDefaultStoreMethod.toString());
				}

				save = StoreMethod.valueof(data);
				session.putCommonSessionData(ID + "/" + DEFAULT_SAVE, save);
			} catch (TigaseDBException ex) {
				log.log(Level.WARNING, "Error getting Message Archive state: {0}", ex
						.getMessage());
				save = StoreMethod.False;
			}		
			
			StoreMethod requiredStoreMethod = getRequiredStoreMethod(session);
			if (save.ordinal() < requiredStoreMethod.ordinal()) {
				save = requiredStoreMethod;
				session.putCommonSessionData(ID + "/" + DEFAULT_SAVE, save);
				try {
					setStoreMethod(session, save);
				} catch (TigaseDBException ex) {
					log.log(Level.WARNING, "Error updating message archiving level to required level {0}", ex.getMessage());
				}
			}
		}
		
		return save;
	}

	public void setAutoSave(XMPPResourceConnection session, Boolean auto)
					throws NotAuthorizedException, TigaseDBException {
		session.setData(SETTINGS, AUTO, String.valueOf(auto));
		session.putCommonSessionData(ID + "/" + AUTO, auto);
	}
	
	public void setStoreMethod(XMPPResourceConnection session, StoreMethod save) 
					throws NotAuthorizedException, TigaseDBException {
		session.setData(SETTINGS, DEFAULT_SAVE, (save == null ? StoreMethod.False : save).toString());
		session.putCommonSessionData(ID + "/" + DEFAULT_SAVE, save);
	}
}
package tigase.archive;

import tigase.xml.Element;

/**
 * Class description
 * @version        Enter version here..., 13/02/16
 * @author         Enter your name here...
 */
public class RSM {
	protected static final String XMLNS           = "http://jabber.org/protocol/rsm";
	private static final String[] SET_AFTER_PATH  = { "set", "after" };
	private static final String[] SET_BEFORE_PATH = { "set", "before" };
	private static final String[] SET_INDEX_PATH = { "set", "index" };

	String after  = null;
	String before = null;
	boolean hasBefore = false;
	Integer count = null;
	String first  = null;
	String last   = null;
	int max = 100;
	Integer index = null;

	/**
	 * @param e
	 */
	public RSM(int defaultMax) {
		this.max = defaultMax;
	}
	
	public RSM() {
	}
	

	/**
	 * Method description
	 * @return
	 */
	public int getMax() {
		return max;
	}

	/**
	 * Method description
	 * @return
	 */
	public Integer getIndex() {
		return index;
	}
	
	/**
	 * Method description
	 * @return
	 */
	public String getAfter() {
		return after;
	}

	/**
	 * Method description
	 * @return
	 */
	public String getBefore() {
		return before;
	}

	public boolean hasBefore() {
		return hasBefore;
	}
	
	public Integer getCount() {
		return count;
	}

	public void setFirst(String first) {
		this.first = first;
	}
	
	public void setLast(String last) {
		this.last = last;
	}

	public void setIndex(Integer index) {
		this.index = index;
	}
	
	/**
	 * Method description
	 * @param count
	 * @param first
	 * @param last
	 */
	public void setResults(Integer count, String first, String last) {
		this.count = count;
		this.first = first;
		this.last  = last;
		this.index = null;
	}

	/**
	 * Set count and index of first result
	 * @param count
	 * @param index 
	 */
	public void setResults(Integer count, Integer index) {
		this.count = count;
		this.index = index;
		this.first = null;
		this.last = null;
	}

	public RSM fromElement(Element e) {
		if (e == null) {
			return this;
		}
		if (e.getXMLNS() != XMLNS) {
			Element x = e.getChild("set", RSM.XMLNS);
			return fromElement(x);
		}

		Element param = e.getChild("max");

		if (param != null) {
			max = Integer.parseInt(param.getCData());
		}
		after  = e.getCDataStaticStr(SET_AFTER_PATH);
		Element beforeEl = e.findChildStaticStr(SET_BEFORE_PATH);
		if (beforeEl != null) {
			hasBefore = true;
			before = beforeEl.getCData();
		}
		String indexStr = e.getCDataStaticStr(SET_INDEX_PATH);
		if (indexStr != null) {
			index = Integer.parseInt(indexStr);
		}
		
		return this;
	}
	
	/**
	 * Method description
	 * @return
	 */
	public Element toElement() {
		Element set = new Element("set");
		set.setXMLNS(XMLNS);
		if ((first != null) && (last != null) || count != null) {
			if (first != null) {
				Element firstEl = new Element("first", first.toString());
				set.addChild(firstEl);
				if (index != null) {
					firstEl.setAttribute("index", index.toString());
				}
			}
			if (last != null) {
				set.addChild(new Element("last", last.toString()));
			}
			if (count != null) {
				set.addChild(new Element("count", count.toString()));
			}
		} else {
			set.addChild(new Element("max", String.valueOf(max)));
			if (after != null) {
				set.addChild(new Element("after", after));
			}
		}

		return set;
	}

	/**
	 * Method description
	 * @param e
	 * @return
	 */
	public static RSM parseRootElement(Element e, int defaultMax) {
		return new RSM(defaultMax).fromElement(e);
	}
	
	/**
	 * Method description
	 * @param e
	 * @return
	 */
	public static RSM parseRootElement(Element e) {
		return RSM.parseRootElement(e, 100);
	}	
}
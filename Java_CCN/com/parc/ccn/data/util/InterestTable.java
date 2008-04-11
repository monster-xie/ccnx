package com.parc.ccn.data.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import com.parc.ccn.data.CompleteName;
import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.query.Interest;

/**
 * Table of Interests, holding an arbitrary value for any  
 * Interest or ContentName.  This is conceptually like a Map<Interest, V> except it supports
 * duplicate entries and has operations for access based on CCN 
 * matching.  An InterestTable may be used to hold real Interests, or merely 
 * ContentNames only, though mixing the two in the same instance of InterestTable
 * is not recommended.
 * @author jthornto
 *
 */

public class InterestTable<V> {

	public interface Entry<T> {
		/**
		 * Get the ContentName of this entry.  All table entries have non-null
		 * ContentName.
		 * @return
		 */
		public ContentName name();
		/**
		 * Get the Interest of this entry.  If a name is entered in the table
		 * then the Interest will be null.
		 * @return Interest if present, null otherwise
		 */
		public Interest interest();
		/**
		 * Get the value of this entry.  A value may be null.
		 * @return
		 */
		public T value();
	}

	protected SortedMap<ContentName,List<Holder<V>>> _contents = new TreeMap<ContentName,List<Holder<V>>>();

	
	protected abstract class Holder<T> implements Entry<T> {
		protected T value;
		public Holder(T v) {
			value= v;
		}
		public T value() {
			return value;
		}
	}
	protected class NameHolder<T> extends Holder<T> {
		protected ContentName name;
		public NameHolder(ContentName n, T v) {
			super(v);
			name = n;
		}
		public ContentName name() {
			return name;
		}
		public Interest interest() {
			return null;
		}
	}
	protected class InterestHolder<T> extends Holder<T> {
		protected Interest interest;
		public InterestHolder(Interest i, T v) {
			super(v);
			interest = i;
		}
		public ContentName name() {
			return interest.name();
		}
		public Interest interest() {
			return interest;
		}
	}
	
	public void add(Interest interest, V value) {
		if (null == interest) {
			throw new NullPointerException("InterestTable may not contain null Interest");
		}
		if (null == interest.name()) {
			throw new NullPointerException("InterestTable may not contain Interest with null name");
		}
		Holder<V> holder = new InterestHolder<V>(interest, value);
		add(holder);
	}
	
	public void add(ContentName name, V value) {
		if (null == name) {
			throw new NullPointerException("InterestTable may not contain null name");
		}
		Holder<V> holder = new NameHolder<V>(name, value);
		add(holder);
	}
	
	protected void add(Holder<V> holder) {
		if (_contents.containsKey(holder.name())) {
			_contents.get(holder.name()).add(holder);
		} else {
			ArrayList<Holder<V>> list = new ArrayList<Holder<V>>(1);
			list.add(holder);
			_contents.put(holder.name(), list);
		}	
	}
	
	protected Holder<V> getMatchByName(ContentName name, CompleteName target) {
		List<Holder<V>> list = _contents.get(name);
		if (null != list) {
			for (Iterator<Holder<V>> holdIt = list.iterator(); holdIt.hasNext(); ) {
				Holder<V> holder = holdIt.next();
				if (null != holder.interest()) {
					if (holder.interest().matches(target)) {
						return holder;
					}
				}	
			}
		}
		return null;
	}
	
	protected List<Holder<V>> getAllMatchByName(ContentName name, CompleteName target) {
		List<Holder<V>> matches = new ArrayList<Holder<V>>();
		List<Holder<V>> list = _contents.get(name);
		if (null != list) {
			for (Iterator<Holder<V>> holdIt = list.iterator(); holdIt.hasNext(); ) {
				Holder<V> holder = holdIt.next();
				if (null != holder.interest()) {
					if (holder.interest().matches(target)) {
						matches.add(holder);
					}
				}	
			}
		}
		return matches;
	}

	protected Holder<V> removeMatchByName(ContentName name, CompleteName target) {
		List<Holder<V>> list = _contents.get(name);
		if (null != list) {
			for (Iterator<Holder<V>> holdIt = list.iterator(); holdIt.hasNext(); ) {
				Holder<V> holder = holdIt.next();
				if (null != holder.interest()) {
					if (holder.interest().matches(target)) {
						holdIt.remove();
						if (list.size() == 0) {
							_contents.remove(name);
						}
						return holder;
					}
				}	
			}
		}
		return null;
	}

	/**
	 * Remove first exact match entry (both name and value match).  
	 * @param name
	 * @param value
	 * @return
	 */
	public Entry<V> remove(ContentName name, V value) {
		Holder<V> result = null;
		List<Holder<V>> list = _contents.get(name);
		if (null != list) {
			for (Iterator<Holder<V>> holdIt = list.iterator(); holdIt.hasNext(); ) {
				Holder<V> holder = holdIt.next();
				if (null == holder.value()) {
					if (null == value) {
						holdIt.remove();
						result = holder;
					}
				} else {
					if (holder.value().equals(value)) {
						holdIt.remove();
						result = holder;
					}
				}
			}
		}
		return result;
	}
	
	/**
	 * Remove first exact match entry (both interest and value match)
	 * @param interest
	 * @param value
	 * @return
	 */
	public Entry<V> remove(Interest interest, V value) {
		Holder<V> result = null;
		List<Holder<V>> list = _contents.get(interest.name());
		if (null != list) {
			for (Iterator<Holder<V>> holdIt = list.iterator(); holdIt.hasNext(); ) {
				Holder<V> holder = holdIt.next();
				if (interest.equals(holder.interest())) {
					if (null == holder.value()) {
						if (null == value) {
							holdIt.remove();
							result = holder;
						}
					} else {
						if (holder.value().equals(value)) {
							holdIt.remove();
							result = holder;
						}
					}
				}
			}
		}
		return result;
	}
	
	protected List<Holder<V>> removeAllMatchByName(ContentName name, CompleteName target) {
		List<Holder<V>> matches = new ArrayList<Holder<V>>();
		List<Holder<V>> list = _contents.get(name);
		if (null != list) {
			for (Iterator<Holder<V>> holdIt = list.iterator(); holdIt.hasNext(); ) {
				Holder<V> holder = holdIt.next();
				if (null != holder.interest()) {
					if (holder.interest().matches(target)) {
						holdIt.remove();
						matches.add(holder);
					}
				}	
			}
			if (list.size() == 0) {
				_contents.remove(name);
			}
		}
		return matches;
	}

	/**
	 * Get best matching Interest for a CompleteName, where best is defined
	 * as longest ContentName.  Any ContentName entries in the table will be 
	 * ignored by this operation, so the Entry returned will have a 
	 * non-null interest. 
	 * @param target - desired CompleteName
	 * @return Entry of best match if any, null if no match
	 */
	public Entry<V> getMatch(CompleteName target) {
		Entry<V> match = null;
		ContentName headname = new ContentName(target.name(), new byte[] {0} ); // need to include equal item in headMap
	    for (Iterator<ContentName> nameIt = _contents.headMap(headname).keySet().iterator(); nameIt.hasNext();) {
			ContentName name = nameIt.next();
			if (name.isPrefixOf(target.name())) {
				// Name match - is there an interest match here?
				match = getMatchByName(name, target);
			}
	    }
		return match;
	}

	/**
	 * Get all matching Interests for a CompleteName.
	 * Any ContentName entries in the table will be 
	 * ignored by this operation, so every Entry returned will have a 
	 * non-null interest. 
	 * @param target - desired CompleteName
	 * @return List of matches, empty if no match
	 */
	public List<Entry<V>> getMatches(CompleteName target) {
		List<Entry<V>> matches = new ArrayList<Entry<V>>();
		ContentName headname = new ContentName(target.name(), new byte[] {0} ); // need to include equal item in headMap
	    for (Iterator<ContentName> nameIt = _contents.headMap(headname).keySet().iterator(); nameIt.hasNext();) {
			ContentName name = nameIt.next();
			if (name.isPrefixOf(target.name())) {
				// Name match - is there an interest match here?
				matches.addAll(getAllMatchByName(name, target));
			}
	    }
	    return matches;
	}
	
	/**
	 * Get all matching entries for a ContentName.
	 * This will return a mix of ContentName and Interest entries if they exist
	 * (and match) in the table, i.e. the Interest of an Entry may be null in some cases.
	 * @param target desired ContentName
	 * @return List of matches, empty if no match
	 */
	public List<Entry<V>> getMatches(ContentName target) {
		List<Entry<V>> matches = new ArrayList<Entry<V>>();
		ContentName headname = new ContentName(target, new byte[] {0} ); // need to include equal item in headMap
	    for (Iterator<ContentName> nameIt = _contents.headMap(headname).keySet().iterator(); nameIt.hasNext();) {
			ContentName name = nameIt.next();
			if (name.isPrefixOf(target)) {
				matches.addAll(_contents.get(name));
			}
	    }
	    return matches;
	}

	/**
	 * Remove and return the best matching Interest for a CompleteName, where best is defined
	 * as longest ContentName.  Any ContentName entries in the table will be 
	 * ignored by this operation, so the Entry returned will have a 
	 * non-null interest. 
	 * @param target - desired CompleteName
	 * @return Entry of best match if any, null if no match
	 */
	public Entry<V> removeMatch(CompleteName target) {
		Entry<V> match = null;
		ContentName matchName = null;
		ContentName headname = new ContentName(target.name(), new byte[] {0} ); // need to include equal item in headMap
	    for (Iterator<ContentName> nameIt = _contents.headMap(headname).keySet().iterator(); nameIt.hasNext();) {
			ContentName name = nameIt.next();
			if (name.isPrefixOf(target.name())) {
				// Name match - is there an interest match here?
				match = getMatchByName(name, target);
				matchName = name;
				// Do not remove here -- need to find best match and avoid disturbing iterator
			}
	    }
	    if (null != match) {
	    	return removeMatchByName(matchName, target);
	    }
		return match;
	}
	
	/**
	 * Remove and return all matching Interests for a CompleteName.
	 * Any ContentName entries in the table will be 
	 * ignored by this operation, so every Entry returned will have a 
	 * non-null interest. 
	 * @param target - desired CompleteName
	 * @return List of matches, empty if no match
	 */
	public List<Entry<V>> removeMatches(CompleteName target) {
		List<Entry<V>> matches = new ArrayList<Entry<V>>();
		List<ContentName> names = new ArrayList<ContentName>();
		ContentName headname = new ContentName(target.name(), new byte[] {0} ); // need to include equal item in headMap
	    for (Iterator<ContentName> nameIt = _contents.headMap(headname).keySet().iterator(); nameIt.hasNext();) {
			ContentName name = nameIt.next();
			if (name.isPrefixOf(target.name())) {
				// Name match - is there an interest match here?
				matches.addAll(getAllMatchByName(name, target));
				names.add(name);
			}
	    }
	    if (matches.size() != 0) {
	    	for (ContentName contentName : names) {
		    	removeAllMatchByName(contentName, target);				
			}
	    }
	    return matches;
	}
	
	/**
	 * Get the number of distinct entries in the table.  Note that duplicate entries
	 * are fully supported, so the number of entries may be much larger than the 
	 * number of ContentNames (sizeNames()).
	 * @return
	 */
	public int size() {
		int result = 0;
	    for (Iterator<ContentName> nameIt = _contents.keySet().iterator(); nameIt.hasNext();) {
			ContentName name = nameIt.next();
			List<Holder<V>> list = _contents.get(name);
			result += list.size();
	    }
	    return result;
	}
	
	/**
	 * Get the number of distinct ContentNames in the table.  Note that duplicate
	 * entries are fully supported, so the number of ContentNames may be much smaller
	 * than the number of entries (size()).
	 * @return
	 */
	public int sizeNames() {
		return _contents.size();
	}

}

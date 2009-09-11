/**
 * A CCNx library test.
 *
 * Copyright (C) 2008, 2009 Palo Alto Research Center, Inc.
 *
 * This work is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation. 
 * This work is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details. You should have received a copy of the GNU General Public
 * License along with this program; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 */

package org.ccnx.ccn.test.impl;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Queue;

import junit.framework.Assert;

import org.ccnx.ccn.config.ConfigurationException;
import org.ccnx.ccn.impl.CCNFlowControl;
import org.ccnx.ccn.io.CCNReader;
import org.ccnx.ccn.profiles.SegmentationProfile;
import org.ccnx.ccn.profiles.VersioningProfile;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Interest;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.protocol.Signature;
import org.ccnx.ccn.protocol.SignedInfo;
import org.ccnx.ccn.test.CCNLibraryTestHarness;
import org.junit.Before;
import org.junit.Test;


/**
 * 
 * @author rasmusse
 *
 */

public class CCNFlowControlTest {
	static private CCNLibraryTestHarness _library ;
	static private CCNReader _reader;
	static ContentName name1;
	static ContentName v1;
	static ContentName v2;	

	static {
		try {
			_library = new CCNLibraryTestHarness();
			_reader = new CCNReader(_library);
			
			name1 = ContentName.fromNative("/foo/bar");
			v1 = VersioningProfile.addVersion(name1);
			// JDT TODO -- sleep is needed because no easy way yet to generate 
			// separate version numbers if generating names fast.
			Thread.sleep(2);
			v2 = VersioningProfile.addVersion(name1);	

		} catch (ConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (MalformedContentNameStringException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// no-op
		}
	}
	
	@Before
	public void setUp() throws Exception {
		fc = new CCNFlowControl(_library);
	}
	
	
	ContentObject obj1 = new ContentObject(name1, new SignedInfo(), "test".getBytes(), (Signature)null);
	ContentName v1s1 = SegmentationProfile.segmentName(v1, 1);
	ContentObject objv1s1 = new ContentObject(v1s1, new SignedInfo(), "v1s1".getBytes(), (Signature)null);		
	ContentName v1s2 = SegmentationProfile.segmentName(v1, 2);
	ContentObject objv1s2 = new ContentObject(v1s2, new SignedInfo(), "v1s2".getBytes(), (Signature)null);	
	ContentName v1s3 = SegmentationProfile.segmentName(v1, 3);
	ContentObject objv1s3 = new ContentObject(v1s3, new SignedInfo(), "v1s3".getBytes(), (Signature)null);	
	ContentName v1s4 = SegmentationProfile.segmentName(v1, 4);
	ContentObject objv1s4 = new ContentObject(v1s4, new SignedInfo(), "v1s4".getBytes(), (Signature)null);
	Queue<ContentObject> queue = _library.getOutputQueue();
	ArrayList<Interest> interestList = new ArrayList<Interest>();
	CCNFlowControl fc = null;

	@Test
	public void testBasicControlFlow() throws Throwable {	
		
		System.out.println("Testing basic control flow functionality and errors");
		_library.reset();
		try {
			fc.put(obj1);
			Assert.fail("Put with no namespace succeeded");
		} catch (IOException e) {}
		fc.addNameSpace("/bar");
		try {
			fc.put(obj1);
			Assert.fail("Put with bad namespace succeeded");
		} catch (IOException e) {}
		fc.addNameSpace("/foo");
		try {
			fc.put(obj1);
		} catch (IOException e) {
			Assert.fail("Put with good namespace failed");
		}
		
	}
	
	@Test
	public void testInterestFirst() throws Throwable {	
		
		normalReset(name1);
		System.out.println("Testing interest arrives before a put");
		interestList.add(new Interest("/bar"));
		fc.handleInterests(interestList);
		fc.put(obj1);
		Assert.assertTrue(queue.poll() == null);
		interestList.add(new Interest("/foo"));
		fc.handleInterests(interestList);
		fc.put(obj1);
		testExpected(queue.poll(), obj1);
	}
	
	@Test
	public void testNextBeforePut() throws Throwable {	

		System.out.println("Testing \"next\" interest arrives before a put");
		normalReset(name1);
		interestList.add(Interest.next(v1s2));
		fc.handleInterests(interestList);
		fc.put(objv1s1);
		Assert.assertTrue(queue.poll() == null);
		fc.put(objv1s3);
		testExpected(queue.poll(), objv1s3);
		
	}
	
	@Test
	public void testLastBeforePut() throws Throwable {	

		System.out.println("Testing \"last\" interest arrives before a put");
		normalReset(name1);
		interestList.add(Interest.last(v1s2));
		fc.handleInterests(interestList);
		fc.put(objv1s1);
		Assert.assertTrue(queue.poll() == null);
		fc.put(objv1s3);
		testExpected(queue.poll(), objv1s3);
		
	}
	
	@Test
	public void testPutsOrdered() throws Throwable {	

		System.out.println("Testing puts output in correct order");
		normalReset(name1);
		interestList.add(new Interest("/foo"));
		fc.handleInterests(interestList);
		fc.put(obj1);
		testExpected(queue.poll(), obj1);
		
	} 
	
	@Test
	public void testRandomOrderPuts() throws Throwable {	

		normalReset(name1);
		
		// Put these in slightly random order. It would be nice to truly randomize this but am
		// not going to bother with that right now.
		fc.put(objv1s4);
		fc.put(objv1s1);
		fc.put(objv1s2);
		fc.put(objv1s3);
		ContentObject co = testExpected(_library.get(v1, 0), objv1s1);
		co = testNext(co, objv1s2);
		co = testNext(co, objv1s3);
		co = testNext(co, objv1s4);
		
	}

	/**
	 * Test method for an order case that failed in practice for 
	 * RepoIOTest when matching was broken.
	 * @throws Throwable
	 */
	@Test
	public void testMixedOrderInterestPut() throws Throwable {	

		normalReset(name1);
		
		// First one normal order exchange: put first, interest next
		fc.put(objv1s1);
		ContentObject co = testExpected(_library.get(v1, 0), objv1s1);

		// Next we get the interest for the next segment before the data
		interestList.add(Interest.next(co,3));
		fc.handleInterests(interestList);

		// Data arrives for the waiting interest, should be sent out
		fc.put(objv1s2);
		testExpected(queue.poll(), objv1s2);

		// Remainder in order, puts first
		fc.put(objv1s3);
		co = testNext(co, objv1s3);
		fc.put(objv1s4);
		co = testNext(co, objv1s4);
	}

	@Test
	public void testWaitForPutDrain() throws Throwable {	

		normalReset(name1);
		fc.put(objv1s2);
		fc.put(objv1s4);
		fc.put(objv1s1);
		fc.put(objv1s3);
		testLast(objv1s1, objv1s4);
		testLast(objv1s1, objv1s3);
		testLast(objv1s1, objv1s2);
		_library.get(new Interest(v1s1), 0);
		
		System.out.println("Testing \"waitForPutDrain\"");
		try {
			fc.waitForPutDrain();
		} catch (IOException ioe) {
			Assert.fail("WaitforPutDrain threw unexpecxted exception");
		}
		fc.put(obj1);
		try {
			fc.waitForPutDrain();
			Assert.fail("WaitforPutDrain succeeded when it should have failed");
		} catch (IOException ioe) {}
	}
	
	@Test
	public void testHighwaterWait() throws Throwable {
		
		// Test that put over highwater fails with nothing draining
		// the buffer
		normalReset(name1);
		fc.setHighwater(4);
		fc.put(objv1s1);
		fc.put(objv1s2);
		fc.put(objv1s3);
		try {
			fc.put(objv1s4);
			Assert.fail("Put over highwater mark succeeded");
		} catch (IOException ioe) {}
		
		// Test that put over highwater succeeds when buffer is
		// drained
		normalReset(name1);
		fc.setHighwater(4);
		fc.setHighwater(4);
		fc.put(objv1s1);
		fc.put(objv1s2);
		fc.put(objv1s3);

		HighWaterHelper hwh = new HighWaterHelper();
		hwh.start();
		fc.put(objv1s4);
	}
	
	public class HighWaterHelper extends Thread {

		public void run() {
			synchronized (this) {
				try {
					Thread.sleep(500);
					_library.get(objv1s1.name(), 0);
				} catch (Exception e) {
					Assert.fail("Caught exception: " + e.getMessage());
				}
			}
		}
		
	}
	
	private void normalReset(ContentName n) throws IOException {
		_library.reset();
		interestList.clear();
		fc = new CCNFlowControl(n, _library);
	}
	
	private ContentObject testNext(ContentObject co, ContentObject expected) throws InvalidParameterException, IOException {
		co = _reader.getNext(co.name(), 3, 0);
		return testExpected(co, expected);
	}
	
	private void testLast(ContentObject co, ContentObject expected) throws InvalidParameterException, IOException {
		co = _reader.getLatest(co.name(), 3, 0);
		testExpected(co, expected);
	}
	
	private ContentObject testExpected(ContentObject co, ContentObject expected) {
		Assert.assertTrue(co != null);
		Assert.assertEquals(co, expected);
		return co;
	}
}
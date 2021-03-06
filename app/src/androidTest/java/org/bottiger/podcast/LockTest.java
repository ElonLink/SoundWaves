/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bottiger.podcast;


import junit.framework.TestCase;

import org.bottiger.podcast.utils.LockHandler;


public class LockTest extends TestCase {

    public void testLock() throws Exception {
    	LockHandler lock = new LockHandler();
    	boolean rc;

    	rc = lock.locked();
        assertTrue(rc);
        assertTrue(lock.getStatus());
        lock.release();
    }

    public void testRelease() throws Exception {
    	LockHandler lock = new LockHandler();;
    	boolean rc;
    	rc = lock.locked();
        assertTrue(lock.getStatus());
        assertTrue(rc);        
        lock.release();
        assertTrue(lock.getStatus()==false);
     }
    
    public void testLockFail() throws Exception {
    	LockHandler lock = new LockHandler();;
    	boolean rc;
    	rc = lock.locked();
        assertTrue(lock.getStatus());
        assertTrue(rc);
        rc = lock.locked();
        assertTrue(lock.getStatus());
        assertTrue(rc==false);
        lock.release();
        assertTrue(lock.getStatus()==false);
        
    }    
}

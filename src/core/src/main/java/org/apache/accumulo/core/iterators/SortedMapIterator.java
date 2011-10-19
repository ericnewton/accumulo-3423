/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.core.iterators;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.accumulo.core.data.ByteSequence;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;

/**
 * A simple iterator over a Java SortedMap
 * 
 * 
 */

public class SortedMapIterator implements InterruptibleIterator {
    private Iterator<Entry<Key,Value>> iter;
    private Entry<Key,Value> entry;
    
    private SortedMap<Key,Value> map;
    private Range range;
    
    private AtomicBoolean interruptFlag;
    private int interruptCheckCount = 0;
    
    public SortedMapIterator deepCopy(IteratorEnvironment env) {
        return new SortedMapIterator(map, interruptFlag);
    }
    
    private SortedMapIterator(SortedMap<Key,Value> map, AtomicBoolean interruptFlag) {
        this.map = map;
        iter = map.entrySet().iterator();
        this.range = new Range();
        if (iter.hasNext()) entry = iter.next();
        else entry = null;
        
        this.interruptFlag = interruptFlag;
    }
    
    public SortedMapIterator(SortedMap<Key,Value> map) {
        this(map, null);
    }
    
    @Override
    public Key getTopKey() {
        return entry.getKey();
    }
    
    @Override
    public Value getTopValue() {
        return entry.getValue();
    }
    
    @Override
    public boolean hasTop() {
        return entry != null;
    }
    
    @Override
    public void next() throws IOException {
        
        if (entry == null) throw new IllegalStateException();
        
        if (interruptFlag != null && interruptCheckCount++ % 100 == 0 && interruptFlag.get()) throw new IterationInterruptedException();
        
        if (iter.hasNext()) {
            entry = iter.next();
            if (range.afterEndKey((Key) entry.getKey())) {
                entry = null;
            }
        } else entry = null;
        
    }
    
    @Override
    public void seek(Range range, Collection<ByteSequence> columnFamilies, boolean inclusive) throws IOException {
        
        if (columnFamilies.size() != 0 || inclusive) {
            throw new IllegalArgumentException("I do not know how to filter column families");
        }
        
        if (interruptFlag != null && interruptFlag.get()) throw new IterationInterruptedException();
        
        this.range = range;
        
        Key key = range.getStartKey();
        if (key == null) {
            key = new Key();
        }
        
        iter = map.tailMap(key).entrySet().iterator();
        if (iter.hasNext()) {
            entry = iter.next();
            if (range.afterEndKey(entry.getKey())) {
                entry = null;
            }
        } else entry = null;
        
        while (hasTop() && range.beforeStartKey(getTopKey())) {
            next();
        }
    }
    
    public void init(SortedKeyValueIterator<Key,Value> source, Map<String,String> options, IteratorEnvironment env) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void setInterruptFlag(AtomicBoolean flag) {
        this.interruptFlag = flag;
    }
}

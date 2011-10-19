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
package org.apache.accumulo.server.master.state;

import java.util.Iterator;

import org.apache.accumulo.core.Constants;
import org.apache.accumulo.core.data.KeyExtent;
import org.apache.accumulo.core.data.Range;
import org.apache.hadoop.io.Text;

public class RootTabletStateStore extends MetaDataStateStore {
    
    public RootTabletStateStore(CurrentState state) {
        super(state);
    }
    
    @Override
    public Iterator<TabletLocationState> iterator() {
        Range range = new Range(Constants.ROOT_TABLET_EXTENT.getMetadataEntry(), false,
                KeyExtent.getMetadataEntry(new Text(Constants.METADATA_TABLE_ID), null), true);
        return new MetaDataTableScanner(range, state.onlineTabletServers(), state.onlineTables());
    }
    
    @Override
    public String name() {
        return "Non-Root Metadata Tablets";
    }
}

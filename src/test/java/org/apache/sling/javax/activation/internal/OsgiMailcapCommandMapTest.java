/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sling.javax.activation.internal;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.activation.CommandInfo;
import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(FrameworkUtil.class)
public class OsgiMailcapCommandMapTest {

    private OsgiMailcapCommandMap mailcapCommandMap;

    private String mailCapString = "image/*; xv %s\n"
            + "test/subtype ;; x-java-view=Foo; x-java-edit=Bar";

    @Mock
    private Bundle bundle;

    @Before
    public void setup() throws IOException {

        bundle = PowerMockito.mock(Bundle.class);
        mockStatic(FrameworkUtil.class);
        when(FrameworkUtil.getBundle(getClass())).thenReturn(bundle);

        mailcapCommandMap = new OsgiMailcapCommandMap();
        mailcapCommandMap.addMailcapEntries(new ByteArrayInputStream(mailCapString.getBytes()), bundle);
    }

    @Test
    public void testAddMailcap() {
        CommandInfo info = mailcapCommandMap.getCommand("foo/bar", "view");
        assertEquals(null, info);

        mailcapCommandMap.addMailcap("foo/bar ;; x-java-view=Foo; x-java-edit=Bar");

        info = mailcapCommandMap.getCommand("foo/bar", "view");
        assertEquals("view", info.getCommandName());
        assertEquals("Foo", info.getCommandClass());

        info = mailcapCommandMap.getCommand("foo/bar", "edit");
        assertEquals("edit", info.getCommandName());
        assertEquals("Bar", info.getCommandClass());

        // Check previous commands are not deleted
        info = mailcapCommandMap.getCommand("test/subtype", "view");
        assertEquals("Foo", info.getCommandClass());
    }

    @Test
    public void testAddMailcapEntries() throws IOException {
        Bundle bundle2 = Mockito.mock(Bundle.class);
        String mailCap = "foo/bar ;; x-java-view=Foo";
        mailcapCommandMap.addMailcapEntries(new ByteArrayInputStream(mailCap.getBytes()), bundle2);

        //check above command is present along with existing commands
        CommandInfo info = mailcapCommandMap.getCommand("foo/bar", "view");
        assertEquals("view", info.getCommandName());
        assertEquals("Foo", info.getCommandClass());

        info = mailcapCommandMap.getCommand("test/subtype", "view");
        assertEquals("Foo", info.getCommandClass());
    }

    @Test
    public void testRemoveMailcapEntriesForBundle() throws IOException {
        Bundle bundle2 = Mockito.mock(Bundle.class);
        String mailCap = "foo/bar ;; x-java-view=Foo";
        mailcapCommandMap.addMailcapEntries(new ByteArrayInputStream(mailCap.getBytes()), bundle2);

        //check above command is present
        CommandInfo info = mailcapCommandMap.getCommand("foo/bar", "view");
        assertEquals("view", info.getCommandName());
        assertEquals("Foo", info.getCommandClass());

        // Remove bundle mailcap entry
        mailcapCommandMap.removeMailcapEntriesForBundle(bundle2);
        info = mailcapCommandMap.getCommand("foo/bar", "view");
        assertEquals(null, info);
    }

    @Test
    public void testGetPreferredCommands() {
        mailcapCommandMap.addMailcap("foo/bar ;; x-java-view=Foo");
        // fallback entry
        mailcapCommandMap.addMailcap("foo/* ;; x-java-fallback-entry=true; x-java-view=FBFoo; x-java-edit=Bar");

        CommandInfo[] preferredCommands = mailcapCommandMap.getPreferredCommands("foo/bar");
        assertEquals(2, preferredCommands.length);
        assertEquals("view", preferredCommands[0].getCommandName());
        assertEquals("Foo", preferredCommands[0].getCommandClass());

        // command from fallback entry
        assertEquals("edit", preferredCommands[1].getCommandName());
        assertEquals("Bar", preferredCommands[1].getCommandClass());
    }

    @Test
    public void testGetAllCommands() {
        mailcapCommandMap.addMailcap("foo/bar ;; x-java-view=Foo");
        // fallback entry
        mailcapCommandMap.addMailcap("foo/* ;; x-java-fallback-entry=true; x-java-view=FBFoo; x-java-edit=Bar");

        CommandInfo[] commands = mailcapCommandMap.getAllCommands("foo/bar");
        assertEquals(3, commands.length);
        assertEquals("view", commands[0].getCommandName());
        assertEquals("Foo", commands[0].getCommandClass());

        // command from fallback entry
        assertEquals("view", commands[1].getCommandName());
        assertEquals("FBFoo", commands[1].getCommandClass());
        assertEquals("edit", commands[2].getCommandName());
        assertEquals("Bar", commands[2].getCommandClass());
    }

    @Test
    public void testGetCommand() {
        mailcapCommandMap.addMailcap("foo/bar ;; x-java-view=Foo");
        // fallback entry
        mailcapCommandMap.addMailcap("foo/* ;; x-java-fallback-entry=true; x-java-edit=Bar");

        CommandInfo info = mailcapCommandMap.getCommand("foo/bar", "view");
        assertEquals("view", info.getCommandName());
        assertEquals("Foo", info.getCommandClass());

        // command from fallback entry
        info = mailcapCommandMap.getCommand("foo/bar", "edit");
        assertEquals("edit", info.getCommandName());
        assertEquals("Bar", info.getCommandClass());
    }

    @Test
    public void testCreateDataContentHandler() throws ClassNotFoundException {
        doReturn(MockDataContentHandler.class).when(bundle).loadClass("Foo");

        mailcapCommandMap.addMailcap("foo/bar ;; x-java-content-handler=Foo");
        DataContentHandler dch = mailcapCommandMap.createDataContentHandler("foo/bar");

        assertTrue(dch instanceof MockDataContentHandler);
        verify(bundle, times(1)).loadClass("Foo");
    }

    @Test
    public void testGetMimeTypes() {
        mailcapCommandMap.addMailcap("foo/bar ;; x-java-view=Foo");
        // fallback entry
        mailcapCommandMap.addMailcap("foo/* ;; x-java-fallback-entry=true; x-java-edit=Bar");

        Set<String> mimeTypes = new HashSet<>(Arrays.asList(mailcapCommandMap.getMimeTypes()));
        assertEquals(4, mimeTypes.size());

        // newly added mime types
        assertTrue(mimeTypes.contains("foo/bar"));
        assertTrue(mimeTypes.contains("foo/*"));

        // mime types added in setup
        assertTrue(mimeTypes.contains("test/subtype"));
        assertTrue(mimeTypes.contains("image/*"));
    }

    @Test
    public void testGetNativeCommands() {
        mailcapCommandMap.addMailcap("foo/bar; fooNativeCommand %s; x-java-view=Foo");

        String[] nativeCommands = mailcapCommandMap.getNativeCommands("foo/bar");
        assertEquals(1, nativeCommands.length);
        assertEquals("foo/bar; fooNativeCommand %s; x-java-view=Foo", nativeCommands[0]);

        nativeCommands = mailcapCommandMap.getNativeCommands("image/*");
        assertEquals(1, nativeCommands.length);
        assertEquals("image/*; xv %s", nativeCommands[0]);
    }

    static class MockDataContentHandler implements DataContentHandler {

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[0];
        }

        @Override
        public Object getTransferData(DataFlavor df, DataSource ds) throws UnsupportedFlavorException, IOException {
            return null;
        }

        @Override
        public Object getContent(DataSource ds) throws IOException {
            return null;
        }

        @Override
        public void writeTo(Object obj, String mimeType, OutputStream os) throws IOException {
        }

    }

}
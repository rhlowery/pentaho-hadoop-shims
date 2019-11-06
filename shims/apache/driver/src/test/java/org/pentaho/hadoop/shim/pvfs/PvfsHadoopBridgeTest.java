/*******************************************************************************
 *
 * Pentaho Big Data
 *
 * Copyright (C) 2019 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.hadoop.shim.pvfs;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.pentaho.di.connections.ConnectionDetails;
import org.pentaho.di.connections.ConnectionManager;
import org.pentaho.hadoop.shim.pvfs.conf.PvfsConf;

import java.io.File;
import java.io.IOException;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith( MockitoJUnitRunner.class )
public class PvfsHadoopBridgeTest {

  private final Path path = new Path( "pvfs://conn/input" );
  private static final String TMPFILE_CONTENTS = "testString";
  @Mock private PvfsConf.ConfFactory confFactory;
  @Mock private PvfsConf pvfsConf;
  @Mock private ConnectionManager connectionManager;
  @Mock private ConnectionDetails details;

  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private File tempFile;
  private PvfsHadoopBridge bridge;

  @Before
  public void before() throws IOException {
    tempFile = folder.newFile( "pvfs.txt" );
    FileUtils.writeStringToFile( tempFile, TMPFILE_CONTENTS );
    when( confFactory.get( any( ConnectionDetails.class ) ) ).thenReturn( pvfsConf );
    when( pvfsConf.mapPath( any( Path.class ) ) ).thenReturn( new Path( tempFile.toURI() ) );
    when( pvfsConf.supportsConnection() ).thenReturn( true );
    when( pvfsConf.conf( any( Path.class ) ) ).thenReturn( new Configuration() );
    when( connectionManager.getConnectionDetails( anyString() ) ).thenReturn( details );

    bridge = new PvfsHadoopBridge( singletonList( confFactory ), connectionManager );
  }

  @Test
  public void getScheme() {
    assertThat( bridge.getScheme(), equalTo( "pvfs" ) );
  }

  @Test
  public void makeQualified() {
    assertThat( bridge.makeQualified( path ).toUri(),
      equalTo( tempFile.toURI() ) );
  }

  @Test
  public void open() throws IOException {
    byte[] content = new byte[ 10 ];
    try ( FSDataInputStream is = bridge.open( path, 5 ) ) {
      is.readFully( content );
    }
    assertThat( new String( content ), equalTo( TMPFILE_CONTENTS ) );
  }

  @Test
  /**
   * Verifies proxying to the underlying fs implementation works correctly, using
   * the LocalFileSystem impl.
   */
  public void testIOOps() throws IOException {
    Path child = new Path( tempFile.getParent(), "child" );
    Path child2 = new Path( tempFile.getParent(), "child_renamed" );
    assertTrue( bridge.mkdirs( child ) );
    assertTrue( bridge.rename( child, child2 ) );
    assertTrue( new File( child2.toUri().getPath() ).exists() );
    assertTrue( bridge.getFileStatus( child2 ).isDirectory() );

    try ( FSDataOutputStream os = bridge.create( new Path( child2, "file" ) ) ) {
      os.writeChars( TMPFILE_CONTENTS );
    }
    assertTrue( new File( child2.toUri().getPath(), "file" ).exists() );

    FileStatus[] status = bridge.listStatus( child2 );
    assertThat( status.length, equalTo( 1 ) );

    bridge.delete( child2, true );
    assertFalse( new File( child2.toUri().getPath(), "file" ).exists() );
  }

  @Test
  public void setGetWorkingDirectory() {
    Path wd = new Path( folder.getRoot().toURI() );
    bridge.setWorkingDirectory( wd );
    assertThat( bridge.getWorkingDirectory(), equalTo( wd ) );
  }
}

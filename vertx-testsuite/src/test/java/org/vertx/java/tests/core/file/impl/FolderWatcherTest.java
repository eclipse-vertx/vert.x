package org.vertx.java.tests.core.file.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.impl.ChangeListener;
import org.vertx.java.core.file.impl.FolderWatcher;
import org.vertx.java.core.file.impl.FolderWatcher.WatchDirContext;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.framework.TestUtils;

import static org.junit.Assert.*;

/**
 *
 */
public class FolderWatcherTest {

	private static DefaultVertx vertx;
  private File modRoot;
  private WatchService watchService;
  
  @BeforeClass
  public static void oneTimeSetUp() {
  	vertx = new DefaultVertx();
  }

  @AfterClass
  public static void oneTimeTearDown() {
  	vertx.stop();
  }
  
	@Before
	public void setUp() {
    modRoot = new File("test_FolderWatcher");
    modRoot.mkdir();
  }
	
	@After
	public void tearDown() throws Exception {
		if (watchService != null) {
			watchService.close();
		}
		this.watchService = null;
    vertx.fileSystem().deleteSync(modRoot.getAbsolutePath(), true);
	}

	/**
	 * Very simply create it and close.
	 * @TODO didn't find a way to validate that the WatchService thread really has stopped 
	 */
	@Test
	public void testConstructor() throws IOException {
		FolderWatcher w = new FolderWatcher();
		w.close();
	}

	/**
	 * Provide a grace period
	 */
	@Test
	public void testConstructorWithGracePeriod() throws IOException {
		FolderWatcher w = new FolderWatcher(1000);
		w.close();
	}

	/**
	 * Invalid grace period
	 */
	@Test(expected=IllegalArgumentException.class)
	public void testConstructorWith0GracePeriod() throws IOException {
		new FolderWatcher(-1);
	}

	/**
	 * Must provide WatchService
	 */
	@Test(expected=NullPointerException.class)
	public void testNullWatchService() throws IOException {
		new FolderWatcher() {
			@Override
			protected WatchService newWatchService() throws IOException {
				return null;
			}
		};
	}

	/**
	 * Instantiate mock watch service
	 */
	@Test
	public void testMockWatchService() throws IOException {
		FolderWatcher w = newMockedFolderWatcher();
		w.processEvents();
		w.close();
	}

	public MockedFolderWatcher newMockedFolderWatcher() throws IOException {
		return new MockedFolderWatcher();
	}
	
	/**
	 * Can't poll closed service => Exception
	 * @throws IOException
	 */
	@Test(expected=ClosedWatchServiceException.class)
	public void testClosed() throws IOException {
		FolderWatcher w = newMockedFolderWatcher();
		w.close();
		w.processEvents();
	}

	@Test
  public void testCreate2FilesNonRecursive() throws Exception {
		MockedFolderWatcher w = newMockedFolderWatcher();
    String modName = "my-mod";
    File modDir = createDirectory(modRoot, modName);
    w.register(modDir.toPath(), false);

    // Create 2 files in the same directory
    createFile(modDir, "foo.js");
    createFile(modDir, "blah.txt");
    
    // process the watchservice events
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    // 2 x create and 2 x modify per file
    assertEquals(2, w.res.size());
    assertEquals(4, w.countEvents.get());
    assertEquals(1, w.countDir.get());
    assertEquals(0, w.countGrace.get());
    
    // Move the time forward
    w.millis += FolderWatcher.CHECK_PERIOD;

    // Nothing should happen yet
    w.clearAndProcessEvents();
    
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(0, w.countGrace.get());

    // move/set the time to be greater or equal the grace period
    w.millis = FolderWatcher.GRACE_PERIOD;

    // process again and we should see a grace event
    w.clearAndProcessEvents();
    
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(1, w.countGrace.get());
    
    w.close();
  }

	@Test
  public void testCreate2FilesNonRecursiveAndListener() throws Exception {
		// Some "collectors" to collect the results
		final Map<Path, Kind<?>> res = new HashMap<>();
		final AtomicInteger countEvents = new AtomicInteger();
		final AtomicInteger countDir = new AtomicInteger();
		final AtomicInteger countGrace = new AtomicInteger();
		
		MockedFolderWatcher w = newMockedFolderWatcher();
    String modName = "my-mod";
    File modDir = createDirectory(modRoot, modName);
    w.register(modDir.toPath(), false, new ChangeListener() {

			@Override
			public void onEvent(WatchEvent<Path> event, WatchDirContext wdir) {
				countEvents.incrementAndGet();
				res.put(event.context(), event.kind());
			}

			@Override
			public void onDirectoryChanged(WatchDirContext wdir, long currentMillis) {
				countDir.incrementAndGet();
			}
			
			@Override
			public void onGraceEvent(WatchDirContext wdir) {
				countGrace.incrementAndGet();
			}
			
		});

    // Create 2 files in the same directory
    createFile(modDir, "foo.js");
    createFile(modDir, "blah.txt");
    
    // process the watchservice events
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    // subclass handlers are not invoked if a listener is registered
    // 2 x create and 2 x modify for both files
    assertEquals(2, res.size());
    assertEquals(4, countEvents.get());
    assertEquals(1, countDir.get());
    assertEquals(0, countGrace.get());
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(0, w.countGrace.get());
    
    // Clean up the collectors
    res.clear();
    countEvents.set(0);
    countDir.set(0);
    countGrace.set(0);
    
    // Move the time forward
    w.millis += FolderWatcher.CHECK_PERIOD;

    // Nothing should happen yet
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    assertEquals(0, res.size());
    assertEquals(0, countEvents.get());
    assertEquals(0, countDir.get());
    assertEquals(0, countGrace.get());
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(0, w.countGrace.get());

    // move/set the time to be greater or equal the grace period
    w.millis = FolderWatcher.GRACE_PERIOD;

    // process again and we should see a grace event
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    assertEquals(0, res.size());
    assertEquals(0, countEvents.get());
    assertEquals(0, countDir.get());
    assertEquals(1, countGrace.get());
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(0, w.countGrace.get());
    
    w.close();
  }

	@Test
  public void testCreate1FileNonRecursive() throws Exception {
		MockedFolderWatcher w = newMockedFolderWatcher();
    String modName = "my-mod";
    File modDir = createDirectory(modRoot, modName);
    w.register(modDir.toPath(), false);

    createFile(modDir, "foo.js");
    
    // process the watchservice events
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    // What we expect: 1 x create and 1 x modify for the same file
    assertEquals(1, w.res.size());
    assertEquals(2, w.countEvents.get());
    assertEquals(1, w.countDir.get());
    assertEquals(0, w.countGrace.get());
    
    // Move the time forward
    w.millis += FolderWatcher.CHECK_PERIOD;

    // Nothing should happen yet
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(0, w.countGrace.get());

    // move/set the time to be greater or equal the grace period
    w.millis = FolderWatcher.GRACE_PERIOD;

    // process again and we should see a grace event
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(1, w.countGrace.get());
    
    w.close();
  }

	@Test
  public void testModify1FileNonRecursive() throws Exception {
		MockedFolderWatcher w = newMockedFolderWatcher();
    String modName = "my-mod";
    File modDir = createDirectory(modRoot, modName);
    w.register(modDir.toPath(), false);

    modifyFile(modDir, "foo.js");
    
    // process the watchservice events
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    // What we expect: 1 x create and 1 x modify for the same file
    assertEquals(1, w.res.size());
    assertEquals(2, w.countEvents.get());
    assertEquals(1, w.countDir.get());
    assertEquals(0, w.countGrace.get());
    
    // Move the time forward
    w.millis += FolderWatcher.CHECK_PERIOD;

    // Nothing should happen yet
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(0, w.countGrace.get());

    // move/set the time to be greater or equal the grace period
    w.millis = FolderWatcher.GRACE_PERIOD;

    // process again and we should see a grace event
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(1, w.countGrace.get());
    
    w.close();
  }

	@Test
  public void testDelete1FileNonRecursive() throws Exception {
		MockedFolderWatcher w = newMockedFolderWatcher();
    String modName = "my-mod";
    File modDir = createDirectory(modRoot, modName);
    createFile(modDir, "foo.js");
    
    w.register(modDir.toPath(), false);
    deleteFile(modDir, "foo.js");
    
    // process the watchservice events
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    // What we expect: 1 x modify and 1 x delete
    assertEquals(1, w.res.size());
    assertEquals(2, w.countEvents.get());
    assertEquals(1, w.countDir.get());
    assertEquals(0, w.countGrace.get());
    
    // Move the time forward
    w.millis += FolderWatcher.CHECK_PERIOD;

    // Nothing should happen yet
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(0, w.countGrace.get());

    // move/set the time to be greater or equal the grace period
    w.millis = FolderWatcher.GRACE_PERIOD;

    // process again and we should see a grace event
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(1, w.countGrace.get());
    
    w.close();
  }

	@Test
  public void testCreateDirectory() throws Exception {
		MockedFolderWatcher w = newMockedFolderWatcher();
    String modName = "my-mod";
    File modDir = createDirectory(modRoot, modName);
    w.register(modDir.toPath(), true);

    createDirectory(modDir, "test-1");
    
    // process the watchservice events
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    // What we expect: 1 x create the directory. The parent is not monitored, hence no event.
    assertEquals(1, w.res.size());
    assertEquals(1, w.countEvents.get());
    assertEquals(1, w.countDir.get());
    assertEquals(0, w.countGrace.get());
    
    // Move the time forward
    w.millis += FolderWatcher.CHECK_PERIOD;

    // Nothing should have happened yet
    w.clearAndProcessEvents();
    
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(0, w.countGrace.get());

    // move/set the time to be greater or equal the grace period
    w.millis = FolderWatcher.GRACE_PERIOD;

    // process again and we should see a grace event
    w.clearAndProcessEvents();
    
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(1, w.countGrace.get());
    
    w.close();
  }

	@Test
  public void testCreateFileRecursive() throws Exception {
		MockedFolderWatcher w = newMockedFolderWatcher();
    String modName = "my-mod";
    File modDir = createDirectory(modRoot, modName);
    w.register(modDir.toPath(), true);

    File subdir = createDirectory(modDir, "test-1");
    Thread.sleep(200);
    
    // make sure the subdir gets registered
    w.clearAndProcessEvents();
    
    createFile(subdir, "foo.js");
    Thread.sleep(200);
    
    // process the watchservice events (but don't clear the counters)
    w.processEvents();
    
    // What we expect: the dirs create and modify events
    assertEquals(2, w.res.size());
    assertEquals(4, w.countEvents.get());
    // It's 3 and not 2 because processEvents() is called twice
    assertEquals(3, w.countDir.get());
    assertEquals(0, w.countGrace.get());

    // move/set the time to be greater or equal the grace period
    w.millis = FolderWatcher.GRACE_PERIOD;

    // process again and we should see a grace event
    w.clearAndProcessEvents();
    
    assertEquals(0, w.res.size());
    assertEquals(0, w.countEvents.get());
    assertEquals(0, w.countDir.get());
    assertEquals(1, w.countGrace.get());
    
    w.close();
  }

	/**
	 * Make sure grace events are fired only once
	 * 
	 * @throws Exception
	 */
	@Test
  public void testOneGraceEvent() throws Exception {
		MockedFolderWatcher w = newMockedFolderWatcher();
    String modName = "my-mod";
    File modDir = createDirectory(modRoot, modName);
    w.register(modDir.toPath(), false);

    // Create 2 files in the same directory
    createFile(modDir, "foo.js");
    createFile(modDir, "blah.txt");
    
    // process the watchservice events
    Thread.sleep(100);

    // process the events (we need the timestamp to calculate the delay later on)
    w.clearAndProcessEvents();

    // move/set the time to be greater or equal the grace period
    w.millis = FolderWatcher.GRACE_PERIOD;

    // process again and we should see a grace event
    w.clearAndProcessEvents();
    
    assertEquals(1, w.countGrace.get());

    // No more grace events what so ever
    w.millis += 100;
    w.clearAndProcessEvents();
    assertEquals(0, w.countGrace.get());

    // No more grace events what so ever
    w.millis += FolderWatcher.GRACE_PERIOD;
    w.clearAndProcessEvents();
    assertEquals(0, w.countGrace.get());

    // No more grace events what so ever
    w.millis += 2 * FolderWatcher.GRACE_PERIOD;
    w.clearAndProcessEvents();
    assertEquals(0, w.countGrace.get());
    
    w.close();
	}

	@Test
  public void testUnregister() throws Exception {
		MockedFolderWatcher w = newMockedFolderWatcher();
    String modName = "my-mod";
    File modDir1 = createDirectory(modRoot, modName);
    File dir1 = createDirectory(modDir1, "test-1");
                createDirectory(modDir1, "test-2");
    File dir3 = createDirectory(dir1, "test-1-1");
    File dir5 = createDirectory(modDir1, "test-3");
    
    File modDir2 = createDirectory(modRoot, modName);
                   createDirectory(modDir2, "my-mod-2");

    w.register(modDir1.toPath(), true);
    w.register(modDir2.toPath(), true);

    int rtn = w.unregister(dir3.toPath());
    assertEquals(3, rtn);

    rtn = w.unregister(dir1.toPath());
    assertEquals(0, rtn);

    rtn = w.unregister(modDir1.toPath());
    assertEquals(0, rtn);

    rtn = w.unregister(dir5.toPath());
    assertEquals(1, rtn);
    
    w.close();
	}

	@Test
  public void testUnregisterFromListener() throws Exception {
		final AtomicInteger countEvents = new AtomicInteger();
		
		MockedFolderWatcher w = newMockedFolderWatcher();
    String modName = "my-mod";
    File modDir = createDirectory(modRoot, modName);
    w.register(modDir.toPath(), false, new ChangeListener() {

			@Override
			public void onEvent(WatchEvent<Path> event, WatchDirContext wdir) {
				countEvents.incrementAndGet();

				// unregister monitoring the directory
				cancel();
			}
		});

    createFile(modDir, "foo.js");
    
    // process the watchservice events
    Thread.sleep(100);
    w.clearAndProcessEvents();
    
    assertEquals(2, countEvents.get());
    
    createFile(modDir, "test.txt");
    Thread.sleep(100);
    countEvents.set(0);
    w.clearAndProcessEvents();
    
    // No more events (key was cancelled)
    assertEquals(0, countEvents.get());
    
    w.close();
	}
	
  private void createFile(File dir, String fileName) throws Exception {
  	String content = TestUtils.randomAlphaString(1000);
  	File f = new File(dir, fileName);
    vertx.fileSystem().writeFileSync(f.getAbsolutePath(), new Buffer(content));
  }

  private void modifyFile(File dir, String fileName) throws Exception {
    File f = new File(dir, fileName);
    FileWriter fw = new FileWriter(f, true);
    fw.write(TestUtils.randomAlphaString(500));
    fw.close();
  }

  private void deleteFile(File dir, String fileName) throws Exception {
    File f = new File(dir, fileName);
    f.delete();
  }

  private File createDirectory(File dir, String dirName) throws Exception {
    File f = new File(dir, dirName);
    if (!f.exists()) {
    	if (f.mkdir() == false) {
    		throw new RuntimeException("Unable to create directory");
    	}
    }
    return f;
  }

	public class MockedFolderWatcher extends FolderWatcher {
		
		// Some "collectors" to collect the results
		final Map<Path, Kind<?>> res = new HashMap<>();
		final AtomicInteger countEvents = new AtomicInteger();
		final AtomicInteger countDir = new AtomicInteger();
		final AtomicInteger countGrace = new AtomicInteger();

		// Current time simulator
		long millis = 0;

		public MockedFolderWatcher() throws IOException {
			super();
		}

		@Override
		protected WatchService newWatchService() throws IOException {
	    watchService = super.newWatchService();
			return watchService;
		}
		
		@Override
		protected long currentTimeMillis() {
			return millis;
		}

		@Override
		public void onEvent(WatchEvent<Path> event, WatchDirContext wdir) {
			countEvents.incrementAndGet();
			Path path = wdir.root().dir().resolve(event.context());
			res.put(path, event.kind());
		}

		@Override
		public void onDirectoryChanged(WatchDirContext wdir, long currentMillis) {
			countDir.incrementAndGet();
		}
		
		@Override
		public void onGraceEvent(WatchDirContext wdir) {
			countGrace.incrementAndGet();
		}
		
		public boolean clearAndProcessEvents() {
	    // Clean up the collectors
	    res.clear();
	    countEvents.set(0);
	    countDir.set(0);
	    countGrace.set(0);
	    
			return processEvents();
		}
	}
}

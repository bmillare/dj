import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class setup {
    public static void deleteDirectoryContents(File directory)
	throws IOException {
        File[] files = directory.listFiles();
        if (files == null) {
	    throw new IOException("Error listing files for " + directory);
        }
        for (File file : files) {
	    deleteRecursively(file);
        }
    }
    public static void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
	    deleteDirectoryContents(file);
        }
        if (!file.delete()) {
	    throw new IOException("Failed to delete " + file);
        }
    }    
    public static File createTempDir() {
	int TEMP_DIR_ATTEMPTS = 100;
	File baseDir = new File(System.getProperty("java.io.tmpdir"));
	String baseName = System.currentTimeMillis() + "-";

	for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
	    File tempDir = new File(baseDir, baseName + counter);
	    if (tempDir.mkdir()) {
		return tempDir;
	    }
	}
	throw new IllegalStateException("Failed to create directory within "
					+ TEMP_DIR_ATTEMPTS + " attempts (tried "
					+ baseName + "0 to " + baseName + (TEMP_DIR_ATTEMPTS - 1) + ')');
    }    
    public static void unzip(File f) {
	try {
	    ZipFile zipFile = new ZipFile(f);
	    Enumeration<?> enu = zipFile.entries();
	    while (enu.hasMoreElements()) {
		ZipEntry zipEntry = (ZipEntry) enu.nextElement();

		String name = zipEntry.getName();
		long size = zipEntry.getSize();
		long compressedSize = zipEntry.getCompressedSize();

		File file = new File(f.getParentFile(), name);
		if (name.endsWith("/")) {
		    file.mkdirs();
		    continue;
		}

		File parent = file.getParentFile();
		if (parent != null) {
		    parent.mkdirs();
		}

		BufferedInputStream is = new BufferedInputStream(zipFile.getInputStream(zipEntry));
		BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(file));
		byte[] bytes = new byte[1024];
		int length;
		while ((length = is.read(bytes)) >= 0) {
		    fos.write(bytes, 0, length);
		}
		is.close();
		fos.close();

	    }
	    zipFile.close();
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public static void get_file_from_URL(URL url, File f) throws java.io.IOException {
	BufferedInputStream is = null;
	BufferedOutputStream os = null;
	try {
	    is = new BufferedInputStream(url.openStream());
	    os = new BufferedOutputStream(new FileOutputStream(f));
	    final int BUF_SIZE = 1 << 8;
	    byte[] buffer = new byte[BUF_SIZE];
	    int bytesRead = -1;
	    while((bytesRead = is.read(buffer)) > -1) {
		os.write(buffer, 0, bytesRead);
	    }
	}
	finally {
	    is.close();
	    os.close();
	}
    }

    // Given a bare dj repository, the goal of this function is to
    // populate the directories with essential system
    // dependencies. This is simply clojure.jar in dj/lib. We extract
    // clojure in dj/opt.
    public static void bootstrap (File dj_directory) {
	File opt = new File(dj_directory, "opt");
	opt.mkdirs();
	File f = new File(opt, "clojure.zip");

	// In order to make this function more flexible, we grab the
	// clojure version, and the download url from the
	// dj/etc/dj.conf.default file
	get_file_from_URL(new URL("http://www.awitness.org/wing_photo.zip"), f);
    }

    // The goal of this function is to create a platform independent
    // way to install dj. This will do the absolute minimum to install
    // dj. Currently this means using the snapshot provided by github
    public static void install (File dj_directory) throws java.io.IOException {
	// This code will be available to a large audience, thus I
	// must display the license agreement
	System.out.println("Copyright (c) Brent Millare. All rights reserved. The use and\n" +
			   "distribution terms for this software are covered by the Eclipse Public\n" +
			   "License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can\n" +
			   "be found in the file epl-v10.html at the root of this distribution. By\n" +
			   "using this software in any fashion, you are agreeing to be bound by\n" +
			   "the terms of this license. You must not remove this notice, or any\n" +
			   "other, from this software.");
	// We need a place to safely download the files so they won't overwrite other existing files
	File tmp_dir = createTempDir();

	// We can call the destination file whatever we want, lets name it dj.zip
	File f = new File(tmp_dir, "dj.zip");

	// Download snapshot zip from github, unzip, delete zip
	get_file_from_URL(new URL("http://www.awitness.org/wing_photo.zip"), f);
	unzip(f);
	f.delete();

	// since we created this zip folder, I will assume no one will
	// write to it. Under this assumption, the only directory in
	// the temp directory is the project directory. This is why I
	// can use [0]. I use renameTo to move this directory to the
	// installation directory. Finally I delete to temporary
	// directory to clean up everything we did.
	tmp_dir.listFiles()[0].renameTo(dj_directory);
	tmp_dir.delete();
    }
    public static void main(String[] args) throws java.io.IOException {
	File dj_dir = new File(System.getProperty("user.dir")+"/dj");
	install(dj_dir);
    }
}
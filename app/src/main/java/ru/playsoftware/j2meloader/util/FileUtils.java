/*
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2020 Nikita Shakarun
 * Copyright 2020-2024 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.playsoftware.j2meloader.util;

import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import kotlin.io.FilesKt;
import ru.playsoftware.j2meloader.EmulatorApplication;
import ru.playsoftware.j2meloader.config.Config;

public class FileUtils {
	private static final String TAG = FileUtils.class.getName();
	private static final int BUFFER_SIZE = 8192;
	public static final String ILLEGAL_FILENAME_CHARS = "[/\\\\:*?\"<>|]";

	public static void copyFiles(File src, File dst, FilenameFilter filter) {
		if (!dst.exists() && !dst.mkdirs()) {
			Log.e(TAG, "copyFiles() failed create dir: " + dst);
			return;
		}
		File[] list = src.listFiles(filter);
		if (list == null) {
			return;
		}
		for (File file : list) {
			File to = new File(dst, file.getName());
			if (file.isDirectory()) {
				copyFiles(src, to, filter);
			} else {
				try {
					copyFileUsingChannel(file, to);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void copyFileUsingChannel(File source, File dest) throws IOException {
		try (FileInputStream fis = new FileInputStream(source);
			 FileChannel sourceChannel = fis.getChannel();
			 FileOutputStream fos = new FileOutputStream(dest);
			 FileChannel destChannel = fos.getChannel()) {
			destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
		}
	}

	public static void deleteDirectory(File dir) {
		if (dir.isDirectory()) {
			File[] listFiles = dir.listFiles();
			if (listFiles != null) {
				for (File file : listFiles) {
					deleteDirectory(file);
				}
			}
		}
		if (!dir.delete() && dir.exists()) {
			Log.w(TAG, "Can't delete file: " + dir);
		}
	}

	public static File getFileForUri(Uri uri) throws IOException {
		if (uri == null) {
			throw new IOException("Missing source URI");
		}
		Context context = EmulatorApplication.getInstance();
		if (context == null) {
			throw new IOException("Application context is unavailable");
		}
		if ("file".equals(uri.getScheme())) {
			String path = uri.getPath();
			if (path != null) {
				File file = new File(path);
				if (file.isFile()) {
					return file;
				}
			}
		}

		File tmpDir = new File(context.getCacheDir(), "installer");
		if ((!tmpDir.isDirectory() && !tmpDir.mkdirs()) || !tmpDir.canWrite()) {
			throw new IOException("Can't create writable directory: " + tmpDir);
		}

		File cachedFile = null;
		try (InputStream in = context.getContentResolver().openInputStream(uri)) {
			if (in == null) {
				throw new IOException("Content provider returned no input stream");
			}

			// Read the file signature safely: providers may return fewer than three bytes
			// in the first read, or even return a zero-length read.
			byte[] signature = new byte[3];
			int signatureLength = 0;
			while (signatureLength < signature.length) {
				int count = in.read(signature, signatureLength, signature.length - signatureLength);
				if (count < 0) {
					break;
				}
				if (count == 0) {
					int value = in.read();
					if (value < 0) {
						break;
					}
					signature[signatureLength++] = (byte) value;
				} else {
					signatureLength += count;
				}
			}
			if (signatureLength == 0) {
				throw new IOException("The selected source is empty");
			}

			String suffix;
			if (signatureLength >= 2 && signature[0] == 0x50 && signature[1] == 0x4B) {
				suffix = ".jar";
			} else if (signatureLength == 3
					&& signature[0] == 'K' && signature[1] == 'J' && signature[2] == 'X') {
				suffix = ".kjx";
			} else {
				suffix = ".jad";
			}

			// Unique cache files prevent two ContentProvider imports from overwriting
			// each other's source while an installer is reading it.
			cachedFile = File.createTempFile("uri_source_", suffix, tmpDir);
			try (OutputStream out = new FileOutputStream(cachedFile)) {
				out.write(signature, 0, signatureLength);
				byte[] buffer = new byte[BUFFER_SIZE];
				int count;
				while ((count = in.read(buffer)) != -1) {
					if (count == 0) {
						int value = in.read();
						if (value < 0) {
							break;
						}
						out.write(value);
					} else {
						out.write(buffer, 0, count);
					}
				}
				out.flush();
			}
			return cachedFile;
		} catch (IOException | RuntimeException e) {
			if (cachedFile != null && cachedFile.exists() && !cachedFile.delete()) {
				Log.w(TAG, "Unable to remove incomplete imported source");
			}
			throw e;
		}
	}

	public static byte[] getBytes(File file) throws IOException {
		return FilesKt.readBytes(file);
	}

	public static void clearDirectory(File dir) {
		final File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (file.isDirectory()) {
				deleteDirectory(file);
			} else {
				//noinspection ResultOfMethodCallIgnored
				file.delete();
			}
		}
	}

	public static String getText(String path) {
		try {
			//noinspection CharsetObjectCanBeUsed
			return FilesKt.readText(new File(path), Charset.forName("UTF-8"));
		} catch (Exception e) {
			Log.e(TAG, "getText: " + path, e);
		}
		return "";
	}

	public static boolean initWorkDir(File dir) {
		if ((dir.isDirectory() || dir.mkdirs()) && dir.canWrite()) {
			//noinspection ResultOfMethodCallIgnored
			new File(dir, Config.SHADERS_DIR).mkdir();
			//noinspection ResultOfMethodCallIgnored
			new File(dir, Config.SOUNDBANKS_DIR).mkdir();
			//noinspection ResultOfMethodCallIgnored
			new File(dir, Config.SKINS_DIR).mkdir();
			try {
				//noinspection ResultOfMethodCallIgnored
				new File(dir, MediaStore.MEDIA_IGNORE_FILENAME).createNewFile();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return true;
		}
		return false;
	}
}

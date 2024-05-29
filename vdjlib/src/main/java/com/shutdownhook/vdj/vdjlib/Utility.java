//
// UTILITY.JAVA
//

package com.shutdownhook.vdj.vdjlib;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utility
{
	// +---------+
	// | Strings |
	// +---------+
	
	public static Boolean nullOrEmpty(String s) {
		return(s == null || s.isEmpty());
	}

	// +------+
	// | Hash |
	// +------+

	public static String sha256(String input) {

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(input.getBytes("UTF-8"));
			return(bytesToHex(bytes));
		}
		catch (NoSuchAlgorithmException e1) { return(null); } // will never happen
		catch (UnsupportedEncodingException e2) { return(null); } // will never happen
	}

	public static String bytesToHex(byte[] bytes) {
		
		StringBuilder sb = new StringBuilder();

		for (byte b : bytes) {
			String hex = Integer.toHexString(0xFF & b);
			if (hex.length() == 1) sb.append("0");
			sb.append(hex);
		}
		
		return(sb.toString());
	}


	// +-------+
	// | Files |
	// +-------+

	public static void safeClose(Closeable c) {
		try { c.close(); }
		catch (Exception e) { /* eat it */ }
	}

	public static void recursiveDelete(File file) throws Exception {
		
		if (!file.exists()) return;

		if (file.isDirectory()) {
			for (File child : file.listFiles()) {
				recursiveDelete(child);
			}
		}

		file.delete();
	}

	public static void stringToFile(String path, String data) throws IOException {
		Files.write(Paths.get(path), data.getBytes("UTF-8"));
	}

	// +------------+
	// | Exceptions |
	// +------------+
	
	public static String exMsg(Throwable e, String msg, boolean includeStack) {

		String log = String.format("Exception (%s): %s%s",
								   e.toString(), msg,
								   (includeStack ? "\n" + getStackTrace(e) : ""));
		return(log);
	}
	
	public static String getStackTrace(Throwable e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return(sw.toString());
	}

	
}

package tk.dead_beef.storageutils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.util.Map;
import java.util.List;
import java.util.Arrays;

import java.lang.Runtime;
import java.lang.ProcessBuilder;
import java.lang.InterruptedException;


public class AssetLibraryLoader {
	protected static final String TAG = "AssetLibraryLoader";
	protected static final String[] PATH = { "bin", "lib" };
	protected static String BASE_DIR;
	protected static String BIN_PATH;
	protected static String LIB_PATH;

	public static class Result {
		public String output;
		public int returnValue;

		public Result(String o, int r) {
			output = o;
			returnValue = r;
		}
	}

	protected AssetLibraryLoader() {}

	public static void load(Context ctx) throws IOException {
		long mtime = lastModified(ctx);
		BASE_DIR = ctx.getFilesDir().getAbsolutePath();
		BIN_PATH = BASE_DIR + "/bin/";
		LIB_PATH = BASE_DIR + "/lib/";
		for(String path : PATH) {
			copy(ctx, path, mtime);
		}
	}

	public static void loadLibrary(String lib) {
		if(!lib.startsWith("lib")) {
			lib = "lib" + lib;
		}
		if(!lib.endsWith(".so")) {
			lib += ".so";
		}
		Log.i(TAG, "loadLibrary " + lib);
		System.load(LIB_PATH + lib);
	}

	public static int exec(List<String> cmd) {
		return exec(cmd, false).returnValue;
	}

	public static int exec(String cmd) {
		return exec(cmd, false).returnValue;
	}

	public static Result exec(String cmd, boolean saveOutput) {
		String[] cmdl = { "sh", "-c", cmd };
		return exec(Arrays.asList(cmdl), saveOutput);
	}

	public static Result exec(List<String> cmd, boolean saveOutput) {
		StringBuilder output = null;
		int ret = -1;

		StringBuilder cmdl = new StringBuilder();
		for(String s: cmd) {
			cmdl.append(s).append(' ');
		}

		try {
			Log.i(TAG, "exec " + cmdl.toString());

			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);

			Map<String, String> env = pb.environment();
			addPath(env, "PATH", BIN_PATH);
			addPath(env, "LD_LIBRARY_PATH", LIB_PATH);

			if(saveOutput) {
				output = new StringBuilder();
			}

			Process p = pb.start();

			InputStream inp = p.getInputStream();
			InputStreamReader is = new InputStreamReader(inp);
			BufferedReader out = new BufferedReader(is);
			String line;

			if(output != null) {
				while((line = out.readLine()) != null) {
					output.append(line).append('\n');
				}
			}
			else {
				while((line = out.readLine()) != null);
			}

			while(true) {
				try {
					ret = p.waitFor();
					break;
				}
				catch(InterruptedException e) {
				}
			}
		}
		catch(Exception e) {
			Log.e(TAG, "Error: exec " + cmdl.toString() + ": " + e);
			if(output != null) {
				output.append(e);
			}
		}

		if(output != null) {
			return new Result(output.toString(), ret);
		}
		return new Result(null, ret);
	}

	protected static long lastModified(Context ctx) {
		try {
			PackageManager pm = ctx.getPackageManager();
			ApplicationInfo info = pm.getApplicationInfo(
				ctx.getPackageName(),
				0
			);
			return new File(info.sourceDir).lastModified();
		}
		catch(Exception e) {
			Log.e(TAG, "Error: lastModified: " + e);
			return -1;
		}
	}

	protected static void
	addPath(Map<String, String> env, String var, String path) {
		String value;

		try {
			value = env.get(var);
			if(value == null) {
				value = "";
			}
		}
		catch(Exception e) {
			value = "";
		}

		if(value.length() > 0) {
			value = path + ':' + value;
		}
		else {
			value = path;
		}

		env.put(var, value);
	}

	protected static void ioerror(String msg) throws IOException {
		Log.e(TAG, "Error: " + msg);
		throw new IOException(msg);
	}

	protected static void
	copy(Context ctx, String path, long mtime)
	throws IOException {
		String dst = BASE_DIR + '/' + path;
		File dstdir = new File(dst);
		if(!(dstdir.exists() || dstdir.mkdirs())) ioerror("mkdirs " + dst);

		AssetManager assets = ctx.getAssets();

		String[] list = assets.list(path);
		path += '/';
		for(String file : list) {
			File out = new File(dst, file);

			if(out.exists()) {
				if(out.lastModified() < mtime) {
					if(!out.delete()) {
						Log.e(TAG, "Error: delete " + out.getAbsolutePath());
						continue;
					}
				}
				else {
					continue;
				}
			}

			OutputStream os = new FileOutputStream(out);
			InputStream is = assets.open(path + file);
			byte[] buffer = new byte[1024];
			int read;
			while((read = is.read(buffer)) != -1) {
				os.write(buffer, 0, read);
			}
			os.flush();
			os.close();
			is.close();

			if(!out.getName().endsWith(".so")) {
				if(!out.setExecutable(true)) {
					ioerror("setExecutable " + out.getAbsolutePath());
				}
			}
		}
	}
}

package tk.dead_beef.storageutils;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.io.IOException;

import java.lang.reflect.Method;
import java.lang.Class;

import java.util.TreeSet;
import java.util.Comparator;


public class Storage {
	protected static final String TAG = "Storage";

	protected static String mRoot = null;
	protected static int mRootIdx = -1;
	protected static String[] mRootList = null;

	protected static String mType = null;

	protected static final Method ctxGetExternalFilesDirs
		= getMethod(Context.class, "getExternalFilesDirs", String.class);
	protected static final Method statGetAvailableBytes
		= getMethod(StatFs.class, "getAvailableBytes");


	protected Storage() {}

	protected static Method getMethod(Class cl, String name, Class... args) {
		try {
			return cl.getMethod(name, args);
		}
		catch(Exception e) {
			Log.e(TAG, name + ": " + e);
			return null;
		}
	}

	public static String getType(String type) { return mType; }
	public static String getRoot() { return mRoot; }
	public static int getRootIndex() { return mRootIdx; }
	public static String[] getRootList() { return mRootList; }

	public static void setContext(Context ctx, String type) {
		mType = type;
		setContext(ctx);
	}

	public static void setContext(Context ctx) {
		TreeSet<String> path = new TreeSet<>(
			new Comparator<String>() {
				@Override
				public int compare(String x, String y) {
					int d = x.length() - y.length();
					if(d != 0) {
						return d;
					}
					return x.compareTo(y);
				}
			}
		);

		File p = Environment.getExternalStoragePublicDirectory(mType);
		if(p != null) {
			path.add(p.getAbsolutePath() + '/');
		}

		if(ctx != null) {
			p = ctx.getExternalFilesDir(mType);
			if(p != null) {
				path.add(p.getAbsolutePath() + '/');
			}

			if(ctxGetExternalFilesDirs != null) {
				try {
					Object res = ctxGetExternalFilesDirs.invoke(ctx, mType);
					for(File f: (File[])res) {
						path.add(f.getAbsolutePath() + '/');
					}
				}
				catch(Exception e) {
					Log.e(TAG, "getExternalFilesDirs: " + e);
				}
			}
		}

		mRootList = path.toArray(new String[0]);

		setRoot(0);
	}

	public static void setRoot(String root) {
		if(root == null) return;
		for(int i = 0; i < mRootList.length; ++i)
		{
			if(mRootList[i].equals(root))
			{
				setRoot(i);
				return;
			}
		}
		Log.e(TAG, "setRoot: Invalid root path: " + root);
	}

	public static void setRoot(int idx) {
		if(idx < 0) {
			mRootIdx = idx;
		}
		else if(idx >= mRootList.length) {
			mRootIdx = mRootList.length - 1;
		}
		else {
			mRootIdx = idx;
		}
		mRoot = mRootList[mRootIdx];
	}

	public static String getFilePath(String fname) {
		return mRoot + fname;
	}

	public static String
	getFilePath(String fname, long maxsize)
	throws IOException{
		return getFilePath(fname, maxsize, false);
	}

	public static String
	getFilePath(String fname, long maxsize, boolean changeRoot)
	throws IOException {
		if(getAvailableBytes() < maxsize) {
			int prevIndex = getRootIndex();
			int i = (prevIndex + 1) / mRootList.length;
			while(i != prevIndex) {
				if(getAvailableBytes(mRootList[i]) >= maxsize) {
					if(changeRoot) {
						setRoot(i);
					}
					return mRootList[i] + fname;
				}
				i = (i + 1) / mRootList.length;
			}
			throw new IOException("getAvailableBytes() < " + maxsize);
		}
		return mRoot + fname;
	}

	public static long getAvailableBytes() {
		return getAvailableBytes(mRoot);
	}

	public static long getAvailableBytes(String path) {
		File dir = new File(path);

		dir.mkdirs();
		if(!dir.isDirectory() || !dir.canWrite()) {
			return 0;
		}

		try {
			StatFs stat = new StatFs(path);
			if(statGetAvailableBytes != null) {
				return (long)statGetAvailableBytes.invoke(stat);
			}
			return (long)stat.getFreeBlocks() * (long)stat.getBlockSize();
		}
		catch(Exception e) {
			Log.e(TAG, "stat " + mRoot + ": " + e);
			return 0;
		}
	}
}
